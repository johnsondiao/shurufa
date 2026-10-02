# 词库构建报告

- 输出：`PersonalIME\PersonalIME\app\src\main\assets\dict\base_words.db`，**413431** 条，26.7 MB
- 裁剪阈值 logp < -26.0（裁掉 153888 条生僻词；**判据用不带字符链的 unigram 分**，故词库规模与 oov_chain_mu 无关）
- λ(oov) = 0.02
- 字符 bigram 链（阶段 4c）：oov_chain_mu = 1.0

## 词源

| 源 | 条数 | 权重 |
|---|---|---|
| word_freq | 55735 | 0.44 |
| oral | 595 | 0.24 |
| modern | 1044 | 0.16 |
| common_boost | 526 | 0.1 |
| structured | 139 | 0.05 |
| thuocl/IT | 15992 | 0.0055 |
| thuocl/animal | 17287 | 0.0055 |
| thuocl/caijing | 3830 | 0.0055 |
| thuocl/car | 1752 | 0.0055 |
| thuocl/chengyu | 8519 | 0.0055 |
| thuocl/diming | 44804 | 0.0055 |
| thuocl/food | 8974 | 0.0055 |
| thuocl/law | 9896 | 0.0055 |
| thuocl/lishimingren | 13658 | 0.0055 |
| thuocl/medical | 18749 | 0.0055 |
| thuocl/poem | 13703 | 0.0055 |

## logp 分布

最小 -65.22 ｜ 中位 -16.91 ｜ 最大 -3.03

## 被裁剪样本（最生僻 30 条）

- 燕雀岂知鵰鹗志 (logp -80.98)
- 不吃羊肉空惹一身膻 (logp -78.98)
- 燕雀安知鸿鹄之志 (logp -75.72)
- 燕雀岂知雕鹗志 (logp -74.61)
- 留得青山在不怕没柴烧 (logp -74.40)
- 一尺水翻腾做百丈波 (logp -73.83)
- 稂不稂莠不莠 (logp -72.85)
- 蝇附骥尾而致千里 (logp -72.16)
- 一尺水翻腾做一丈波 (logp -70.75)
- 死诸葛吓走生仲达 (logp -70.60)
- 燕雀安知鸿鹄志 (logp -69.69)
- 丢下耙儿弄扫帚 (logp -69.34)
- 打破砂锅璺到底 (logp -68.61)
- 伈伈睍睍 (logp -68.38)
- 唼唼哫哫 (logp -68.38)
- 娉娉褭褭 (logp -68.38)
- 彣彣彧彧 (logp -68.38)
- 怗怗竦竦 (logp -68.38)
- 恓恓遑遑 (logp -68.38)
- 沋沋湲湲 (logp -68.38)
- 熚熚烞烞 (logp -68.38)
- 獉獉狉狉 (logp -68.38)
- 睢睢盱盱 (logp -68.38)
- 翂翂翐翐 (logp -68.38)
- 蚩蚩嚚嚚 (logp -68.38)
- 蛩蛩駏驉 (logp -68.38)
- 螭鬽魍魉 (logp -68.38)
- 訚訚衎衎 (logp -68.38)
- 訾訾潝潝 (logp -68.38)
- 誾誾衎衎 (logp -68.38)
