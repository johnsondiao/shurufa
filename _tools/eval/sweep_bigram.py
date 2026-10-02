#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
bigram 参数扫描：在语料集上对比不同 β（bigram 权重）与 DP-K（每位置保留路径数）。

用法：
    python sweep_bigram.py --lm ../lm/bigram.db
    python sweep_bigram.py --lm ../lm/bigram.db --beta 0,500,1000,1500 --k 3,5
"""
import argparse
import os
import sqlite3
import sys

# 注意：evaluate 模块在导入时会把 sys.stdout 换成 UTF-8 包装器，
# 这里若再包一层，内层包装器会被 GC 回收并关闭底层 buffer（I/O operation on closed file）。
# 因此只用 reconfigure，不重新构造包装器。
try:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
except Exception:  # noqa: BLE001
    pass

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import evaluate as E  # noqa: E402

ROOT = E.ROOT


def run(db, lm, beta, sent_k, cases, seg_pen=0):
    res = []
    for word, _domain, _prio, _kind, py, digits in cases:
        wc = E.simulate_input_t9(db, digits)
        sc = E.simulate_sentence_candidates(db, digits, lm=lm, beta=beta,
                                            sent_k=sent_k, seg_penalty=seg_pen)
        rw = wc.index(word) + 1 if word in wc else None
        rs = sc.index(word) + 1 if word in sc else None
        found = [x for x in (rw, rs) if x]
        res.append(dict(rank=min(found) if found else None,
                        rank_sent=rs))
    return res


def summarize(res, key="rank"):
    n = len(res)
    hit = lambda k: sum(1 for r in res if r[key] and r[key] <= k) / n   # noqa: E731
    return dict(
        top1=sum(1 for r in res if r[key] == 1) / n,
        top3=hit(3), top5=hit(5),
        reach=sum(1 for r in res if r[key]) / n,
        mean_rank=sum((r[key] or 60) for r in res) / n,
        mrr=sum((1.0 / r[key]) if r[key] else 0.0 for r in res) / n,
    )


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--db", default=os.path.join(
        ROOT, "PersonalIME", "PersonalIME", "app", "src", "main", "assets",
        "dict", "base_words.db"))
    ap.add_argument("--lm", default=os.path.join(os.path.dirname(os.path.abspath(__file__)),
                                                 "..", "lm", "bigram.db"))
    ap.add_argument("--beta", default="200,400,600,900")
    ap.add_argument("--k", default="8")
    ap.add_argument("--seg-pen", default="0,2000,3500,5000",
                    help="每多一段扣多少毫纳特（长度偏好，独立于 bigram 权重）")
    ap.add_argument("--center", default="1", help="1=边界项中心化 0=不中心化，逗号分隔")
    args = ap.parse_args()

    phrase_table, char_table = E.load_pinyin_sources()
    corpus = E.load_corpus(os.path.join(os.path.dirname(os.path.abspath(__file__)), "corpus.tsv"))
    cases = []
    for word, domain, prio, kind in corpus:
        py = E.annotate(word, phrase_table, char_table)
        if py:
            cases.append((word, domain, prio, kind, py, E.to_digits(py)))
    db = sqlite3.connect(args.db)
    E.detect_schema(db)

    print("语料 %d 条" % len(cases))
    print()
    print("| 中心化 | beta |  K | 段罚 |  Top-1 |  Top-3 |  Top-5 |  可达率 | 平均位次 |   MRR | 整句Top-1 |")
    print("|--------|------|---:|-----:|--------|--------|--------|---------|----------|-------|-----------|")
    for cen in [int(x) for x in args.center.split(",")]:
        lm = None
        if args.lm and os.path.exists(args.lm):
            lm = E.BigramLM(args.lm, center=bool(cen))
        for k in [int(x) for x in args.k.split(",")]:
            for sp in [int(x) for x in args.seg_pen.split(",")]:
                for b in [int(x) for x in args.beta.split(",")]:
                    res = run(db, lm, b, k, cases, seg_pen=sp)
                    s = summarize(res, "rank")
                    ss = summarize(res, "rank_sent")
                    print("| %s | %4d | %2d | %4d | %5.1f%% | %5.1f%% | %5.1f%% | %6.1f%% | %8.2f | %.3f | %8.1f%% |"
                          % ("是" if cen else "否", b, k, sp,
                             s["top1"] * 100, s["top3"] * 100, s["top5"] * 100,
                             s["reach"] * 100, s["mean_rank"], s["mrr"],
                             ss["top1"] * 100))
    return 0


if __name__ == "__main__":
    sys.exit(main())
