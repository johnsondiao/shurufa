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
    },
    # 字符级回退权重：无任何语料证据的词只能拿到 λ 份的字符模型概率。
    # 越小 => 无证据词被压得越低（但现代新词也越难进来，所以靠 oral/THUOCL 补）。
    "lambda_oov": 0.02,
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


def read_lines(path):
    if not os.path.exists(path):
        return []
    with open(path, encoding="utf-8") as f:
        return [ln.rstrip("\n") for ln in f if ln.strip()]


# ─────────────────────────────────────────────────────────────────────────────
# 加载注音源
# ─────────────────────────────────────────────────────────────────────────────
def load_char_readings():
    """
    单字 -> [读音1, 读音2, ...]，读音1 为最常用。
    kTGHZ2013（《通用规范汉字表》8105 字）给全部读音；
    kMandarin_8105 给最常用读音，用于把最常见读音排到第一位。
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
def build_logp(sources, char_prob):
    """
    sources: {source_name: [word by rank]} —— 域内词表按 Zipf 折算后加权混合
    返回 word -> logp
    """
    lam = CONFIG["lambda_oov"]
    weights = CONFIG["sources"]
    mix = Counter()          # word -> 累计 (weight * zipf)，多源命中自然累加
    thuocl = sources.get("thuocl", {})

    # 普通源
    for name in ("word_freq", "oral", "modern", "common_boost"):
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


def oov_logp(word, char_prob):
    """无任何语料证据的词：只有字符模型那一份"""
    return math.log(CONFIG["lambda_oov"]) + char_logp(word, char_prob)


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

    print("\n② 采集证据源 …")
    sources = {
        "word_freq": src_word_freq(),
        "oral": src_oral(),
        "modern": src_modern(),
        "common_boost": src_common_boost(),
        "thuocl": src_thuocl(),
    }
    cn_words = src_cn_words()
    for name in ("word_freq", "oral", "modern", "common_boost"):
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
            lp = oov_logp(w, char_prob)
            stat["无证据"] += 1
        # 裁剪：无证据 且 整体由生僻字构成 -> 裁掉（现代词有 oral/THUOCL/modern 兜）
        if not attested and mean_char_prob(w) < CONFIG["mean_char_floor"]:
            pruned.append((w, lp))
            stat["裁：由生僻字构成"] += 1
            continue
        if lp < CONFIG["prune_logp"]:
            pruned.append((w, lp))
            stat["裁：低于绝对下限"] += 1
            continue
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
    ])
    c.execute("COMMIT")
    c.execute("VACUUM")
    n = c.execute("SELECT COUNT(*) FROM base_words").fetchone()[0]
    db.close()
    size = os.path.getsize(out_db) / 1024 / 1024
    print(f"   {out_db}  {n} 条  {size:.1f} MB")

    if report_path:
        try:
            out_label = os.path.relpath(out_db, ROOT)
        except ValueError:          # 跨盘符
            out_label = out_db
        with open(report_path, "w", encoding="utf-8") as f:
            f.write("# 词库构建报告\n\n")
            f.write(f"- 输出：`{out_label}`，**{n}** 条，{size:.1f} MB\n")
            f.write(f"- 裁剪阈值 logp < {CONFIG['prune_logp']}（裁掉 {len(pruned)} 条生僻词）\n")
            f.write(f"- λ(oov) = {CONFIG['lambda_oov']}\n\n")
            f.write("## 词源\n\n| 源 | 条数 | 权重 |\n|---|---|---|\n")
            for name in ("word_freq", "oral", "modern", "common_boost"):
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
