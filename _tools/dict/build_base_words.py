#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
PersonalIME 词库构建流水线（阶段 1）

把散落的 15 个魔法档位常量 + 5 张补丁表，替换为一个可解释的概率模型：

    P(w) = (1-λ) · P_corpus(w) + λ · P_char(w)
    logp(w) = ln P(w)

其中
    P_corpus(w) = Σ_s  W_s · Zipf_s(w)       多源语料证据的加权混合
    Zipf_s(w)   = (1/(rank_s(w)+K_s)) / Z_s  每个源内部按 Zipf 律折算
    P_char(w)   = Π p_char(c_i)               字符级回退（无直接语料证据的词）

设计要点
  1. 证据可累加：一个词同时出现在《常用词表》和领域词表里，概率自然更高——
     不需要"找个不撞档的魔法数字"。
  2. 连续值：彻底消灭"5.5 万词挤进 8 个档位 → 并列 → 排序退化为入库序"。
  3. 单一量纲：logp 是自然对数概率，将来 userScore / bigram 可以直接相加。
  4. 全部参数集中在 CONFIG，有依据、可评测拟合；不再散落在 Kotlin 常量里。

产物
  app/src/main/assets/dict/base_words.db   可直接打包的只读词库（含 base_words 表 + meta）
  _tools/dict/build_report.md              构建统计报告

用法：
  python build_base_words.py                 # 全量构建
  python build_base_words.py --report-only   # 只出统计，不写 db
"""

import argparse
import csv
import hashlib
import io
import math
import os
import re
import sqlite3
import sys
import unicodedata
from collections import Counter

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
APP = os.path.join(ROOT, "PersonalIME", "PersonalIME", "app", "src", "main")
ASSETS = os.path.join(APP, "assets")
DATA = os.path.join(ROOT, "_data")
SOURCES = os.path.join(HERE, "sources")
# 旧版资产的归档：word_freq / cn_words / cn_chars 等原始数据源。
# 它们曾放在 app/src/main/assets/ 下并被 App 运行时读取；新架构词库离线产出，
# 运行时不再读这些文件，故移到这里只作为流水线的输入。
LEGACY = os.path.join(SOURCES, "legacy")

# ─────────────────────────────────────────────────────────────────────────────
# 全部可调参数集中在此处（曾有 15 个魔法档位散落在 Kotlin 里，这是替代品）
# ─────────────────────────────────────────────────────────────────────────────
CONFIG = {
    # 语料证据源：weight 是混合权重（Σweight 应 ≈ 1），k 是 Zipf 平滑常数，
    # 越大表示该源内部"头部不够突出"。
    # weight 依据：word_freq 是 2.5 亿字语料，覆盖最广，给主权重；
    #              oral 是人工精选、精准命中口语盲区，给高权重；
    #              领域词表（THUOCL）单个域内样本小、且多为长尾专业词，权重低。
    "sources": {
        "word_freq":    {"weight": 0.44, "k": 10},
        "oral":         {"weight": 0.24, "k": 3},
        "modern":       {"weight": 0.16, "k": 5},
        "common_boost": {"weight": 0.10, "k": 5},
        "thuocl":       {"weight": 0.06, "k": 50},   # 每个领域各一份，均分该权重
        # corpus_words = 语料直抽词（_tools/dict/extract_corpus_words.py 生成）。
        # weight 0.30 是按"它补的是什么"定的，不是随手填的：
        #   它和 word_freq 同性质（都是常用词频次证据），但语料只 ~2.6 亿字
        #   （news 16M + opensub 170M + wiki 45M），且 opensub 是影视字幕、带翻译腔，
        #   证据密度不如 word_freq 号称的 2.5 亿字，所以给 0.44 的约七成；
        #   又不低于 oral 的 0.24 —— 因为缺的正是"功能词"，靠人工 595 词的 oral 补不回来。
        # 接进来后必须跑回归：语料直抽会带进噪声词（「新冠肺」这类），
        # 若 Top-1 / 首屏命中率不升，就把这个权重往下调，别硬塞。
        "corpus_words": {"weight": 0.20, "k": 10},
        # structured 是"净增"权重（Σweight 因此为 1.05，不再等于 1）：
        # 实测把现有 5 个源等比缩放来腾出权重，会让 Top-1 掉 0.6 个百分点——
        # 因为那几个源之间的相对关系是调过的，任何缩放都是无谓扰动。
        # mix 不进 softmax 归一，多出来的这一点只等于给"有证据的词"整体涨一点点，
        # 而结构化组合词的收益远大于此。
        #
        # weight 只能给 0.05，不能更高：Zipf 是按**表内序位**归一化的，
        # 表越短每个词分到的份额越大。structured 只有 ~150 词，0.10 时表尾的
        # 「八年」竟能压过「报告」（-8.21 vs -8.46）——因为 150 词的 1/120 份额
        # 和 5.6 万词表的 1/121 份额只差一个 weight。0.05 后表尾落到 -9.1，
        # 回到"补盲区"而不是"造新霸榜"。
        "structured":   {"weight": 0.05, "k": 4},
    },
    # 字符级回退权重：无任何语料证据的词只能拿到 λ 份的字符模型概率。
    # 越小 => 无证据词被压得越低（但现代新词也越难进来，所以靠 oral/THUOCL 补）。
    "lambda_oov": 0.02,
    # ── 阶段 4c：字符 bigram 链修正（0 = 关闭，与旧行为逐比特相同）
    # 加在 P_char 上，表达"这个字串本身顺不顺"。见 CharBigram / char_model_logp。
    # 取值 1.0 = PMI 项全额计入（不是凑出来的数：实测 μ∈[0.5,1.5] 是指标平台，
    # 两端单调回落——μ=2.0 掉到 85.6%，μ=3.0 崩到 79.9%；1.0 是平台中心）。
    "oov_chain_mu": 1.0,
    # 链模型文件：与设备端 LanguageModel 复用同一份（见 CharBigram 的"为什么读它"）
    "oov_chain_db": os.path.join(HERE, "..", "lm", "bigram.db"),
    # 未知字符的概率下限（生僻汉字）
    "char_prob_floor": 1e-7,
    # 裁剪规则（治本"生僻词霸榜"）：
   #   多字词若无任何语料证据，且"各字字频的几何平均"低于 mean_char_floor，才裁掉。
    #   为什么用几何平均而不是"最生僻的那个字"：后者会把「保鲜膜/婉约/留白」这类
    #   "只有一个字偏冷"的正当词一起误杀（实测可达率掉 6 个百分点）。
    #   几何平均能区分：俵寄 2.9e-6 / 猋急 5.7e-6 / 麋膏 4.1e-6（噪声）
    #   对比 婉约 7.8e-5 / 保鲜膜 2.2e-4 / 留白 5.0e-4（正当词）。
    #   注意：裁剪只为控制体积；排序正确性由 logp 保证（生僻词 logp 天然极低）。
    "mean_char_floor": 3e-5,
    # 兜底绝对下限：无论含什么字，logp 低于此值一律不收录
    "prune_logp": -26.0,
    # 异读变体的降权（纳特）。见 sources/readings_extra.txt：
    # 把含俗读字的词按"替换该字读音"再生成一条可打路径，扣这么多分，
    # 保证标准读音优先，但俗读输入（如 shen'mo）也能命中而不再是死路。
    "variant_penalty": 2.0,
    # 单字一律保留（组句必需），不参与裁剪
    "keep_all_chars": True,
}

# ─────────────────────────────────────────────────────────────────────────────
# 拼音工具
# ─────────────────────────────────────────────────────────────────────────────
LETTER_TO_DIGIT = {}
for _l, _d in [("abc", "2"), ("def", "3"), ("ghi", "4"), ("jkl", "5"),
               ("mno", "6"), ("pqrs", "7"), ("tuv", "8"), ("wxyz", "9")]:
    for _c in _l:
        LETTER_TO_DIGIT[_c] = _d


def to_digits(pinyin):
    return "".join(LETTER_TO_DIGIT.get(c, c) for c in pinyin.lower() if c != "'")


def normalize_syllable(syl):
    """带声调音节 -> 无调小写；ü -> v（与词库既有编码一致）"""
    s = syl.replace("ü", "v").replace("Ü", "V")
    return "".join(c for c in unicodedata.normalize("NFD", s)
                   if unicodedata.category(c) != "Mn").lower()


def write_version_file(db_path):
    """
    把 DB 内容哈希写进同名 `.version` 文件（与 db 同目录，扩展名换成 .version）。

    设备端 AssetDatabase 只在「assets 里的版本 ≠ 本地已装版本」时才重新解包。
    所以这个文件**必须**随词库一起变——历史上它是手写整数，改了词库忘记 +1 就会
    让覆盖安装的用户静默地继续用旧词库。内容哈希不存在"忘记"这个失败模式。
    """
    h = hashlib.md5()
    with open(db_path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    vpath = os.path.splitext(db_path)[0] + ".version"
    with open(vpath, "w", encoding="utf-8") as f:
        f.write(h.hexdigest()[:12])
    print(f"   版本号 -> {vpath}  {h.hexdigest()[:12]}")
    return vpath


def read_lines(path):
    if not os.path.exists(path):
        return []
    with open(path, encoding="utf-8") as f:
        return [ln.rstrip("\n") for ln in f if ln.strip()]


# ─────────────────────────────────────────────────────────────────────────────
# 加载注音源
# ─────────────────────────────────────────────────────────────────────────────
def load_reading_extras():
    """
    补充读音（sources/readings_extra.txt）：官方字表未收、但日常输入必须支持的常读/俗读。
    格式：字 <TAB> 读音[,读音...]。返回 {字: [读音...]}。
    只追加，不改变主读音——主读音始终由语料投票决定。
    """
    extra = {}
    for line in read_lines(os.path.join(SOURCES, "readings_extra.txt")):
        if line.startswith("#"):
            continue
        parts = line.split("\t") if "\t" in line else line.split()
        if len(parts) < 2:
            continue
        ch = parts[0].strip()
        if len(ch) != 1:
            continue
        bucket = extra.setdefault(ch, [])
        for r in parts[1].replace(",", " ").split():
            rs = normalize_syllable(r)
            if rs and rs not in bucket:
                bucket.append(rs)
    return extra


def load_char_readings():
    """
    单字 -> [读音1, 读音2, ...]，读音1 为最常用。
    kTGHZ2013（《通用规范汉字表》8105 字）给全部读音；
    kMandarin_8105 给最常用读音，用于把最常见读音排到第一位；
    最后并入 readings_extra.txt 的补充读音（追加，不影响上述主读音顺序）。
    """
    primary, all_readings = {}, {}
    p = os.path.join(DATA, "pinyin-data", "kMandarin_8105.txt")
    for line in read_lines(p):
        if line.startswith("#") or ":" not in line:
            continue
        left, right = line.split(":", 1)
        if not left.strip().upper().startswith("U+"):
            continue
        ch = chr(int(left.strip()[2:], 16))
        cand = right.split("#")[0].strip().split(",")[0].strip().split()
        if cand:
            primary[ch] = normalize_syllable(cand[0])

    p = os.path.join(DATA, "pinyin-data", "kTGHZ2013.txt")
    for line in read_lines(p):
        if line.startswith("#") or ":" not in line:
            continue
        left, right = line.split(":", 1)
        if not left.strip().upper().startswith("U+"):
            continue
        ch = chr(int(left.strip()[2:], 16))
        reads = [normalize_syllable(r) for r in right.split("#")[0].strip().split(",") if r.strip()]
        if reads:
            all_readings[ch] = reads

    out = {}
    for ch in set(all_readings) | set(primary):
        reads = list(all_readings.get(ch, []))
        first = primary.get(ch)
        if first:
            reads = [first] + [r for r in reads if r != first]
        if not reads:
            continue
        out[ch] = reads

    # 补充读音：追加到末尾。放在最后是为了不让它顶掉官方主读音的顺序。
    for ch, rs in load_reading_extras().items():
        cur = out.setdefault(ch, [])
        for r in rs:
            if r not in cur:
                cur.append(r)
    return out


def load_phrase_pinyin():
    """词组 -> 拼音（' 连接）。来源 phrase-pinyin-data/large_pinyin.txt（41 万条，mozillazg）"""
    table = {}
    p = os.path.join(DATA, "phrase-pinyin-data", "large_pinyin.txt")
    for line in read_lines(p):
        if line.startswith("#") or ":" not in line:
            continue
        left, right = line.split(":", 1)
        left = left.strip()
        right = right.split("#")[0].strip()
        if not right or len(left) < 2:
            continue
        syls = right.split()
        if len(syls) != len(left):
            continue
        table.setdefault(left, "'".join(normalize_syllable(s) for s in syls))
    return table


class Annotator:
    def __init__(self):
        self.chars = load_char_readings()
        self.phrases = load_phrase_pinyin()
        self.hit_phrase = self.hit_char = self.miss = 0
        self.bad_phrase = 0

    def annotate(self, word):
        """
        返回 (拼音, 是否整词命中)。
        逐字拼合是回退路径，不是错误。
        整词条目要做**读音合法性校验**：音节数对得上、但某个字拿到了不属于它的读音时
        弃用整词条目改逐字拼合。数据源里确有这类脏条目，实测两例：
          「人有旦夕祸福」被判为 di'you'dan'xi'huo'fu（人 -> di）
          「工口」被判为 ei'luo（工 -> ei、口 -> luo）
        不校验的后果不是"这个词读错"这么轻——这两条会通过语料投票
        污染整本字典：所有含「人」的词都在给 di 投票，最终把「人」的主读音投成 di。
        """
        py = self.phrases.get(word)
        if py:
            syls = py.split("'")
            if len(syls) == len(word) and self._valid_alignment(word, syls):
                self.hit_phrase += 1
                return py, True
            self.bad_phrase += 1
        syls = []
        for ch in word:
            reads = self.chars.get(ch)
            if not reads:
                self.miss += 1
                return None, False
            syls.append(reads[0])
        self.hit_char += 1
        return "'".join(syls), False

    def _valid_alignment(self, word, syls):
        """每个音节必须是该字已知读音之一；任一处不合法即判定整词条目脏。"""
        for ch, syl in zip(word, syls):
            reads = self.chars.get(ch)
            if not reads:
                continue          # 生僻字无读音表，不做判断，不因此否定整条
            if syl not in reads:
                return False
        return True

    def readings(self, ch):
        return self.chars.get(ch, [])


# ─────────────────────────────────────────────────────────────────────────────
# 字符频率（P_char 用）
# ─────────────────────────────────────────────────────────────────────────────
def load_char_prob():
    p = os.path.join(DATA, "char_freq.csv")
    probs = {}
    if not os.path.exists(p):
        return probs
    with open(p, encoding="utf-8") as f:
        for row in csv.DictReader(f):
            try:
                probs[row["汉字"]] = float(row["频率（%）"]) / 100.0
            except (KeyError, ValueError):
                continue
    return probs


def char_logp(word, char_prob):
    total = 0.0
    for ch in word:
        p = char_prob.get(ch, CONFIG["char_prob_floor"])
        total += math.log(max(p, CONFIG["char_prob_floor"]))
    return total


def char_model_logp(word, char_prob, chain=None):
    """
    字符模型 log P_char(w)（阶段 4c）。

        基础项  = Σ ln p_char(c_i)                字符 unigram 连乘（阶 0）
        链修正  = μ · Σ PMI(c_{i-1}→c_i)          字符 bigram 链（阶 1），点互信息形式

    为什么要加链：unigram 连乘对**词内邻接**完全不敏感。「保温杯」和「保温北」只要
    各字字频相近就得分相同，于是长尾词的内部顺序基本是随机的。真实输入法在这里用
    「这个字串本身顺不顺」来判——也就是字符 bigram。

    为什么是 PMI 而不是"减全局均值"：见 CharBigram.pmi_sum，后者会删掉 42% 的词库。

    为什么用可调的 μ 而不是直接换成链概率：μ=0 严格退化为旧行为，可以逐个 μ 做消融，
    且不动 prune_logp / mean_char_floor 的绝对标定，改动可归因、可回滚。
    """
    lp = char_logp(word, char_prob)
    mu = CONFIG.get("oov_chain_mu", 0.0)
    if chain is not None and mu > 0.0 and len(word) > 1:
        lp += mu * chain.pmi_sum(word)
    return lp


def single_char_logp(ch, char_prob, corpus_logp):
    """
    单字概率 = 该字在语料中的字频（char_freq.csv，5707 字实测）。
    不能用 oov_logp：单字不是 OOV，不该被 λ 折扣——那会把「与/长/少」这类
    高频单字压到同数字组的后面。若该字另有词表证据，取两者较高者。
    """
    p = char_prob.get(ch)
    lp = math.log(p) if p and p > 0 else math.log(CONFIG["char_prob_floor"])
    other = corpus_logp.get(ch)
    return max(lp, other) if other is not None else lp


# ─────────────────────────────────────────────────────────────────────────────
# 字符 bigram 链（阶段 4c）：读 _tools/lm/bigram.db
# ─────────────────────────────────────────────────────────────────────────────
class CharBigram:
    """
    离线只读的字符 bigram，与设备端 LanguageModel 是**同一个模型文件**。

    为什么要读它而不是重新训练：设备端整句打分用的就是这份表，
    词库构建若另起一份，两边对"什么字串顺"的判断会不一致，
    于是出现"整句路径觉得顺、词条先验觉得差"的自相矛盾排序。

    退避与设备端**逐比特一致**：未保留的 (a,b) 用
        ln P_uni(b) + ln(δ/(c(a)+δ))
    两项分别来自 char_uni 的 logp / norm 列，是精确退避而非近似。
    """

    def __init__(self, path):
        self.ok = False
        self.mean = 0
        self.bi = {}
        self.uni = {}
        self.floor = -100_000
        if not path or not os.path.exists(path):
            return
        con = sqlite3.connect(f"file:{path}?mode=ro", uri=True)
        try:
            for k, v in con.execute("SELECT key, value FROM lm_meta"):
                if k == "mean_logp":
                    self.mean = int(v)
            min_lp = min_nm = None
            for cp, lp, nm in con.execute("SELECT cp, logp, norm FROM char_uni"):
                self.uni[cp] = (lp, nm)
                if min_lp is None or lp < min_lp:
                    min_lp = lp
                if min_nm is None or nm < min_nm:
                    min_nm = nm
            for prev, nxt, lp in con.execute("SELECT prev, next, logp FROM bigram"):
                self.bi[(prev, nxt)] = lp
        finally:
            con.close()
        if min_lp is not None and min_nm is not None:
            # 与 Kotlin LanguageModel.floorScore 同式：最罕见后字 × 最常见前字
            self.floor = min_lp + min_nm
        self.ok = bool(self.uni)
        print(f"   字符 bigram：{len(self.bi)} 条 ｜ unigram {len(self.uni)} 字 ｜ "
              f"mean_logp={self.mean}")

    def raw(self, a, b):
        """未中心化的 ln P(b|a)，单位毫纳特。前字不在字符集时返回 None（= 无信息）"""
        v = self.bi.get((ord(a), ord(b)))
        if v is not None:
            return v
        nxt = self.uni.get(ord(b))
        if nxt is None:
            return self.floor          # 后字几乎不出现：强稀有证据，给下限
        prv = self.uni.get(ord(a))
        if prv is None:
            return None
        return nxt[0] + prv[1]

    def centered_sum(self, word):
        """
        Σ[raw(a→b) − mean_logp]，转成纳特。
        **已弃用**——见 pmi_sum 的说明，保留只为对照实验。
        """
        total = 0
        for a, b in zip(word, word[1:]):
            r = self.raw(a, b)
            if r is not None:
                total += r - self.mean
        return total / 1000.0

    def pmi_sum(self, word):
        """
        Σ[ln P(b|a) − ln P_uni(b)]，转成纳特 —— 逐边界的**点互信息**。

        为什么不能用"减语料全局均值"（centered_sum）来中心化：
        全局均值是一个常数，减完之后每个边界仍带一个随字数线性累积的负偏移，
        于是长词被系统性压低。实测 μ=1.0 时词库从 387593 条掉到 223514 条
        （**删掉了 42% 的词**）——因为被压到 prune_logp 之下的词直接出局了。
        这与"补全词库"的目标正好相反。

        PMI 形式没有这个问题：在真实条件分布下
        Σ_b P(b|a)·ln[P(b|a)/P_uni(b)] = KL(P(·|a) ‖ P_uni) ≥ 0 且逐前字近似抵消，
        所以它不会给整条长尾一个单向漂移。它衡量的也正是我们想要的东西——
        「这两个字凑在一起，比它们各自按自身频率偶然相邻更可信多少」，
        这就是搭配抽取里的经典判据。

        退避边界（未保留的 (a,b)）代入 P(b|a)=P_uni(b)·δ/(c(a)+δ) 后，
        该项恰为 ln(δ/(c(a)+δ))，即"前字本身很罕见"，与设备端 LanguageModel
        的退避口径一致。
        """
        total = 0
        for a, b in zip(word, word[1:]):
            r = self.raw(a, b)
            if r is None:
                continue
            u = self.uni.get(ord(b))
            if u is not None:
                total += r - u[0]
        return total / 1000.0


# ─────────────────────────────────────────────────────────────────────────────
# 采集各证据源（每个源产出一个有序词表：list[(word, rank)]）
# ─────────────────────────────────────────────────────────────────────────────
def src_word_freq():
    """《现代汉语常用词表》5.6 万词，"词 频级名次" —— 按名次升序，越小越常用"""
    rows = []
    for line in read_lines(os.path.join(LEGACY, "word_freq.txt")):
        parts = line.split()
        if len(parts) != 2:
            continue
        try:
            rows.append((parts[0], int(parts[1])))
        except ValueError:
            continue
    rows.sort(key=lambda r: r[1])
    return [w for w, _ in rows]


def src_corpus_words():
    """
    语料直抽词源（sources/corpus_words.txt，"词 频次"，按频次降序）。

    **为什么必须有它**：现有五个源（word_freq / oral / modern / common_boost /
    structured）加十二个 THUOCL 领域表，**没有任何一个收录虚词类功能词**——
    「我的 / 好了 / 多了 / 来了 / 看着 / 大了」全部查无此词，以「了」收尾的整库
    只有 165 条且多是「一着 / 上着」这类生僻串。用户报的「好多了」打不出来就卡在这。
    根因不是阈值，是词源缺口：`legacy/word_freq.txt` 连「我的 / 这个」都没有
    （却收「题库 / 签筒」），`cn_words.txt` 是 `拼音 词 频次` 的**词组清单**，
    只有「我的世界 / 多了去了」这种长串。

    而这些词在语料里 abundant（实测 opensub+wiki：「我的」32.2 万次、
    「好了」9.8 万次、「好多了」3.1 千次）。所以不手工补词，而是把语料里本来就有的
    词证据抽出来（见 `_tools/dict/extract_corpus_words.py`），与 word_freq 完全同构
    地按序位进 Zipf 混合。

    产物由脚本生成、不手写：手写等于往资产里塞拍脑袋的词，下次还会再缺一批。
    """
    out = []
    path = os.path.join(SOURCES, "corpus_words.txt")
    if not os.path.exists(path):
        print("   [警告] 缺 sources/corpus_words.txt，先跑 "
              "`python _tools/dict/extract_corpus_words.py`")
        return out
    for line in read_lines(path):
        if line.startswith("#"):
            continue
        parts = line.split()
        if not parts:
            continue
        out.append(parts[0])      # 行序即频次序；频次本身不参与（Zipf 用序位）
    return out


def src_oral():
    """人工精编的口语高频词（sources/oral.txt，文件顺序即频次序）"""
    out = []
    for line in read_lines(os.path.join(SOURCES, "oral.txt")):
        if line.startswith("#"):
            continue
        for tok in line.split():
            out.append(tok)
    return out


def src_common_boost():
    """复用既有的人工精选常用词（_data/common_boost_words.txt，空格分隔）"""
    out = []
    for line in read_lines(os.path.join(DATA, "common_boost_words.txt")):
        if line.startswith("#"):
            continue
        out.extend(line.split())
    return out


def src_modern():
    """
    现代常用词表（sources/modern.txt）：cn_words 是"汉语词典"式词表，
    成语古语全但现代科技/商业/生活/学术词严重缺失（协程/容灾/报错/颗粒度/
    微服务/打工人/保研 在 40 万条库里一条都没有）。这份表专补这块。
    """
    out = []
    for line in read_lines(os.path.join(SOURCES, "modern.txt")):
        if line.startswith("#"):
            continue
        for tok in line.split():
            out.append(tok)
    return out


def src_structured():
    """
    结构化组合词表（sources/structured.txt）：星期X / X月 / X点 / 第X个 / X年 / X个 …
    这类"可推导的高频组合词"被所有通用词表系统性漏收，但它们恰恰是输入法最常打的词。
    见文件头注释：星期一 -23.0、星期六完全缺失、一年/两点/第一个 直接不存在。
    """
    out = []
    for line in read_lines(os.path.join(SOURCES, "structured.txt")):
        if line.startswith("#"):
            continue
        for tok in line.split():
            out.append(tok)
    return out


def src_thuocl():
    """THUOCL 领域词表："词\tDF"，按 DF 降序。返回 {领域名: [词...]}"""
    out = {}
    if not os.path.isdir(SOURCES):
        return out
    for fn in sorted(os.listdir(SOURCES)):
        if not fn.startswith("THUOCL_") or not fn.endswith(".txt"):
            continue
        domain = fn[len("THUOCL_"):-len(".txt")]
        rows = []
        for line in read_lines(os.path.join(SOURCES, fn)):
            if line.startswith("#"):
                continue
            parts = line.split("\t")
            if len(parts) < 2:
                parts = line.split()
            if not parts:
                continue
            word = parts[0].strip()
            try:
                df = int(parts[1].strip())
            except (IndexError, ValueError):
                df = 0
            if word:
                rows.append((word, df))
        rows.sort(key=lambda r: -r[1])
        out[domain] = [w for w, _ in rows]
    return out


def src_cn_words():
    """既有词组资产：只取"有这个词"作为词表清单，其词频字段平行无区分度，不作证据"""
    words = []
    for line in read_lines(os.path.join(LEGACY, "cn_words.txt")):
        parts = line.split(" ")
        if len(parts) != 3:
            continue
        words.append(parts[1])
    return words


# ─────────────────────────────────────────────────────────────────────────────
# logp 计算
# ─────────────────────────────────────────────────────────────────────────────
def build_logp(sources, char_prob, chain=None):
    """
    sources: {source_name: [word by rank]} —— 域内词表按 Zipf 折算后加权混合
    返回 word -> logp
    """
    lam = CONFIG["lambda_oov"]
    weights = CONFIG["sources"]
    mix = Counter()          # word -> 累计 (weight * zipf)，多源命中自然累加
    thuocl = sources.get("thuocl", {})

    # 普通源
    for name in ("word_freq", "oral", "modern", "common_boost", "structured",
                 "corpus_words"):
        words = sources.get(name) or []
        if not words:
            continue
        cfg = weights[name]
        k = cfg["k"]
        z = sum(1.0 / (r + k) for r in range(1, len(words) + 1))
        for rank, w in enumerate(words, 1):
            mix[w] += cfg["weight"] * (1.0 / (rank + k)) / z

    # THUOCL 各领域：每个域单独按 Zipf 归一，域权重再均分
    if thuocl:
        n_dom = len(thuocl)
        cfg = weights["thuocl"]
        k = cfg["k"]
        per_dom = cfg["weight"] / n_dom
        for _dom, words in thuocl.items():
            if not words:
                continue
            z = sum(1.0 / (r + k) for r in range(1, len(words) + 1))
            for rank, w in enumerate(words, 1):
                mix[w] += per_dom * (1.0 / (rank + k)) / z

    logp = {}
    for w, pc in mix.items():
        p = (1.0 - lam) * pc + lam * math.exp(char_logp(w, char_prob))
        logp[w] = math.log(p) if p > 0 else -1e9
    return logp


def oov_logp(word, char_prob, chain=None):
    """
    无任何语料证据的词：只有字符模型那一份。

    chain 非空时叠加字符 bigram 链修正（阶段 4c）。注意这里返回的是**入库分数**，
    不是裁剪判据——裁剪用的是不带链的 unigram 分，见 build() 里的说明。
    """
    return math.log(CONFIG["lambda_oov"]) + char_model_logp(word, char_prob, chain)


# ─────────────────────────────────────────────────────────────────────────────
# 主流程
# ─────────────────────────────────────────────────────────────────────────────
FLAG_CHAR, FLAG_FOREIGN, FLAG_ORAL, FLAG_DOMAIN = 1, 8, 16, 4


def build(out_db, report_path, report_only=False):
    print("① 加载注音源 …")
    ann = Annotator()
    print(f"   单字读音表 {len(ann.chars)} 字 ｜ 词组拼音表 {len(ann.phrases)} 条")
    char_prob = load_char_prob()
    print(f"   字符频率表 {len(char_prob)} 字")
    # 阶段 4c：只在 μ>0 时才加载链模型（加载要读 13 万行，不白花时间）
    chain = None
    if CONFIG.get("oov_chain_mu", 0.0) > 0.0:
        chain = CharBigram(CONFIG.get("oov_chain_db"))
        if not chain.ok:
            print("   [警告] 字符 bigram 不可用，oov_chain_mu 被忽略（回退到纯 unigram）")
            chain = None

    print("\n② 采集证据源 …")
    sources = {
        "word_freq": src_word_freq(),
        "oral": src_oral(),
        "modern": src_modern(),
        "common_boost": src_common_boost(),
        "structured": src_structured(),
        "corpus_words": src_corpus_words(),
        "thuocl": src_thuocl(),
    }
    cn_words = src_cn_words()
    for name in ("word_freq", "oral", "modern", "common_boost", "structured",
                 "corpus_words"):
        print(f"   {name:<14} {len(sources[name]):>7} 条")
    for d, ws in sources["thuocl"].items():
        print(f"   thuocl/{d:<8} {len(ws):>7} 条")
    print(f"   cn_words 清单  {len(cn_words):>7} 条")

    print("\n③ 计算连续 logp（多源 Zipf 混合 + 字符级回退）…")
    logp = build_logp(sources, char_prob)
    print(f"   有语料证据的词 {len(logp)} 条")

    # 词表清单：单字（全保留）+ 多字（各源并集）
    single_chars = set()
    for line in read_lines(os.path.join(LEGACY, "cn_chars.txt")):
        parts = line.split(" ")
        if len(parts) == 3:
            single_chars.add(parts[1])
    single_chars |= set(ann.chars.keys())

    multi = set(cn_words) | set(logp.keys())
    print(f"   单字清单 {len(single_chars)} 个 ｜ 多字清单 {len(multi)} 条")

    print("\n④ 注音 + 裁剪 …")
    print(f"   [参数] mean_char_floor={CONFIG['mean_char_floor']:g}  prune_logp={CONFIG['prune_logp']}  "
          f"lambda_oov={CONFIG['lambda_oov']}")
    oral_set = set(sources["oral"])
    domain_set = set()
    for ws in sources["thuocl"].values():
        domain_set |= set(ws)

    rows = []            # (pinyin, word, digits, logp, flags)
    stat = Counter()
    pruned = []

    def mean_char_prob(w):
        """各字字频的几何平均：衡量"这个词整体上由常见字构成的程度" """
        lp_sum = sum(math.log(max(char_prob.get(ch, CONFIG["char_prob_floor"]),
                                 CONFIG["char_prob_floor"])) for ch in w)
        return math.exp(lp_sum / len(w))

    for w in sorted(multi):
        if len(w) < 2:
            continue
        lp = logp.get(w)
        attested = lp is not None
        if not attested:
            # 裁剪判据用**不带链**的 unigram 分。
            # 为什么必须分开：prune_logp=-26 是按 unigram 尺度标定的绝对阈值。
            # 链修正会把罕用字开头的词整体推低（退避项 ln(δ/(c(a)+δ)) 本身就很负），
            # 若让裁剪看带链的分数，μ 一开就会把词库砍掉 -9.8%…-42.3% 的词——
            # 这与"补全词库"的目标正好相反，而且会让 μ 同时控制"砍多少词"和"怎么排序"，
            # 无法归因。这里把两件事彻底分开：**是否收录**只看 unigram，**排序**才看链。
            lp_uni = oov_logp(w, char_prob, None)
            stat["无证据"] += 1
        else:
            lp_uni = lp
        # 裁剪：无证据 且 整体由生僻字构成 -> 裁掉（现代词有 oral/THUOCL/modern 兜）
        if not attested and mean_char_prob(w) < CONFIG["mean_char_floor"]:
            pruned.append((w, lp_uni))
            stat["裁：由生僻字构成"] += 1
            continue
        if lp_uni < CONFIG["prune_logp"]:
            pruned.append((w, lp_uni))
            stat["裁：低于绝对下限"] += 1
            continue
        # 通过收录判据后，才把链修正加进入库分数（只影响同数字组内的先后）
        lp = oov_logp(w, char_prob, chain) if not attested else lp
        py, whole = ann.annotate(w)
        if not py:
            stat["注音失败"] += 1
            continue
        flags = 0
        if w in oral_set:
            flags |= FLAG_ORAL
        if w in domain_set:
            flags |= FLAG_DOMAIN
        if re.search(r"[a-zA-Z]", w):
            flags |= FLAG_FOREIGN
        rows.append((py, w, to_digits(py), lp, flags))
        stat["保留整词注音" if whole else "保留逐字注音"] += 1

    # ── 异读变体（sources/readings_extra.txt）
    # 官方字表不收的常读会让整串数字**不可达**——不是"排得靠后"，是根本出不来。
    # 例：么 官方只有 me，于是 98674466（做·什么）在库里一条候选都没有。
    # 做法：对每个词，把含补充读音的字**逐个**替换成该读音，生成一条降权变体。
    # 限制每个词最多替换 1 个字，避免多俗读字组合爆炸（当前表里只有「么」一条，
    # 但机制要能容纳后续加词）。变体扣 variant_penalty，标准读音仍优先。
    extras = load_reading_extras()
    n_var = 0
    if extras:
        base_rows = rows
        for py, w, _dg, lp, fl in base_rows:
            syls = py.split("'")
            if len(syls) != len(w):
                continue
            for i, ch in enumerate(w):
                for alt in extras.get(ch, ()):
                    if alt == syls[i]:
                        continue
                    ns = list(syls)
                    ns[i] = alt
                    npy = "'".join(ns)
                    rows.append((npy, w, to_digits(npy), lp - CONFIG["variant_penalty"], fl))
                    n_var += 1
        stat["异读变体"] = n_var
        print(f"   补充读音 {sum(len(v) for v in extras.values())} 条 / "
              f"{len(extras)} 字 -> 生成异读变体 {n_var} 条")

    # 单字：保留全部读音。
    # 主读音由语料统计决定，而不是查表——kMandarin_8105 把「长」的主读音标成 zhǎng、
    # 「行」标成 xíng，两者对错各半。改用"该读音在多少高频道词里出现过"来投票：
    # 长城/长江/长期 会让 cháng 胜出，长大/长辈 给 zhǎng 加分但不足以翻盘。
    reading_weight = {}
    for py, w, _dg, lp, _fl in rows:
        syls = py.split("'")
        if len(syls) != len(w):
            continue
        wgt = math.exp(max(lp, -30.0))
        for ch, syl in zip(w, syls):
            reads = ann.readings(ch)
            # 只统计"合法的字-读音对"，避免脏注音投票污染主读音
            if reads and syl not in reads:
                continue
            reading_weight.setdefault(ch, {})
            reading_weight[ch][syl] = reading_weight[ch].get(syl, 0.0) + wgt

    for ch in sorted(single_chars):
        lp = single_char_logp(ch, char_prob, logp)
        reads = list(ann.readings(ch))
        voted = sorted(reading_weight.get(ch, {}).items(), key=lambda kv: -kv[1])
        # 语料投票的读音排在前面，其余按字典顺序补齐
        ordered = [r for r, _ in voted] + [r for r in reads if r not in {x for x, _ in voted}]
        if not ordered:
            stat["单字注音失败"] += 1
            continue
        # 把该字的字频按各读音的语料投票份额分摊：
        # p(字 c, 读音 r) = p_char(c) × share(r)
        # 比"次读音固定罚 1.2 nats"更讲道理——罚多少由数据决定。
        # 例：的 的 de 份额约 0.99、di 约 0.01，于是 的-di 只拿到 1% 的字频，
        # 而不再靠一个拍脑袋的常数去压。
        total_w = sum(w for _r, w in voted)
        shares = {r: (w / total_w) for r, w in voted} if total_w > 0 else {}
        for i, r in enumerate(ordered[:4]):
            share = shares.get(r)
            if share and share > 0:
                lp_r = lp + math.log(share)
            else:
                # 无语料投票的读音：按表内位置降权
                lp_r = lp - (i + 1) * 3.0
            rows.append((r, ch, to_digits(r), lp_r, FLAG_CHAR))

    # 去重：同 (digits, word) 取最高 logp
    best = {}
    for py, w, dg, lp, fl in rows:
        key = (dg, w)
        if key not in best or lp > best[key][3]:
            best[key] = (py, w, dg, lp, fl)
    rows = list(best.values())
    print(f"   保留 {len(rows)} 条 ｜ 裁剪 {len(pruned)} 条 ｜ "
          f"整词注音 {stat['保留整词注音']} / 逐字 {stat['保留逐字注音']} / 无证据 {stat['无证据']}")
    print(f"   裁剪明细：由生僻字构成 {stat["裁：由生僻字构成"]} ／ 低于绝对下限 {stat['裁：低于绝对下限']}")

    # logp 分布
    print("\n⑤ logp 分布")
    vals = sorted(r[3] for r in rows)
    print(f"   最小 {vals[0]:.2f}  10% {vals[len(vals)//10]:.2f}  中位 {vals[len(vals)//2]:.2f}  "
          f"90% {vals[len(vals)*9//10]:.2f}  最大 {vals[-1]:.2f}")

    print("\n⑥ 抽查关键排序")
    for probe in (["你好", "您好", "密函", "昵好", "米糕", "蜜柑", "拟稿", "微服务", "内卷", "打工人"]):
        for r in rows:
            if r[1] == probe:
                print(f"   {probe:<8} {r[0]:<14} digits={r[2]:<10} logp={r[3]:6.2f}")
                break

    if report_only:
        return rows

    print("\n⑦ 写出词库 …")
    os.makedirs(os.path.dirname(out_db), exist_ok=True)
    if os.path.exists(out_db):
        os.remove(out_db)
    db = sqlite3.connect(out_db)
    db.isolation_level = None
    c = db.cursor()
    c.execute("PRAGMA journal_mode=OFF")
    c.execute("PRAGMA synchronous=OFF")
    c.execute("""CREATE TABLE base_words(
        pinyin TEXT NOT NULL,
        word   TEXT NOT NULL,
        digits TEXT NOT NULL,
        -- logp 存成"毫纳特"整数（自然对数 × 1000），2 字节即可容纳 -32.7~32.7；
        -- 精度 0.001 nats（概率比 0.1%）对排序完全够用，比 REAL 每行省 6 字节。
        -- 读取方（Kotlin）需除以 1000 还原。见 meta.logp_scale。
        logp   INTEGER NOT NULL,
        flags  INTEGER NOT NULL DEFAULT 0,
        PRIMARY KEY (digits, logp, word)) WITHOUT ROWID""")
    c.execute("BEGIN")
    c.executemany("INSERT OR REPLACE INTO base_words(pinyin,word,digits,logp,flags) "
                  "VALUES(?,?,?,?,?)",
                  [(py, w, dg, int(round(lp * 1000)), fl) for py, w, dg, lp, fl in rows])
    # 主键顺序 (digits, logp, word) 本身就是候选查询要的顺序，
    # 无需再建 (digits, logp) 索引——WITHOUT ROWID 表按主键物理存储。
    # 也不建 pinyin 索引：`pinyin = ?` / `pinyin GLOB` 的全部调用点都能
    # 改成「先按 digits 查（有主键），再内存过滤拼音」，省下约 10 MB。
    c.execute("CREATE INDEX idx_base_word ON base_words(word)")
    c.execute("CREATE TABLE meta(key TEXT PRIMARY KEY, value TEXT)")
    c.executemany("INSERT INTO meta VALUES(?,?)", [
        ("version", "1"),
        ("rows", str(len(rows))),
        ("logp_scale", "1000"),
        ("mean_char_floor", str(CONFIG["mean_char_floor"])),
        ("prune_logp", str(CONFIG["prune_logp"])),
        ("lambda_oov", str(CONFIG["lambda_oov"])),
        ("oov_chain_mu", str(CONFIG.get("oov_chain_mu", 0.0))),
    ])
    c.execute("COMMIT")
    c.execute("VACUUM")
    n = c.execute("SELECT COUNT(*) FROM base_words").fetchone()[0]
    db.close()
    size = os.path.getsize(out_db) / 1024 / 1024
    print(f"   {out_db}  {n} 条  {size:.1f} MB")

    # 随包的版本号文件：设备端只在「assets 里的版本 ≠ 本地已装版本」时才重新解包。
    # 手动维护整数版本号是个陷阱——改了词库却忘了 +1，覆盖安装时用户拿到的是**旧词库**，
    # 而且完全静默。改成内容哈希：词库真变了版本才变，忘了也不会漏。
    write_version_file(out_db)

    if report_path:
        try:
            out_label = os.path.relpath(out_db, ROOT)
        except ValueError:          # 跨盘符
            out_label = out_db
        with open(report_path, "w", encoding="utf-8") as f:
            f.write("# 词库构建报告\n\n")
            f.write(f"- 输出：`{out_label}`，**{n}** 条，{size:.1f} MB\n")
            f.write(f"- 裁剪阈值 logp < {CONFIG['prune_logp']}"
                    f"（裁掉 {len(pruned)} 条生僻词；**判据用不带字符链的 unigram 分**，"
                    f"故词库规模与 oov_chain_mu 无关）\n")
            f.write(f"- λ(oov) = {CONFIG['lambda_oov']}\n")
            mu = CONFIG.get("oov_chain_mu", 0.0)
            f.write(f"- 字符 bigram 链（阶段 4c）：oov_chain_mu = {mu}"
                    f"{'（关闭，无证据词只用字符 unigram 连乘）' if mu <= 0 else ''}\n\n")
            f.write("## 词源\n\n| 源 | 条数 | 权重 |\n|---|---|---|\n")
            for name in ("word_freq", "oral", "modern", "common_boost", "structured"):
                f.write(f"| {name} | {len(sources[name])} | "
                        f"{CONFIG['sources'][name]['weight']} |\n")
            for d, ws in sources["thuocl"].items():
                f.write(f"| thuocl/{d} | {len(ws)} | "
                        f"{CONFIG['sources']['thuocl']['weight']/max(1,len(sources['thuocl'])):.4f} |\n")
            f.write(f"\n## logp 分布\n\n最小 {vals[0]:.2f} ｜ 中位 "
                    f"{vals[len(vals)//2]:.2f} ｜ 最大 {vals[-1]:.2f}\n")
            f.write(f"\n## 被裁剪样本（最生僻 30 条）\n\n")
            for w, lp in sorted(pruned, key=lambda x: x[1])[:30]:
                f.write(f"- {w} (logp {lp:.2f})\n")
        print(f"   报告 {report_path}")

    return rows


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default=os.path.join(ASSETS, "dict", "base_words.db"))
    ap.add_argument("--report", default=os.path.join(HERE, "build_report.md"))
    ap.add_argument("--report-only", action="store_true")
    ap.add_argument("--set", action="append", default=[], metavar="KEY=VALUE",
                    help="临时覆盖 CONFIG 项（用于调参实验），如 --set lambda_oov=0.05")
    a = ap.parse_args()
    for kv in a.set:
        key, _, val = kv.partition("=")
        CONFIG[key] = (float(val) if re.match(r"^-?\d*\.?\d+(?:[eE][-+]?\d+)?$", val)
                       else val)
    build(a.out, a.report, a.report_only)
