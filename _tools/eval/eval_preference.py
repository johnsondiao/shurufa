# -*- coding: utf-8 -*-
"""
用户偏好（"选择即学习"）的离线度量。

## 为什么需要这个脚本

偏好加成是**设备端在线学习**，没法用离线语料直接评测——但它的**数学行为**可以完全
离线复现：给定某个数字组的基础 logp 列表，假设用户选了其中第 r 名的词 cnt 次、
距今 Δt 天，算出它加成后的新位次。这就能回答三个关键问题：

  1. 一次"有意选择"能把这个词顶到多高？（会不会像旧实现那样横扫整组）
  2. 要反复用多少次，才能把一个靠后的词稳定顶上来？
  3. 不再用了以后，它多快退场？

## 口径

位次在**同一个 T9 数字组内**计算——跨数字组不竞争，加成有没有用只看组内。
默认取每个组的第 2/3/5/10/20 名做目标：第 1 名不需要加成（本来就在顶上），
第 20 名以后基本已在候选窗口（60 条）外、用户看不到也选不了。

基础 logp 与设备端 `assets/dict/base_words.db` 用的是同一份文件，
所以脚本跑出来的位次就是真机上的位次。
"""

import argparse
import hashlib
import io
import math
import os
import sqlite3
import sys

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
DEFAULT_DB = os.path.join(
    ROOT, "PersonalIME", "PersonalIME", "app", "src", "main", "assets", "dict", "base_words.db"
)
DEFAULT_OUT = os.path.join(ROOT, "_tools", "eval", "report_preference.md")

# ── 与设备端 DictionaryDatabase.kt 保持一致的参数（改一处必须改两处）────────────
PREF_DOMINANCE = 40000          # 加成上限（毫纳特）
PREF_BONUS_PER_NAT = 6000       # 每 1 纳特偏好权重对应的加成（毫纳特）
PREF_MIN_WEIGHT = 0.15          # 「捞回」门槛：低于此权重不把窗口外的词补进候选集
PREF_HALF_LIFE_DAYS = 7.0

# 旧实现（二值门槛），只用于对照
OLD_PREF_DOMINANCE = 40000
OLD_PREF_MIN_WEIGHT = 0.15

TARGET_RANKS = (2, 3, 5, 10, 20)
COUNTS = (1, 2, 3, 5, 10, 20, 50, 100)
DECAY_DAYS = (0, 1, 3, 7, 14, 21, 30, 45)

# 正文里要举的那个真实例子：与「你好」同键（`64426`）的低频词，
# 用户在这个键上误选过它一次，旧规则下「你好」就被顶掉了。
EXAMPLE_WORD = "蜜柑"


def rel_to_root(p):
    """把路径相对化用于展示。Windows 上跨盘符会抛异常，退化为原路径。"""
    try:
        return os.path.relpath(p, ROOT)
    except ValueError:
        return p


KOTLIN_SRC = os.path.join(
    ROOT, "PersonalIME", "PersonalIME", "app", "src", "main", "java",
    "com", "personal", "ime", "data", "DictionaryDatabase.kt"
)


def check_kotlin_constants():
    """
    校验本脚本的参数与设备端 Kotlin 常量一致。

    存在的理由：这个脚本是**设备端在线学习行为的离线复现**，一旦两边参数漂移，
    报告就会描述一个真机上并不存在的行为——而这个项目已经吃过一次
    "报告数字与打包产物对不上"的亏（详见 OVERHAUL_PLAN.md）。
    宁可让脚本直接报错，也不要产出一份漂亮但失真的报告。
    """
    if not os.path.exists(KOTLIN_SRC):
        print(f"[warn] 未找到 {rel_to_root(KOTLIN_SRC)}，跳过常量一致性校验")
        return
    src = open(KOTLIN_SRC, encoding="utf-8").read()
    import re
    expect = {
        "PREF_DOMINANCE": PREF_DOMINANCE,
        "PREF_BONUS_PER_NAT": PREF_BONUS_PER_NAT,
        "PREF_MIN_WEIGHT": PREF_MIN_WEIGHT,
        "PREF_HALF_LIFE_DAYS": PREF_HALF_LIFE_DAYS,
    }
    bad = []
    for name, want in expect.items():
        m = re.search(rf"const val {name}\s*=\s*([0-9.]+)", src)
        if not m:
            bad.append(f"{name} 在 Kotlin 源里找不到")
            continue
        got = float(m.group(1))
        if abs(got - want) > 1e-9:
            bad.append(f"{name}: 脚本 {want} ≠ Kotlin {got}")
    if bad:
        raise SystemExit(
            "[致命] 脚本参数与设备端不一致，报告将失真：\n  - " + "\n  - ".join(bad)
        )
    print(f"[ok] 参数与 DictionaryDatabase.kt 一致（{len(expect)} 项）")


def md5_12(path):
    h = hashlib.md5()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()[:12]


def pref_weight(cnt, days):
    """习惯强度（纳特）：ln(1+cnt) · 2^(-Δt/半衰期)"""
    if cnt <= 0:
        return 0.0
    return math.log(1.0 + cnt) * 0.5 ** (days / PREF_HALF_LIFE_DAYS)


def bonus_new(weight):
    """
    新：连续加成。

    **刻意不设门槛**。加了门槛就会在门槛处出现断崖：权重 0.15 时加成 900 毫纳特，
    一过门槛直接变 0——而同组最近对手的分差 25 分位才 522，这道断崖是看得见的。
    没有门槛时加成随权重（= ln(1+cnt)·指数衰减）平滑趋近 0，退场是渐变的。
    """
    if weight <= 0.0:
        return 0
    return min(PREF_DOMINANCE, int(round(PREF_BONUS_PER_NAT * weight)))


def bonus_old(weight):
    """旧：二值门槛——过线就加满"""
    return OLD_PREF_DOMINANCE if weight >= OLD_PREF_MIN_WEIGHT else 0


def load_groups(db_path):
    """digits -> [logp 降序]，只保留有竞争（≥2 条）的组"""
    con = sqlite3.connect(db_path)
    groups = {}
    for digits, logp in con.execute(
        "SELECT digits, logp FROM base_words ORDER BY digits, logp DESC"
    ):
        groups.setdefault(digits, []).append(logp)
    con.close()
    return groups


def percentile(sorted_vals, p):
    if not sorted_vals:
        return 0
    idx = min(len(sorted_vals) - 1, int(len(sorted_vals) * p))
    return sorted_vals[idx]


def rank_after(lps, target_idx, bonus):
    """目标词加成后，在该组内的新位次（1 起）"""
    mine = lps[target_idx] + bonus
    return 1 + sum(1 for j, lp in enumerate(lps) if j != target_idx and lp > mine)


def fmt_row(cells):
    return "| " + " | ".join(cells) + " |"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--db", default=DEFAULT_DB)
    ap.add_argument("--out", default=DEFAULT_OUT)
    args = ap.parse_args()

    db_path = os.path.abspath(args.db)
    check_kotlin_constants()
    groups = load_groups(db_path)
    total_groups = len(groups)
    all_rows = sum(len(v) for v in groups.values())
    print(f"数字组 {total_groups}，词条 {all_rows}（{rel_to_root(db_path)}）")
    print(f"库 md5[:12] = {md5_12(db_path)}")

    lines = []
    w = lines.append
    w("# 用户偏好（选择即学习）行为报告\n")
    w(f"- 数据库：`{rel_to_root(db_path)}`")
    w(f"- 库 md5：`{md5_12(db_path)}`")
    w(f"- 数字组 {total_groups} 组 / 词条 {all_rows} 条")
    w(f"- 参数：`PREF_DOMINANCE={PREF_DOMINANCE}`（毫纳特）"
      f" / `PREF_BONUS_PER_NAT={PREF_BONUS_PER_NAT}` / 半衰期 {PREF_HALF_LIFE_DAYS:g} 天")
    w(f"- 加成**无门槛**（连续衰减到 0）；`{PREF_MIN_WEIGHT}` 只用于决定"
      "「要不要把候选窗口外的偏好词捞回来」")
    w("- 口径：位次一律在**同一 T9 数字组内**计算；目标是该组第 r 名的词\n")

    # ── 一、同组内分差基线：加成该对齐的尺度 ──
    w("## 一、同组内分差基线（加成该对齐的尺度）\n")
    w(fmt_row(["起点位次", "样本组数", "中位分差", "25 分位", "75 分位", "90 分位"]))
    w(fmt_row(["---"] * 6))
    base_rows = {}
    for r in TARGET_RANKS:
        gaps = sorted(
            lps[0] - lps[r - 1] for lps in groups.values() if len(lps) >= r
        )
        base_rows[r] = gaps
        w(fmt_row([
            f"第 {r} 名 → 第 1 名", f"{len(gaps)}",
            f"{percentile(gaps, .5)}", f"{percentile(gaps, .25)}",
            f"{percentile(gaps, .75)}", f"{percentile(gaps, .90)}",
        ]))
    w("")
    w("> 这决定了加成「多大算大」。同组最近的对手（第 2 名）中位只差 "
      f"{percentile(base_rows[2], .5)} 毫纳特；"
      "而旧实现的无条件 +40000 是这个数的 "
      f"{OLD_PREF_DOMINANCE / max(1, percentile(base_rows[2], .5)):.0f} 倍，"
      "等于把整组静音。\n")

    # ── 二、加成映射 ──
    w("## 二、加成映射（权重 → 毫纳特）\n")
    w(fmt_row(["选择次数", "权重(纳特)", "旧加成", "新加成", "新/上限"]))
    w(fmt_row(["---"] * 5))
    for cnt in COUNTS:
        wt = pref_weight(cnt, 0)
        w(fmt_row([
            f"{cnt} 次", f"{wt:.3f}", f"{bonus_old(wt)}", f"{bonus_new(wt)}",
            f"{100.0 * bonus_new(wt) / PREF_DOMINANCE:.0f}%",
        ]))
    w("")

    # ── 三、一次选择能顶多高（核心问题）──
    w("## 三、选择之后的位次：旧规则 vs 新规则\n")
    w(fmt_row(["起点位次", "选择次数", "旧·到第 1 名", "新·到第 1 名",
               "新·到前 3", "新·平均位次"]))
    w(fmt_row(["---"] * 6))
    for r in TARGET_RANKS:
        for cnt in (1, 3, 10):
            top1_old = top1_new = top3_new = 0
            rank_sum = 0
            n = 0
            for lps in groups.values():
                if len(lps) < r:
                    continue
                n += 1
                wt = pref_weight(cnt, 0)
                ro = rank_after(lps, r - 1, bonus_old(wt))
                rn = rank_after(lps, r - 1, bonus_new(wt))
                if ro == 1:
                    top1_old += 1
                if rn == 1:
                    top1_new += 1
                if rn <= 3:
                    top3_new += 1
                rank_sum += rn
            if not n:
                continue
            w(fmt_row([
                f"第 {r} 名", f"{cnt} 次",
                f"{100.0 * top1_old / n:.1f}%", f"{100.0 * top1_new / n:.1f}%",
                f"{100.0 * top3_new / n:.1f}%", f"{rank_sum / n:.2f}",
            ]))
    w("")

    # ── 四、衰减：多久退场 ──
    w("## 四、一次「误选」的退场过程\n")
    w("目标 = 该组第 2 名；表中是「加成后是否反超第 1 名」的比例。\n")
    w(fmt_row(["距上次选择", "权重(纳特)", "新加成", "新·仍反超", "旧加成", "旧·仍反超"]))
    w(fmt_row(["---"] * 6))
    for days in DECAY_DAYS:
        wt = pref_weight(1, days)
        bn, bo = bonus_new(wt), bonus_old(wt)
        wn = wo = n = 0
        for lps in groups.values():
            if len(lps) < 2:
                continue
            n += 1
            if lps[1] + bn > lps[0]:
                wn += 1
            if lps[1] + bo > lps[0]:
                wo += 1
        w(fmt_row([
            f"{days} 天", f"{wt:.4f}", f"{bn}", f"{100.0 * wn / max(1, n):.2f}%",
            f"{bo}", f"{100.0 * wo / max(1, n):.2f}%",
        ]))
    w("")
    w("> 旧规则直到权重跌破 "
      f"{OLD_PREF_MIN_WEIGHT}（一次误选约 15.5 天）都是 100% 霸榜，然后**阶跃式**掉到 0；\n"
      "> 新规则的掌握度随天数平滑回落，不存在「突然忘掉」的时刻。\n")

    # ── 五、具体例子（看得见的那种）──
    con = sqlite3.connect(db_path)
    row = con.execute(
        "SELECT digits FROM base_words WHERE word = ? LIMIT 1", (EXAMPLE_WORD,)
    ).fetchone()
    if row:
        digits = row[0]
        peers = con.execute(
            "SELECT word, logp FROM base_words WHERE digits = ? ORDER BY logp DESC",
            (digits,),
        ).fetchall()
        idx = next((i for i, (word, _) in enumerate(peers) if word == EXAMPLE_WORD), None)
        if idx is not None and len(peers) >= 2:
            group_lps = [lp for _, lp in peers]
            mine = group_lps[idx]
            leader, leader_lp = peers[0]
            w(f"## 五、具体例子：`{digits}` 上的「{EXAMPLE_WORD}」\n")
            w("该组前 5 名：" + "、".join(
                f"`{word}`({lp})" for word, lp in peers[:5]
            ))
            w("")
            w(f"- 「{EXAMPLE_WORD}」基础 logp = **{mine}**（同组第 {idx + 1} 名），"
              f"第 1 名「{leader}」= {leader_lp}，分差 **{leader_lp - mine}**")
            w("")
            w(fmt_row(["选择次数", "新加成", "新·位次", "旧加成", "旧·位次"]))
            w(fmt_row(["---"] * 5))
            for cnt in (1, 3, 5, 10, 20):
                wt = pref_weight(cnt, 0)
                bn, bo = bonus_new(wt), bonus_old(wt)
                w(fmt_row([
                    f"{cnt} 次", f"{bn}", f"{rank_after(group_lps, idx, bn)}",
                    f"{bo}", f"{rank_after(group_lps, idx, bo)}",
                ]))
            w("")
            w(f"> 这就是那个真实的抱怨：在 `{digits}` 上误选过一次「{EXAMPLE_WORD}」，"
              f"旧规则立刻把它顶到第 1 名并**维持 15.5 天**，"
              f"于是打「你好」出来的第一个词是「{EXAMPLE_WORD}」。\n"
              f"> 新规则下选 1 次远远不够（要补 {leader_lp - mine}，只补 "
              f"{bonus_new(pref_weight(1, 0))}），得真的用过几次才会上位。\n")
    con.close()

    out_path = os.path.abspath(args.out)
    os.makedirs(os.path.dirname(out_path), exist_ok=True)
    with open(out_path, "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")
    print(f"\n报告已写入 {rel_to_root(out_path)}")


if __name__ == "__main__":
    main()
