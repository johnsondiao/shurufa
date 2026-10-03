#!/usr/bin/env python
"""
词频模型校准分析：用**网页语料的真实频次**当标尺，找模型系统性失准的模式。

## 为什么需要它

补词只是把词塞进词库，**"在库里"不等于"用户打得出来"**。补进去的 4 字词
（如「人民日报」logp -15788）排在同数字串末尾的话，用户体感依然是"打不出来"。
所以要用外部证据（网页语料频次）反过来检查模型给出的分数是否合理：

  模型分排名 vs 语料频次排名，如果两者严重不一致，说明模型在某种词型上系统性偏了。

## 输出

1. Spearman 秩相关（模型排序与真实频次排序的一致性）
2. 按词长/结构分组的"模型分位 vs 频次分位"偏差 —— 偏差最大的组就是要调的地方
3. 「高频却低分」词清单（模型最该提分的词）
4. 「低频却高分」词清单（模型虚高的词，可能挤压用户词）

用法：`python calibrate_web.py [--corpus ...] [--db ...]`
"""
from __future__ import annotations

import argparse
import collections
import io
import os
import sqlite3
import sys

# stdout 必须用 reconfigure 而不是再包一层 TextIOWrapper：
# 被 import 时（校准分析要 import spearman），旧 wrapper 会被 GC 并连同
# 底层 buffer 一起 close，新 wrapper 就撞 "I/O operation on closed file"。
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
else:
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
CORPUS = os.path.join(ROOT, "_tools", "lm", "corpus", "web_corpus.txt")
DB = os.path.join(ROOT, "PersonalIME", "PersonalIME", "app", "src", "main",
                  "assets", "dict", "base_words.db")
OUT = os.path.join(HERE, "calibration_web.md")


def spearman(xs, ys):
    """秩相关（并列取平均秩）。"""
    def ranks(v):
        order = sorted(range(len(v)), key=lambda i: v[i])
        r = [0.0] * len(v)
        i = 0
        while i < len(order):
            j = i
            while j + 1 < len(order) and v[order[j + 1]] == v[order[i]]:
                j += 1
            avg = (i + j) / 2 + 1
            for k in range(i, j + 1):
                r[order[k]] = avg
            i = j + 1
        return r
    rx, ry = ranks(xs), ranks(ys)
    n = len(xs)
    if n < 3:
        return float("nan")
    mx = sum(rx) / n
    my = sum(ry) / n
    cov = sum((a - mx) * (b - my) for a, b in zip(rx, ry))
    vx = sum((a - mx) ** 2 for a in rx) ** 0.5
    vy = sum((b - my) ** 2 for b in ry) ** 0.5
    return cov / (vx * vy) if vx and vy else float("nan")


def pct_rank(sorted_vals, v):
    """v 在 sorted_vals（升序）里的分位 0..1"""
    lo, hi = 0, len(sorted_vals)
    while lo < hi:
        mid = (lo + hi) // 2
        if sorted_vals[mid] < v:
            lo = mid + 1
        else:
            hi = mid
    return lo / max(1, len(sorted_vals))


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--corpus", default=CORPUS)
    ap.add_argument("--db", default=DB)
    ap.add_argument("--min-freq", type=int, default=2)
    ap.add_argument("--top", type=int, default=50)
    args = ap.parse_args()

    import jieba
    jieba.setLogLevel(60)
    cnt = collections.Counter()
    for line in open(args.corpus, encoding="utf-8", errors="ignore"):
        if line.startswith("#"):
            continue
        for w in jieba.lcut(line):
            w = w.strip()
            if 2 <= len(w) <= 4 and all('\u4e00' <= c <= '\u9fff' for c in w):
                cnt[w] += 1
    cnt = collections.Counter({w: c for w, c in cnt.items() if c >= args.min_freq})
    print(f"网页语料候选词 {len(cnt)}（≥{args.min_freq} 次）")

    con = sqlite3.connect(args.db)
    rows = []                      # (word, freq, logp, digits)
    for w, f in cnt.items():
        r = con.execute("SELECT logp,digits FROM base_words WHERE word=?", (w,)).fetchone()
        if r:
            rows.append((w, f, r[0], r[1]))
    print(f"其中在库 {len(rows)}，缺失 {len(cnt) - len(rows)}")

    freqs = [r[1] for r in rows]
    logps = [r[2] for r in rows]
    rho = spearman(logps, freqs)
    print(f"\nSpearman 秩相关（模型 logp vs 语料频次）= {rho:.3f}")
    print("  （1.0=完全一致；0.7+ 算可用；<0.5 说明排序与真实使用频率脱节）")

    # ── 按词长分组看偏差 ──────────────────────────────────────────────────
    print("\n=== 按词长看偏差（模型分位 - 频次分位；正=模型给高了分）===")
    all_logp_sorted = sorted(logps)
    groups = collections.defaultdict(list)
    for w, f, lp, dg in rows:
        groups[len(w)].append((f, lp))
    md = ["# 词频模型校准分析（网页语料 × 模型 logp）", "",
          f"- 语料：`web_corpus.txt` ｜ 词数 {len(rows)}（≥{args.min_freq} 次，在库部分）",
          f"- Spearman 秩相关：**{rho:.3f}**（模型排序 vs 真实频次排序）", "",
          "## 按词长的分数偏差", "",
          "| 词长 | 词数 | 平均模型分位 | 平均频次分位 | 偏差 |", "|---|---|---|---|---|"]
    for L in sorted(groups):
        g = groups[L]
        mp = sum(pct_rank(all_logp_sorted, lp) for _, lp in g) / len(g)
        fp = sum(pct_rank(sorted(freqs), f) for f, _ in g) / len(g)
        dev = mp - fp
        print(f"  {L} 字  {len(g):>5} 词   模型分位 {mp:.3f}  频次分位 {fp:.3f}  "
              f"偏差 {dev:+.3f}  {'← 模型低估' if dev < -0.05 else ('← 模型虚高' if dev > 0.05 else '')}")
        md.append(f"| {L} 字 | {len(g)} | {mp:.3f} | {fp:.3f} | {dev:+.3f} |")

    # ── 高频却低分：模型最该提分的词 ──────────────────────────────────────
    under = sorted(rows, key=lambda r: (pct_rank(all_logp_sorted, r[2])
                                        - pct_rank(sorted(freqs), r[1])))[: args.top]
    print(f"\n=== 高频却低分（模型最该提分，Top {args.top}）===")
    for w, f, lp, dg in under[:25]:
        print(f"  {w:<8} 网页出现 {f:>3} 次   logp {lp:>7}   ({dg})")
    over = sorted(rows, key=lambda r: (pct_rank(sorted(freqs), r[1])
                                       - pct_rank(all_logp_sorted, r[2])))[:25]
    print(f"\n=== 低频却高分（模型虚高，可能挤压用户想打的词）===")
    for w, f, lp, dg in over[:20]:
        print(f"  {w:<8} 网页出现 {f:>3} 次   logp {lp:>7}   ({dg})")

    md += ["", "## 高频却低分（模型最该提分）", "", "| 词 | 网页频次 | logp |", "|---|---|---|"]
    for w, f, lp, dg in under:
        md.append(f"| {w} | {f} | {lp} |")
    md += ["", "## 低频却高分（模型虚高）", "", "| 词 | 网页频次 | logp |", "|---|---|---|"]
    for w, f, lp, dg in over:
        md.append(f"| {w} | {f} | {lp} |")
    with open(OUT, "w", encoding="utf-8") as fh:
        fh.write("\n".join(md) + "\n")
    print(f"\n-> {OUT}")
    return 0


if __name__ == "__main__":
    sys.exit(main())