#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
PersonalIME 大规模分层测试集生成器（离线，只读语料 + 词库，不改动 APK）

做什么：
  从真实语料里自动抽 (前文上下文, 目标词) 样本，按「目标词长 × 上下文长度 ×
  语料来源」三层配额输出 bench.tsv。每条样本自带真实上文，而不是人工编的孤立词条。

为什么需要它：
  现有的 corpus.tsv（492 条）是人工挑的，覆盖窄、带主观性，而且**上下文恒为空**。
  真实使用中用户是「一边上屏一边接着打」，同一个数字串里选哪个字常常没有上文就无从判断。
  学术界 PD / P2C 基准（Zhang et al. 2019，ACL 2022 PinyinGPT）之所以是公认口径，
  正是因为它按「上下文长度 0-3 / 4-9 / 10+ 字 × 目标词长 1-3 / 4-9 / 10+ 字」分格统计
  P@1 / P@5 / P@10——缺了上下文维度，就无法解释「这个字它老选错」。

怎么做分词（没有现成分词器）：
  用 base_words.db 本身做最大正向匹配。这正好是输入法自己的词典，
  分词结果天然就是「输入法眼里的一段话」，比引入外部分词器更贴合评测目标。

输出：
  bench.tsv  每行：前文上下文 <TAB> 目标词 <TAB> 语料来源 <TAB> 目标词在句首标记
  bench_meta.json  各格实际条数

用法：
  python build_testset.py
  python build_testset.py --per-cell 400 --out bench.tsv
"""

import argparse
import gzip
import io
import json
import os
import sqlite3
import sys
import tarfile

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
LM_DIR = os.path.join(ROOT, "_tools", "lm")
CORPUS_DIR = os.path.join(LM_DIR, "corpus")
DEFAULT_DB = os.path.join(ROOT, "PersonalIME", "PersonalIME", "app",
                          "src", "main", "assets", "dict", "base_words.db")

CJK_LO, CJK_HI = 0x4E00, 0x9FFF

# 三个来源对应学术界 PD 基准里的「领域」维度：新闻 / 影视字幕（口语） / 百科
SOURCES = [
    ("news", "news300k.tar.gz"),
    ("spoken", "opensub2016.gz"),
    ("wiki", "wikimedia.gz"),
]

# 上下文长度档（单位：前文汉字个数）—— 与 PD 基准的 context length 分档对齐
CTX_BINS = [(0, 0, "ctx0"), (1, 3, "ctx1-3"), (4, 9, "ctx4-9"), (10, 999, "ctx10+")]
CTX_PROBE = 24          # 最多取前文 24 个字作为「已上屏文本」
MIN_WORD_LEN = 2        # 目标词至少 2 字；单字单独一批（见 --include-single）
MAX_WORD_LEN = 9


def is_cjk(ch):
    return CJK_LO <= ord(ch) <= CJK_HI


def ctx_bin(n):
    for lo, hi, name in CTX_BINS:
        if lo <= n <= hi:
            return name
    return CTX_BINS[-1][2]


def word_bin(n):
    if n <= 2:
        return "w1-2"
    if n <= 4:
        return "w3-4"
    if n <= 9:
        return "w5-9"
    return "w10+"


def load_vocab(db_path, max_len=MAX_WORD_LEN):
    """词库本身即为分词词典（最大正向匹配）。"""
    con = sqlite3.connect(db_path)
    vocab = {w for (w,) in con.execute("SELECT word FROM base_words")}
    con.close()
    vocab = {w for w in vocab if 1 <= len(w) <= max_len}
    return vocab


def segment(text, vocab):
    """最大正向匹配分词；未命中就退化为单字（非中文字符亦按单字）。"""
    segs, i, n = [], 0, len(text)
    while i < n:
        end = min(n, i + MAX_WORD_LEN)
        hit = None
        for j in range(end, i, -1):
            if text[i:j] in vocab:
                hit = text[i:j]
                break
        if hit:
            segs.append(hit)
            i += len(hit)
        else:
            segs.append(text[i])
            i += 1
    return segs


INDEX_MARK = ("inv_", "words", ".sql", ".sql")


def looks_like_text(tf, member):
    """读前几 KB，判断这个 member 是不是中文正文（而非索引/词表）。"""
    try:
        with tf.extractfile(member) as f:
            head = f.read(8192).decode("utf-8", errors="ignore")
    except Exception:
        return False
    if not head:
        return False
    cjk = sum(1 for c in head if is_cjk(c))
    return cjk / max(1, len(head)) > 0.05


def pick_member(tf):
    """
    在 tar 里挑**中文正文** member。

    踩过的坑，值得写死在这里：news300k.tar.gz 里排第一的是 `inv_w.txt`（词频倒排索引，
    纯数字三列，112MB），取最大会挑中它；退一步取 members[0] 也是它——于是读到 43 万行
    数字、分词命中 0、样本池安静地保持 0，脚本照常跑完、照常出报告，
    只是 news 那一路一个样本都没有。所以必须**按内容探测**：
    从最大的开始，取第一个「前 8KB 里汉字占比 >5%」的。
    对 news 这个包，真正命中的是 `sentences.txt`（44.5MB 中文句子）。
    """
    files = [m for m in tf.getmembers()
             if m.isfile() and not any(k in m.name for k in INDEX_MARK)]
    for m in sorted(files, key=lambda m: -m.size):
        if looks_like_text(tf, m):
            return m
    return None


def iter_lines(path):
    """tar.gz / .gz 流式逐行读，避免把几百 MB 一次性读进内存。"""
    if path.endswith(".tar.gz"):
        with tarfile.open(path, "r:gz") as tf:
            member = pick_member(tf)
            if member is None:
                return
            with tf.extractfile(member) as f:
                for line in io.TextIOWrapper(f, encoding="utf-8", errors="ignore"):
                    yield line
    else:
        with gzip.open(path, "rt", encoding="utf-8", errors="ignore") as f:
            for line in f:
                yield line


def take_samples(src, max_lines):
    """从一段真实句子里抽出 (前文, 目标词) 样本。"""
    out = []
    segs = segment(src, VOCAB)
    # 只在「中文连续段」内取样：找最长连续 CJK 段
    buf = []
    for s in segs + [""]:
        if s and all(is_cjk(c) for c in s):
            buf.append(s)
            continue
        flush_seg(buf, out)
        buf = [s] if s else []
    for s in buf:
        flush_seg([s], out)
    return out


# segment() 把无法匹配的字符按单字返回，制表符("\t")就是其中之一。
# 它混进上下文里会把 TSV 撑成额外一列：实测 253/8579 行因此变成 6 列，
# 下游按位置取字段全部错位，报的错还是莫名其妙的
# "invalid literal for int(): 'news'"。所以在这里就掐掉。
JUNK_TOKENS = ("\t", "\r", "\n", "\x0b", "\x0c")


def flush_seg(tokens, out):
    """把一个连续中文片段的所有 (前文, 词) 对都收进来（下游按配额挑）。"""
    for i, tok in enumerate(tokens):
        if len(tok) < MIN_WORD_LEN:
            continue
        before = "".join(t for t in tokens[max(0, i - CTX_PROBE):i]
                         if t not in JUNK_TOKENS)
        out.append((before, tok))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--db", default=DEFAULT_DB)
    ap.add_argument("--out", default=os.path.join(os.path.dirname(os.path.abspath(__file__)),
                                                  "bench.tsv"))
    ap.add_argument("--per-cell", type=int, default=260,
                    help="每个 (来源×词长×上下文) 格取多少条")
    ap.add_argument("--include-single", action="store_true",
                    help="把单字也作为目标词纳入（真实输入里打单字占比很高）")
    ap.add_argument("--max-bytes", type=int, default=120 * 1024 * 1024,
                    help="每个来源最多扫多少字节的解压文本（防止跑太久）")
    args = ap.parse_args()

    if not os.path.exists(args.db):
        print(f"[错误] 找不到词库：{args.db}")
        return 1
    if not os.path.isdir(CORPUS_DIR):
        print(f"[错误] 找不到语料目录：{CORPUS_DIR}")
        return 1

    global VOCAB
    VOCAB = load_vocab(args.db)
    print(f"分词词典：词库 {len(VOCAB)} 条（最大词长 {MAX_WORD_LEN}）")

    # pool[src][(wbin, ctxbin)] = [(ctx, word), ...]
    pool = {}
    stats = {}
    for src, fname in SOURCES:
        path = os.path.join(CORPUS_DIR, fname)
        if not os.path.exists(path):
            print(f"  [跳过] 缺语料 {fname}")
            continue
        pool[src] = {}
        stats[src] = {"lines": 0, "chars": 0, "samples": 0}
        budget = args.max_bytes
        for line in iter_lines(path):
            if budget <= 0:
                break
            budget -= len(line.encode("utf-8", errors="ignore"))
            stats[src]["lines"] += 1
            stats[src]["chars"] += len(line)
            for ctx, word in take_samples(line, None):
                if not args.include_single and len(word) < MIN_WORD_LEN:
                    continue
                key = (word_bin(len(word)), ctx_bin(len(ctx)))
                bucket = pool[src].setdefault(key, [])
                if len(bucket) < 4000:      # 每格先攒够候选，再轮转取样，避免全挤在前几句
                    bucket.append((ctx, word))
                    stats[src]["samples"] += 1
        print(f"  {src:<8} 行 {stats[src]['lines']:>7} ｜ 样本池 {stats[src]['samples']:>7}")

    if not pool:
        print("[错误] 没有任何语料可选")
        return 1

    # 轮转取样：每格均匀取，而不是把配额全给语料前几段
    chosen, counts = [], {}
    for src in pool:
        for key, bucket in pool[src].items():
            step = max(1, len(bucket) // (args.per_cell + 1))
            picked = bucket[::step][: args.per_cell]
            if len(picked) < args.per_cell:
                picked += bucket[::-1][: args.per_cell - len(picked)]
            counts[(src, key)] = len(picked)
            for ctx, word in picked:
                chosen.append((ctx, word, src))

    # 打散后输出，避免同一句子的邻近样本连在一起
    import random
    random.Random(20261002).shuffle(chosen)

    out_path = args.out
    with open(out_path, "w", encoding="utf-8") as f:
        f.write("# PersonalIME 大规模分层评测语料（自动生成）\n")
        f.write("# 字段：前文上下文 <TAB> 目标词 <TAB> 语料来源 <TAB> 目标词长 <TAB> 前文长度\n")
        for ctx, word, src in chosen:
            f.write(f"{ctx}\t{word}\t{src}\t{len(word)}\t{len(ctx)}\n")

    meta = {
        "db": args.db,
        "per_cell": args.per_cell,
        "total": len(chosen),
        "by_src": stats,
        "by_cell": {f"{s}|{kb}|{cb}": c for (s, (kb, cb)), c in counts.items()},
    }
    with open(out_path + ".meta.json", "w", encoding="utf-8") as f:
        json.dump(meta, f, ensure_ascii=False, indent=2)

    n_ctx0 = sum(1 for c, w, s in chosen if not c)
    print(f"\n测试集 {out_path}：{len(chosen)} 条")
    print(f"  无上下文（孤立输入）{n_ctx0} 条 ｜ 带上下文 {len(chosen) - n_ctx0} 条")
    for src in pool:
        n_src = sum(1 for c, w, s in chosen if s == src)
        print(f"  {src:<8} {n_src} 条")
    return 0


if __name__ == "__main__":
    sys.exit(main())
