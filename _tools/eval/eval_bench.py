#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
PersonalIME 大基准评测：国标口径 + 学术界 PD 分格 + 三层归因（离线）

和 evaluate.py 的分工：
  evaluate.py — 492 条人工语料，回答「定向挑出来的词排序对不对」，是回归门禁。
  本脚本     — 上万条真实语料自动生成的样本，回答「整体上好不好用、词都打不出来在哪一层」。

为什么要两套：人工语料有主观性（挑得出来说明它本来就在你脑子里），
真实语料才有分布。只看人工语料会高估水平；只看真实语料又缺少可控对照。

三套指标来源（这是本脚本存在的理由，不是自创的）：
  A. 国标 GB 18031-2000 / GB/T 19246-2003（数字键盘汉字输入）
     平均码长 < 4~6 键/字（字词混合 < 4）、重码字词键选率 < 6%（拼音）、
     首屏候选命中率 ≥ 85%（检测机构口径）。这三条是**唯一针对数字键盘**的国标指标，
     正好命中我们这个九键 T9，且全部可离线计算。
  B. 学术界 PD / P2C 基准（Zhang et al. 2019；ACL 2022 PinyinGPT）：
     按「上下文长度 0-3 / 4-9 / 10+ 字 × 目标词长 1-3 / 4-9 / 10+ 字」分格报 P@1/P@5/P@10。
     这是中文拼音输入法公认的评测口径。Google IME 在该基准 Top-1 约 57~71%。
  C. CHI / Soukoreff & MacKenzie 文本输入通用指标（KSPC、TER 思路）：
     这里落地成「期望击键数 EK」——把候选位次折算成用户实际要付出的操作步，
     比 Top-1 更贴近体感。

最重要的产出是 **D. 三层归因**：把每个「打不出来的词」分到
  L1 词库根本没有 / L2 词在库里但数字串拼不出来 / L3 拼得出来但排太靠后。
不分开这三层的后果就是瞎调参——给 L1 词调排序权重永远不会有收益。

用法：
  python eval_bench.py
  python eval_bench.py --bench bench.tsv --out report_bench.md
"""

import argparse
import io
import os
import sqlite3
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

# 复用现有引擎复现（evaluate.py 的 main 有 __main__ 保护，可安全 import）
# 必须在 import **之后**重新包一层 stdout：evaluate.py 模块级也做了一次
# TextIOWrapper 包装，第二轮包装让第一轮那个 wrapper 被回收并顺手 close 了
# 底层 buffer，于是 import 完成后的 sys.stdout 是个指向已关闭 buffer 的死对象
# ——症状是第一个 print 就抛 "I/O operation on closed file"。
from evaluate import (  # noqa: E402
    BigramLM, annotate, detect_schema, has_cjk, load_pinyin_sources,
    rel_to_root, simulate_input_t9, simulate_sentence_candidates, to_digits,
)

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
DEFAULT_DB = os.path.join(ROOT, "PersonalIME", "PersonalIME", "app",
                          "src", "main", "assets", "dict", "base_words.db")
LM_PATH = os.path.join(ROOT, "_tools", "lm", "bigram.db")
HERE = os.path.dirname(os.path.abspath(__file__))

# 与 APK 内 DictionaryDatabase / PinyinEngine 保持一致
CTX_BETA = 1600          # CTX_BETA_PER_MILLE（阶段 4a 实测值）
FIRST_SCREEN = 5         # 首屏可见候选数（用于「首屏命中率」与 L3 判定）
MISS_RANK = 999          # 不可达时的位次记数

# 0 字和 1-3 字的标签必须分开： former 是「孤立输入」，后者是「短上文」。
# 曾经两档都叫 "0-3"，报告里出现两行同标签不同数的怪现象，还会让人误以为重复计数。
CTX_BINS = [(0, 0, "无"), (1, 3, "1-3"), (4, 9, "4-9"), (10, 999, "10+")]
WORD_BINS = [(1, 2, "1-3"), (3, 4, "4-9"), (5, 999, "10+")]


def ctx_bin(n):
    return next(name for lo, hi, name in CTX_BINS if lo <= n <= hi)


def word_bin(n):
    return next(name for lo, hi, name in WORD_BINS if lo <= n <= hi)


def load_bench(path, limit=None):
    rows, skipped = [], 0
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.rstrip("\n")
            if not line.strip() or line.startswith("#"):
                continue
            p = line.split("\t")
            # 必须严格 5 列且可转整数。
            # 曾经因为语料里的制表符被分词器当成普通单字混进上下文，
            # 让 3% 的行变成 6 列，然后 int('news') 这种莫名其妙的报错。
            if len(p) != 5:
                skipped += 1
                continue
            try:
                ctx, word, src, wlen, clen = p[0], p[1], p[2], int(p[3]), int(p[4])
            except ValueError:
                skipped += 1
                continue
            rows.append(dict(ctx=ctx, word=word, src=src, wlen=wlen, clen=clen))
            if limit and len(rows) >= limit:
                break
    return rows, skipped


def in_dict(db, word):
    cur = db.cursor()
    try:
        cur.execute("SELECT 1 FROM base_words WHERE word=? LIMIT 1", (word,))
        return cur.fetchone() is not None
    except sqlite3.Error:
        return None


# ------------------------------------------------------------------ 指标

def agg(rows, key="rank"):
    """核心指标聚合。找一个子集的第 key 号位次（None=不可达）。"""
    n = len(rows)
    if not n:
        return None
    ranks = [r[key] for r in rows]
    # 不可达的位次是 None，不能进 sum()，否则 Python 直接抛
    # "unsupported operand type(s) for +: 'int' and 'NoneType'"
    reach = [rk for rk in ranks if rk]
    at = lambda k: sum(1 for r in reach if r <= k)  # noqa: E731
    base_keys = sum(r["keystrokes"] for r in rows)
    total_chars = sum(r["wlen"] for r in rows)
    # 期望击键数：基础击键 + 选键成本（位次>1 每多一位算 1 次选键）+ 不可达按重输整串计
    extra = 0
    for r in rows:
        if r[key] is None:
            extra += r["keystrokes"] + 1      # 重输整串 + 一次失败
        elif r[key] > 1:
            extra += (r[key] - 1) // FIRST_SCREEN + 1
    return dict(
        n=n,
        p1=at(1) / max(1, len(reach)),
        p5=at(FIRST_SCREEN) / max(1, len(reach)),
        p10=at(10) / max(1, len(reach)),
        top1=sum(1 for r in ranks if r == 1) / n,
        first_screen=at(FIRST_SCREEN) / n,        # 国标「首屏候选命中率」（分母含不可达）
        reach=len(reach) / n,
        mean_rank=sum(reach) / max(1, len(reach)),
        ek=(base_keys + extra) / max(1, total_chars),   # 期望击键数/字
        kspc=base_keys / max(1, total_chars),          # 纯编码击键数/字
        heavy_rate=sum(1 for r in rows if (r[key] or MISS_RANK) > FIRST_SCREEN) / n,
    )


RANK_LABEL = {1: "L0 首屏命中", 0: "L3 位次落后",
              -1: "L2 不可达", -2: "L1 词库缺失"}


def layer_of(r, rdict):
    """三层归因：词库缺失 > 不可达 > 位次落后 > 正常。"""
    if rdict is False:
        return -2, "L1 词库缺失"
    if r["rank"] is None:
        return -1, "L2 不可达"
    if r["rank"] > FIRST_SCREEN:
        return 0, "L3 位次落后"
    return 1, "L0 首屏命中"


HEADER = ("| 分组 | n | P@1 | P@5 | P@10 | 首屏命中 | 可达率 | 平均位次 | 期望击键/字 | 重码选键率 |\n"
          "|---|---|---|---|---|---|---|---|---|")


def fmt(s):
    if not s:
        return ""
    return (f"| {s['label']} | {s['n']} | {s['p1']*100:.1f}% | {s['p5']*100:.1f}% | "
            f"{s['p10']*100:.1f}% | {s['first_screen']*100:.1f}% | {s['reach']*100:.1f}% | "
            f"{s['mean_rank']:.2f} | {s['ek']:.2f} | {s['heavy_rate']*100:.1f}% |")


def line(label, s):
    if not s:
        return ""
    return (f"{label:<14} {s['n']:>5}   {s['p1']*100:>5.1f}%  {s['p5']*100:>5.1f}%  "
            f"{s['p10']*100:>5.1f}%   {s['first_screen']*100:>5.1f}%  {s['reach']*100:>5.1f}%   "
            f"{s['mean_rank']:>5.2f}   {s['ek']:>5.2f}   {s['heavy_rate']*100:>5.1f}%")


# 与上面 line() 的列宽逐格对齐（改了 line() 的 f-string 就要同步改这里）
TABLE_HEAD = (f"{'分组':<14} {'n':>5}   {'P@1':>5}  {'P@5':>5}  {'P@10':>5}   "
              f"{'首屏':>5}  {'可达':>5}   {'位次':>5}   {'击键/字':>7}   {'重码率':>7}")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--db", default=DEFAULT_DB)
    ap.add_argument("--bench", default=os.path.join(HERE, "bench.tsv"))
    ap.add_argument("--lm", default=LM_PATH)
    ap.add_argument("--out", default=os.path.join(HERE, "report_bench.md"))
    ap.add_argument("--limit", type=int, default=0)
    ap.add_argument("--worst", type=int, default=60)
    args = ap.parse_args()

    if not os.path.exists(args.db):
        print(f"[错误] 找不到数据库 {args.db}")
        return 1
    if not os.path.exists(args.bench):
        print(f"[错误] 找不到测试集 {args.bench}；先跑 python build_testset.py")
        return 1

    lm = BigramLM(args.lm) if os.path.exists(args.lm) else None
    phrase_table, char_table = load_pinyin_sources()
    db = sqlite3.connect(args.db)
    detect_schema(db)
    total_rows = db.execute("SELECT COUNT(*) FROM base_words").fetchone()[0]

    rows, skipped = load_bench(args.bench, args.limit or None)
    print(f"测试集 {len(rows)} 条（跳过格式错误 {skipped}）｜ 词库 {total_rows} 条 ｜ "
          f"上下文重排 β={CTX_BETA/1000:.1f} ｜ 首屏取前 {FIRST_SCREEN}")
    if lm:
        print(f"语言模型：字符 bigram {len(lm.bi)} 条")
    print()

    # ---------------------------------------------------------------- 逐条评测
    results, no_py, failures = [], 0, 0
    for r in rows:
        py = annotate(r["word"], phrase_table, char_table)
        if not py or not has_cjk(r["word"]):
            no_py += 1
            continue
        digits = to_digits(py)
        # 上下文探针：与 PersonalIMEService.contextCharBeforeCursor() 一致，取末字
        ctx = r["ctx"][-1:] if r["ctx"] else ""
        try:
            wc = simulate_input_t9(db, digits, lm=lm, ctx=ctx, ctx_beta=CTX_BETA)
            sc = simulate_sentence_candidates(db, digits, lm=lm, beta=CTX_BETA,
                                              ctx=ctx, ctx_beta=CTX_BETA, sent_k=3)
        except sqlite3.Error as e:
            failures += 1
            continue
        rw = wc.index(r["word"]) + 1 if r["word"] in wc else None
        rs = sc.index(r["word"]) + 1 if r["word"] in sc else None
        rank = min(x for x in (rw, rs) if x) if (rw or rs) else None
        rdict = in_dict(db, r["word"])
        r["rank"] = rank                 # layer_of 需要，写回原始样本不影响 results 副本
        layer, layer_name = layer_of(r, rdict)
        results.append(dict(r, pinyin=py, digits=digits, keystrokes=len(digits),
                            rank=rank, rank_word=rw, rank_sent=rs, in_dict=rdict,
                            layer=layer, layer_name=layer_name,
                            ctx_bin=ctx_bin(r["clen"]), word_bin=word_bin(r["wlen"])))
        if failures:
            print(f"[警告] {failures} 条评测异常，已跳过（多为 sqlite 错误，检查 db 是否损坏）")
        if no_py:
            print(f"[警告] {no_py} 条无拼音标注，已跳过")

    if not results:
        print("无可评测条目")
        return 1

    def sub(pred):
        return [r for r in results if pred(r)]

    groups = [
        ("总体", results),
        ("无上文（孤立输入）", sub(lambda r: not r["ctx"])),
        ("带上文（真实连打）", sub(lambda r: r["ctx"])),
        ("来源 news", sub(lambda r: r["src"] == "news")),
        ("来源 spoken", sub(lambda r: r["src"] == "spoken")),
        ("来源 wiki", sub(lambda r: r["src"] == "wiki")),
        ("—— 上下文分格 ——", None),
    ]
    ctx_groups = [f"上文 {b[2]} 字" for b in CTX_BINS]
    w_groups = None

    print("=" * 104)
    print("A. 国标 GB 18031 / GB/T 19246 口径（数字键盘汉字输入）")
    print("=" * 104)
    print("   平均码长 逐字<6 键/字、字词混合<4；重码字词键选率 拼音<6%；首屏候选命中率 ≥85%\n")
    print(TABLE_HEAD)
    for name, subset in groups[:5]:
        if not subset:
            continue
        print(line(name, agg(subset)))
    print(f"\n  （首屏={FIRST_SCREEN} 个候选；重码选键率 = 需要点选/翻页才能选中的字数占比）")

    print("\n" + "=" * 104)
    print("B. 学术界 PD / P2C 分格（上下文长度 × 目标词长）")
    print("=" * 104)
    print("   Google IME 在该基准 Top-1 约 57~71%，可作参照\n")
    print(TABLE_HEAD)
    for wb in WORD_BINS:
        for cb in CTX_BINS:
            subset = sub(lambda r, w=wb, c=cb:
                         r["wlen"] >= w[0] and r["wlen"] <= w[1]
                         and r["clen"] >= c[0] and r["clen"] <= c[1])
            if subset:
                print(line(f"词长{wb[2]}/上文{cb[2]}", agg(subset)))

    print("\n" + "=" * 104)
    print("C. 三层归因（「打不出来的词」到底出在哪一层）")
    print("=" * 104)
    layer_rows = []
    for name in ("L0 首屏命中", "L1 词库缺失", "L2 不可达", "L3 位次落后"):
        subset = sub(lambda r, n=name: r["layer_name"] == n)
        if not subset:
            continue
        layer_rows.append((name, subset))
        s = agg(subset)
        print(f"  {name:<12} {len(subset):>5} 条  {len(subset)/len(results)*100:>5.1f}%   "
              f"P@1={s['p1']*100:>5.1f}%  首屏={s['first_screen']*100:>5.1f}%  "
              f"可达={s['reach']*100:>5.1f}%  平均位次={s['mean_rank']:>5.2f}  "
              f"击键/字={s['ek']:.2f}")
    print("\n  L1 词库根本没有 = 调排序权重永远救不回来，只能补词；"
          "L2/L3 才是排序问题。")

    # ---------------------------------------------------------------- 打不出来的词
    bad = [r for r in results if r["layer_name"].startswith(("L1", "L2", "L3"))]
    bad.sort(key=lambda r: (r["layer"], -(r["wlen"]), r["word"]))

    print("\n" + "=" * 104)
    print(f"D. 未命中的词（{len(bad)} / {len(results)} 条）")
    print("=" * 104)
    shown = 0
    for name in ("L1 词库缺失", "L2 不可达", "L3 位次落后"):
        part = [r for r in bad if r["layer_name"] == name]
        if not part:
            continue
        print(f"\n  【{name}】{len(part)} 条，样例：")
        for r in part[: args.worst if shown == 0 else 20]:
            rk = r["rank"] or "不可达"
            print(f"    {r['word']:<14} {r['pinyin']:<24} {r['digits']:<16} "
                  f"{r['src']}/{r['wlen']}字  位次={rk:<6} "
                  f"词条={r['rank_word'] or '-'} 整句={r['rank_sent'] or '-'}")
        shown += 1

    # ---------------------------------------------------------------- 报告
    md = []
    md.append("# PersonalIME 大基准评测报告（国标口径 + PD 分格 + 三层归因）\n")
    md.append(f"- 数据库：`{rel_to_root(args.db)}`（{total_rows} 条）")
    md.append(f"- 测试集：`{rel_to_root(args.bench)}`（{len(results)} 条，语料自动生成）")
    md.append(f"- 上下文重排 β={CTX_BETA/1000:.1f} ｜ 首屏取前 {FIRST_SCREEN} 个候选\n")
    md.append("## A. 国标三条（GB 18031-2000 数字键盘）\n")
    md.append(f"| 指标 | 要求 | 实测 | 判定 |")
    md.append("|---|---|---|---|")
    ov = agg(results)
    md.append(f"| 平均码长（字词混合输入） | < 4.0 键/字 | {ov['kspc']:.2f} |"
              f"{' 达标' if ov['kspc'] < 4 else ' 未达标'} |")
    md.append(f"| 重码字词键选率 | < 6% | {ov['heavy_rate']*100:.1f}% |"
              f"{' 达标' if ov['heavy_rate'] < 0.06 else ' 未达标'} |")
    md.append(f"| 首屏候选命中率 | ≥ 85% | {ov['first_screen']*100:.1f}% |"
              f"{' 达标' if ov['first_screen'] >= 0.85 else ' 未达标'} |")
    md.append("\n## B. 总体与分组\n")
    md.append(HEADER)
    for name, subset in groups[:5]:
        if subset:
            s = agg(subset)
            md.append(f"| {name} | {s['n']} | {s['p1']*100:.1f}% | {s['p5']*100:.1f}% | "
                      f"{s['p10']*100:.1f}% | {s['first_screen']*100:.1f}% | {s['reach']*100:.1f}% | "
                      f"{s['mean_rank']:.2f} | {s['ek']:.2f} | {s['heavy_rate']*100:.1f}% |")
    md.append("\n## C. PD 式分格（上下文长度 × 目标词长）\n")
    md.append(HEADER)
    for wb in WORD_BINS:
        for cb in CTX_BINS:
            subset = sub(lambda r, w=wb, c=cb:
                         r["wlen"] >= w[0] and r["wlen"] <= w[1]
                         and r["clen"] >= c[0] and r["clen"] <= c[1])
            if subset:
                s = agg(subset)
                md.append(f"| 词长{wb[2]}/上文{cb[2]} | {s['n']} | {s['p1']*100:.1f}% | "
                          f"{s['p5']*100:.1f}% | {s['p10']*100:.1f}% | "
                          f"{s['first_screen']*100:.1f}% | {s['reach']*100:.1f}% | "
                          f"{s['mean_rank']:.2f} | {s['ek']:.2f} | {s['heavy_rate']*100:.1f}% |")
    md.append("\n## D. 三层归因\n")
    md.append("| 层次 | 条数 | 占比 | 含义 | 对策 |")
    md.append("|---|---|---|---|---|")
    MEAN = {"L0 首屏命中": ("排对了", "—"),
            "L1 词库缺失": ("词库里根本没有这个词", "补词（structured/modern 源），调排序无效"),
            "L2 不可达": ("词在库里，但对应数字串拼不出来", "修解码/分词或补同音词条"),
            "L3 位次落后": ("能拼出来但排在首屏外", "调排序权重 / 上下文重排")}
    for name, subset in layer_rows:
        s = agg(subset)
        md.append(f"| {name} | {len(subset)} | {len(subset)/len(results)*100:.1f}% | "
                  f"{MEAN[name][0]} | {MEAN[name][1]} |")
    md.append("\n## E. 未命中的词清单\n")
    md.append("| 层次 | 词 | 拼音 | 数字串 | 来源 | 字数 | 位次 | 词条路径 | 整句路径 |")
    md.append("|---|---|---|---|---|---|---|---|---|")
    for r in bad:
        md.append(f"| {r['layer_name']} | {r['word']} | {r['pinyin']} | {r['digits']} | "
                  f"{r['src']} | {r['wlen']} | {'不可达' if not r['rank'] else r['rank']} | "
                  f"{r['rank_word'] or '-'} | {r['rank_sent'] or '-'} |")

    with open(args.out, "w", encoding="utf-8") as f:
        f.write("\n".join(md) + "\n")
    print(f"\n报告已写入 {args.out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
