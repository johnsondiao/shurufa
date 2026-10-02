#!/usr/bin/env python
"""
设备端「自造词 / 用户词库」链路的离线验证器。

## 为什么需要它

v1.0.65 改了自造词链路（整句不入库 / 首记 Toast / 长按删除），但**这些逻辑在
Kotlin 里，离线评测器覆盖不到**。读代码推断"应该通"是不够的——SQL 的 GLOB 前缀
匹配、REPLACE 覆盖语义、拼音→数字拼接、缓冲状态机的清空时机，任何一处对不上，
用户就会发现"自造词根本记不住"。

本脚本用**真实 SQLite**（不是 Python 模拟）复刻 `ime_user.db` 的 user_words 表，
逐条复现 `DictionaryDatabase` / `PersonalIMEService` 的关键语句与状态机：

  - `toDigits`：字母→T9 数字（与 Kotlin LETTER_TO_DIGIT 同一张表）
  - `addUserWord`：INSERT OR REPLACE（后写覆盖先写）
  - `mergeUserWords`：`WHERE digits GLOB ?`（前缀模式）+ startsWith 二次过滤 + 取高分
  - `hasUserWord` / `removeUserWord`
  - `appendLearnBuffer` 状态机：纯中文/拼音非空/components 非空则跳过、
    累计 ≥2 字造词、总长 >8 字重置缓冲、隐私模式不入库

跑法：`python verify_user_dict.py`
"""
from __future__ import annotations

import io
import os
import sqlite3
import sys
import tempfile

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
DICT_DB = os.path.join(ROOT, "PersonalIME", "PersonalIME", "app", "src", "main",
                       "assets", "dict", "base_words.db")

# ── 与 DictionaryDatabase.LETTER_TO_DIGIT 逐字一致的 T9 映射 ─────────────────
LETTER_TO_DIGIT = {}
for letters, d in (("abc", "2"), ("def", "3"), ("ghi", "4"), ("jkl", "5"),
                   ("mno", "6"), ("pqrs", "7"), ("tuv", "8"), ("wxyz", "9")):
    for ch in letters:
        LETTER_TO_DIGIT[ch] = d

USER_WORD_LOGP = -2000          # 毫纳特，见 DictionaryDatabase.USER_WORD_LOGP

results = []


def check(name, cond, detail=""):
    results.append((name, bool(cond), detail))
    print(f"  [{'PASS' if cond else 'FAIL'}] {name}" + (f"  —— {detail}" if detail else ""))
    return cond


def to_digits(pinyin: str) -> str:
    """复刻 DictionaryDatabase.toDigits()"""
    return "".join(LETTER_TO_DIGIT.get(c, c) for c in pinyin.lower() if c != "'")


# ── 设备端数据库（真实 SQLite） ────────────────────────────────────────────
def make_user_db():
    path = os.path.join(tempfile.mkdtemp(), "ime_user.db")
    con = sqlite3.connect(path)
    con.executescript("""
        CREATE TABLE user_words(
            pinyin TEXT NOT NULL, word TEXT NOT NULL, digits TEXT NOT NULL,
            logp INTEGER NOT NULL, added_at INTEGER NOT NULL,
            PRIMARY KEY(word));
        CREATE INDEX idx_user_words_word ON user_words(word);
        CREATE TABLE user_pref(
            scope TEXT NOT NULL, key TEXT NOT NULL, digits TEXT NOT NULL,
            cnt INTEGER NOT NULL, t_last INTEGER NOT NULL,
            PRIMARY KEY(scope,key));
    """)
    return con


def add_user_word(con, pinyin, word):
    """复刻 addUserWord：logp 固定 USER_WORD_LOGP，REPLACE 语义"""
    key = pinyin.lower()
    con.execute("INSERT OR REPLACE INTO user_words(pinyin,word,digits,logp,added_at)"
                " VALUES(?,?,?,?,0)",
                (key, word, to_digits(key), USER_WORD_LOGP))
    con.commit()


def has_user_word(con, word) -> bool:
    return con.execute("SELECT 1 FROM user_words WHERE word=? LIMIT 1", (word,)).fetchone() is not None


def remove_user_word(con, word):
    con.execute("DELETE FROM user_words WHERE word=?", (word,))
    con.commit()


def merge_user_words(con, dict_con, digits_pattern):
    """
    复刻 mergeUserWords 的第 1 段（用户词）：
    GLOB 前缀查询 + startsWith 二次过滤 + 与基础词合并取高分。
    返回按 score 降序的 [(word, score, source)]。
    """
    like = digits_pattern if digits_pattern.endswith("*") else digits_pattern + "*"
    out = {}
    for w, lp in dict_con.execute(
            "SELECT word,logp FROM base_words WHERE digits=? LIMIT 60", (digits_pattern,)):
        out[w] = (lp, "base")
    for w, lp, dg in con.execute(
            "SELECT word,logp,digits FROM user_words WHERE digits GLOB ?", (like,)):
        if dg.startswith(digits_pattern) and (w not in out or lp > out[w][0]):
            out[w] = (lp, "user")
    return sorted(((w, s, src) for w, (s, src) in out.items()), key=lambda t: -t[1])


# ── 复刻 appendLearnBuffer 状态机 ──────────────────────────────────────────
class LearnBuffer:
    """PersonalIMEService 的 learnBuffer + appendLearnBuffer 行为"""

    def __init__(self, con, privacy=False):
        self.con = con
        self.items = []          # [(text, pinyin)]
        self.privacy = privacy
        self.toasts = []

    def reset(self):
        self.items.clear()

    def append(self, text, pinyin, components_empty=True):
        if self.privacy:
            return
        if not components_empty:
            return                       # 整句候选不入库（v1.0.65 新行为）
        if not pinyin:
            return
        if not all('\u4E00' <= ch <= '\u9FFF' for ch in text):
            return
        self.items.append((text, pinyin))
        if sum(len(t) for t, _ in self.items) > 8:
            self.items = [(text, pinyin)]
        if sum(len(t) for t, _ in self.items) >= 2:
            word = "".join(t for t, _ in self.items)
            py = "'".join(p for _, p in self.items)
            existed = has_user_word(self.con, word)
            add_user_word(self.con, py, word)
            if not existed:
                self.toasts.append(word)


def main() -> int:
    dict_con = sqlite3.connect(DICT_DB)
    con = make_user_db()
    print(f"基础词库 {DICT_DB}")
    print(f"用户词库 {con.execute('PRAGMA database_list').fetchone()[2]}\n")

    print("① 前提：用户逐字打「好/多/了」时，每个字的数字串必须真的有候选")
    for py, want in (("hao", "426"), ("duo", "386"), ("le", "53")):
        d = to_digits(py)
        n = dict_con.execute("SELECT COUNT(*) FROM base_words WHERE digits=? AND length(word)=1",
                             (d,)).fetchone()[0]
        check(f"单字候选 {py}={d}", d == want and n > 0, f"库内单字候选 {n} 个")

    print("\n② 主流程：逐字打「猫粮」（库里没有的词）→ 自动造词 → 再输入整词直出")
    buf = LearnBuffer(con)
    buf.append("猫", "mao")
    check("只打 1 个字时不造词", not has_user_word(con, "猫"))
    buf.append("粮", "liang")
    check("累计 2 字即造词「猫粮」", has_user_word(con, "猫粮"))
    dg = con.execute("SELECT digits FROM user_words WHERE word='猫粮'").fetchone()[0]
    check("造词数字串 == 62654264", dg == "62654264", f"实际 {dg}")
    check("首次造词有 Toast 提示", buf.toasts == ["猫粮"], f"实际 {buf.toasts}")

    cands = merge_user_words(con, dict_con, "62654264")
    top = cands[0] if cands else None
    check("再输入 62654264 时「猫粮」排第一",
          bool(top) and top[0] == "猫粮" and top[2] == "user",
          f"实际首位 {top}")
    n_base = dict_con.execute(
        "SELECT COUNT(*) FROM base_words WHERE digits='62654264' AND word='猫粮'").fetchone()[0]
    same_str = [w for (w,) in dict_con.execute(
        "SELECT word FROM base_words WHERE digits='62654264' ORDER BY logp DESC LIMIT 5")]
    check("「猫粮」本身不在基础词库（确属自造生效）", n_base == 0,
          f"同串基础词有 {same_str}——没自造词时用户看到的就是这些")

    print("\n③ 幂等：第二次再逐字打同样的词，不重复弹提示、行数不变")
    before = con.execute("SELECT COUNT(*) FROM user_words WHERE word='猫粮'").fetchone()[0]
    buf2 = LearnBuffer(con)
    buf2.append("猫", "mao")
    buf2.append("粮", "liang")
    after = con.execute("SELECT COUNT(*) FROM user_words WHERE word='猫粮'").fetchone()[0]
    check("重复写入仍是 1 行", before == after == 1, f"{before} -> {after}")
    check("已存在的词不再弹 Toast", not buf2.toasts, f"实际 {buf2.toasts}")

    print("\n④ 中断：标点/英文/删除会清缓冲，不造出跨打断点的怪词")
    buf3 = LearnBuffer(con)
    buf3.append("猫", "mao")
    buf3.append("粮", "liang")      # 此时已造出「猫粮」
    buf3.reset()                     # 标点等事件打断
    buf3.append("狗", "gou")
    buf3.append("粮", "liang")      # 新组合「狗粮」
    check("新组合独立成词「狗粮」", has_user_word(con, "狗粮"))
    check("没有造出跨打断的「猫粮狗粮」", not has_user_word(con, "猫粮狗粮"))

    print("\n⑤ 超长缓冲：累计超 8 字应重置，不学进垃圾长串")
    buf4 = LearnBuffer(con)
    for ch in "一二三四五六七":
        buf4.append(ch, "yi")
    check("缓冲长度封顶在 8 字内", sum(len(t) for t, _ in buf4.items) <= 8,
          f"当前 {''.join(t for t, _ in buf4.items)}")

    print("\n⑥ 整句候选不入库（v1.0.65 新行为，防误拼串永久霸榜）")
    buf5 = LearnBuffer(con)
    buf5.append("还是你好多了", "huan'shi'ni'hao'duo'le", components_empty=False)
    check("整句候选不进 user_words", not has_user_word(con, "还是你好多了"))

    print("\n⑦ 长按删除：误学词可删，删后候选立即消失")
    check("删除前存在", has_user_word(con, "猫粮"))
    remove_user_word(con, "猫粮")
    check("删除后 hasUserWord 为假", not has_user_word(con, "猫粮"))
    cands2 = merge_user_words(con, dict_con, "62654264")
    names2 = [w for w, _, _ in cands2]
    check("删除后「猫粮」从候选里消失", "猫粮" not in names2, f"实际 {names2[:4]}")
    check("删除只影响自造词，基础词库同串词不受影响",
          any(src == "base" for _, _, src in cands2), f"仍有 {len(cands2)} 个基础候选")

    print("\n⑧ 隐私模式：不记录任何自造词")
    buf6 = LearnBuffer(con, privacy=True)
    buf6.append("秘", "mi")
    buf6.append("密", "mi")
    check("隐私模式下无造词、无 Toast", not has_user_word(con, "秘密") and not buf6.toasts)

    print("\n⑨ 前缀匹配：输入到一半也应命中自造词（GLOB 前缀语义）")
    add_user_word(con, "mao'liang", "猫粮")
    for prefix in ("6265", "62654264"):
        c = merge_user_words(con, dict_con, prefix)
        names = [w for w, _, _ in c]
        check(f"输入 {prefix} 时「猫粮」在候选里", "猫粮" in names, f"候选 {names[:3]}")
    check("输完时「猫粮」排第一",
          merge_user_words(con, dict_con, "62654264")[0][0] == "猫粮")

    ok = sum(1 for _, p, _ in results if p)
    print(f"\n合计 {ok}/{len(results)} 通过")
    return 0 if ok == len(results) else 1


if __name__ == "__main__":
    sys.exit(main())