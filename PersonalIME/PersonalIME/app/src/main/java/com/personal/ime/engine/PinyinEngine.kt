package com.personal.ime.engine

import com.personal.ime.data.DictionaryDatabase
import com.personal.ime.data.WordEntry

/**
 * T9 拼音引擎：基于数字序列前缀匹配。
 *
 * 词库中每个词条入库时预计算 T9 数字序列（如 能不能 -> 6364286364），
 * 输入时直接按数字前缀查询，天然支持：
 * - 渐进输入：每按一键都有候选，无需等音节打完
 * - 续打匹配：输入 6364 也能命中更长的 能不能（其数字序列以 6364 开头）
 * - 半音节容忍：输入 63642（neng + b 一半）仍能保持 能不能 在候选中
 *
 * ## 打分量纲（相对旧版的关键变化）
 * 旧版候选按整数 `frequency` 排序，而该字段由 15 个互不统一的魔法档位写入，
 * 5.5 万词被压进 8 个离散值 -> 同音组内大量并列 -> 排序退化为数据库返回顺序
 * （用户感受就是"随机"，「你好」被同档生僻词挤到第 6 就是这种并列造成的）。
 *
 * 新版 `score` 是**毫纳特（ln 概率 × 1000）连续值**，由离线流水线产出：
 *   多层证据（常用词表 + 口语词 + 现代词 + 领域词 + 字符级回退）加权混合。
 * 同一量纲下可以直接相加，为后续 bigram / 用户模型留好了接口。
 */
class PinyinEngine(private val database: DictionaryDatabase) {

    /**
     * @param score 毫纳特：基础 logp + 用户偏好加成，越大越优
     * @param components 仅整句候选使用：各组成词的拼音（上屏时逐词学习偏好）
     * @param componentWords 与 components 一一对应，存词条本身——
     *        逐词学习必须带词，只给拼音会把同音异义词一并抬上去
     * @param matchTier 匹配层级（0=恰好打完），供展示层把打完的词置顶
     */
    data class Candidate(
        val text: String,
        val score: Int,
        val pinyin: String = "",
        val components: List<String> = emptyList(),
        val matchTier: Int = 2,
        val componentWords: List<String> = emptyList()
    )

    // T9 数字 -> 字母（用于候选栏拼音回显的分段计算）
    private val digitLetters = mapOf(
        '2' to "abc", '3' to "def", '4' to "ghi", '5' to "jkl",
        '6' to "mno", '7' to "pqrs", '8' to "tuv", '9' to "wxyz"
    )

    // 有效拼音表（用于拼音回显时过滤分段）
    private val validPinyins = setOf(
        "a", "ai", "an", "ang", "ao",
        "ba", "bai", "ban", "bang", "bao", "bei", "ben", "beng", "bi", "bian", "biao", "bie", "bin", "bing", "bo", "bu",
        "ca", "cai", "can", "cang", "cao", "ce", "cen", "ceng", "cha", "chai", "chan", "chang", "chao", "che", "chen", "cheng", "chi", "chong", "chou", "chu", "chua", "chuai", "chuan", "chuang", "chui", "chun", "chuo", "ci", "cong", "cou", "cu", "cuan", "cui", "cun", "cuo",
        "da", "dai", "dan", "dang", "dao", "de", "dei", "den", "deng", "di", "dia", "dian", "diao", "die", "ding", "diu", "dong", "dou", "du", "duan", "dui", "dun", "duo",
        "e", "ei", "en", "eng", "er",
        "fa", "fan", "fang", "fei", "fen", "feng", "fo", "fou", "fu",
        "ga", "gai", "gan", "gang", "gao", "ge", "gei", "gen", "geng", "gong", "gou", "gu", "gua", "guai", "guan", "guang", "gui", "gun", "guo",
        "ha", "hai", "han", "hang", "hao", "he", "hei", "hen", "heng", "hong", "hou", "hu", "hua", "huai", "huan", "huang", "hui", "hun", "huo",
        "ji", "jia", "jian", "jiang", "jiao", "jie", "jin", "jing", "jiong", "jiu", "ju", "juan", "jue", "jun",
        "ka", "kai", "kan", "kang", "kao", "ke", "kei", "ken", "keng", "kong", "kou", "ku", "kua", "kuai", "kuan", "kuang", "kui", "kun", "kuo",
        "la", "lai", "lan", "lang", "lao", "le", "lei", "leng", "li", "lia", "lian", "liang", "liao", "lie", "lin", "ling", "liu", "lo", "long", "lou", "lu", "lv", "luan", "lue", "lun", "luo",
        "ma", "mai", "man", "mang", "mao", "me", "mei", "men", "meng", "mi", "mian", "miao", "mie", "min", "ming", "miu", "mo", "mou", "mu",
        "na", "nai", "nan", "nang", "nao", "ne", "nei", "nen", "neng", "ni", "nian", "niang", "niao", "nie", "nin", "ning", "niu", "nong", "nou", "nu", "nv", "nuan", "nue", "nuo",
        "o", "ou",
        "pa", "pai", "pan", "pang", "pao", "pei", "pen", "peng", "pi", "pian", "piao", "pie", "pin", "ping", "po", "pou", "pu",
        "qi", "qia", "qian", "qiang", "qiao", "qie", "qin", "qing", "qiong", "qiu", "qu", "quan", "que", "qun",
        "ran", "rang", "rao", "re", "ren", "reng", "ri", "rong", "rou", "ru", "rua", "ruan", "rui", "run", "ruo",
        "sa", "sai", "san", "sang", "sao", "se", "sen", "seng", "sha", "shai", "shan", "shang", "shao", "she", "shei", "shen", "sheng", "shi", "shou", "shu", "shua", "shuai", "shuan", "shuang", "shui", "shun", "shuo", "si", "song", "sou", "su", "suan", "sui", "sun", "suo",
        "ta", "tai", "tan", "tang", "tao", "te", "tei", "teng", "ti", "tian", "tiao", "tie", "ting", "tong", "tou", "tu", "tuan", "tui", "tun", "tuo",
        "wa", "wai", "wan", "wang", "wei", "wen", "weng", "wo", "wu",
        "xi", "xia", "xian", "xiang", "xiao", "xie", "xin", "xing", "xiong", "xiu", "xu", "xuan", "xue", "xun",
        "ya", "yan", "yang", "yao", "ye", "yi", "yin", "ying", "yo", "yong", "you", "yu", "yuan", "yue", "yun",
        "za", "zai", "zan", "zang", "zao", "ze", "zei", "zen", "zeng", "zha", "zhai", "zhan", "zhang", "zhao", "zhe", "zhei", "zhen", "zheng", "zhi", "zhong", "zhou", "zhu", "zhua", "zhuai", "zhuan", "zhuang", "zhui", "zhun", "zhuo", "zi", "zong", "zou", "zu", "zuan", "zui", "zun", "zuo"
    )

    fun inputT9(digits: String, context: Char? = null): List<Candidate> {
        if (digits.isEmpty()) return emptyList()
        // 词库尚未完成安装/挂载时由调用方展示提示，这里直接返回空避免阻塞主线程
        if (!database.isReady) return emptyList()

        // 分隔符（'）切出强制音节边界：记录数字长累计位置，如 94'26 -> [2, 4]
        val boundaries = mutableListOf<Int>()
        var acc = 0
        for (ch in digits) {
            if (ch == '\'') {
                if (acc > 0 && boundaries.lastOrNull() != acc) boundaries.add(acc)
            } else {
                acc++
            }
        }
        val plain = digits.filter { it != '\'' }

        // LinkedHashMap 保持插入序：恰好打完的词最先加入，排序时同层内稳定有序。
        // key=词条，value=(分数, 拼音, matchTier)；tier 0=恰好打完 1=已打部分 2=续打更长，
        // 主流输入法的第一规则："打完的词置顶"，否则会被大量前缀词/高频单字淹没
        val merged = LinkedHashMap<String, Triple<Int, String, Int>>()

        fun absorb(entries: List<WordEntry>, tier: Int) {
            for (e in entries) {
                if (e.word.none { it in '\u4E00'..'\u9FFF' }) continue
                if (!matchesBoundaries(e.pinyin, boundaries)) continue
                val prev = merged[e.word]
                if (prev == null || tier < prev.third) merged[e.word] = Triple(e.score, e.pinyin, tier)
            }
        }

        // 1) 恰好打完：数字与词条完全相等 —— 最高优先（如 243884 = 撤退）。
        //    同音组可达 260+ 条（如 94=xi/yi/zi），与最终展示窗口 60 对齐
        absorb(database.queryExact(plain, CANDIDATE_LIMIT), 0)

        // 2) 已打部分：从长到短找已完整输入的最长前缀，短词在续打时仍可见；
        //    恰好打完有结果时跳过（避免短词抢占名额）
        if (merged.values.none { it.third == 0 }) {
            for (p in plain.length - 1 downTo 1) {
                val exact = database.queryExact(plain.substring(0, p), PREFIX_EXACT_LIMIT)
                if (exact.any { it.word.any { c -> c in '\u4E00'..'\u9FFF' } }) {
                    absorb(exact, 1)
                    break
                }
            }
        }

        // 3) 续打匹配：数字序列以输入开头的更长词；已有更高优先级的词不降级覆盖
        absorb(database.queryPrefix(plain, CONTINUE_LIMIT), 2)

        return merged.entries
            .map {
                // 阶段 4a：同一个数字串里该先出哪个词，取决于前文（见 contextBonus）
                Candidate(it.key, it.value.first + contextBonus(context, it.key.firstOrNull()),
                          it.value.second, emptyList(), it.value.third)
            }
            .sortedWith(
                // 匹配层级升序（打完的词置顶）；层内分数降序；同分短词优先
                compareBy<Candidate> { c -> merged[c.text]?.third ?: 2 }
                    .thenByDescending { it.score }
                    .thenBy { it.text.length }
            )
            .take(CANDIDATE_LIMIT)
    }

    /**
     * 整句/组合候选：把整串数字切分成若干词库词条的组合（覆盖全部输入）。
     * 如 548744... -> "就是完整的"。
     *
     * ## 打分（相对旧版的关键变化）
     * 旧版 `score/segments + n`（n = 输入数字长度）是个纯凑参数的启发式：
     * 两条路径的分数不可比，且随输入长度漂移。
     * 新版改成**累加对数概率**，三项都是"对数概率"量纲，可直接相加：
     *
     *     Σ logp(词)                     —— 语言先验（词有多常用）
     *   + β · Σ bigram(前词末字, 后词首字)  —— 上下文（这个搭接顺不顺），已中心化
     *   - 段罚 · (段数 - 1)              —— 切分偏好（偏向更少更长的词）
     *
     * 拆成三项而不是揉进一个权重，是为了可独立调参：
     * 早期把长度偏好隐含在 bigram 的未中心化求和里，结果「上下文强度」和
     * 「偏向长词程度」被同一个 β 绑死，无法单独调。
     *
     * 不同切分覆盖同一串数字，因此分数可直接比较——这也是能启用 bigram 的前提。
     */
    fun sentenceCandidates(digits: String, limit: Int = 3, context: Char? = null): List<Candidate> {
        // 分词键（'）切出强制音节边界：整句切分的词边界必须落在这些位置上
        val boundaries = mutableListOf<Int>()
        var acc = 0
        for (ch in digits) {
            if (ch == '\'') {
                if (acc > 0 && boundaries.lastOrNull() != acc) boundaries.add(acc)
            } else {
                acc++
            }
        }
        val plain = digits.filter { it != '\'' }
        val n = plain.length
        // 太短无组合意义；过长控制 DP 开销；词库未就绪不查
        if (n < 4 || n > 16 || !database.isReady) return emptyList()

        data class Path(
            val segments: Int, val score: Int, val text: String, val pinyin: String,
            val components: List<String>, val componentWords: List<String>
        )

        val dp = Array(n + 1) { mutableListOf<Path>() }
        dp[0].add(Path(0, 0, "", "", emptyList(), emptyList()))

        for (i in 1..n) {
            val paths = mutableListOf<Path>()
            for (j in maxOf(0, i - MAX_WORD_DIGITS) until i) {
                // 强制边界不能落在词内部：跨边界的 (j, i) 切分直接跳过
                if (boundaries.any { it > j && it < i }) continue
                val prevList = dp[j]
                if (prevList.isEmpty()) continue
                val words = database.queryExact(plain.substring(j, i), WORDS_PER_SUB)
                if (words.isEmpty()) continue
                for (prev in prevList) {
                    // 首个词没有左邻字：既无边界项，也不计段罚
                    val isFirst = prev.segments == 0
                    for (w in words) {
                        if (w.word.none { it in '\u4E00'..'\u9FFF' }) continue
                        var add = w.score
                        if (isFirst) {
                            // 首词的左邻是"上文"（上一个上屏词的末字），不是本串内的词
                            add += contextBonus(context, w.word.firstOrNull())
                        } else {
                            add -= LM_SEG_PENALTY
                            // 语言模型未就绪时返回 null：跳过该项，视为"无上下文信息"
                            database.bigramScore(prev.text.last(), w.word.first())?.let {
                                add += Math.floorDiv(it * LM_BETA_PER_MILLE, 1000)
                            }
                        }
                        paths.add(
                            Path(
                                prev.segments + 1,
                                prev.score + add,
                                prev.text + w.word,
                                if (prev.pinyin.isEmpty()) w.pinyin else prev.pinyin + "'" + w.pinyin,
                                prev.components + w.pinyin,
                                prev.componentWords + w.word
                            )
                        )
                    }
                }
            }
            // 累计对数概率降序（分数是负数，越大越优）；同分取段数少者（倾向整词）
            dp[i] = paths.sortedWith(
                compareByDescending<Path> { it.score }.thenBy { it.segments }
            ).take(K).toMutableList()
        }

        // segments>=2 才是真正的"组合"（单词候选已由 inputT9 覆盖）
        return dp[n]
            .filter { it.segments >= 2 }
            .map { Candidate(it.text, it.score, it.pinyin, it.components, componentWords = it.componentWords) }
            .distinctBy { it.text }
            .take(limit)
    }

    /**
     * 用户选了某个读法后的输入：拼音过滤下推到 DB 精确查询，
     * 避免只在内存小窗口内过滤导致该读法的字被窗口截断（如 94 选 yi 后 意/易 打不出）。
     */
    fun inputT9ByPinyin(digits: String, selected: String, limit: Int = 60,
                        context: Char? = null): List<Candidate> {
        if (digits.isEmpty() || !database.isReady) return emptyList()
        val boundaries = mutableListOf<Int>()
        var acc = 0
        for (ch in digits) {
            if (ch == '\'') {
                if (acc > 0 && boundaries.lastOrNull() != acc) boundaries.add(acc)
            } else {
                acc++
            }
        }
        val plain = digits.filter { it != '\'' }
        // 归一化后的完整前缀（去空格/分隔符）；DB 查询用首音节前缀，内存再按完整前缀过滤
        val selKey = selected.replace(" ", "").replace("'", "")
        val dbPrefix = selected.split(' ').firstOrNull()?.replace("'", "") ?: selKey

        val merged = LinkedHashMap<String, Triple<Int, String, Int>>()
        fun absorb(entries: List<WordEntry>, tier: Int) {
            for (e in entries) {
                if (e.word.none { it in '\u4E00'..'\u9FFF' }) continue
                if (!matchesBoundaries(e.pinyin, boundaries)) continue
                if (!e.pinyin.replace("'", "").startsWith(selKey)) continue
                val prev = merged[e.word]
                if (prev == null || tier < prev.third) merged[e.word] = Triple(e.score, e.pinyin, tier)
            }
        }

        absorb(database.queryExactAndPinyin(plain, dbPrefix, limit), 0)
        absorb(database.queryPrefix(plain, CONTINUE_LIMIT), 2)

        return merged.entries
            .map {
                Candidate(it.key, it.value.first + contextBonus(context, it.key.firstOrNull()),
                          it.value.second, emptyList(), it.value.third)
            }
            .sortedWith(
                compareBy<Candidate> { c -> merged[c.text]?.third ?: 2 }
                    .thenByDescending { it.score }
                    .thenBy { it.text.length }
            )
            .take(limit)
    }

    /**
     * 阶段 4a：上下文加成。分数 = β_ctx · 中心化 bigram(上文末字, 候选首字)。
     *
     * 解决的是词频模型**原理上无法解决**的问题：同一个数字串里选哪个字。
     *   例 9494 = yi'xi / zi'xi / yi'zi …；8426 = tian / tiao。
     *   这几个字的语料频率本就接近，谁排前面纯看谁在语料里多出现几次——
     *   可实际答案完全取决于前文：前文是「今」时 8426 必是「天」，是「一」时必是「条」。
     *
     * 用中心化后的 bigram（只表达"比平均搭接顺多少"），所以：
     *   - 不引入长度偏置（见 LanguageModel 的中心化说明）
     *   - 语言模型未就绪 / 上文为空 / 首字不在字符集 时返回 0，退化为纯词频排序
     * 语言模型未就绪时返回 0 而不是负值很重要：不能因为"查不到"就惩罚候选。
     */
    private fun contextBonus(context: Char?, first: Char?): Int {
        if (context == null || first == null || first == context) return 0
        val s = database.bigramScore(context, first) ?: return 0
        return Math.floorDiv(s * CTX_BETA_PER_MILLE, 1000)
    }

    /**
     * 强制音节边界验证：候选拼音的音节切分须覆盖输入的所有边界位置。
     * - 带 ' 的拼音（资产词）：音节边界由数据源确定，严格校验
     * - 连写拼音（精编词/单字）：允许任意有效音节切分覆盖边界（DP）
     */
    private fun matchesBoundaries(pinyin: String, boundaries: List<Int>): Boolean {
        if (boundaries.isEmpty()) return true
        if (pinyin.contains('\'')) {
            val lens = mutableSetOf<Int>()
            var acc = 0
            for (syl in pinyin.split('\'')) {
                if (syl.isEmpty()) continue
                acc += syl.length
                lens.add(acc)
            }
            return boundaries.all { it in lens }
        }
        val n = pinyin.length
        val reachable = BooleanArray(n + 1)
        reachable[0] = true
        for (i in 1..n) {
            for (j in maxOf(0, i - MAX_PINYIN_LEN) until i) {
                if (reachable[j] && pinyin.substring(j, i) in validPinyins) {
                    reachable[i] = true
                    break
                }
            }
        }
        if (!reachable[n]) return true // 无法切分（英文词等）：不因分隔符排除
        return boundaries.all { it in 0..n && reachable[it] }
    }

    /**
     * 候选栏拼音回显：把 T9 数字串分段为可读拼音（音节间空格分隔）。
     * 例如 42638 -> ["gao du"]；尾部尚不成音节时返回已解析出的最长部分。
     */
    fun pinyinSplits(digits: String, limit: Int = 3): List<String> {
        if (digits.isEmpty()) return emptyList()

        if (digits.contains('\'')) {
            val parts = digits.split('\'').filter { it.isNotEmpty() }
            val rendered = parts.map { seg -> fullSplits(seg).firstOrNull() ?: seg }
            return listOf(rendered.joinToString(" "))
        }

        val full = fullSplits(digits)
        if (full.isNotEmpty()) {
            return rankSplits(full.distinct(), digits).take(limit)
        }
        for (i in digits.length - 1 downTo 1) {
            val prefix = digits.substring(0, i)
            val prefixSplits = fullSplits(prefix)
            if (prefixSplits.isNotEmpty()) return rankSplits(prefixSplits.distinct(), prefix).take(limit)
        }
        return emptyList()
    }

    /**
     * 读法排序：音节数少的优先；同音节数内按"该读法实际能打出的首选字/词"的 logp 之和降序。
     * 读法顺序与用户选中后实际看到的候选一致（如 243884 的 che tui 因"撤"排前）。
     *
     * 旧版用"代表字词频 ×2"做整读法成词加成，在 logp 量纲下"乘 2"没有意义
     * （负数的 2 倍反而更低），改为**加法奖励**：读法本身是词库词条时给固定加成。
     */
    private fun rankSplits(splits: List<String>, digits: String): List<String> {
        if (splits.size <= 1) return splits
        if (!database.isReady) return splits.sortedBy { it.count { c -> c == ' ' } }

        val repCache = HashMap<String, WordEntry?>()
        fun representative(syllable: String, start: Int, end: Int): WordEntry? =
            repCache.getOrPut("$syllable@$start") {
                database.queryExact(digits.substring(start, end), 40)
                    .firstOrNull { it.pinyin.replace("'", "") == syllable }
            }

        val score = HashMap<String, Int>(splits.size)
        val repText = HashMap<String, String>(splits.size)
        for (reading in splits) {
            var total = 0
            var pos = 0
            val reps = StringBuilder()
            for (syl in reading.split(' ')) {
                val rep = representative(syl, pos, pos + syl.length)
                total += rep?.score ?: SELECTOR_NO_REP_PENALTY
                if (reps.isNotEmpty()) reps.append(' ')
                reps.append(rep?.word ?: syl)
                pos += syl.length
            }
            // 整读法成词加成：读法本身就是词库词条时（如 chao ji = 超级）给固定奖励，
            // 真词读法必须显著压过"只靠代表字分高"的拼字读法
            if (database.topWordByPinyinExact(reading.replace(" ", "'")) != null) {
                total += SELECTOR_WHOLE_WORD_BONUS
            }
            score[reading] = total
            repText[reading] = reps.toString()
        }

        return splits.sortedWith(
            compareBy({ it.count { c -> c == ' ' } }, { -(score[it] ?: 0) }, { repText[it] ?: "" })
        )
    }

    /** 整串全部有效拼音分段（有界 DP；段长<=6，每位置封顶 MAX_DISPLAY_SPLITS） */
    private fun fullSplits(digits: String): List<String> {
        val n = digits.length
        if (n == 0) return emptyList()
        val dp = Array(n + 1) { mutableListOf<String>() }
        dp[0].add("")

        for (i in 1..n) {
            // 长段优先生成：完整音节（如 che）先于垃圾组合（如 ai+e）占用封顶名额
            for (j in maxOf(0, i - MAX_PINYIN_LEN) until i) {
                if (dp[j].isEmpty()) continue
                val matches = segmentPinyins(digits.substring(j, i))
                if (matches.isEmpty()) continue
                for (prefix in dp[j]) {
                    for (py in matches) {
                        if (dp[i].size >= MAX_DISPLAY_SPLITS) break
                        dp[i].add(if (prefix.isEmpty()) py else "$prefix $py")
                    }
                    if (dp[i].size >= MAX_DISPLAY_SPLITS) break
                }
            }
        }
        return dp[n]
    }

    /** 单个数字段的所有有效拼音（字母组合规模有界，段长 <= 6） */
    private fun segmentPinyins(segment: String): List<String> {
        if (segment.length > MAX_PINYIN_LEN) return emptyList()
        var combos = listOf("")
        for (d in segment) {
            val letters = digitLetters[d] ?: return emptyList()
            combos = combos.flatMap { pre -> letters.map { pre + it } }
        }
        return combos.filter { it in validPinyins }
    }

    /**
     * 联想候选：返回词库中以已上屏词为前缀的更长词条（如 中国 -> 中国人/中国梦）。
     * 无语言模型下的实用近似；真正的上下文预测留给 bigram 阶段。
     */
    fun associate(base: String): List<Candidate> {
        if (base.isEmpty() || !database.isReady) return emptyList()
        if (base.none { it in '\u4E00'..'\u9FFF' }) return emptyList()
        return database.queryWordsByPrefix(base, 30)
            .filter { it.word.length <= base.length + 4 }
            .map { Candidate(it.word, it.score, it.pinyin) }
            .take(20)
    }

    fun inputFullPinyin(pinyin: String): List<Candidate> {
        if (pinyin.isEmpty() || !database.isReady) return emptyList()
        val key = pinyin.lowercase()
        return database.queryPrefix(DictionaryDatabase.toDigits(key), 60)
            .filter { it.pinyin.replace("'", "").startsWith(key) }
            .map { Candidate(it.word, it.score, it.pinyin) }
            .take(20)
    }

    /** 拼音 → T9 数字序列 */
    fun pinyinToDigits(pinyin: String): String = DictionaryDatabase.toDigits(pinyin)

    /** 用户选词学习：记一次（带衰减的）偏好。旧版按拼音更新会连带宠坏同音词 */
    fun learnSelection(pinyin: String, word: String) {
        database.bumpPreference(pinyin, word)
    }

    /** 用户主动加词 / 整句组合学出的新词：入用户词库，不衰减 */
    fun addUserWord(pinyin: String, word: String) {
        database.addUserWord(pinyin, word)
    }

    fun hasUserWord(word: String): Boolean = database.hasUserWord(word)

    /** 删除自造词（长按候选触发）；基础词库的词删不掉，返回是否真的删了 */
    fun removeUserWord(word: String) {
        database.removeUserWord(word)
    }

    companion object {
        private const val CANDIDATE_LIMIT = 60
        private const val PREFIX_EXACT_LIMIT = 16
        private const val CONTINUE_LIMIT = 96

        /** 拼音最长字母数（zhuang/chuang = 6） */
        private const val MAX_PINYIN_LEN = 6

        /** 整句 DP：每个位置保留的路径数上限 */
        private const val K = 8

        /**
         * bigram 权重（×1000）。0.6 是在 473 条评测语料上扫出来的平台期取值：
         * 再大开始被偶发共现带偏，再小上下文几乎不起作用。
         */
        private const val LM_BETA_PER_MILLE = 600

        /**
         * 上文权重（×1000，阶段 4a）：候选分数 += β_ctx · 中心化 bigram(上文末字, 候选首字)。
         *
         * 比串内 β 大得多是刻意的：串内边界是"同一条输入内部的搭接"，多个候选共享同量级
         * 的词频差异，上下文只需微调；而候选重排面对的是"同一数字串里几个语料频率本就
         * 相近的字"（8426 = 天/条/调/挑），词频那一项几乎没有区分力，只能靠上下文定夺。
         *
         * 1.6 取自 `_tools/eval/eval_context.py` 的扫描：平台期很宽（1200~2600 都在
         * Top-1 90%+ 且 Top-3 100%），取平台中段而非饱和点（2000+）——饱和意味着
         * 上下文已完全压过词频，一旦 bigram 估计偏了就没有兜底。
         */
        private const val CTX_BETA_PER_MILLE = 1600

        /**
         * 段罚（毫纳特）：每多切一段扣 1.5 纳特，偏向更长（更少段）的切分。
         * 与 bigram 权重分离，因为「上下文强度」和「偏向长词程度」是两件事。
         * 语言模型缺失时此项仍然生效——它本身是个合理的切分先验。
         */
        private const val LM_SEG_PENALTY = 1500

        /** 整句 DP：单词数字长上限（涵盖绝大多数 2-4 字词） */
        private const val MAX_WORD_DIGITS = 8

        /** 整句 DP：每个子串取的词条数上限 */
        private const val WORDS_PER_SUB = 6

        /** 拼音回显每个位置保留的分段数上限 */
        private const val MAX_DISPLAY_SPLITS = 16

        /** 读法代表字缺失时的惩罚分（毫纳特），保证无候选的读法排到最后 */
        private const val SELECTOR_NO_REP_PENALTY = -30000

        /** 读法本身是词库词条时的加法奖励（毫纳特，约 8 纳特 ≈ 3000 倍偏好） */
        private const val SELECTOR_WHOLE_WORD_BONUS = 8000
    }
}
