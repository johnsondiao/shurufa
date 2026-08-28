package com.personal.ime.engine

import com.personal.ime.data.DictionaryDatabase

/**
 * T9 拼音引擎：基于数字序列前缀匹配。
 *
 * 词库中每个词条入库时预计算 T9 数字序列（如 能不能 -> 6364286364），
 * 输入时直接按数字前缀查询，天然支持：
 * - 渐进输入：每按一键都有候选，无需等音节打完
 * - 续打匹配：输入 6364 也能命中更长的 能不能（其数字序列以 6364 开头）
 * - 半音节容忍：输入 63642（neng + b 一半）仍能保持 能不能 在候选中
 */
class PinyinEngine(private val database: DictionaryDatabase) {

    /** components 仅整句候选使用：组成该句的各词，上屏时逐词学习词频 */
    data class Candidate(val text: String, val frequency: Int, val pinyin: String = "", val components: List<String> = emptyList())

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

    fun inputT9(digits: String): List<Candidate> {
        if (digits.isEmpty()) return emptyList()
        // 词库尚未完成首次导入时由调用方展示提示，这里直接返回空避免阻塞主线程
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
        // key=词条，value=(词频, 拼音, matchTier)；tier 0=恰好打完 1=已打部分 2=续打更长，
        // 主流输入法的第一规则：“打完的词置顶”，否则会被大量前缀词/高频单字淹没（如 撤退/词 打不出）
        val merged = LinkedHashMap<String, Triple<Int, String, Int>>()

        // 1) 恰好打完：数字与词条完全相等 —— 最高优先（如 243884 = 撤退）。
        //    同音组可达 260+ 条（如 94=xi/yi/zi），与最终展示窗口 60 对齐；
        //    全量覆盖率模拟：取 16 时 23.8% 词条打不出，取 60 降至 ~4.5%
        database.queryByDigitsExact(plain, 60)
            .filter { (word, pinyin, _) ->
                word.any { it in '\u4E00'..'\u9FFF' } && matchesBoundaries(pinyin, boundaries)
            }
            .forEach { (word, pinyin, freq) -> merged[word] = Triple(freq, pinyin, 0) }

        // 2) 已打部分：从长到短找已完整输入的最长前缀，短词在续打时仍可见；
        //    恰好打完有结果时跳过（避免短词抢占名额）
        if (!merged.any { it.value.third == 0 }) {
            for (p in plain.length - 1 downTo 1) {
                val exact = database.queryByDigitsExact(plain.substring(0, p), 16)
                    .filter { (word, pinyin, _) ->
                        word.any { it in '\u4E00'..'\u9FFF' } && matchesBoundaries(pinyin, boundaries)
                    }
                if (exact.isNotEmpty()) {
                    exact.forEach { (word, pinyin, freq) ->
                        if (word !in merged) merged[word] = Triple(freq, pinyin, 1)
                    }
                    break
                }
            }
        }

        // 3) 续打匹配：数字序列以输入开头的更长词；已有更高优先级的词不降级覆盖，
        //    多取一些再截断（边界验证会淘汰部分候选）
        database.queryByDigitsPrefix(plain, 96)
            .filter { (word, pinyin, _) ->
                word.any { it in '\u4E00'..'\u9FFF' } && matchesBoundaries(pinyin, boundaries)
            }
            .forEach { (word, pinyin, freq) ->
                if (word !in merged) merged[word] = Triple(freq, pinyin, 2)
            }

        return merged.entries
            .map { Candidate(it.key, it.value.first, it.value.second) }
            .sortedWith(
                // 匹配层级升序（打完的词置顶）；层内词频降序；同频短词优先。
                // LinkedHashMap 插入序保证同层同频内“恰好打完”的词条稳定靠前。
                compareBy<Candidate> { c -> merged[c.text]?.third ?: 2 }
                    .thenByDescending { it.frequency }
                    .thenBy { it.text.length }
            )
            .take(CANDIDATE_LIMIT)
    }

    /**
     * 整句/组合候选：把整串数字切分成若干词库词条的组合（覆盖全部输入）。
     * 如 548744... -> “就是完整的”。用 DP 找“词数最少、词频最高”的若干切分，
     * 让用户连续打字（不按空格断词）也能出多词组合候选。
     */
    fun sentenceCandidates(digits: String, limit: Int = 3): List<Candidate> {
        // 分词键（'）切出强制音节边界：整句切分的词边界必须落在这些位置上，
        // 与 inputT9 的边界语义保持一致（94'26 只出 xi'an 类组合，排除 xian 类）
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

        // segments 越少越优（倾向更长的词），同词数下词频总和越高越优
        data class Path(val segments: Int, val score: Int, val text: String, val pinyin: String, val components: List<String>)

        val dp = Array(n + 1) { mutableListOf<Path>() }
        dp[0].add(Path(0, 0, "", "", emptyList()))
        val K = 3              // 每个位置保留的候选路径数（控制规模）
        val MAX_WORD_DIGITS = 8 // 单词数字长上限（涵盖绝大多数 2-4 字词）
        val WORDS_PER_SUB = 6   // 每个子串取的词条数上限
        val cmp = compareBy<Path>({ it.segments }, { -it.score })

        for (i in 1..n) {
            val paths = mutableListOf<Path>()
            for (j in maxOf(0, i - MAX_WORD_DIGITS) until i) {
                // 强制边界不能落在词内部：跨边界的 (j, i) 切分直接跳过，
                // 这样合法切分必然在每个边界处断词
                if (boundaries.any { it > j && it < i }) continue
                val prevList = dp[j]
                if (prevList.isEmpty()) continue
                val sub = plain.substring(j, i)
                val words = database.queryByDigitsExact(sub, WORDS_PER_SUB)
                if (words.isEmpty()) continue
                for (prev in prevList) {
                    for ((word, pinyin, freq) in words) {
                        if (!word.any { it in '\u4E00'..'\u9FFF' }) continue
                        paths.add(
                            Path(
                                prev.segments + 1,
                                prev.score + freq,
                                prev.text + word,
                                if (prev.pinyin.isEmpty()) pinyin else prev.pinyin + "'" + pinyin,
                                prev.components + word
                            )
                        )
                    }
                }
            }
            dp[i] = paths.sortedWith(cmp).take(K).toMutableList()
        }

        // segments>=2 才是真正的“组合”（单词候选已由 inputT9 覆盖）
        return dp[n]
            .filter { it.segments >= 2 }
            .map { Candidate(it.text, it.score, it.pinyin, it.components) }
            .distinctBy { it.text }
            .take(limit)
    }

    /**
     * 用户选了某个读法后的输入：拼音过滤下推到 DB 精确查询，
     * 避免只在内存小窗口内过滤导致该读法的字被窗口截断（如 94 选 yi 后 意/易 打不出）。
     */
    fun inputT9ByPinyin(digits: String, selected: String, limit: Int = 60): List<Candidate> {
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
        // 1) 恰好打完：数字相等 + 拼音以选中读法开头（DB 层过滤）
        database.queryByDigitsExactAndPinyin(plain, dbPrefix, limit)
            .filter { (word, pinyin, _) ->
                word.any { it in '\u4E00'..'\u9FFF' } && matchesBoundaries(pinyin, boundaries)
                        && pinyin.replace("'", "").startsWith(selKey)
            }
            .forEach { (word, pinyin, freq) -> merged[word] = Triple(freq, pinyin, 0) }

        // 2) 续打：以输入开头的更长词，同样按选中读法过滤；已有词不降级覆盖
        database.queryByDigitsPrefix(plain, 96)
            .filter { (word, pinyin, _) ->
                word.any { it in '\u4E00'..'\u9FFF' } && matchesBoundaries(pinyin, boundaries)
                        && pinyin.replace("'", "").startsWith(selKey)
            }
            .forEach { (word, pinyin, freq) ->
                if (word !in merged) merged[word] = Triple(freq, pinyin, 2)
            }

        return merged.entries
            .map { Candidate(it.key, it.value.first, it.value.second) }
            .sortedWith(
                compareBy<Candidate> { c -> merged[c.text]?.third ?: 2 }
                    .thenByDescending { it.frequency }
                    .thenBy { it.text.length }
            )
            .take(limit)
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
        // reachable[i]：前 i 个字母可完整切分为有效音节
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
     * 输入含强制分隔符（94'26）时，各段独立取默认切分后拼接。
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
            return full.distinct()
                .sortedBy { it.count { c -> c == ' ' } }  // 空格少的优先（切分少的 = 完整拼音）
                .take(limit)
        }
        // 整串不可分段：回退到最长的可完整分段前缀
        for (i in digits.length - 1 downTo 1) {
            val prefixSplits = fullSplits(digits.substring(0, i))
            if (prefixSplits.isNotEmpty()) return prefixSplits.distinct().take(limit)
        }
        return emptyList()
    }

    /** 整串全部有效拼音分段（有界 DP；段长<=6，每位置封顶 MAX_DISPLAY_SPLITS） */
    private fun fullSplits(digits: String): List<String> {
        val n = digits.length
        if (n == 0) return emptyList()
        val dp = Array(n + 1) { mutableListOf<String>() }
        dp[0].add("")

        for (i in 1..n) {
            // 长段优先生成：完整音节（如 che）先于垃圾组合（如 ai+e）占用封顶名额，
            // 否则正确读法会被短段组合挤出（如 243884 丢失 che tui）
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

    fun inputFullPinyin(pinyin: String): List<Candidate> {
        if (pinyin.isEmpty()) return emptyList()

        return database.queryWords(pinyin.lowercase(), 20)
            .map { Candidate(it.first, it.second) }
    }

    /** 拼音 → T9 数字序列 */
    fun pinyinToDigits(pinyin: String): String = DictionaryDatabase.toDigits(pinyin)

    /** 用户选词后提升词频 */
    fun incrementFrequency(word: String) {
        database.incrementFrequency(word)
    }

    fun addWord(pinyin: String, word: String) {
        database.insertWord(pinyin, word)
    }

    companion object {
        // 覆盖率实测：20 条时 23.8% 词条不可达，60 条时仅 4.5%（超大同音组尾部）
        private const val CANDIDATE_LIMIT = 60

        /** 拼音最长字母数（zhuang/chuang = 6） */
        private const val MAX_PINYIN_LEN = 6

        /** 拼音回显每个位置保留的分段数上限：太小会让正确读法被剪掉（如 che tui），16 兼顾质量与开销 */
        private const val MAX_DISPLAY_SPLITS = 16
    }
}
