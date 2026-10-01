"""离线冒烟测试：把 DictionaryDatabase 里的 SQL 原样搬到 Python 跑一遍。
目的：编译期抓不到 SQL 语法/索引问题（如 ATTACH 别名、GLOB 模式、ORDER BY 字段）。
"""
import os
import sqlite3
import sys
import time

BASE_DB = os.path.join(
    os.path.dirname(__file__), "..", "..",
    "PersonalIME", "PersonalIME", "app", "src", "main", "assets", "dict", "base_words.db",
)
BASE_DB = os.path.abspath(BASE_DB)

ALIAS = "base"
TABLE = "base_words"
T_USER_WORDS = "user_words"
T_USER_PREF = "user_pref"

LETTER_TO_DIGIT = {
    **{c: "2" for c in "abc"}, **{c: "3" for c in "def"}, **{c: "4" for c in "ghi"},
    **{c: "5" for c in "jkl"}, **{c: "6" for c in "mno"}, **{c: "7" for c in "pqrs"},
    **{c: "8" for c in "tuv"}, **{c: "9" for c in "wxyz"},
}


def to_digits(text):
    return "".join(LETTER_TO_DIGIT.get(c, c) for c in text.lower() if c != "'")


def setup():
    c = sqlite3.connect(":memory:")
    c.execute(f"ATTACH DATABASE ? AS {ALIAS}", (BASE_DB,))
    c.executescript(f"""
        CREATE TABLE {T_USER_WORDS}(
            pinyin TEXT NOT NULL, word TEXT NOT NULL, digits TEXT NOT NULL,
            logp INTEGER NOT NULL, added_at INTEGER NOT NULL, PRIMARY KEY(digits, word));
        CREATE INDEX idx_user_words_word ON {T_USER_WORDS}(word);
        CREATE TABLE {T_USER_PREF}(
            scope TEXT NOT NULL, key TEXT NOT NULL, digits TEXT NOT NULL,
            cnt INTEGER NOT NULL, t_last INTEGER NOT NULL, PRIMARY KEY(scope, key, digits));
        CREATE INDEX idx_user_pref_digits ON {T_USER_PREF}(digits);
    """)
    return c


def q_exact(c, digits, limit):
    return c.execute(
        f"SELECT word,pinyin,logp,flags FROM {ALIAS}.{TABLE} "
        "WHERE digits = ? ORDER BY logp DESC LIMIT ?", (digits, limit)).fetchall()


def q_prefix(c, digits, limit):
    return c.execute(
        f"SELECT word,pinyin,logp,flags FROM {ALIAS}.{TABLE} "
        "WHERE digits GLOB ? ORDER BY logp DESC LIMIT ?", (digits + "*", limit)).fetchall()


def q_words_by_prefix(c, prefix, limit):
    return c.execute(
        f"SELECT word,pinyin,logp,flags FROM {ALIAS}.{TABLE} "
        "WHERE word GLOB ? AND word != ? ORDER BY logp DESC LIMIT ?",
        (prefix + "*", prefix, limit)).fetchall()


def q_words_en(c, prefix, limit):
    return c.execute(
        f"SELECT word FROM {T_USER_WORDS} "
        "WHERE word GLOB ? AND word GLOB '[a-zA-Z]*' ORDER BY added_at DESC LIMIT ?",
        (prefix.lower() + "*", limit)).fetchall()


def main():
    if not os.path.exists(BASE_DB):
        print("BASE DB MISSING:", BASE_DB)
        return 1
    c = setup()
    now = int(time.time())

    # 1) 核心样例：你好 = 64426
    rows = q_exact(c, to_digits("ni'hao"), 6)
    print("1) 64426 精确 Top6:")
    for i, r in enumerate(rows, 1):
        print(f"   {i}. {r[0]:<6} {r[1]:<10} logp={r[2]} flags={r[3]}")
    assert rows and rows[0][0] == "你好", f"期望 你好 居首，实际 {rows[0][0] if rows else None}"

    # 2) 前缀查询（续打）：6364 -> 能不能
    rows = q_prefix(c, "6364", 5)
    print("2) 6364 前缀 Top5:", [r[0] for r in rows])
    assert rows, "前缀查询无结果（GLOB 模式可能有问题）"

    # 3) 联想：中国 -> 中国人/中国梦
    rows = q_words_by_prefix(c, "中国", 8)
    print("3) 联想 中国 ->", [r[0] for r in rows])
    assert rows, "联想查询无结果（word 索引/ GLOB 可能有问题）"

    # 4) 插入用户词 + 偏好，验证 GLOB 合并
    c.execute(f"INSERT INTO {T_USER_WORDS} VALUES(?,?,?,?,?)",
              ("ni'hao", "妮好", "64426", -2000, now))
    c.execute(f"INSERT INTO {T_USER_PREF} VALUES(?,?,?,?,?)",
              ("word", "密函", "64426", 3, now))
    pat = "64426*"
    uw = c.execute(f"SELECT word FROM {T_USER_WORDS} WHERE digits GLOB ?", (pat,)).fetchall()
    up = c.execute(f"SELECT key,cnt,t_last FROM {T_USER_PREF} WHERE scope=? AND digits GLOB ?",
                   ("word", pat)).fetchall()
    print("4) 用户词命中:", [r[0] for r in uw], " 偏好命中:", [r[0] for r in up])
    assert any(r[0] == "妮好" for r in uw), "用户词 GLOB 未命中"
    assert any(r[0] == "密函" for r in up), "偏好 GLOB 未命中"

    # 5) 英文学习词查询通道
    c.execute(f"INSERT INTO {T_USER_WORDS} VALUES(?,?,?,?,?)",
              ("kotlin", "kotlin", to_digits("kotlin"), -2000, now + 1))
    rows = q_words_en(c, "kot", 10)
    print("5) 英文预测 kot ->", [r[0] for r in rows])
    assert any(r[0] == "kotlin" for r in rows), "英文查询未命中"

    # 6) 索引是否被用上（EXPLAIN QUERY PLAN）
    for sql, arg in [
        (f"SELECT word FROM {ALIAS}.{TABLE} WHERE digits = ? ORDER BY logp DESC LIMIT 6", ("64426",)),
        (f"SELECT word FROM {ALIAS}.{TABLE} WHERE digits GLOB ? ORDER BY logp DESC LIMIT 6", ("6364*",)),
        (f"SELECT word FROM {ALIAS}.{TABLE} WHERE word GLOB ? AND word != ? ORDER BY logp DESC LIMIT 8",
         ("中国*", "中国")),
    ]:
        plan = c.execute("EXPLAIN QUERY PLAN " + sql, arg).fetchall()
        print("6) PLAN:", " | ".join(p[-1] for p in plan))

    print("\nALL SQL SMOKE CHECKS PASSED")
    return 0


if __name__ == "__main__":
    sys.exit(main())
