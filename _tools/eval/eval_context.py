#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
上下文重排评测（阶段 4a）

回答一个问题：**"上一个上屏词的末字" 这个上下文，能不能把当前候选排对？**

对同一条输入，跑两遍：
  无上下文 —— 只看词频先验（等于用户刚开始打、或者光标前面没有中文）
  有上下文 —— 分数再加 β_ctx · 中心化 bigram(前文末字, 候选首字)

两侧都同时跑词条路径与整句路径，取更优位次（和真实上屏一致：用户不关心哪条路径给的）。

用法：
  python eval_context.py --db <base_words.db> --lm <bigram.db> --ctx-beta 600,900,1200
"""

import argparse
import os
import sqlite3
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import evaluate as E  # noqa: E402

try:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
except Exception:
    pass

HERE = os.path.dirname(os.path.abspath(__file__))
DEFAULT_DB = os.path.join(os.path.dirname(os.path.dirname(HERE)),
                          "PersonalIME", "PersonalIME", "app", "src", "main",
                          "assets", "dict", "base_words.db")
DEFAULT_LM = os.path.join(os.path.dirname(HERE), "lm", "bigram.db")


def load_cases(path):
    cases = []
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.rstrip("\n")
            if not line.strip() or line.startswith("#"):
                continue
            p = line.split("\t")
            if len(p) < 2:
                continue
            note = p[2].strip() if len(p) > 2 else ""
            cases.append((p[0].strip(), p[1].strip(), note))
    return cases


def rank_of(word, wc, sc):
    rw = wc.index(word) + 1 if word in wc else None
    rs = sc.index(word) + 1 if word in sc else None
    found = [x for x in (rw, rs) if x]
    return min(found) if found else None, rw, rs


def run(db, lm, cases, digits_of, ctx_beta, beta, sent_k, seg_penalty, use_ctx):
    rows = []
    for prev, word, note in cases:
        d = digits_of.get(word)
        if not d:
            continue
        ctx = prev[-1] if use_ctx else None
        wc = E.simulate_input_t9(db, d, lm=lm, ctx=ctx, ctx_beta=ctx_beta)
        sc = E.simulate_sentence_candidates(db, d, lm=lm, beta=beta, sent_k=sent_k,
                                            seg_penalty=seg_penalty,
                                            ctx=ctx, ctx_beta=ctx_beta)
        r, rw, rs = rank_of(word, wc, sc)
        rows.append(dict(prev=prev, word=word, note=note, digits=d, rank=r,
                         rank_word=rw, rank_sent=rs))
    return rows


def summarize(rows):
    n = len(rows) or 1
    miss = 60
    hit = lambda k: sum(1 for r in rows if r["rank"] and r["rank"] <= k) / n  # noqa: E731
    return dict(n=len(rows),
                top1=sum(1 for r in rows if r["rank"] == 1) / n,
                top3=hit(3), top5=hit(5), top10=hit(10),
                reach=sum(1 for r in rows if r["rank"]) / n,
                mean_rank=sum((r["rank"] or miss) for r in rows) / n,
                mrr=sum((1.0 / r["rank"]) if r["rank"] else 0.0 for r in rows) / n)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--db", default=DEFAULT_DB)
    ap.add_argument("--lm", default=DEFAULT_LM)
    ap.add_argument("--corpus", default=os.path.join(HERE, "context.tsv"))
    ap.add_argument("--ctx-beta", default="0,400,700,1000,1400,2000",
                    help="上下文权重（毫，逗号分隔，0=关闭）")
    ap.add_argument("--beta", type=int, default=600, help="串内 bigram 权重（毫）")
    ap.add_argument("--sent-k", type=int, default=8)
    ap.add_argument("--seg-penalty", type=int, default=1500)
    ap.add_argument("--out", default=None)
    ap.add_argument("--worst", type=int, default=12)
    args = ap.parse_args()

    if not os.path.exists(args.db):
        print(f"[错误] 找不到数据库：{args.db}")
        return 1
    if not os.path.exists(args.lm):
        print(f"[错误] 找不到语言模型：{args.lm}")
        return 1

    phrase_table, char_table = E.load_pinyin_sources()
    cases = load_cases(args.corpus)
    db = sqlite3.connect(args.db)
    E.detect_schema(db)
    lm = E.BigramLM(args.lm, center=True)

    digits_of, skipped = {}, []
    for _prev, word, _note in cases:
        py = E.annotate(word, phrase_table, char_table)
        if not py:
            skipped.append(word)
            continue
        # 注意：keyed by word，所以同词多前文（今天/明天/昨天…）在这里合并成一个数字串，
        # 但下面 run() 是逐条 (前文, 期望词) 跑的——用例数按 cases 算，不按本字典算。
        digits_of[word] = E.to_digits(py)

    print(f"用例 {len(cases)} 条（{len(digits_of)} 个不同期望词）"
          f" ｜ 跳过 {len(skipped)} 条"
          f"{'（' + ' '.join(skipped) + '）' if skipped else ''}")
    print(f"串内 β={args.beta/1000:.2f} ｜ 段罚={args.seg_penalty/1000:.2f} 纳特"
          f" ｜ DP-K={args.sent_k}\n")

    lines = ["# 上下文重排评测（阶段 4a）", "",
             f"- 数据库：`{args.db}`",
             f"- 语言模型：`{args.lm}`",
             f"- 用例：`{args.corpus}`（{len(cases)} 条）", "",
             "分数 = Σ logp(词) + β·Σ 串内中心化 bigram − 段罚·(段数−1)"
             " + β_ctx·中心化 bigram(前文末字, 候选首字)", "",
             "|  β_ctx | 上下文 |  Top-1 |  Top-3 |  Top-5 | 可达率 | 平均位次 |   MRR |",
             "|--------|--------|--------|--------|--------|--------|----------|-------|"]

    best_top1, best_rows, best_cb = -1.0, None, None
    for cb in [int(x) for x in args.ctx_beta.split(",")]:
        rows = run(db, lm, cases, digits_of, cb, args.beta, args.sent_k,
                   args.seg_penalty, use_ctx=(cb != 0))
        s = summarize(rows)
        lines.append(f"| {cb:>6} | {'有' if cb else '无':<6} | {s['top1']*100:>5.1f}% | "
                     f"{s['top3']*100:>5.1f}% | {s['top5']*100:>5.1f}% | {s['reach']*100:>5.1f}% | "
                     f"{s['mean_rank']:>8.2f} | {s['mrr']:>5.3f} |")
        print(f"β_ctx={cb:>5}  Top-1 {s['top1']*100:5.1f}%  Top-3 {s['top3']*100:5.1f}%  "
              f"可达 {s['reach']*100:5.1f}%  位次 {s['mean_rank']:5.2f}  MRR {s['mrr']:.3f}")
        if cb and s["top1"] > best_top1:
            best_top1, best_rows, best_cb = s["top1"], rows, cb

    if best_rows:
        cb = best_cb
        base_rows = run(db, lm, cases, digits_of, 0, args.beta, args.sent_k,
                        args.seg_penalty, use_ctx=False)
        base = {r["word"]: r for r in base_rows}
        lines += ["", f"## β_ctx={cb} 相对无上下文的逐条变化", "",
                  "| 前文 | 期望词 | 数字串 | 无上下文 | 有上下文 | 说明 |",
                  "|---|---|---|---|---|---|"]
        changed = [r for r in best_rows
                   if base.get(r["word"], {}).get("rank") != r["rank"]]
        changed.sort(key=lambda r: (base.get(r["word"], {}).get("rank") or 99))
        for r in changed[:args.worst]:
            b = base.get(r["word"], {}).get("rank")
            lines.append(f"| {r['prev']} | {r['word']} | `{r['digits']}` | "
                         f"{b if b else '不可达'} | **{r['rank'] if r['rank'] else '不可达'}** | {r['note']} |")
        if not changed:
            lines.append("| — | — | — | — | — | （无变化） |")
        lines += ["", f"改善 {sum(1 for r in changed if (base.get(r['word'], {}).get('rank') or 99) > (r['rank'] or 99))} 条 ｜ "
                      f"变差 {sum(1 for r in changed if (base.get(r['word'], {}).get('rank') or 99) < (r['rank'] or 99))} 条"]

    text = "\n".join(lines) + "\n"
    if args.out:
        with open(args.out, "w", encoding="utf-8") as f:
            f.write(text)
        print(f"\n报告已写入 {args.out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
