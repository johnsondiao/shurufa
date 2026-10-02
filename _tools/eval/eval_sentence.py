#!/usr/bin/env python
"""
整句转写 + T9 容错评测（借鉴媒体横评的「整句准确率」与经典 T9 的邻键容错能力）。

## 为什么需要它

此前的评测全是**词级**的（给定词看排名），但用户实际打字是**句子级**的：
整句拼音敲进去，看整句出来对不对。媒体横评（凤凰/太平洋对 5 款输入法的 PK）
把「整句准确率」当核心指标：1000 条 5~10 字短句，统计首选准确率与前 5 准确率。
学术界对应 pinyin-to-text 的 CER（字错误率）。我们的整句 DP（阶段 4a）从未被量化过。

T9 容错是老 T9 的标志性能力（Wikipedia）：`testing` = 8378464，打成 8278494
（两个邻键错误）仍能建议出 testing——靠检查邻键实现纠错。我们引擎没有该功能，
先测「错 1 个数字后整句崩成什么样」的基线，损失大才值得做容错。

## 方法

- 从语料抽 6~14 字纯中文短句；
- 每字的数字串直接查 base_words.db 的**单字行**（读音已按语料投票分摊，与设备端
  真实读音一致——不在评测器里重新实现注音，避免两套口径）；
- 整句数字串喂 `simulate_sentence_candidates`（复现引擎整句 DP）；
- 指标：句准确率（首选==原句）、CER（编辑距离/句长）、Top-5 命中；
- `--typo`：每句随机 1 位替换成**物理邻键**（3×4 数字键盘上下左右+斜角），
  重跑并对比，量化一次误按的破坏力。

用法：
  python eval_sentence.py --n 300            # 干净整句基线
  python eval_sentence.py --n 300 --typo     # 加容错对照
"""
from __future__ import annotations

import argparse
import collections
import gzip
import io
import os
import random
import re
import sqlite3
import sys
import tarfile

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
sys.path.insert(0, HERE)
from evaluate import detect_schema, simulate_sentence_candidates  # noqa: E402  复用引擎整句 DP

DB = os.path.join(ROOT, "PersonalIME", "PersonalIME", "app", "src", "main",
                  "assets", "dict", "base_words.db")
CORPUS = os.path.join(ROOT, "_tools", "lm", "corpus")

CJK = re.compile(r"[\u4e00-\u9fff]+")
MIN_LEN, MAX_LEN = 4, 9      # 字数。上限受整句 DP 的 16 位限制约束：
                             # 每字拼音折成 1~2 位数字（平均 ~1.7），9 字 ≈ 15 位

# 3×4 数字键盘的物理邻键（上下左右 + 斜角），容错扰动用
NEIGHBORS = {
    "1": "245", "2": "13456", "3": "256",
    "4": "12578", "5": "12346789", "6": "23589",
    "7": "458", "8": "45679", "9": "568",
}


def load_char_digits(db_path):
    """单字 -> 数字串（取 logp 最高的读音，与设备端首读音一致）。"""
    con = sqlite3.connect(db_path)
    best = {}
    for w, dg, lp in con.execute(
            "SELECT word,digits,logp FROM base_words WHERE length(word)=1"):
        if w not in best or lp > best[w][1]:
            best[w] = (dg, lp)
    con.close()
    return {ch: dg for ch, (dg, _) in best.items()}


def iter_lines():
    """opensub 字幕行（天然短句）+ wiki 行。"""
    with gzip.open(os.path.join(CORPUS, "opensub2016.gz"), "rt",
                   encoding="utf-8", errors="ignore") as f:
        for line in f:
            yield line
    with gzip.open(os.path.join(CORPUS, "wikimedia.gz"), "rt",
                   encoding="utf-8", errors="ignore") as f:
        for line in f:
            yield line


def sample_sentences(n, seed=20261002):
    rng = random.Random(seed)
    pool = []
    for i, line in enumerate(iter_lines()):
        for seg in CJK.findall(line):
            if MIN_LEN <= len(seg) <= MAX_LEN:
                pool.append(seg)
        if i > 3_000_000 or len(pool) >= 200_000:
            break
    rng.shuffle(pool)
    return pool[:n]


def edit_distance(a, b):
    if a == b:
        return 0
    prev = list(range(len(b) + 1))
    for i, ca in enumerate(a, 1):
        cur = [i]
        for j, cb in enumerate(b, 1):
            cur.append(min(prev[j] + 1, cur[j - 1] + 1,
                           prev[j - 1] + (ca != cb)))
        prev = cur
    return prev[-1]


def typo_digit(digits, rng):
    """随机 1 位替换成物理邻键。"""
    i = rng.randrange(len(digits))
    ch = digits[i]
    if ch not in NEIGHBORS:
        return digits
    nb = NEIGHBORS[ch]
    return digits[:i] + rng.choice(nb) + digits[i + 1:]


def run(con, samples, char_dg, label, limit=5):
    n = 0
    top1 = top5 = 0
    cer_sum = 0.0
    undecodable = 0
    examples_bad = []
    for sent, digits in samples:
        outs = simulate_sentence_candidates(con, digits, limit=limit)
        if not outs:
            undecodable += 1
            continue
        n += 1
        hit1 = outs[0] == sent
        hit5 = sent in outs
        top1 += hit1
        top5 += hit5
        cer = edit_distance(outs[0], sent) / len(sent)
        cer_sum += min(cer, 2.0)
        if not hit1 and len(examples_bad) < 12:
            examples_bad.append((sent, outs[0]))
    if n == 0:
        print(f"[{label}] 没有可解码样本")
        return None
    print(f"\n== {label} ==")
    print(f"  样本 {n}（不可解码跳过 {undecodable}）")
    print(f"  整句首选准确率  {top1 / n * 100:6.1f}%")
    print(f"  整句 Top-5 命中 {top5 / n * 100:6.1f}%")
    print(f"  平均 CER        {cer_sum / n * 100:6.1f}%")
    if examples_bad:
        print("  失误样例（原句 -> 首选）:")
        for s, o in examples_bad:
            print(f"    {s}  ->  {o}")
    return dict(n=n, top1=top1 / n, top5=top5 / n, cer=cer_sum / n)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--n", type=int, default=300)
    ap.add_argument("--typo", action="store_true", help="附加错 1 键对照")
    ap.add_argument("--db", default=DB)
    ap.add_argument("--seed", type=int, default=20261002)
    args = ap.parse_args()

    char_dg = load_char_digits(args.db)
    print(f"单字读音 {len(char_dg)} 字（取库内最高 logp 读音）")

    con = sqlite3.connect(args.db)
    detect_schema(con)          # simulate_sentence_candidates 依赖 SCHEMA 全局
    rng = random.Random(args.seed)
    samples = []
    for sent in sample_sentences(args.n * 3, args.seed):
        # 按"数字位数"过滤（整句 DP 上限 16 位），不是按字数——
        # 每字 1~2 位数字，9 字句就可能 15 位。第一版按字数算，300 句只挑出 2 句。
        # 语料含繁体/生僻字（库 8105 字之外），逐字有读音才收，别在 sum 里二次索引。
        if not all(ch in char_dg for ch in sent):
            continue
        digits = "".join(char_dg[ch] for ch in sent)
        if 4 <= len(digits) <= 16:
            samples.append((sent, digits))
        if len(samples) >= args.n:
            break
    print(f"可评测样本 {len(samples)} 句")

    clean = run(con, samples, char_dg, "干净输入（整句一次敲完）")

    if args.typo:
        typo_samples = [(s, typo_digit(d, rng)) for s, d in samples]
        bad = run(con, typo_samples, char_dg, "错 1 键（物理邻键扰动）")
        if clean and bad:
            print("\n== 容错基线结论 ==")
            print(f"  首选准确率 {clean['top1'] * 100:.1f}% -> {bad['top1'] * 100:.1f}%"
                  f"（绝对掉 {abs(clean['top1'] - bad['top1']) * 100:.1f}pt）")
            print(f"  CER        {clean['cer'] * 100:.1f}% -> {bad['cer'] * 100:.1f}%")
            print("  一次误按即整句崩 -> 值得做邻键容错；掉得不多 -> 优先级低")
    return 0


if __name__ == "__main__":
    sys.exit(main())
