#!/usr/bin/env python
"""
从真实语料抽取**词级 n-gram 证据**，产出新词源 `sources/corpus_words.txt`（按频次降序）。

## 为什么需要它

用户实测反馈「好多了」打不出来（首选被整句拼出的「还是你好多了」占掉）。查词库发现：

    SELECT word FROM base_words WHERE word LIKE '%了'   ->  全库仅 165 条
    「多了」「好了」「我的」「来了」「去了」「看着」  ->  全部不在库

这不是一个词的偶发缺失，而是**一整类功能词缺失**。根因在词源结构：

  - `legacy/word_freq.txt`（5.6 万，"谓现代汉语常用词表"）
    里连「我的 / 这个 / 好了」都没有，倒是有「题库 / 签筒 / 织女」这类偏词，
    且 freq 字段是乱的（题库 39177、辉煌 2666）——它不是按频次排的常用词表。
  - `legacy/cn_words.txt`（40 万）是**词组清单**，格式 `拼音 词 频次`，
    里面只有「我的世界 / 多了去了」这类长串，**没有单独的「我的 / 多了」**。
  - `sources/oral.txt`(595) / `modern.txt`(1044, 偏 IT 技术词) / `structured.txt`(~150)
    都是小规模人工表，覆盖不到功能词。
  - THUOCL 全部十二个领域表都是专业名词（医疗 / 法律 / 地名 / 成语…）。

也就是说：**没有任何一个现有词源收录「X了」「我X」这类虚词/功能词组合**。
于是这些词在词库里拿不到任何语料证据，只能靠 `lambda_oov` 那 0.02 的字符模型兜底，
排序必然垫底，长句路径一介入就被整体带偏。

而这些词在语料里 abundant（`grep` 实测 opensub 字幕 + wiki 百科）：

    好多了 3120 次    我的 322,327 次    好了 98,512 次    多了 17,532 次

所以正确做法是**把语料里本来就有的词证据抽出来**，而不是手工往表里塞词
——后者治标不治本，而且下次还会再缺一批。这也符合本项目「参数/证据从实测数据反推」的纪律。

## 抽词判据

经典 n-gram 抽词：滑窗取 n 元，按频次排序，再用**左右边界信息**滤噪声。
本项目走保守路线（宁可少抽，不引入翻译腔垃圾）：

  1. 只收纯汉字（不含数字/字母/标点）——避免把字幕里的中英混排拽进来；
  2. 频次门槛 MIN_FREQ（默认 40）滤掉偶然连串；
  3. 边界判据：n-gram 的**左字 + 首字**与**末字 + 右字**都不能构成
     一个频次显著更高的更长 n-gram（否则说明它只是某个长串的内部片段，不是独立词）。
     实测这一步能挡掉「的了人」「是好不」这类非词。

输出按频次降序，下游 build_base_words.py 按 Zipf 折减取用（与 word_freq 完全同构）。
"""
from __future__ import annotations

import argparse
import collections
import gzip
import io
import json
import os
import re
import sqlite3
import sys
import tarfile
import time

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
CORPUS = os.path.join(ROOT, "_tools", "lm", "corpus")
OUT_DIR = os.path.join(HERE, "sources")
OUT_TXT = os.path.join(OUT_DIR, "corpus_words.txt")
OUT_META = OUT_TXT + ".meta.json"

# news 语料的成员名里带这些的是**词频倒排索引 / 词表 / SQL 建表语句**，不是正文。
# 踩过的坑：co_s.txt 是 63MB 的倒排索引（纯数字四列），比 44MB 的真实正文
# sentences.txt 还大；照"取最大的文件"挑会读进一堆数字，样本池为 0 且不报错。
INDEX_MARK = ("inv_", "words", ".sql", "_n.", "_s.", "co_")
CJK = re.compile(r"[\u4e00-\u9fff]+")

MIN_FREQ = 40        # 频次门槛：实测「好多了」在全语料 ~3.1k，「多了去了」~65，都远超
MIN_FREQ3 = 100      # 3 字门槛：手写词表回归暴露出「开会了 118 / 发工资 115」这类词
                     # 证据在 100~300 之间，300 的门槛会漏；100 以下开始混入噪声
MIN_FREQ4 = 12       # 4 字门槛：4-gram 空间大、噪声多，门槛要比 3 字高
NGRAMS = (2, 3)      # 2/3 字：全量计数
LONG_SEED = 3        # 4 字候选的"种子"门槛：4-gram w 只有在 w[:3] 与 w[1:] 都是
                     # 频次 ≥3 的 3-gram 时才计数（见 pass2 的"为什么两遍扫描"）
MAX_KEEP2 = 50000    # 2/3/4 字**分池**配额。共用一个 4 万总池的教训：
                     # 2 字词频次≥40 的有 21 万条，把低频 2 字词全放进总池排序，
                     # 会把「差不多了(923 次)」「快好了(426 次)」这类真短语挤出 4 万名开外——
                     # 手写 248 词回归里它们恰好全缺。分池后 3 字短语不受 2 字长尾挤压。
                     # 3 字池 3 万仍不够：频次 115~300 的 3 字词（发工资 115 / 开会了 118 /
                     # 洗澡了 206）排在 3 万名之外（≥300 的就有 3.03 万条）→ 3 字池放大到全收，
                     # 2 字池提到 5 万（覆盖到频次 ~130，救回「跑着 134 / 饺子 207」）。
                     # 体积代价 ~3 MB，验收看 build 产物大小与首屏命中率不跌破。
MAX_KEEP3 = 80000
MAX_KEEP4 = 50000


def open_text_stream(path):
    """
    按扩展名产出行（tar.gz 内部挑最大的正文成员）。

    必须做成生成器（yield）而不是"for 循环 + return 文件句柄"：
    tarfile / gzip 对象在 `with` 块结束时会被 close，若把内层 TextIOWrapper
    直接 return 出去，调用方第一次读就会撞 `I/O operation on closed file`
    ——曾经因此三语料全军覆没、抽到 0 个词，而且只报一行 warn，不崩。
    """
    if not os.path.exists(path):
        return
    if path.endswith(".tar.gz"):
        with tarfile.open(path, "r:gz") as tf:
            members = [m for m in tf.getmembers() if m.isfile()]
            if not members:
                return
            cands = [m for m in members
                     if not any(k in m.name for k in INDEX_MARK)]
            best = None
            for m in (cands or members):
                if m.size < 1024:
                    continue
                with tf.extractfile(m) as fh:
                    head = fh.read(4096)
                if sum(1 for c in CJK.findall(head.decode("utf-8", "ignore"))) < 20:
                    continue          # 不是中文正文（倒排索引/词表）
                if best is None or m.size > best.size:
                    best = m
            if best is None:
                return
            with tf.extractfile(best) as fh:
                for line in io.TextIOWrapper(fh, encoding="utf-8", errors="ignore"):
                    yield line
    elif path.endswith(".gz"):
        with gzip.open(path, "rt", encoding="utf-8", errors="ignore") as fh:
            for line in fh:
                yield line
    else:
        # 纯文本语料（web_corpus.txt 由 fetch_web_corpus.py 抓取生成）
        with open(path, encoding="utf-8", errors="ignore") as fh:
            for line in fh:
                yield line


def add_counts(counter, line):
    """对一行里的每个中文连续段做 2/3-gram 计数（4-gram 走 pass2 的受限统计）。"""
    for seg in CJK.findall(line):
        n = len(seg)
        for size in NGRAMS:
            if n < size:
                continue
            for i in range(n - size + 1):
                counter[size][seg[i:i + size]] += 1


def add_counts_4gram(counter, line, seed3):
    """
    pass2：只统计"左右两段都是高频 3-gram"的 4-gram。

    ## 为什么两遍扫描，而不是直接全量数 4-gram

    全量 4-gram 有 **3000 万种**（3-gram 才 1500 万），dict 装不下；
    而按频次门槛事后过滤也没用——种类数已经炸了，内存先崩。

    ## 为什么这个受限条件能挡住噪声

    看网页语料 4-gram Top60，真词与跨词碎片几乎各一半：
        真词：人工智能(97) 脑机接口(69) 人民日报(66) 具身智能(33) 重要讲话(25)
        碎片：学习进行(54) 近平总(53) 机接口技(19) 国共产党(22)
    区别很干净：**真词的两个 3-gram 重叠段本身也是高频**
        人工智能 = 人工智 + 工智能（都高频）
        学习进行 = 学习进 + 习进行（都不是高频 3-gram，直接不统计）
        近平总   = 近平总 + 平总书（左边就不是高频，连候选都进不了）
    所以"两个重叠 3-gram 都在高频集合里"这一条，就把碎片挡在计数之外——
    不是事后过滤，是**根本不进候选池**，内存与噪声一起解决。
    """
    for seg in CJK.findall(line):
        n = len(seg)
        if n < 4:
            continue
        for i in range(n - 3):
            a = seg[i:i + 3]
            b = seg[i + 1:i + 4]
            if seed3.get(a, 0) >= LONG_SEED and seed3.get(b, 0) >= LONG_SEED:
                counter[seg[i:i + 4]] += 1


def load_phrase_lexicon():
    """
    读 `legacy/cn_words.txt`（`拼音 词 频次`）当**词组词典**。

    用途：滤掉「新冠肺」这种**长词内部片段**。它在语料里出现 4140 次
    （比真词「好多了」的 3142 还多，光靠频次门槛挡不住），但它严格寄生在
    「新冠肺炎」内部；而「我的」虽然也是「我的世界」的前缀，**前缀/后缀要保留**。
    判据：把每个长词（≥5 字）的内部 3 字子串（不在首尾）登记成"非词"。
    """
    path = os.path.join(HERE, "sources", "legacy", "cn_words.txt")
    if not os.path.exists(path):
        return set()
    bad = set()
    for line in open(path, encoding="utf-8", errors="ignore"):
        parts = line.split(" ")
        if len(parts) != 3:
            continue
        w = parts[1]
        if len(w) < 5:
            continue
        for i in range(1, len(w) - 2):        # 首尾位置的 3 字要保留（那是真前缀/后缀）
            bad.add(w[i:i + 3])
    return bad


def build_inside(n_gram_len, freq_long):
    """
    inside[子] = 所有包含它的更长串频次之和（子串频次传播）。

    「新冠肺」这种漏网的就是靠它挡掉的：它在语料里 4140 次，但「新冠肺炎」
    这个 4 字串就有 8000+ 次，且每个「新冠肺」几乎都长在「新冠肺炎」里面，
    于是 inside['新冠肺'] ≈ 8000 > 自身频次 → 判为非词。

    实现方式：扫一遍更长串的表（几百万条），对每个 n-gram 子串记下**包住它的
    最长串里的最高频次**，O(更长串条数 × 子串数)，比"逐个候选枚举所有长串"快几个
    数量级。

    **为什么取 max 而不是 sum**：一开始用 sum，结果把「我的」这种最该收的词杀了。
    「我的」频次 32 万，但在语料里后面接什么字都行（我的天/我的妈/我的国…），
    于是十几个 3 字串各自累加，总和轻轻松松越过 32 万×0.6 的线；而频次更低的
    「新冠肺」只被「新冠肺炎」一个 4 字串包住，sum 反而不高——判据正好反了。
    寄生片段的本质是"被**某一个**高频长串反复裹住"，不是"出现在很多不同长串里"，
    所以贡献取 max。改完：「新冠肺」inside≈8000 > 自身 4140 → 挡掉；
    「我的」inside≈max(3 字串) ≪ 自身 32 万 → 保留。
    """
    inside = collections.defaultdict(int)
    for long_s, c in freq_long.items():
        for i in range(len(long_s) - n_gram_len + 1):
            sub = long_s[i:i + n_gram_len]
            if c > inside[sub]:
                inside[sub] = c
    return inside


def is_word(gram, f, inside, ratio=0.9):
    """
    寄生判据：inside[w] = 包住 w 的**最长串里的最高频次**（max 传播，见 build_inside）。

    ratio 0.9 的依据：寄生片段的特征是"几乎从不独立出现"——
      「新冠肺」4140 次，但「新冠肺炎」一个串就 8000+，比值远超 1 → 杀；
      「发工资」115 次，「发工资了」约 100 次，比值 ≈0.87 < 0.9 → **必须留**。
    早期用 0.6 时「发工资/开会了/洗澡了」这类「X了」短语全被误杀：
    它们天然总裹在「X了Y」的 4 字串里，0.6 线把"正常的黏着"错判成"寄生"。
    """
    return inside.get(gram, 0) < f * ratio


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--min-freq", type=int, default=MIN_FREQ)
    # 3 字词门槛单独设（默认比 2 字词高）：3 元组合空间大得多，频次 40 的 3 字串
    # 里非词比例很高。实测「好多了」3.1k、「新冠肺」4.1k，两者量级相当，
    # 所以 3 字的门槛不能只靠频次，主要靠词组词典挡内部片段。
    ap.add_argument("--min-freq3", type=int, default=MIN_FREQ3)
    ap.add_argument("--min-freq4", type=int, default=MIN_FREQ4)
    ap.add_argument("--max-keep2", type=int, default=MAX_KEEP2)
    ap.add_argument("--max-keep3", type=int, default=MAX_KEEP3)
    ap.add_argument("--max-keep4", type=int, default=MAX_KEEP4)
    ap.add_argument("--out", default=OUT_TXT)
    args = ap.parse_args()

    sources = [
        ("news", os.path.join(CORPUS, "news300k.tar.gz")),
        ("spoken", os.path.join(CORPUS, "opensub2016.gz")),
        ("wiki", os.path.join(CORPUS, "wikimedia.gz")),
        # 当代网页文本：现有语料是 2020 新闻 + 影视字幕 + 百科旧快照，
        # 缺的正是当代新词（脑机接口/具身智能/闪充/智驾…）。由 fetch_web_corpus.py 生成。
        ("web", os.path.join(CORPUS, "web_corpus.txt")),
    ]

    freq = collections.defaultdict(collections.Counter)
    t0 = time.time()
    for name, path in sources:
        if not os.path.exists(path):
            print(f"  [skip] {name}: 不存在 {path}")
            continue
        rows = 0
        chars = 0
        try:
            for line in open_text_stream(path):
                rows += 1
                chars += len(line)
                add_counts(freq, line)
        except Exception as e:                      # 单个语料坏掉不该中断整轮
            print(f"  [warn] {name} 扫描中断：{e}")
        top = {s: freq[s].most_common(3) for s in NGRAMS}
        print(f"  {name:<8} 行 {rows:>9,}  字符 {chars:>11,}  "
              f"用时 {time.time() - t0:5.1f}s")
        for s in NGRAMS:
            print(f"       {s}-gram 种类 {len(freq[s]):>9,}  头部 {top.get(s, [])[:3]}")

    # ── pass2：受限统计 4-gram（只统计左右两段都是高频 3-gram 的，见函数 docstring）──
    seed3 = {w: n for w, n in freq[3].items() if n >= LONG_SEED}
    print(f"\n  pass2：4-gram 受限统计（种子 3-gram ≥{LONG_SEED} 的有 {len(seed3):,} 个）…")
    f4 = collections.Counter()
    for name, path in sources:
        if not os.path.exists(path):
            continue
        try:
            for line in open_text_stream(path):
                add_counts_4gram(f4, line, seed3)
        except Exception as e:
            print(f"  [warn] {name} 4-gram 扫描中断：{e}")
    print(f"  4-gram 候选 {len(f4):,} 种  头部 {f4.most_common(8)}")

    # ── 抽词：频次门槛 + 子串占比 + 词组词典内部片段过滤 ────────────────────
    f3 = {w: n for w, n in freq[3].items() if n >= max(args.min_freq, args.min_freq3)}
    print(f"  4-gram(≥{args.min_freq4}) {sum(1 for v in f4.values() if v >= args.min_freq4):>9,} 条   "
          f"3-gram(≥{max(args.min_freq, args.min_freq3)}) {len(f3):>9,} 条")

    phrase_bad = load_phrase_lexicon()
    print(f"  词组词典内部片段 {len(phrase_bad):,} 条（用于挡「新冠肺」这类寄生片段）")

    inside2 = build_inside(2, f3)     # 「我的」被「我的世界」包住的频次
    inside3 = build_inside(3, f4)     # 「新冠肺」被「新冠肺炎」包住的频次

    kept3 = []
    for w, n in f3.items():
        if not is_word(w, n, inside3) or w in phrase_bad:
            continue
        kept3.append((w, n))
    n3_kept = len(kept3)

    # 4 字词：判据只有 频次门槛 + 词组词典（没有 5-gram 可做 inside 传播）。
    # 噪声主要由 pass2 的种子条件挡掉了，剩余的真碎片 rank 靠后、logp 极低，
    # 排不进首屏，只是占点体积。
    kept4 = []
    for w, n in f4.items():
        if n < args.min_freq4 or w in phrase_bad:
            continue
        kept4.append((w, n))
    n4_kept = len(kept4)

    kept2 = []
    for w, n in freq[2].items():
        if n < args.min_freq:
            continue
        if not is_word(w, n, inside2):
            continue
        kept2.append((w, n))
    n2_kept = len(kept2)

    print(f"  通过判据：4 字 {n4_kept:,} 条 ｜ 3 字 {n3_kept:,} 条 ｜ 2 字 {n2_kept:,} 条")
    # 分池截断：各长度分别按频次取 top，互不挤压（教训见 MAX_KEEP2 注释）
    for lst in (kept2, kept3, kept4):
        lst.sort(key=lambda kv: (-kv[1], kv[0]))
    kept3 = kept3[: args.max_keep3]
    kept2 = kept2[: args.max_keep2]
    kept4 = kept4[: args.max_keep4]
    kept = kept2 + kept3 + kept4
    print(f"  收入：2 字 {len(kept2):,} (配额 {args.max_keep2:,}) ｜ "
          f"3 字 {len(kept3):,} (配额 {args.max_keep3:,}) ｜ "
          f"4 字 {len(kept4):,} (配额 {args.max_keep4:,})")

    os.makedirs(os.path.dirname(args.out), exist_ok=True)
    with open(args.out, "w", encoding="utf-8") as f:
        f.write("# 语料直抽词源（由 _tools/dict/extract_corpus_words.py 生成）\n")
        f.write("# 格式：每行一词；频次写在 #freq 注释之外的列里（下游按行序当 Zipf 序位）\n")
        for w, n in kept:
            f.write(f"{w} {n}\n")

    with open(args.out + ".meta.json", "w", encoding="utf-8") as f:
        json.dump({
            "source": os.path.relpath(args.out, ROOT),
            "min_freq": args.min_freq,
            "ngrams": list(NGRAMS),
            "picked": len(kept),
            "words": [w for w, _ in kept],
        }, f, ensure_ascii=False)

    print(f"\n抽词 {len(kept)} 条 -> {args.out}")
    print("头部 30：", " ".join(f"{w}({n})" for w, n in kept[:30]))
    for probe in ("好多了", "我的", "好了", "多了", "来了", "看着"):
        hit = next(((n) for w, n in kept if w == probe), None)
        print(f"  {probe}: {'频次 ' + str(hit) if hit else '【未抽到】'}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
