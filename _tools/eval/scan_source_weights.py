#!/usr/bin/env python
"""
词频模型参数标定：用**网页语料的真实频次**当标尺，扫描证据源权重，找出最优组合。

## 起因

校准分析发现模型对网页词汇的 Spearman 秩相关只有 0.42，且**2 字词系统性虚高
+0.09 分位**。诊断高分低频词的分数来源，发现全部来自**短表源**：

    「不行」-5774  ← oral 表（仅 609 条，Zipf 归一后每词份额巨大）
    「变量」-6525  ← modern 表（1044 条）
    「三次」-6851  ← structured 表（139 条）

这些表本意是"补长尾盲区"（把语料里没有的常用口语词塞进来），
实际却让表内**每个词**都拿到接近头部的分数，把真正的高频词压下去。

## 本脚本做什么

遍历候选 (weight, k) 组合，每组：改 CONFIG → 重建词库 → 算校准指标 →
记录并挑最优。指标有两类，必须一起看：

  1. **校准类**（网页语料频次为标尺）：Spearman 秩相关、2/3/4 字分位偏差
  2. **回归类**（492 条人工语料 Top-1）：防止为了校准好看而打坏既有指标

用第一类选参数区间，用第二类做最终确认。

用法：`python scan_source_weights.py [--only oral]`
"""
from __future__ import annotations

import argparse
import collections
import io
import os
import re
import sqlite3
import subprocess
import sys

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
BUILD = os.path.join(ROOT, "_tools", "dict", "build_base_words.py")
DB = os.path.join(ROOT, "PersonalIME", "PersonalIME", "app", "src", "main",
                  "assets", "dict", "base_words.db")
CORPUS = os.path.join(ROOT, "_tools", "lm", "corpus", "web_corpus.txt")
EVAL = os.path.join(HERE, "evaluate.py")

PY_EXE = sys.executable

# 候选：oral 权重/平滑、modern 权重/平滑
GRID = {
    "oral": [(w, k) for w in (0.24, 0.18, 0.12, 0.06) for k in (3, 8)],
    "modern": [(w, k) for w in (0.16, 0.10, 0.04) for k in (5, 12)],
}


def set_param(src: str, name: str, weight: float, k: int) -> str:
    """把 CONFIG['sources'] 里某个源的 weight/k 改成给定值，返回新源码。"""
    pat = re.compile(r'("%s"\s*:\s*\{)\s*"weight"\s*:\s*[\d.]+\s*,\s*"k"\s*:\s*\d+' % name)
    rep = r'\1 "weight": %s, "k": %d' % (weight, k)
    new, n = pat.subn(rep, src)
    if n != 1:
        raise SystemExit(f"改写 {name} 失败（匹配 {n} 处）")
    return new


def spearman(xs, ys):
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
    mx, my = sum(rx) / n, sum(ry) / n
    cov = sum((a - mx) * (b - my) for a, b in zip(rx, ry))
    vx = sum((a - mx) ** 2 for a in rx) ** 0.5
    vy = sum((b - my) ** 2 for b in ry) ** 0.5
    return cov / (vx * vy) if vx and vy else float("nan")


def pct_rank(sorted_vals, v):
    lo, hi = 0, len(sorted_vals)
    while lo < hi:
        mid = (lo + hi) // 2
        if sorted_vals[mid] < v:
            lo = mid + 1
        else:
            hi = mid
    return lo / max(1, len(sorted_vals))


def load_web_rows():
    import jieba
    jieba.setLogLevel(60)
    cnt = collections.Counter()
    for line in open(CORPUS, encoding="utf-8", errors="ignore"):
        if line.startswith("#"):
            continue
        for w in jieba.lcut(line):
            w = w.strip()
            if 2 <= len(w) <= 4 and all('\u4e00' <= c <= '\u9fff' for c in w):
                cnt[w] += 1
    cnt = collections.Counter({w: c for w, c in cnt.items() if c >= 2})
    con = sqlite3.connect(DB)
    rows = []
    for w, f in cnt.items():
        r = con.execute("SELECT logp FROM base_words WHERE word=?", (w,)).fetchone()
        if r:
            rows.append((w, f, r[0]))
    con.close()
    return rows


def metrics(rows):
    freqs = [r[1] for r in rows]
    logps = [r[2] for r in rows]
    rho = spearman(logps, freqs)
    srt, sf = sorted(logps), sorted(freqs)
    dev = {}
    g = collections.defaultdict(list)
    for w, f, lp in rows:
        g[len(w)].append((f, lp))
    for L, gg in g.items():
        mp = sum(pct_rank(srt, lp) for _, lp in gg) / len(gg)
        fp = sum(pct_rank(sf, f) for f, _ in gg) / len(gg)
        dev[L] = mp - fp
    return rho, dev, len(rows)


def regression_top1():
    """跑 492 条人工语料，拿综合 Top-1（防止为了校准好看打坏既有指标）"""
    out = subprocess.run([PY_EXE, EVAL, "--out", os.path.join(HERE, "_tmp_scan.md")],
                         cwd=HERE, capture_output=True, text=True, encoding="utf-8",
                         errors="replace", timeout=900)
    m = re.search(r"\|\s*总体\s*\|\s*492\s*\|\s*([\d.]+)%", out.stdout or "")
    return float(m.group(1)) if m else float("nan")


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--only", default="oral", choices=list(GRID))
    ap.add_argument("--skip-regression", action="store_true",
                    help="只算校准指标，不跑 492 回归（快，但只能初筛）")
    args = ap.parse_args()

    src0 = open(BUILD, encoding="utf-8").read()
    name = args.only
    backup = src0

    print(f"扫描 {name} 的 (weight, k)；每组重建词库 + 算校准"
          f"{'（含 492 回归）' if not args.skip_regression else ''}")
    print(f"{'weight':>7} {'k':>4} | {'Spearman':>9} {'2字偏差':>9} {'3字偏差':>9} "
          f"{'4字偏差':>9} | {'492 Top-1':>9}")
    print("-" * 76)
    results = []
    for w, k in GRID[name]:
        src = set_param(backup, name, w, k)
        open(BUILD, "w", encoding="utf-8", newline="").write(src)
        rb = subprocess.run([PY_EXE, BUILD], cwd=os.path.dirname(BUILD),
                            capture_output=True, text=True, encoding="utf-8",
                            errors="replace", timeout=1800)
        if rb.returncode != 0:
            print(f"{w:>7} {k:>4} | 构建失败：{(rb.stderr or '')[-120:]}")
            continue
        rows = load_web_rows()
        rho, dev, n = metrics(rows)
        top1 = float("nan") if args.skip_regression else regression_top1()
        results.append((w, k, rho, dev, top1))
        print(f"{w:>7} {k:>4} | {rho:>9.4f} {dev.get(2, 0):>+9.3f} {dev.get(3, 0):>+9.3f} "
              f"{dev.get(4, 0):>+9.3f} | {top1:>8.1f}%")

    open(BUILD, "w", encoding="utf-8", newline="").write(backup)
    if results:
        best = max(results, key=lambda r: (r[2], -abs(r[3].get(2, 0))))
        print(f"\n按 Spearman 最优：{name} weight={best[0]} k={best[1]} "
              f"(rho={best[2]:.4f}, 2字偏差{best[3].get(2, 0):+.3f}, 492={best[4]:.1f}%)")
    print("已恢复 build_base_words.py 原始配置")
    return 0


if __name__ == "__main__":
    sys.exit(main())