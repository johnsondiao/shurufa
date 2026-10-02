#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
阶段 4b：字符级 bigram 语言模型构建。

用途
----
九键整句候选 DP 里，各分段（词）之间靠 unigram 词频打分，**没有上下文**，
于是「说得好 / 说的好」「在哪 / 在那」这类同音歧义无法区分。本脚本从真实语料
统计**字符级 bigram**，为「上一词末字 → 下一词首字」的边界提供条件概率。

为什么是字符级
--------------
词级 bigram 需要 38 万词表的两两共现，语料再大也极度稀疏；字符级只需 ~8000 字
的两两共现，同样语料下估计稳定得多，剪枝量化后体积只有几 MB。
（OVERHAUL_PLAN.md 第 4b 步即此设计。）

平滑与打分
----------
Dirichlet（加δ）平滑，δ 取语料无关的小常数：

    P(b|a) = (c(a,b) + δ·P_uni(b)) / (c(a) + δ)

好处是「见过的组合恒优于没见过的」——纯 MLE + Stupid-Backoff 不保证这条，
罕见但见过的组合会输给没见过的，排序会抖。

剪枝后未保留的 (a,b) 用精确的退避值：

    P_backoff(b|a) = δ·P_uni(b) / (c(a) + δ)
                   = exp(logp_uni[b] + norm_logp[a])

其中 norm_logp[a] = ln(δ/(c(a)+δ)) 随 char_uni 表一起存下，因此未命中不求近似。

输出
----
    bigram.db
      char_uni(cp PK, logp, norm)   -- ~8000 行，可整体载入内存
      bigram(prev, next, logp)      -- PK(prev,next)，剪枝后 Top-N/prev
      lm_meta(key, value)
"""
import gzip
import os
import sqlite3
import sys
import tarfile
import time

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
CORPUS = os.path.join(HERE, "corpus")
CHARSET = os.path.join(HERE, "charset.txt")
OUT = os.path.join(HERE, "bigram.db")
ASSET_DIR = os.path.join(ROOT, "PersonalIME", "PersonalIME", "app", "src",
                         "main", "assets", "dict")

CONFIG = {
    # 软化系数：越大越依赖字符 unigram，越小越依赖语料共现
    "delta": 0.6,
    # 剪枝：低于该计数的 bigram 一律丢弃
    "min_count": 3,
    # 剪枝：每个前字最多保留多少个后继（按计数降序）
    "top_n": 48,
    # 单条语料最多吸收多少字符（控制内存与耗时）
    "per_source_chars": 22_000_000,
    # 语料行进入统计的最低"字集合命中率"（低于此判定为繁体/日文/杂讯）
    "min_in_charset_ratio": 0.75,
    "min_cjk_in_line": 2,
}

# 假名 / 谚文 / 注音 等非中文字符，出现即判定该行非简体中文
KANA_HANGUL = {
    "HIRAGANA", "KATAKANA", "HANGUL", "BOPOMOFO", "BOPOMOFO_EXTENDED",
}


def is_kana_or_hangul(ch):
    import unicodedata
    try:
        return unicodedata.name(ch).split()[0] in KANA_HANGUL
    except ValueError:
        return False


def is_cjk(ch):
    o = ord(ch)
    return (0x4E00 <= o <= 0x9FFF or 0x3400 <= o <= 0x4DBF
            or 0xF900 <= o <= 0xFAFF or 0x20000 <= o <= 0x2FA1F)


def load_charset():
    """字符集 = 基础词库里出现过的所有 CJK 字。

    只统计"输入法真的能打出来"的字，避免把日文假名/繁体外字/生僻符号
    的噪声共现算进模型。charset.txt 缺失时从词库现算，保证流水线自足。
    """
    if not os.path.exists(CHARSET):
        base_db = os.path.join(ASSET_DIR, "base_words.db")
        if not os.path.exists(base_db):
            raise SystemExit("缺少 %s，无法推导字符集（先跑 build_base_words.py）" % base_db)
        con = sqlite3.connect(base_db)
        chars = set()
        for (w,) in con.execute("SELECT word FROM base_words"):
            for ch in w:
                o = ord(ch)
                if (0x4E00 <= o <= 0x9FFF or 0x3400 <= o <= 0x4DBF
                        or 0xF900 <= o <= 0xFAFF or 0x20000 <= o <= 0x2FA1F):
                    chars.add(ch)
        con.close()
        with open(CHARSET, "w", encoding="utf-8") as f:
            f.write("".join(sorted(chars)))
        print("已从基础词库推导字符集 -> %s" % CHARSET)

    with open(CHARSET, encoding="utf-8") as f:
        chars = f.read().strip()
    return {ord(c): i for i, c in enumerate(chars) if c.strip()}, len(chars)


# ---------------------------------------------------------------- 语料读取

def iter_leipzig(path):
    """Leipzig `<corpus>-sentences.txt`，位于 tar.gz 内，行格式 `id\\t句子`。"""
    with tarfile.open(path, "r:gz") as tf:
        member = None
        for m in tf.getmembers():
            if m.name.endswith("-sentences.txt"):
                member = m
                break
        if member is None:
            return
        fh = tf.extractfile(member)
        for raw in fh:
            line = raw.decode("utf-8", "ignore")
            tab = line.find("\t")
            yield line[tab + 1:] if tab >= 0 else line


def iter_plain_gz(path):
    with gzip.open(path, "rt", encoding="utf-8", errors="ignore") as f:
        for line in f:
            yield line


SOURCES = [
    {"file": "zho_news_2020_300K.tar.gz", "kind": "leipzig", "tag": "news"},
    {"file": "opensub2016.gz", "kind": "plain", "tag": "opensub"},
    {"file": "wikimedia.gz", "kind": "plain", "tag": "wiki"},
]


def iter_source(spec):
    path = os.path.join(CORPUS, spec["file"])
    if not os.path.exists(path):
        return
    if spec["kind"] == "leipzig":
        yield from iter_leipzig(path)
    else:
        yield from iter_plain_gz(path)


# ---------------------------------------------------------------- 统计

def harvest(idx_of, charset_size, per_source_cap, ratio_floor, min_cjk):
    """流式统计 unigram / bigram 计数，返回 (uni, bi, stats)。"""
    uni = [0] * charset_size
    bi = {}
    stats = {}
    for spec in SOURCES:
        t0 = time.time()
        kept = lines = dropped = 0
        chars = 0
        cache = {}          # 单字 → 索引（None 表示非法/断链）

        for line in iter_source(spec):
            lines += 1
            if chars >= per_source_cap:
                break
            seq = []
            cjk_n = hit_n = 0
            bad = False
            for ch in line:
                if is_cjk(ch):
                    cjk_n += 1
                    i = cache.get(ch, -2)
                    if i == -2:
                        i = idx_of.get(ord(ch), -1)
                        cache[ch] = i
                    if i >= 0:
                        hit_n += 1
                        seq.append(i)
                    else:
                        seq.append(-1)      # 链断点
                elif is_kana_or_hangul(ch):
                    bad = True
                    break
            if bad or cjk_n < min_cjk:
                dropped += 1
                continue
            if cjk_n and hit_n / cjk_n < ratio_floor:
                dropped += 1
                continue

            kept += 1
            chars += len(seq)
            prev = -1
            for i in seq:
                if i < 0:
                    prev = -1
                    continue
                uni[i] += 1
                if prev >= 0:
                    k = prev * charset_size + i
                    bi[k] = bi.get(k, 0) + 1
                prev = i

        stats[spec["tag"]] = dict(lines=lines, kept=kept, dropped=dropped,
                                  chars=chars, secs=round(time.time() - t0, 1))
        print("  [%s] 行 %d / 采用 %d / 丢弃 %d，字 %d（%.1fs）"
              % (spec["tag"], lines, kept, dropped, chars, time.time() - t0),
              flush=True)
    return uni, bi, stats


def build(idx_of, charset_size, want_chars, uni, bi, cfg):
    total = sum(uni)
    delta = cfg["delta"]

    # 字符 unigram 概率 + 退避常数
    char_rows = []
    import math
    log = math.log
    for i, c in enumerate(uni):
        if c <= 0:
            continue
        logp = round(log(c / total) * 1000)
        norm = round(log(delta / (c + delta)) * 1000)
        char_rows.append((i, logp, norm))
    uni_logp = {i: lp for i, lp, _ in char_rows}

    # 边界对数概率的期望 Σ P(c)·ln P(c) = −H(字符)，即中心化常数。
    # 打分时每个边界减掉它，边界项就只表达「比平均好多少」，不再随分段数变负。
    mean_logp = round(sum((c / total) * log(c / total) for c in uni if c > 0) * 1000)

    # 按前字分组，按计数降序剪枝
    by_prev = {}
    for k, c in bi.items():
        if c < cfg["min_count"]:
            continue
        a, b = divmod(k, charset_size)
        by_prev.setdefault(a, []).append((c, b))

    bigram_rows = []
    kept = 0
    pruned = 0
    for a, items in by_prev.items():
        ca = uni[a]
        items.sort(key=lambda t: (-t[0], t[1]))
        keep = items[:cfg["top_n"]]
        pruned += len(items) - len(keep)
        for c, b in keep:
            p = (c + delta * pow(2.718281828459045, uni_logp[b] / 1000.0)) / (ca + delta)
            if p <= 0:
                continue
            bigram_rows.append((a, b, round(log(p) * 1000)))
            kept += 1

    return char_rows, bigram_rows, dict(
        total_chars=total, char_rows=len(char_rows),
        bigram_kept=kept, bigram_pruned=pruned,
        distinct_before_prune=len(bi),
        mean_logp=mean_logp,
    )


def write_db(path, char_rows, bigram_rows, idx_of, meta, want_chars):
    if os.path.exists(path):
        os.remove(path)
    con = sqlite3.connect(path)
    con.executescript("""
        PRAGMA journal_mode=OFF;
        PRAGMA synchronous=OFF;
        PRAGMA page_size=4096;
        CREATE TABLE char_uni(
            cp   INTEGER NOT NULL,   -- 前/后字的 unicode 码点
            logp INTEGER NOT NULL,   -- 毫纳特, ln P_uni(c)
            norm INTEGER NOT NULL,   -- 毫纳特, ln(delta/(count(c)+delta))
            PRIMARY KEY (cp)) WITHOUT ROWID;
        CREATE TABLE bigram(
            prev INTEGER NOT NULL,   -- 前字码点
            next INTEGER NOT NULL,   -- 后字码点
            logp INTEGER NOT NULL,   -- 毫纳特, ln P(next|prev)
            PRIMARY KEY (prev, next)) WITHOUT ROWID;
        CREATE TABLE lm_meta(key TEXT PRIMARY KEY, value TEXT);
    """)
    rev = {i: cp for cp, i in idx_of.items()}
    con.executemany("INSERT INTO char_uni(cp,logp,norm) VALUES(?,?,?)",
                    [(rev[i], lp, nm) for i, lp, nm in char_rows])
    con.executemany("INSERT INTO bigram(prev,next,logp) VALUES(?,?,?)",
                    [(rev[a], rev[b], lp) for a, b, lp in bigram_rows])
    con.executemany("INSERT INTO lm_meta(key,value) VALUES(?,?)",
                    sorted(meta.items()))
    con.commit()
    con.execute("CREATE INDEX idx_bigram_next ON bigram(next)")
    con.commit()
    con.execute("VACUUM")
    con.commit()
    con.close()


def main():
    cfg = CONFIG
    print("阶段 4b · 字符级 bigram 构建")
    idx_of, charset_size = load_charset()
    print("字符集：%d 字" % charset_size)

    t0 = time.time()
    uni, bi, stats = harvest(idx_of, charset_size, cfg["per_source_chars"],
                             cfg["min_in_charset_ratio"], cfg["min_cjk_in_line"])
    print("统计完成：%d 秒，字符总数 %d，不同 bigram %d"
          % (time.time() - t0, sum(uni), len(bi)), flush=True)

    char_rows, bigram_rows, m = build(idx_of, charset_size, charset_size, uni, bi, cfg)
    print("剪枝后：char_uni %d 行 / bigram %d 行（丢弃 %d，%.1f%%）"
          % (m["char_rows"], m["bigram_kept"], m["bigram_pruned"],
             100.0 * m["bigram_pruned"] / max(m["distinct_before_prune"], 1)))

    meta = {
        "version": "1",
        "delta": str(cfg["delta"]),
        "min_count": str(cfg["min_count"]),
        "top_n": str(cfg["top_n"]),
        "total_chars": str(m["total_chars"]),
        "charset_size": str(charset_size),
        "bigram_rows": str(m["bigram_kept"]),
        "mean_logp": str(m["mean_logp"]),
        "sources": ",".join("%s:%d" % (k, v["kept"]) for k, v in stats.items()),
    }
    write_db(OUT, char_rows, bigram_rows, idx_of, meta, charset_size)
    size = os.path.getsize(OUT)
    print("写出 %s（%.2f MB）" % (OUT, size / 1048576))
    return 0


if __name__ == "__main__":
    sys.exit(main())
