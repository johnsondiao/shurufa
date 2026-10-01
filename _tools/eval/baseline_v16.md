# PersonalIME 排序质量评测报告

- 数据库：`_tools\eval\baseline_v16.db`（411715 条）
- 语料：`_tools\eval\corpus.tsv`（词条 414 条 / 组合串 58 条）

## 词条排序（inputT9 路径）

| 分组       |    n |  Top-1 |  Top-3 |  Top-5 | Top-10 |  可达率 | 平均位次 |   MRR |
|------------|------|--------|--------|--------|--------|---------|----------|-------|
| 总体         |  414 |   61.4% |   76.3% |   81.4% |   84.3% |   89.4% |   8.66 | 0.700 |

### 按优先级

| 分组       |    n |  Top-1 |  Top-3 |  Top-5 | Top-10 |  可达率 | 平均位次 |   MRR |
|------------|------|--------|--------|--------|--------|---------|----------|-------|
| core       |  142 |   83.1% |   96.5% |   98.6% |   98.6% |  100.0% |   1.38 | 0.900 |
| common     |  222 |   54.1% |   70.3% |   76.1% |   80.6% |   86.9% |  10.57 | 0.638 |
| niche      |   50 |   32.0% |   46.0% |   56.0% |   60.0% |   70.0% |  20.86 | 0.409 |

### 按领域

| 分组       |    n |  Top-1 |  Top-3 |  Top-5 | Top-10 |  可达率 | 平均位次 |   MRR |
|------------|------|--------|--------|--------|--------|---------|----------|-------|
| time       |   27 |   92.6% |  100.0% |  100.0% |  100.0% |  100.0% |   1.07 | 0.963 |
| chat       |   31 |   90.3% |  100.0% |  100.0% |  100.0% |  100.0% |   1.10 | 0.952 |
| life       |   33 |   78.8% |   78.8% |   84.8% |   84.8% |   84.8% |  10.12 | 0.803 |
| char       |   50 |   74.0% |   92.0% |   96.0% |   96.0% |  100.0% |   1.78 | 0.836 |
| law        |   24 |   70.8% |   87.5% |   87.5% |   87.5% |   95.8% |   4.71 | 0.791 |
| med        |   26 |   65.4% |   84.6% |   92.3% |   96.2% |   96.2% |   4.04 | 0.766 |
| edu        |   22 |   59.1% |   72.7% |   86.4% |   86.4% |  100.0% |   4.14 | 0.699 |
| it         |   68 |   51.5% |   58.8% |   63.2% |   66.2% |   72.1% |  18.99 | 0.565 |
| office     |   51 |   51.0% |   70.6% |   74.5% |   80.4% |   86.3% |  10.55 | 0.624 |
| lit        |   38 |   50.0% |   76.3% |   92.1% |   94.7% |  100.0% |   2.84 | 0.646 |
| fin        |   27 |   29.6% |   55.6% |   59.3% |   77.8% |   81.5% |  14.59 | 0.454 |
| net        |   17 |   17.6% |   41.2% |   41.2% |   41.2% |   64.7% |  27.00 | 0.288 |

## 整句组合（sentenceCandidates 路径）

| 分组       |    n |  Top-1 |  Top-3 |  Top-5 | Top-10 |  可达率 | 平均位次 |   MRR |
|------------|------|--------|--------|--------|--------|---------|----------|-------|
| 总体         |   58 |   19.0% |   24.1% |   24.1% |   24.1% |   24.1% |  45.83 | 0.213 |

### 按优先级

| 分组       |    n |  Top-1 |  Top-3 |  Top-5 | Top-10 |  可达率 | 平均位次 |   MRR |
|------------|------|--------|--------|--------|--------|---------|----------|-------|
| core       |   36 |   11.1% |   16.7% |   16.7% |   16.7% |   16.7% |  50.25 | 0.134 |
| common     |   19 |   36.8% |   42.1% |   42.1% |   42.1% |   42.1% |  35.21 | 0.395 |
| niche      |    3 |    0.0% |    0.0% |    0.0% |    0.0% |    0.0% |  60.00 | 0.000 |

### 按领域

| 分组       |    n |  Top-1 |  Top-3 |  Top-5 | Top-10 |  可达率 | 平均位次 |   MRR |
|------------|------|--------|--------|--------|--------|---------|----------|-------|
| chat       |   48 |   22.9% |   29.2% |   29.2% |   29.2% |   29.2% |  42.88 | 0.257 |
| life       |    2 |    0.0% |    0.0% |    0.0% |    0.0% |    0.0% |  60.00 | 0.000 |
| med        |    2 |    0.0% |    0.0% |    0.0% |    0.0% |    0.0% |  60.00 | 0.000 |
| net        |    1 |    0.0% |    0.0% |    0.0% |    0.0% |    0.0% |  60.00 | 0.000 |
| office     |    3 |    0.0% |    0.0% |    0.0% |    0.0% |    0.0% |  60.00 | 0.000 |
| time       |    2 |    0.0% |    0.0% |    0.0% |    0.0% |    0.0% |  60.00 | 0.000 |

## 未达标清单（124 条）

| 词条 | 拼音 | 数字串 | 领域/优先级/类型 | 位次 |
|---|---|---|---|---|
| 在吗 | zai'ma | 92462 | chat/core/phrase | 不可达 |
| 谢谢你 | xie'xie'ni | 94394364 | chat/core/phrase | 不可达 |
| 打扰了 | da'rao'le | 3272653 | chat/core/phrase | 不可达 |
| 辛苦了 | xin'ku'le | 9465853 | chat/core/phrase | 不可达 |
| 麻烦你了 | ma'fan'ni'le | 623266453 | chat/core/phrase | 不可达 |
| 收到 | shou'dao | 7468326 | chat/core/phrase | 不可达 |
| 明白了 | ming'bai'le | 646422453 | chat/core/phrase | 不可达 |
| 哈哈哈 | ha'ha'ha | 424242 | chat/core/phrase | 不可达 |
| 怎么了 | zen'me'le | 9366353 | chat/core/phrase | 不可达 |
| 去哪里 | qu'na'li | 786254 | chat/core/phrase | 不可达 |
| 吃饭了 | chi'fan'le | 24432653 | chat/core/phrase | 不可达 |
| 睡觉了 | shui'jue'le | 748458353 | chat/core/phrase | 不可达 |
| 我想你 | wo'xiang'ni | 969426464 | chat/core/phrase | 不可达 |
| 知道了 | zhi'dao'le | 94432653 | chat/core/phrase | 不可达 |
| 知道了没 | zhi'dao'le'mei | 94432653634 | chat/core/phrase | 不可达 |
| 等一下 | deng'yi'xia | 336494942 | chat/core/phrase | 不可达 |
| 稍等 | shao'deng | 74263364 | chat/core/phrase | 不可达 |
| 马上到 | ma'shang'dao | 6274264326 | chat/core/phrase | 不可达 |
| 在路上 | zai'lu'shang | 9245874264 | chat/core/phrase | 不可达 |
| 马上就好 | ma'shang'jiu'hao | 6274264548426 | chat/core/phrase | 不可达 |
| 说得好 | shuo'de'hao | 748633426 | chat/core/phrase | 不可达 |
| 有道理 | you'dao'li | 96832654 | chat/core/phrase | 不可达 |
| 真的吗 | zhen'de'ma | 94363362 | chat/core/phrase | 不可达 |
| 好厉害 | hao'li'hai | 42654424 | chat/core/phrase | 不可达 |
| 太棒了 | tai'bang'le | 824226453 | chat/core/phrase | 不可达 |
| 加油 | jia'you | 542968 | chat/core/phrase | 不可达 |
| 辛苦了啦 | xin'ku'le'la | 946585352 | chat/core/phrase | 不可达 |
| 不好意思啊 | bu'hao'yi'si'a | 2842694742 | chat/core/phrase | 不可达 |
| 收到 | shou'dao | 7468326 | office/core/phrase | 不可达 |
| 加班 | jia'ban | 542226 | office/core/phrase | 不可达 |
| 长 | zhang | 94264 | char/core/word | 12 |
| 和 | he | 43 | char/core/word | 11 |
| 下班了 | xia'ban'le | 94222653 | chat/core/phrase | 3 |
| 在的 | zai'de | 92433 | chat/core/phrase | 2 |
| 好不好 | hao'bu'hao | 42628426 | chat/common/phrase | 不可达 |
| 可以吗 | ke'yi'ma | 539462 | chat/common/phrase | 不可达 |
| 好吗 | hao'ma | 42662 | chat/common/phrase | 不可达 |
| 在哪 | zai'na | 92462 | chat/common/phrase | 不可达 |
| 干嘛 | gan'ma | 42662 | chat/common/phrase | 不可达 |
| 没问题啊 | mei'wen'ti'a | 634936842 | chat/common/phrase | 不可达 |
| 通缩 | tong'suo | 8664786 | fin/common/word | 不可达 |
| 做多 | zuo'duo | 986386 | fin/niche/word | 不可达 |
| 止盈 | zhi'ying | 9449464 | fin/niche/word | 不可达 |
| 仓位管理 | cang'wei'guan'li | 2264934482654 | fin/niche/word | 不可达 |
| 资产负债表 | zi'chan'fu'zhai'biao | 9424263894242426 | fin/niche/word | 不可达 |
| 数据结构 | shu'ju'jie'gou | 74858543468 | it/common/word | 不可达 |
| 线程池 | xian'cheng'chi | 942624364244 | it/common/word | 不可达 |
| 协程 | xie'cheng | 94324364 | it/common/word | 不可达 |
| 微服务 | wei'fu'wu | 9343898 | it/common/word | 不可达 |
| 限流 | xian'liu | 9426548 | it/common/word | 不可达 |
| 负载均衡 | fu'zai'jun'heng | 389245864364 | it/common/word | 不可达 |
| 容灾 | rong'zai | 7664924 | it/common/word | 不可达 |
| 多线程 | duo'xian'cheng | 386942624364 | it/common/word | 不可达 |
| 序列化 | xu'lie'hua | 98543482 | it/common/word | 不可达 |
| 容器编排 | rong'qi'bian'pai | 7664742426724 | it/niche/word | 不可达 |
| 内存泄漏 | nei'cun'xie'lou | 634286943568 | it/niche/word | 不可达 |
| 机器学习 | ji'qi'xue'xi | 547498394 | it/common/word | 不可达 |
| 大模型 | da'mo'xing | 32669464 | it/common/word | 不可达 |
| 梯度下降 | ti'du'xia'jiang | 843894254264 | it/niche/word | 不可达 |
| 反向传播 | fan'xiang'chuan'bo | 326942642482626 | it/niche/word | 不可达 |
| 卷积 | juan'ji | 582654 | it/niche/word | 不可达 |
| 训练集 | xun'lian'ji | 986542654 | it/common/word | 不可达 |
| 回滚版本 | hui'gun'ban'ben | 484486226236 | it/niche/word | 不可达 |
| 报错 | bao'cuo | 226286 | it/common/word | 不可达 |
| 诉讼时效 | su'song'shi'xiao | 7876647449426 | law/niche/word | 不可达 |
| 数据线 | shu'ju'xian | 748589426 | life/common/word | 不可达 |
| 蓝牙耳机 | lan'ya'er'ji | 526923754 | life/common/word | 不可达 |
| 扫地机器人 | sao'di'ji'qi'ren | 726345474736 | life/common/word | 不可达 |
| 满减 | man'jian | 6265426 | life/common/word | 不可达 |
| 拼团 | pin'tuan | 7468826 | life/common/word | 不可达 |
| 点外卖 | dian'wai'mai | 3426924624 | life/common/phrase | 不可达 |
| 取快递 | qu'kuai'di | 78582434 | life/common/phrase | 不可达 |
| 靶向 | ba'xiang | 2294264 | med/niche/word | 不可达 |
| 消化不良 | xiao'hua'bu'liang | 94264822854264 | med/niche/phrase | 不可达 |
| 心脑血管 | xin'nao'xue'guan | 9466269834826 | med/niche/phrase | 不可达 |
| 直播间 | zhi'bo'jian | 944265426 | net/common/word | 不可达 |
| 打工人 | da'gong'ren | 324664736 | net/common/word | 不可达 |
| 显眼包 | xian'yan'bao | 9426926226 | net/common/word | 不可达 |
| 摸鱼 | mo'yu | 6698 | net/common/phrase | 不可达 |
| 拉胯 | la'kua | 52582 | net/common/word | 不可达 |
| 电子榨菜 | dian'zi'zha'cai | 342694942224 | net/niche/word | 不可达 |
| 精神内耗 | jing'shen'nei'hao | 54647436634426 | net/niche/word | 不可达 |
| 闭环 | bi'huan | 244826 | office/common/word | 不可达 |
| 颗粒度 | ke'li'du | 535438 | office/common/word | 不可达 |
| 拉通 | la'tong | 528664 | office/common/word | 不可达 |
| 痛点 | tong'dian | 86643426 | office/common/word | 不可达 |
| 组合拳 | zu'he'quan | 98437826 | office/common/word | 不可达 |
| 生态位 | sheng'tai'wei | 74364824934 | office/niche/word | 不可达 |
| 排期 | pai'qi | 72474 | office/common/word | 不可达 |
| 摸鱼 | mo'yu | 6698 | office/common/phrase | 不可达 |
| 星期一至五 | xing'qi'yi'zhi'wu | 9464749494498 | time/niche/phrase | 不可达 |
| 以后再说 | yi'hou'zai'shuo | 944689247486 | time/common/phrase | 不可达 |
| 搭子 | da'zi | 3294 | net/common/word | 41 |
| 灰度 | hui'du | 48438 | it/common/word | 39 |
| 考研 | kao'yan | 526926 | edu/common/word | 31 |
| 财报 | cai'bao | 224226 | fin/common/word | 27 |
| 社死 | she'si | 74374 | net/common/word | 18 |
| 自习 | zi'xi | 9494 | edu/common/word | 16 |
| 质证 | zhi'zheng | 94494364 | law/niche/word | 16 |
| 留白 | liu'bai | 548224 | lit/niche/word | 16 |
| 弹幕 | dan'mu | 32668 | net/common/word | 15 |
| 打法 | da'fa | 3232 | office/common/word | 15 |
| 死锁 | si'suo | 74786 | it/niche/word | 14 |
| 归因 | gui'yin | 484946 | office/common/word | 14 |
| 保研 | bao'yan | 226926 | edu/common/word | 12 |
| 回滚 | hui'gun | 484486 | it/common/word | 12 |
| 婉约 | wan'yue | 926983 | lit/niche/word | 12 |
| 真香 | zhen'xiang | 943694264 | net/common/word | 12 |
| 赋能 | fu'neng | 386364 | office/common/word | 12 |
| 网关 | wang'guan | 92644826 | it/common/word | 11 |
| 监事 | jian'shi | 5426744 | law/niche/word | 11 |
| 期权 | qi'quan | 747826 | fin/common/word | 9 |
| 复利 | fu'li | 3854 | fin/common/word | 9 |
| 止损 | zhi'sun | 944786 | fin/niche/word | 9 |
| 幂等 | mi'deng | 643364 | it/common/word | 9 |
| 隐喻 | yin'yu | 94698 | lit/niche/word | 9 |
| 心率 | xin'lu | 94658 | med/common/word | 8 |
| 复盘 | fu'pan | 38726 | office/common/word | 8 |
| 净值 | jing'zhi | 5464944 | fin/common/word | 7 |
| 简历 | jian'li | 542654 | office/common/word | 7 |
| 估值 | gu'zhi | 48944 | fin/common/word | 6 |
| 下线 | xia'xian | 9429426 | it/common/word | 6 |
| 对齐 | dui'qi | 38474 | office/common/word | 6 |
| 好的呢 | hao'de'ne | 4263363 | chat/common/phrase | 2 |
