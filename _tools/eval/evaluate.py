#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
PersonalIME 排序质量评测（离线，不改动 APK 与业务代码）

做什么：
  1. 读 corpus.tsv（词条/组合串 + 领域 + 优先级）
  2. 从 phrase-pinyin-data 自动标注拼音（带声调 -> 无调 + 音节分隔 + ü->v）
  3. 离线复现引擎的两条真实上屏路径：
       · type=word   -> PinyinEngine.inputT9()        单次候选直接命中
       · type=phrase -> PinyinEngine.sentenceCandidates() 整句组合候选
  4. 报告 Top-1/Top-3/Top-5 命中率、平均位次、可达率、MRR，并按领域/优先级分组

为什么：
  没有指标就只能靠"感觉哪个词排第几"来调排序，重构会退化成猜谜。
  这个脚本是所有后续改动的验证门禁：任何改动前后各跑一次，指标不得下降。

为什么分两类：
  「好的/吃饭了/马上就好」这类口语组合串本来就不该作为单个词条存在——
  真实输入法靠"整句候选"把它们组合出来。把两类混在一起评，
  会把"组合能力弱"错误计入"词条排序差"，导致优化方向跑偏。

用法：
  python evaluate.py                         # 评测 _data/test_dict.db
  python evaluate.py --db path/to.db
  python evaluate.py --out report.md --worst 40
"""

import argparse
import io
import os
import sqlite3
import sys
import unicodedata

# Windows 控制台默认 GBK，直接 print 中文会 UnicodeEncodeError，故包装为 utf-8。
# 这里必须**幂等**：eval_bench.py 要 import 本模块，而本模块被 import 时也会执行到这行；
# 每多包一层，上一层的 wrapper 被 GC 回收时会顺手 close 掉共用的底层 buffer，
# 结果谁都写不出东西（"I/O operation on closed file"）。
# 所以只在「当前还不是 utf-8 的 TextIOWrapper」时才包。
_cur = sys.stdout
if not (isinstance(_cur, io.TextIOWrapper) and _cur.encoding.lower() == "utf-8"):
    sys.stdout = io.TextIOWrapper(_cur.buffer, encoding="utf-8", errors="replace")

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
# 默认评**真正打包进 APK 的那一份**词库，而不是任何中间产物。
#
# 这里曾经指向 _data/test_dict.db —— 那是改版前的 v16 遗留库（words/frequency 表、
# 411715 条、9 月的产物）。后果很隐蔽：不带 --db 直接跑，得到的是 57.7% 这种
# 毫无意义的数字，而人会以为"当前水平就这样"，进而对着错误基线调参。
# 评测对象必须只有一个真身：assets 里那份会被设备解包的库。
DEFAULT_DB = os.path.join(ROOT, "PersonalIME", "PersonalIME", "app",
                          "src", "main", "assets", "dict", "base_words.db")
# v16 遗留库仍在 _data/test_dict.db，需要做历史对照时显式传 --db。
PINYIN_DATA = os.path.join(ROOT, "_data", "phrase-pinyin-data", "large_pinyin.txt")
CHAR_DATA = os.path.join(ROOT, "_data", "pinyin-data", "kMandarin_8105.txt")

# 与 DictionaryDatabase.LETTER_TO_DIGIT 完全一致
LETTER_TO_DIGIT = {}
for _letters, _digit in [
    ("abc", "2"), ("def", "3"), ("ghi", "4"), ("jkl", "5"),
    ("mno", "6"), ("pqrs", "7"), ("tuv", "8"), ("wxyz", "9"),
]:
    for _ch in _letters:
        LETTER_TO_DIGIT[_ch] = _digit

CJK_LO, CJK_HI = 0x4E00, 0x9FFF


def rel_to_root(path):
    """
    相对 ROOT 的展示用路径。跨盘符时原样返回。

    为什么需要这个包装：Windows 上 `os.path.relpath()` 在**不同盘符**之间会抛
    ValueError（不存在公共前缀）。而 `--out` 的报告是在所有计算之后、逐行写出去的，
    一旦抛异常，报告文件就只剩最上面那个标题 —— 而且因为异常发生在"打完了"之后，
    很容易被当成"报告就很短"而忽略。实测：把临时库放在 C: 盘、仓库在 D: 盘时，
    报告只剩 42 字节。所以这里必须兜住，宁可显示绝对路径也不能中断写出。
    """
    try:
        return os.path.relpath(path, ROOT)
    except ValueError:
        return path


def to_digits(pinyin: str) -> str:
    """拼音 -> T9 数字串（忽略音节分隔符）"""
    return "".join(LETTER_TO_DIGIT.get(c, c) for c in pinyin.lower().replace("'", ""))


def strip_tone(syllable: str) -> str:
    """带声调音节 -> 无调；ü/ǖ/ǘ/ǚ/ǜ -> v（与词库编码一致）"""
    s = syllable.replace("ü", "v").replace("Ü", "V")
    decomposed = unicodedata.normalize("NFD", s)
    return "".join(c for c in decomposed if unicodedata.category(c) != "Mn").lower()


def load_pinyin_sources():
    """返回 (phrase_table, char_table)，多读音取第一个（最常见读法）。"""
    char_table, phrase_table = {}, {}
    if os.path.exists(CHAR_DATA):
        with open(CHAR_DATA, encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if not line or line.startswith("#") or ":" not in line:
                    continue
                left, right = line.split(":", 1)
                left = left.strip()
                if not left.upper().startswith("U+"):
                    continue
                try:
                    ch = chr(int(left[2:], 16))
                except ValueError:
                    continue
                right = right.split("#")[0].strip()
                cand = right.split(",")[0].strip().split() if right else []
                if cand:
                    char_table[ch] = "'".join(strip_tone(c) for c in cand)
    if os.path.exists(PINYIN_DATA):
        with open(PINYIN_DATA, encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if not line or line.startswith("#") or ":" not in line:
                    continue
                left, right = line.split(":", 1)
                left = left.strip()
                right = right.split("#")[0].strip()
                if not right or not left:
                    continue
                syls = right.split()
                if len(left) == 1:
                    char_table.setdefault(left, strip_tone(syls[0]))
                    continue
                if len(syls) != len(left):   # 音节数≠字数 => 多音歧义条目，跳过
                    continue
                phrase_table.setdefault(left, "'".join(strip_tone(s) for s in syls))
    return phrase_table, char_table


def annotate(word, phrase_table, char_table):
    """词条 -> 拼音。优先整词条目，退化为逐字拼合（对应流水线的"最大匹配补注音"）。"""
    py = phrase_table.get(word)
    if py:
        return py
    syls = []
    for ch in word:
        c = char_table.get(ch)
        if not c:
            return None
        syls.append(c)
    return "'".join(syls)


def load_corpus(path):
    items = []
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.rstrip("\n")
            if not line.strip() or line.startswith("#"):
                continue
            p = line.split("\t")
            if len(p) < 3:
                continue
            kind = p[3].strip() if len(p) > 3 else "word"
            items.append((p[0].strip(), p[1].strip(), p[2].strip(), kind))
    return items


def has_cjk(word):
    return any(CJK_LO <= ord(c) <= CJK_HI for c in word)


# ---------------------------------------------------------------- 语言模型

class BigramLM:
    """
    字符级 bigram：给「上一词末字 → 下一词首字」的边界打分（毫纳特）。

    命中条件概率直接取表内值；未剪枝掉的组合用精确退避
        ln P_uni(b) + ln(δ/(c(a)+δ))
    两项分别来自 char_uni 的 logp / norm 列，因此退避不是近似。

    center=True 时每个边界再减去 lm_meta.mean_logp（语料上边界对数概率的期望，
    约等于负的字符熵）。这一步很关键：sum 形式的边界项会随分段数线性变负，
    使「段数多的路径」被系统性压低，整句候选会被单词条挤空；中心化后该量
    只表达「这个搭接比平均好多少」，不再隐含长度惩罚。
    """

    def __init__(self, path, center=False):
        con = sqlite3.connect(path)
        self.uni = {cp: (lp, nm) for cp, lp, nm
                    in con.execute("SELECT cp, logp, norm FROM char_uni")}
        self.bi = {(p, n): lp for p, n, lp
                   in con.execute("SELECT prev, next, logp FROM bigram")}
        self.meta = dict(con.execute("SELECT key, value FROM lm_meta"))
        con.close()
        self.center = center
        self.mean_logp = int(self.meta.get("mean_logp", "0")) if center else 0
        # 退避下限：最罕见的后字 × 最常见的前字，即中心化前可能取到的最小值。
        if self.uni:
            self.floor_raw = (min(lp for lp, _ in self.uni.values()) +
                              min(nm for _, nm in self.uni.values()))
        else:
            self.floor_raw = -100000

    def raw(self, prev_ch, next_ch):
        hit = self.bi.get((ord(prev_ch), ord(next_ch)))
        if hit is not None:
            return hit
        u = self.uni.get(ord(next_ch))
        if u is None:
            # 后字不在 char_uni：这**不是"无信息"**，而是"这个字在 4500 万字语料里
            # 几乎不出现"，是相当强的稀有证据，必须给退避下限。
            # 曾经返回 None 被调用方当成 0 分，后果是「未知」压过「已知但搭接差」：
            # 上文为「后」时打 8426，「蜩/龆/盷」（语料里几乎为零）靠 0 加成，
            # 反超中心化后为 -11.7 的「天」，直接把「天」挤出候选。
            return self.floor_raw
        n = self.uni.get(ord(prev_ch))
        if n is None:
            # 前字不在字符集（拉丁字母/标点当上下文）：真正的"无上下文信息"
            return None
        return u[0] + n[1]

    def score(self, prev_ch, next_ch):
        v = self.raw(prev_ch, next_ch)
        return None if v is None else v - self.mean_logp


# 词库 schema 适配：旧 words(frequency 整数档位) / 新 base_words(logp 连续值)
SCHEMA = {}


def detect_schema(db):
    tables = {r[0] for r in db.execute("SELECT name FROM sqlite_master WHERE type='table'")}
    if "base_words" in tables:
        scale = 1
        try:
            row = db.execute("SELECT value FROM meta WHERE key='logp_scale'").fetchone()
            scale = int(row[0]) if row else 1     # 存储为毫纳特整数
        except sqlite3.Error:
            scale = 1
        SCHEMA.update(table="base_words", score="logp", order="logp DESC",
                      logp_scale=scale)
    else:
        SCHEMA.update(table="words", score="frequency", order="frequency DESC",
                      logp_scale=1)
    return SCHEMA


# ---------------------------------------------------------------- 引擎路径复现

def simulate_input_t9(db, digits, candidate_limit=60, lm=None, ctx=None, ctx_beta=1000):
    """
    复现 PinyinEngine.inputT9()：
      1) 恰好打完（digits 相等，LIMIT 60，tier 0）
      2) 若 (1) 无结果：从长到短找已完整输入的最长前缀（LIMIT 16），首个非空即用，tier 1
      3) 续打（digits 前缀 GLOB，LIMIT 96，tier 2）
    排序键：(matchTier 升序, 分数降序, 词长 升序)，取前 60。
    语料拼音不带强制边界符，故 matchesBoundaries 恒真，略去。

    ctx / ctx_beta：上一个上屏文本的末字 + 其权重。
    候选分数 += ctx_beta · 中心化 bigram(ctx, 候选首字)。
    这是"阶段 4a 候选级重排"——同一个数字串里哪个字更该出现，取决于前文。
    """
    cur = db.cursor()
    t, s, o = SCHEMA["table"], SCHEMA["score"], SCHEMA["order"]
    merged = {}

    def fetch(sql, args, limit):
        cur.execute(sql, args + (limit,))
        return [r for r in cur.fetchall() if has_cjk(r[0])]

    def ctx_bonus(word):
        # `not ctx`（空串）必须和 None 一起挡掉：孤立输入的样本根本没有上文，
        # 拿空串去查字符 bigram 会在 ord('') 上直接抛 TypeError。
        if ctx is None or not ctx or lm is None or not word:
            return 0
        bs = lm.score(ctx, word[0])
        if bs is None:
            return 0
        return int(ctx_beta * bs) // 1000      # 与 Kotlin Math.floorDiv 对齐

    exact = fetch(f"SELECT word, pinyin, {s} FROM {t} WHERE digits=? "
                  f"ORDER BY {o} LIMIT ?", (digits,), candidate_limit)
    for word, _py, score in exact:
        merged[word] = (score, 0)

    if not exact:
        for p in range(len(digits) - 1, 0, -1):
            pre = fetch(f"SELECT word, pinyin, {s} FROM {t} WHERE digits=? "
                        f"ORDER BY {o} LIMIT ?", (digits[:p],), 16)
            if pre:
                for word, _py, score in pre:
                    if word not in merged:
                        merged[word] = (score, 1)
                break

    pref = fetch(f"SELECT word, pinyin, {s} FROM {t} WHERE digits GLOB ? "
                 f"ORDER BY {o} LIMIT ?", (digits + "*",), 96)
    for word, _py, score in pref:
        if word not in merged:
            merged[word] = (score, 2)

    ordered = sorted(merged.items(),
                     key=lambda kv: (kv[1][1], -(kv[1][0] + ctx_bonus(kv[0])), len(kv[0])))
    return [w for w, _ in ordered[:candidate_limit]]


def simulate_sentence_candidates(db, digits, limit=3, lm=None, beta=1000, sent_k=3,
                                 seg_penalty=0, ctx=None, ctx_beta=1000):
    """
    复现 PinyinEngine.sentenceCandidates()：
      含数字串切分为词库词条组合（覆盖全部输入），DP 保留每位置 K 条路径。

      打分 = Σ 词 logp
           + β · Σ 边界字符 bigram logp（毫纳特，已中心化时只表达相对优劣）
           - 段数惩罚 · (段数 - 1)

    长度偏好由 seg_penalty 单独控制；若把长度惩罚混进 bigram 权重，
       β 就会同时调节「上下文强度」和「偏向长词程度」，无法独立调参。
      无语言模型时退化为纯 Σ 词 logp。
      返回 segments>=2 的组合文本列表。
    """
    n = len(digits)
    if n < 4 or n > 16:
        return []
    t, s, o = SCHEMA["table"], SCHEMA["score"], SCHEMA["order"]
    K, MAX_WORD_DIGITS, WORDS_PER_SUB = sent_k, 8, 6
    cur = db.cursor()
    dp = [[] for _ in range(n + 1)]
    dp[0] = [(0, 0, "")]        # (segments, score, text)
    is_logp = (s == "logp")

    for i in range(1, n + 1):
        paths = []
        for j in range(max(0, i - MAX_WORD_DIGITS), i):
            if not dp[j]:
                continue
            cur.execute(f"SELECT word, {s} FROM {t} WHERE digits=? "
                        f"ORDER BY {o} LIMIT ?", (digits[j:i], WORDS_PER_SUB))
            ws = [r for r in cur.fetchall() if has_cjk(r[0])]
            if not ws:
                continue
            for segs, score, text in dp[j]:
                for w, f in ws:
                    # logp 已是整数毫纳特，直接累加即可；旧整数档位原样累加
                    add = int(f)
                    if j == 0:
                        # 首词：左邻是"上文"（上一个上屏词的末字），不是本串内的词。
                        # 孤立输入样本没有上文，ctx 是空串而非 None——同样要挡掉，
                        # 否则 ord('') 抛 TypeError，整轮评测中断。
                        if ctx and lm is not None:
                            bs = lm.score(ctx, w[0])
                            if bs is not None:
                                add += int(ctx_beta * bs) // 1000
                    else:
                        add -= seg_penalty
                        if lm is not None:
                            bs = lm.score(text[-1], w[0])
                            if bs is not None:
                                add += int(beta * bs) // 1000
                    paths.append((segs + 1, score + add, text + w))
        if is_logp:
            # 概率量纲：路径总分越高（越接近 0）越优；同分取段数少者（倾向整词）
            paths.sort(key=lambda p: (-p[1], p[0]))
        else:
            # 旧整数档位量纲：段均词频 + 输入长度（Kotlin 原式，含整数除法）
            paths.sort(key=lambda p: (-((p[1] // p[0]) + n) if p[0] else 0, p[0]))
        dp[i] = paths[:K]

    out, seen = [], set()
    for segs, _score, text in dp[n]:
        if segs < 2 or text in seen:
            continue
        seen.add(text)
        out.append(text)
    return out[:limit]


# ---------------------------------------------------------------- 报告

def make_summary(subset, label, key="rank"):
    n = len(subset)
    if n == 0:
        return None
    miss = 60
    hit = lambda k: sum(1 for r in subset if r[key] and r[key] <= k) / n  # noqa: E731
    return dict(
        label=label, n=n,
        top1=sum(1 for r in subset if r[key] == 1) / n,
        top3=hit(3), top5=hit(5), top10=hit(10),
        reach=sum(1 for r in subset if r[key]) / n,
        mean_rank=sum((r[key] or miss) for r in subset) / n,
        mrr=sum((1.0 / r[key]) if r[key] else 0.0 for r in subset) / n,
    )


HEADER = ("| 分组       |    n |  Top-1 |  Top-3 |  Top-5 | Top-10 |  可达率 | 平均位次 |   MRR |\n"
          "|------------|------|--------|--------|--------|--------|---------|----------|-------|")


def fmt_row(s):
    return (f"| {s['label']:<10} | {s['n']:>4} | {s['top1']*100:>6.1f}% | {s['top3']*100:>6.1f}% | "
            f"{s['top5']*100:>6.1f}% | {s['top10']*100:>6.1f}% | {s['reach']*100:>6.1f}% | "
            f"{s['mean_rank']:>6.2f} | {s['mrr']:>5.3f} |")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--db", default=DEFAULT_DB)
    ap.add_argument("--corpus", default=os.path.join(os.path.dirname(os.path.abspath(__file__)), "corpus.tsv"))
    ap.add_argument("--lm", default=None, help="bigram.db 路径；给了才启用语言模型")
    ap.add_argument("--beta", type=int, default=1000, help="bigram 权重（相对词 logp，1000=1.0）")
    ap.add_argument("--sent-k", type=int, default=3, help="整句 DP 每位置保留路径数")
    ap.add_argument("--seg-penalty", type=int, default=0,
                    help="每多切一段扣多少毫纳特（长度偏好，与 bigram 权重解耦）")
    ap.add_argument("--out", default=None)
    ap.add_argument("--worst", type=int, default=25)
    args = ap.parse_args()

    if not os.path.exists(args.db):
        print(f"[错误] 找不到数据库：{args.db}")
        return 1

    lm = None
    if args.lm:
        if not os.path.exists(args.lm):
            print(f"[错误] 找不到语言模型：{args.lm}")
            return 1
        lm = BigramLM(args.lm)

    phrase_table, char_table = load_pinyin_sources()
    corpus = load_corpus(args.corpus)
    db = sqlite3.connect(args.db)
    detect_schema(db)
    total_rows = db.execute(f"SELECT COUNT(*) FROM {SCHEMA['table']}").fetchone()[0]

    print(f"词条拼音 {len(phrase_table)} 条 ｜ 单字拼音 {len(char_table)} 条 ｜ "
          f"语料 {len(corpus)} 条 ｜ 词表 {SCHEMA['table']}({SCHEMA['score']}) {total_rows} 条")
    if lm is None:
        print("语言模型：未启用")
    else:
        print(f"语言模型：字符 bigram {len(lm.bi)} 条 / unigram {len(lm.uni)} 条"
              f" ｜ β={args.beta/1000:.2f} ｜ DP-K={args.sent_k}"
              f" ｜ 段罚={args.seg_penalty/1000:.2f} 纳特")
    print()

    results, skipped = [], []
    for word, domain, priority, kind in corpus:
        py = annotate(word, phrase_table, char_table)
        if not py:
            skipped.append(word)
            continue
        digits = to_digits(py)
        # 两条真实上屏路径都跑：用户不关心候选是哪条路径给出的，能选中就是好
        wc = simulate_input_t9(db, digits)
        sc = simulate_sentence_candidates(db, digits, lm=lm, beta=args.beta,
                                          sent_k=args.sent_k, seg_penalty=args.seg_penalty)
        rw = wc.index(word) + 1 if word in wc else None
        rs = sc.index(word) + 1 if word in sc else None
        found = [x for x in (rw, rs) if x]
        results.append(dict(word=word, pinyin=py, digits=digits, domain=domain,
                            priority=priority, kind=kind,
                            rank=min(found) if found else None,
                            rank_word=rw, rank_sent=rs))

    if skipped:
        print(f"[跳过] 无拼音标注 {len(skipped)} 条：{' '.join(skipped)}\n")
    if not results:
        print("无可评测条目")
        return 1

    words = [r for r in results if r["kind"] == "word"]
    phrases = [r for r in results if r["kind"] == "phrase"]

    report = []
    groups = [
        ("综合（词条路径 + 整句路径取优）", results, "rank"),
        ("按类型：词条", words, "rank"),
        ("按类型：口语组合串", phrases, "rank"),
        ("诊断：仅 inputT9 词条路径", results, "rank_word"),
        ("诊断：仅 sentenceCandidates 整句路径", results, "rank_sent"),
    ]
    for title, subset, key in groups:
        if not subset:
            continue
        overall = make_summary(subset, "总体", key)
        by_prio = [make_summary([r for r in subset if r["priority"] == p], p, key)
                   for p in ("core", "common", "niche")]
        by_dom = [make_summary([r for r in subset if r["domain"] == d], d, key)
                  for d in sorted({r["domain"] for r in subset})]

        print("=" * 100)
        print(title)
        print("=" * 100)
        print(HEADER)
        print(fmt_row(overall))
        print("\n按优先级：")
        print(HEADER)
        for s in by_prio:
            if s:
                print(fmt_row(s))
        print("\n按领域：")
        print(HEADER)
        for s in sorted(by_dom, key=lambda x: -x["top1"]):
            print(fmt_row(s))
        print()

        report.append((title, overall, by_prio, sorted(by_dom, key=lambda x: -x["top1"])))

    bad = [r for r in results if not r["rank"] or r["rank"] > 5]
    bad.sort(key=lambda r: (r["priority"] != "core", -(r["rank"] or 999), r["domain"]))
    print("=" * 100)
    print(f"未达标清单（{len(bad)} 条：综合位次 > 5 或不可达）")
    print("=" * 100)
    for r in bad[: args.worst]:
        tag = "【不可达】" if not r["rank"] else f"第 {r['rank']:>2} 位"
        path = f"词条={r['rank_word'] or '-'} 整句={r['rank_sent'] or '-'}"
        print(f"  {tag}  {r['word']:<12} {r['pinyin']:<22} {r['digits']:<15} "
              f"{r['domain']}/{r['priority']:<6} {path}")
    if len(bad) > args.worst:
        print(f"  … 另有 {len(bad) - args.worst} 条")

    if args.out:
        with open(args.out, "w", encoding="utf-8") as f:
            f.write("# PersonalIME 排序质量评测报告\n\n")
            f.write(f"- 数据库：`{rel_to_root(args.db)}`（{total_rows} 条）\n")
            f.write(f"- 语料：`{rel_to_root(args.corpus)}`"
                    f"（词条 {len(words)} 条 / 组合串 {len(phrases)} 条）\n\n")
            for title, overall, by_prio, by_dom in report:
                f.write(f"## {title}\n\n{HEADER}\n{fmt_row(overall)}\n\n")
                f.write("### 按优先级\n\n" + HEADER + "\n")
                for s in by_prio:
                    if s:
                        f.write(fmt_row(s) + "\n")
                f.write("\n### 按领域\n\n" + HEADER + "\n")
                for s in by_dom:
                    f.write(fmt_row(s) + "\n")
                f.write("\n")
            f.write(f"## 未达标清单（{len(bad)} 条）\n\n")
            f.write("| 词条 | 拼音 | 数字串 | 领域/优先级/类型 | 位次 |\n|---|---|---|---|---|\n")
            for r in bad:
                f.write(f"| {r['word']} | {r['pinyin']} | {r['digits']} | "
                        f"{r['domain']}/{r['priority']}/{r['kind']} | "
                        f"{'不可达' if not r['rank'] else r['rank']} |\n")
        print(f"\n报告已写入 {args.out}")

    return 0


if __name__ == "__main__":
    sys.exit(main())
