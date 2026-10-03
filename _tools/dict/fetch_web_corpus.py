#!/usr/bin/env python
"""
抓取中文网页正文，存成语料文件 `_tools/lm/corpus/web_corpus.txt`。

## 为什么做这件事

用户诉求：「把网络上的词汇常用词都打一遍，看哪些有问题」。
但更根本的问题是：**词库的网络词汇缺口**。现有语料（news 2020 / opensub 影视字幕 /
wikimedia 百科）都是**旧快照 + 影视翻译腔**，缺的正是当代网络语与新词
（vibe coding、点赞、种草、首发、破防…）。所以抓下来的网页文本有两个用途：

  1. **测试集**：jieba 分词后逐词查库，统计用户视角的"打不出来"清单（外部词表，
     与我们的词库无循环论证）；
  2. **词源语料**：跑 `extract_corpus_words.py` 时多一个源，把网络词汇抽进词库。

## 抓取要点（踩过的坑）

- **百度百科 403、维基 000/超时、网易 403、知乎 302**：本机可达的是
  新华网(news.cn)、澎湃(thepaper.cn)、凤凰科技(tech.ifeng.com)、
  China Daily。用这几家，别在反爬站点上浪费时间。
- 国内站点编码不统一（GBK/UTF-8 混杂），必须**按 meta charset 嗅探 + 逐个回退**，
  否则整篇乱码，静默变成"抓不到词"。
- 首页多为标题密集型，词汇密度反而高于正文，够用；正文页链接需再抓，成本高。

用法：`python fetch_web_corpus.py`（默认 6 个页面；`--urls` 可自定义）
"""
from __future__ import annotations

import argparse
import io
import os
import re
import subprocess
import sys
import time

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

HERE = os.path.dirname(os.path.abspath(__file__))
CORPUS = os.path.join(HERE, "..", "lm", "corpus")
OUT = os.path.join(CORPUS, "web_corpus.txt")

DEFAULT_URLS = [
    ("新华网-首页", "http://www.news.cn/"),
    ("新华网-科技", "http://www.news.cn/tech/"),
    ("新华网-生活", "http://www.news.cn/life/"),
    ("人民网", "http://www.people.com.cn/"),
    ("界面新闻", "https://www.jiemian.com/"),
    ("36氪", "https://36kr.com/"),
    ("新浪", "https://www.sina.com.cn/"),
    ("雷锋网", "https://www.leiphone.com/"),
    ("DoNews", "https://www.donews.com/"),
    ("凤凰科技", "https://tech.ifeng.com/"),
    ("澎湃", "https://www.thepaper.cn/"),
]

# 文章页链接：抓列表页正文太短（首页多为标题 + JS 渲染，实测只 1 万字符），
# 必须顺着列表页的链接去抓文章详情页才有多字正文。
LINK = re.compile(r'href=["\'](https?://[^"\'<>]+\.(?:html?|shtml)[^"\'<>]*)["\']', re.I)

CJK = re.compile(r"[\u4e00-\u9fff]")
# 连续中文段落：>=8 字才算正文/标题，太短的是导航碎片
PARA = re.compile(r"[\u4e00-\u9fff，。！？、；：“”‘’（）《》\s\-—…·]{8,}")
DROP_TAGS = re.compile(r"<(script|style|noscript)[^>]*>.*?</\1>", re.S | re.I)
ANY_TAG = re.compile(r"<[^>]+>")


def fetch(url: str, timeout: int = 40) -> str:
    """curl 抓页面并按 meta charset 解码；失败时逐个回退编码。"""
    try:
        raw = subprocess.run(
            ["curl", "-s", "--max-time", str(timeout), "-A",
             "Mozilla/5.0 (Windows NT 10.0; Win64; x64)", url],
            capture_output=True, timeout=timeout + 10).stdout
    except Exception as e:
        print(f"    [warn] 抓取异常 {url}: {e}")
        return ""
    if not raw:
        return ""
    head = raw[:2000].decode("ascii", "ignore")
    m = re.search(r'charset=["\']?([\w-]+)', head, re.I)
    encs = []
    if m:
        encs.append({"gb2312": "gb18030", "gbk": "gb18030"}.get(
            m.group(1).lower(), m.group(1).lower()))
    encs += ["utf-8", "gb18030", "latin-1"]
    for enc in encs:
        try:
            return raw.decode(enc)
        except (UnicodeDecodeError, LookupError):
            continue
    return raw.decode("utf-8", "ignore")


def extract_paragraphs(html: str) -> list[str]:
    body = DROP_TAGS.sub(" ", html)
    body = ANY_TAG.sub("\n", body)
    out = []
    for seg in PARA.findall(body):
        seg = re.sub(r"\s+", " ", seg).strip()
        n = len(CJK.findall(seg))
        if n >= 8:
            out.append(seg)
    return out


def _same_site(link: str, base: str) -> bool:
    """同域判断：只跟同站链接，外链多为导航/广告，词汇噪声大。"""
    def host(u):
        m = re.match(r"https?://([^/]+)", u)
        return m.group(1).lower().replace("www.", "") if m else ""
    return host(link) == host(base)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--urls", nargs="*", default=None,
                    help="形如 '名称|URL'，可传多个")
    ap.add_argument("--out", default=OUT)
    ap.add_argument("--follow", type=int, default=12,
                    help="每个列表页顺链抓多少篇正文（0=不抓）")
    ap.add_argument("--sleep", type=float, default=0.4, help="请求间隔秒，别把人家站点打疼")
    ap.add_argument("--min-chars", type=int, default=60000,
                    help="总中文字符数下限，不够就报警")
    args = ap.parse_args()

    targets = DEFAULT_URLS
    if args.urls:
        targets = [tuple(u.split("|", 1)) for u in args.urls]

    all_paras, total_cjk = [], 0
    for name, url in targets:
        html = fetch(url)
        if not html:
            print(f"  {name:<12} 抓取失败 {url}")
            continue
        paras = extract_paragraphs(html)
        cjk = sum(len(CJK.findall(p)) for p in paras)
        all_paras.extend(paras)
        total_cjk += cjk
        print(f"  {name:<12} 列表页 段落 {len(paras):>4}  中文 {cjk:>6,}")

        if args.follow:
            links, seen_l = [], set()
            for href in LINK.findall(html):
                base = href.split("#")[0]
                if base in seen_l:
                    continue
                seen_l.add(base)
                links.append(base)
            # 只取前 N 个，且不追同域外链（外链多为导航/广告，词汇噪声大）
            picked = [l for l in links if _same_site(l, url)][: args.follow]
            n_page = 0
            for link in picked:
                time.sleep(args.sleep)
                art = fetch(link, timeout=30)
                if not art:
                    continue
                ap_ = extract_paragraphs(art)
                c2 = sum(len(CJK.findall(p)) for p in ap_)
                if c2 < 100:                 # 跳过导航残留/空页面
                    continue
                all_paras.extend(ap_)
                total_cjk += c2
                n_page += 1
            print(f"{'':<12} └ 跟进 {n_page}/{len(picked)} 篇正文")

    if not all_paras:
        print("没抓到任何中文段落，检查网络/URL")
        return 1

    seen, uniq = set(), []
    for p in all_paras:
        if p not in seen:
            seen.add(p)
            uniq.append(p)

    os.makedirs(os.path.dirname(args.out), exist_ok=True)
    with open(args.out, "w", encoding="utf-8") as f:
        f.write("# 网络抓取语料（由 _tools/dict/fetch_web_corpus.py 生成）\n")
        f.write("# 来源：" + "、".join(u for _, u in targets) + "\n")
        for p in uniq:
            f.write(p + "\n")

    print(f"\n去重后段落 {len(uniq)}，中文 {total_cjk:,} 字符 -> {args.out}")
    if total_cjk < args.min_chars:
        print(f"  [警告] 低于下限 {args.min_chars:,}，网页语料可能太少，"
              f"抽词与测试的统计力不足")
    return 0


if __name__ == "__main__":
    sys.exit(main())