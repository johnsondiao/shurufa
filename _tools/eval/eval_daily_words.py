#!/usr/bin/env python
"""
用户视角的常用词回归：拿一份**手工编写的日常场景词表**逐词查词库。

与 eval_bench.py（语料自动抽样）互补：
bench.tsv 用词库自己分词 → 抽出的词必然在库里，L1 天然测不出来（循环论证）；
本脚本词表完全手写、独立于词库，专门暴露"用户天天打、库里却没有"的缺口。

判定口径（与 DictionaryDatabase 一致）：
  - L1 不在库：SELECT word=? 无行 → 用户只能整句拼或逐字打，必是痛点；
  - L3 位次落后：同数字串内按 logp 排序，位次 > 5（首屏 5 个候选之外）；
  - L0 正常：位次 ≤ 5。

用法：
  python eval_daily_words.py            # 全量
  python eval_daily_words.py --worst    # 只看问题词
"""
from __future__ import annotations

import argparse
import collections
import io
import os
import sqlite3
import sys

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
DB = os.path.join(ROOT, "PersonalIME", "PersonalIME", "app", "src", "main",
                  "assets", "dict", "base_words.db")

FIRST_SCREEN = 5

# 手写的用户日常词表（按场景分组）。刻意独立于任何词库文件——
# 这份表本身就是"用户视角"，从词库里挑词就又循环论证了。
DAILY_WORDS = {
    "称呼家人": [
        "爸爸", "妈妈", "哥哥", "姐姐", "弟弟", "妹妹", "爷爷", "奶奶",
        "外公", "外婆", "老公", "老婆", "儿子", "女儿", "孩子", "宝宝",
        "老师", "同事", "朋友", "老板", "客户", "邻居", "亲戚", "同学",
    ],
    "日常动作": [
        "吃饭", "睡觉", "起床", "洗澡", "上班", "下班", "加班", "回家",
        "出门", "回来", "到家", "逛街", "买菜", "做饭", "散步", "休息",
        "睡觉了", "吃饭了", "下班了", "到家了", "洗澡了", "起床了",
    ],
    "时间日期": [
        "今天", "明天", "昨天", "现在", "刚才", "早上", "中午", "下午",
        "晚上", "半夜", "周末", "星期天", "星期一", "下个月", "年底",
        "生日", "纪念日", "放假", "请假", "准时", "迟到", "早点", "晚点",
    ],
    "通讯科技": [
        "微信", "手机", "电脑", "充电", "密码", "红包", "转账", "快递",
        "外卖", "网购", "视频", "电话", "短信", "流量", "信号", "下载",
        "安装", "升级", "死机", "重启", "开热点", "扫一扫", "二维码",
    ],
    "情绪评价": [
        "高兴", "开心", "难过", "生气", "心烦", "累", "舒服", "不舒服",
        "不错", "可以", "没问题", "好的", "行了", "算了", "得了", "真的",
        "哈哈", "唉", "讨厌", "可爱", "厉害", "辛苦", "感动的", "无语",
    ],
    "口语高频": [
        "怎么办", "为什么", "多少钱", "在哪里", "不好意思", "没关系",
        "不客气", "对不起", "谢谢", "再见", "等一下", "马上", "立刻",
        "可能", "应该", "知道", "不知道", "没事", "快点", "慢点",
        "随便", "挺好的", "不太好", "怎么样", "什么样", "多久", "几个",
    ],
    "出行生活": [
        "医院", "感冒", "发烧", "吃药", "超市", "商场", "停车", "加油",
        "地铁", "公交", "打车", "火车", "高铁", "飞机", "酒店", "旅游",
        "出门了", "在路上", "堵车", "导航", "红灯", "路口",
    ],
    "工作学习": [
        "开会", "报告", "邮件", "合同", "工资", "发工资", "开会了",
        "交作业", "考试", "复习", "预习", "自习", "毕业", "面试", "简历",
        "辞职", "入职", "培训", "出差", "谈客户", "写方案", "做表格",
    ],
    "功能词虚词": [
        "好了", "多了", "少了", "大了", "小了", "来了", "去了", "走了",
        "吃了", "喝了", "说了", "看了", "想了", "买了", "卖了", "做了",
        "看着", "说着", "想着", "等着", "跑着", "我的", "你的", "他的",
        "我们的", "好多了", "快好了", "差不多了", "就好了", "是啊",
        "但是", "而且", "所以", "然后", "还是", "就是", "要是", "除了",
    ],
    "饮食": [
        "早饭", "午饭", "晚饭", "夜宵", "喝水", "喝茶", "咖啡", "奶茶",
        "水果", "苹果", "香蕉", "西瓜", "米饭", "面条", "饺子", "包子",
        "外卖到了", "点外卖", "吃什么", "好吃", "难吃", "辣", "清淡",
    ],
}


def rank_of(con, word, digits, logp):
    """同数字串内按 logp 的位次（1 起）。"""
    return con.execute(
        "SELECT COUNT(*) FROM base_words WHERE digits=? AND logp>?", (digits, logp)
    ).fetchone()[0] + 1


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--worst", action="store_true", help="只输出不在库或位次落后的词")
    ap.add_argument("--db", default=DB)
    args = ap.parse_args()

    con = sqlite3.connect(args.db)
    n_total = collections.Counter()
    rows = []           # (场景, 词, 状态, 位次, digits, logp, pinyin)

    for scene, words in DAILY_WORDS.items():
        for w in words:
            n_total[scene] += 1
            r = con.execute(
                "SELECT digits,logp,pinyin FROM base_words WHERE word=?", (w,)
            ).fetchone()
            if r is None:
                rows.append((scene, w, "L1不在库", None, "-", None, "-"))
                continue
            digits, logp, pinyin = r
            rk = rank_of(con, w, digits, logp)
            status = "L0正常" if rk <= FIRST_SCREEN else "L3位次落后"
            rows.append((scene, w, status, rk, digits, logp, pinyin))

    by_status = collections.Counter(r[2] for r in rows)
    print(f"词表 {sum(n_total.values())} 词（{len(DAILY_WORDS)} 个场景，手工编写，独立于词库）")
    print(f"  L0 正常（首屏内） {by_status['L0正常']}")
    print(f"  L3 位次落后       {by_status['L3位次落后']}")
    print(f"  L1 不在库         {by_status['L1不在库']}")
    print()

    if not args.worst:
        print("== 各场景明细 ==")
        for scene, words in DAILY_WORDS.items():
            sub = [r for r in rows if r[0] == scene]
            bad = [r for r in sub if r[2] != "L0正常"]
            print(f"  {scene:<6} {len(sub):>3} 词  正常 {len(sub)-len(bad):>3}  "
                  f"问题 {len(bad):>2}  ({len(bad)/len(sub)*100:.0f}%)")

    problems = [r for r in rows if r[2] != "L0正常"]
    if problems:
        print(f"\n== 问题词（{len(problems)}）==")
        cur = None
        for scene, w, st, rk, dg, lp, py in problems:
            if scene != cur:
                print(f"  [{scene}]")
                cur = scene
            if st == "L1不在库":
                print(f"    {w:<8} 不在库")
            else:
                print(f"    {w:<8} 第 {rk} 位  ({py}, {dg}, logp={lp})")
    else:
        print("\n全部通过。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
