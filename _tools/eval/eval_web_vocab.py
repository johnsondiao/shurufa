#!/usr/bin/env python
"""
网页词汇全量打一遍：jieba 分词 → 逐词查词库 → 分层统计。

## 与已有评测的分工

- `bench.tsv`（语料抽样）：用**词库自己**分词，抽出的词必然在库里 → 测不出缺词；
- `eval_daily_words.py`（248 手写词）：覆盖日常，但只有 248 个词、场景有限；
- **本脚本**：拿**真实网页文本**（`_tools/lm/corpus/web_corpus.txt`，由
  `fetch_web_corpus.py` 抓取）用 **jieba** 分词——第三方分词器，与我们的词库
  无关，因此**没有循环论证**，且词量数千、覆盖当代网络词汇。

## 为什么用真实网页而不是现成语料

现有语料是 2020 新闻 + 影视字幕 + 百科旧快照，缺的正是当代网络语与新词。
用户抱怨的"想打的词它自己没有"，网络词汇是重灾区。

判定口径与 eval_daily_words 一致：
  - L1 不在库：完全打不出来（用户只能逐字）；
  - L3 位次落后：能拼出来但不在首屏（前 5）之外；
  - L0 正常：首屏内。
"""
from __future__ import annotations

import argparse
import collections
import io
import os
import sqlite3
import sys

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
CORPUS = os.path.join(ROOT, "_tools", "lm", "corpus", "web_corpus.txt")
DB = os.path.join(ROOT, "PersonalIME", "PersonalIME", "app", "src", "main",
                  "assets", "dict", "base_words.db")
OUT_REPORT = os.path.join(HERE, "report_web_vocab.md")
OUT_MISSING = os.path.join(HERE, "web_vocab_missing.txt")

FIRST_SCREEN = 5
CJK_ONLY = None      # 运行时编译

# 停用词/虚词组合：jieba 会切出"的了""的一个"这类，测它们没有意义
STOP = set("""的 了 是 在 和 与 及 或 也 都 就 而 及其 以及 这个 那个 什么 怎么
我们 你们 他们 她们 它们 自己 人家 大家 因为 所以 但是 如果 虽然 于是 而且 并且
不是 没有 可以 可能 应该 需要 进行 通过 由于 关于 对于 根据 按照 作为 成为
其中 之后 之前 时候 一样 一些 一样 这些 那些 这种 那种 如此 这样 那样 之后
表示 认为 觉得 知道 看到 听到 说到 目前 现在 已经 曾经 将要 正在 一直 总是
非常 十分 比较 特别 尤其 主要 重要 方面 情况 问题 工作 时间 时候 一定 可能
今天 昨天 明天 今年 去年 明年 上午 下午 中午 晚上 早上""".split())


def load_corpus(path):
    lines = []
    with open(path, encoding="utf-8", errors="ignore") as f:
        for line in f:
            line = line.strip()
            if line and not line.startswith("#"):
                lines.append(line)
    return lines


def tokenize(lines):
    """jieba 分词 -> 词频表（只留 2~6 字纯中文、去停用词）"""
    import jieba
    jieba.setLogLevel(60)
    cnt = collections.Counter()
    for line in lines:
        for w in jieba.lcut(line):
            w = w.strip()
            if len(w) < 2 or len(w) > 6:
                continue
            if not all('\u4e00' <= c <= '\u9fff' for c in w):
                continue
            if w in STOP:
                continue
            cnt[w] += 1
    return cnt


def rank_of(con, digits, logp):
    return con.execute("SELECT COUNT(*) FROM base_words WHERE digits=? AND logp>?",
                       (digits, logp)).fetchone()[0] + 1


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--corpus", default=CORPUS)
    ap.add_argument("--db", default=DB)
    ap.add_argument("--top", type=int, default=120, help="问题词清单条数")
    ap.add_argument("--min-freq", type=int, default=1, help="词频门槛（滤偶发词）")
    ap.add_argument("--corpus-chars", type=int, default=0, help="只取语料前 N 字（调试）")
    args = ap.parse_args()

    if not os.path.exists(args.corpus):
        print(f"缺语料 {args.corpus}，先跑 _tools/dict/fetch_web_corpus.py")
        return 1
    lines = load_corpus(args.corpus)
    if args.corpus_chars:
        lines, buf, n = [], [], 0
        for l in lines:
            buf.append(l)
            n += len(l)
            if n >= args.corpus_chars:
                break
        lines = buf
    total_chars = sum(len(l) for l in lines)
    print(f"网页语料 {len(lines)} 段 / {total_chars:,} 中文字符")

    cnt = tokenize(lines)
    cnt = collections.Counter({w: c for w, c in cnt.items() if c >= args.min_freq})
    print(f"jieba 分出候选词 {len(cnt)} 个（≥{args.min_freq} 次）")

    con = sqlite3.connect(args.db)
    rows = []
    for w, f in cnt.items():
        r = con.execute("SELECT digits,logp,pinyin FROM base_words WHERE word=?", (w,)).fetchone()
        if r is None:
            rows.append((w, f, "L1", 0, "-", None, "-"))
            continue
        dg, lp, py = r
        rk = rank_of(con, dg, lp)
        rows.append((w, f, "L0" if rk <= FIRST_SCREEN else "L3", rk, dg, lp, py))

    by = collections.Counter(r[2] for r in rows)
    n = len(rows)
    print(f"\n=== 打一遍的结果（{n} 词）===")
    print(f"  L0 首屏可打   {by['L0']:>5}  {by['L0']/n*100:5.1f}%")
    print(f"  L3 位次落后   {by['L3']:>5}  {by['L3']/n*100:5.1f}%")
    print(f"  L1 库中没有   {by['L1']:>5}  {by['L1']/n*100:5.1f}%")

    print("\n按词长分布（L1 占比 = 缺口）:")
    lens = collections.defaultdict(collections.Counter)
    for w, f, st, rk, dg, lp, py in rows:
        lens[len(w)][st] += 1
    for L in sorted(lens):
        c = lens[L]
        tot = sum(c.values())
        bar = "█" * int(c['L1'] / max(1, tot) * 30)
        print(f"  {L} 字  {tot:>5} 词   可打 {c['L0']/tot*100:5.1f}%   缺 {c['L1']/tot*100:5.1f}%  {bar}")

    # 高频缺失 = 体感最差的（用户天天遇到）
    miss = sorted([r for r in rows if r[2] == "L1"], key=lambda r: -r[1])
    late = sorted([r for r in rows if r[2] == "L3"], key=lambda r: (-r[1], r[3]))
    print(f"\n=== L1 库中没有（按语料频次 Top {args.top}）===")
    for w, f, st, rk, dg, lp, py in miss[: args.top]:
        print(f"  {w:<8} 语料出现 {f:>4} 次")
    print(f"\n=== L3 位次落后（Top 40）===")
    for w, f, st, rk, dg, lp, py in late[:40]:
        print(f"  {w:<8} 第 {rk:>3} 位  {py}  ({dg})")

    with open(OUT_MISSING, "w", encoding="utf-8") as f:
        f.write("# 网页词汇分词后查库失败的词（供批量补词用）\n")
        f.write("# 格式：词 语料频次 层级 [位次]（L1 排最前）\n")
        for w, freq, st, rk, dg, lp, py in sorted(rows, key=lambda r: (r[2], -r[1])):
            if st == "L0":
                continue
            f.write(f"{w} {freq} {st} {rk}\n")

    md = ["# 网页词汇全量评测报告（jieba 分词 × 词库查询）", "",
          f"- 语料：`_tools/lm/corpus/web_corpus.txt`（{len(lines)} 段 / {total_chars:,} 字符，"
          "由 `_tools/dict/fetch_web_corpus.py` 抓取）",
          f"- 分词：**jieba**（第三方，与本项目词库无关，无循环论证）",
          f"- 数据库：`base_words.db`", "",
          "## 总体", "",
          f"| 分层 | 词数 | 占比 | 含义 |", "|---|---|---|---|",
          f"| L0 首屏可打 | {by['L0']} | {by['L0']/n*100:.1f}% | 正常 |",
          f"| L3 位次落后 | {by['L3']} | {by['L3']/n*100:.1f}% | 能拼出但不在首屏 |",
          f"| L1 库中没有 | {by['L1']} | {by['L1']/n*100:.1f}% | 完全打不出来 |", "",
          "## 按词长", "", "| 词长 | 词数 | 可打率 | 缺口率 |", "|---|---|---|---|"]
    for L in sorted(lens):
        c = lens[L]
        tot = sum(c.values())
        md.append(f"| {L} 字 | {tot} | {c['L0']/tot*100:.1f}% | {c['L1']/tot*100:.1f}% |")
    md += ["", "## L1 高频缺失词（语料频次 Top 100）", "", "| 词 | 语料频次 |", "|---|---|"]
    for w, f, st, rk, dg, lp, py in miss[:100]:
        md.append(f"| {w} | {f} |")
    with open(OUT_REPORT, "w", encoding="utf-8") as f:
        f.write("\n".join(md) + "\n")
    print(f"\n报告 -> {OUT_REPORT}")
    print(f"问题词清单 -> {OUT_MISSING}")
    return 0


if __name__ == "__main__":
    sys.exit(main())