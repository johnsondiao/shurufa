#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
【Legacy】复现当前 v16 词库，仅用于评测基线对照。

为什么不直接用 App 生成 dictionary.db：
  评测要在改动前后可比，需要一个可重复构建、与 App onCreate 逐条对齐的副本。
  本脚本严格复现 DictionaryDatabase.kt 的 onCreate + 8~16 升级路径的全部规则
  （含 15 个魔法档位常量），从而得到"改造前"的真实基线。

新架构请使用 build_base_words.py，本文件是留给对照用的历史快照。
运行：python build_current_v16.py --out ../eval/baseline_v16.db
"""

import argparse
import io
import os
import re
import sqlite3
import sys

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
ASSETS = os.path.join(HERE, "sources", "legacy")
"""旧版运行时资产（cn_chars/cn_words/word_freq/补丁表）的归档位置。

新架构下这些文件已从 app/src/main/assets/ 移出（App 不再读取），
但本脚本要严格复现旧版 v16 的导入规则，故仍从这里读取。
"""
KT = os.path.join(ROOT, "PersonalIME", "PersonalIME", "app", "src", "main", "java",
                  "com", "personal", "ime", "data", "DictionaryDatabase.kt")

LETTER_TO_DIGIT = {}
for _l, _d in [("abc", "2"), ("def", "3"), ("ghi", "4"), ("jkl", "5"),
               ("mno", "6"), ("pqrs", "7"), ("tuv", "8"), ("wxyz", "9")]:
    for _c in _l:
        LETTER_TO_DIGIT[_c] = _d

# ---- 与 Kotlin 完全一致的魔法档位常量（就是这套东西要被消灭）----
USER_TIER = 95
ORAL_TIER = 92
COMMON_TIER = 88
MULTI_PRON_TIER = 88
TECH_FREQ = 100
SEED_FREQ = 90


def to_digits(text):
    return "".join(LETTER_TO_DIGIT.get(c, c) for c in text.lower() if c != "'")


def tier_for_rank(rank):
    for lim, t in [(1000, 89), (3000, 86), (8000, 83), (15000, 79),
                   (25000, 74), (35000, 70), (45000, 65)]:
        if rank <= lim:
            return t
    return 62


def read_asset(name):
    path = os.path.join(ASSETS, name)
    if not os.path.exists(path):
        return []
    with open(path, encoding="utf-8") as f:
        return [ln.rstrip("\n") for ln in f if ln.strip()]


def read_pairs(body):
    out = []
    for m in re.finditer(r'"([a-zA-Z\']+?)"\s+to\s+"([^"]+)"', body):
        out.append((m.group(1).lower(), m.group(2)))
    return out


def build(out_path):
    if os.path.exists(out_path):
        os.remove(out_path)
    db = sqlite3.connect(out_path)
    db.isolation_level = None
    c = db.cursor()

    c.execute("PRAGMA journal_mode=OFF")
    c.execute("PRAGMA synchronous=OFF")
    c.execute("""CREATE TABLE words(
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        pinyin TEXT NOT NULL, word TEXT NOT NULL,
        frequency INTEGER DEFAULT 1, digits TEXT NOT NULL DEFAULT '',
        UNIQUE(pinyin, word))""")

    kt = open(KT, encoding="utf-8").read()
    # insertTechTerms 的 techTerms mapOf + commonWords listOf + insertPhraseSeeds mapOf
    # 三者都是 "拼音" to "词" 形式；Kotlin 中英文词 100、中文词 90
    pairs = read_pairs(kt)
    c.execute("BEGIN")
    rows = []
    for py, word in pairs:
        freq = TECH_FREQ if re.search(r"[a-zA-Z]", word) else SEED_FREQ
        rows.append((py, word, freq, to_digits(py)))
    c.executemany("INSERT OR IGNORE INTO words(pinyin,word,frequency,digits) VALUES(?,?,?,?)", rows)
    print(f"  手编种子：{len(rows)} 条")

    # loadAssetWords：cn_chars.txt / cn_words.txt，每行 "拼音 词 词频"
    for asset in ("cn_chars.txt", "cn_words.txt"):
        rows = []
        for line in read_asset(asset):
            p = line.split(" ")
            if len(p) != 3:
                continue
            try:
                freq = int(p[2])
            except ValueError:
                continue
            rows.append((p[0], p[1], freq, to_digits(p[0])))
        c.executemany("INSERT OR IGNORE INTO words(pinyin,word,frequency,digits) VALUES(?,?,?,?)", rows)
        print(f"  {asset}：{len(rows)} 条")

    c.execute("CREATE INDEX idx_words_word ON words(word)")
    c.execute("COMMIT")

    # applyWordFreqTiers（v16：8 档），只升不降，仅 2+ 字词
    c.execute("BEGIN")
    rows = []
    for line in read_asset("word_freq.txt"):
        p = line.split(" ")
        if len(p) != 2:
            continue
        try:
            t = tier_for_rank(int(p[1]))
        except ValueError:
            continue
        rows.append((t, p[0], t))
    c.executemany("UPDATE words SET frequency=? WHERE word=? AND length(word)>=2 AND frequency<?", rows)
    c.execute("COMMIT")
    print(f"  word_freq 分档：{len(rows)} 条")

    # boostCommonWords（88）
    rows = [(COMMON_TIER, p[0], p[1], COMMON_TIER) for p in
            (ln.split(" ") for ln in read_asset("common_boost.txt")) if len(p) == 2]
    c.execute("BEGIN")
    c.executemany("UPDATE words SET frequency=? WHERE pinyin=? AND word=? AND frequency<?", rows)
    c.execute("COMMIT")
    print(f"  common_boost：{len(rows)} 条")

    # boostCommonChars（字 词频），仅单字
    rows = []
    for line in read_asset("char_boost.txt"):
        p = line.split(" ")
        if len(p) != 2:
            continue
        try:
            f = int(p[1])
        except ValueError:
            continue
        rows.append((f, p[0], f))
    c.execute("BEGIN")
    c.executemany("UPDATE words SET frequency=? WHERE word=? AND length(word)=1 AND frequency<?", rows)
    c.execute("COMMIT")
    print(f"  char_boost：{len(rows)} 条")

    # applyOralBoost（92）
    rows = []
    for line in read_asset("oral_boost.txt"):
        p = line.split(" ")
        if len(p) == 2:
            rows.append((ORAL_TIER, p[0], p[1], ORAL_TIER))
    c.execute("BEGIN")
    c.executemany("UPDATE words SET frequency=? WHERE pinyin=? AND word=? AND frequency<?", rows)
    c.execute("COMMIT")
    print(f"  oral_boost：{len(rows)} 条")

    # normalizeWordDuplicates：连写拼音行让位给分隔拼音行，词频取同词同数字最大值
    c.execute("BEGIN")
    c.execute("""UPDATE words SET frequency=(SELECT MAX(w2.frequency) FROM words w2
                   WHERE w2.word=words.word AND w2.digits=words.digits)
                 WHERE pinyin GLOB '*''*' AND length(word)>=2 AND EXISTS(
                   SELECT 1 FROM words w3 WHERE w3.word=words.word AND w3.digits=words.digits
                     AND w3.pinyin NOT GLOB '*''*')""")
    c.execute("""DELETE FROM words WHERE pinyin NOT GLOB '*''*' AND length(word)>=2 AND EXISTS(
                   SELECT 1 FROM words w4 WHERE w4.word=words.word AND w4.digits=words.digits
                     AND w4.pinyin GLOB '*''*')""")
    c.execute("COMMIT")

    # insertMultiPron（88）
    rows = []
    for line in read_asset("multi_pron.txt"):
        p = line.split(" ")
        if len(p) == 2:
            rows.append((p[0], p[1], MULTI_PRON_TIER, to_digits(p[0])))
    c.execute("BEGIN")
    c.executemany("INSERT OR IGNORE INTO words(pinyin,word,frequency,digits) VALUES(?,?,?,?)", rows)
    c.executemany("UPDATE words SET frequency=? WHERE pinyin=? AND word=? AND frequency<?",
                  [(MULTI_PRON_TIER, py, w, MULTI_PRON_TIER) for py, w, _f, _d in rows])
    c.execute("COMMIT")
    print(f"  multi_pron：{len(rows)} 条")

    c.execute("CREATE INDEX idx_words_digits_freq ON words(digits, frequency DESC)")
    c.execute("CREATE INDEX idx_words_pinyin_freq ON words(pinyin, frequency DESC)")
    n = c.execute("SELECT COUNT(*) FROM words").fetchone()[0]
    c.execute("PRAGMA user_version=16")
    db.close()
    print(f"  完成：{out_path}（{n} 条）")
    return n


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default=os.path.join(HERE, "..", "eval", "baseline_v16.db"))
    a = ap.parse_args()
    print("复现当前 v16 词库 …")
    build(os.path.abspath(a.out))
