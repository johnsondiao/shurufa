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

    data class Candidate(val text: String, val frequency: Int)

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

        val merged = LinkedHashMap<String, Int>()

        // 1) 续打匹配：词条数字序列以输入开头的词（含恰好等长的词）。
        //    带 ' 时按强制音节边界过滤（如 94'26 只要 xi'an 类，排除 xian 类），
        //    验证会淘汰部分候选，故多取一些再截断
        database.queryByDigitsPrefix(plain, 48)
            .filter { (word, pinyin, _) ->
                word.any { it in '\u4E00'..'\u9FFF' } && matchesBoundaries(pinyin, boundaries)
            }
            .forEach { (word, _, freq) ->
                merged[word] = (merged[word] ?: 0) + freq
            }

        // 2) 回退：从长到短找"恰好打完"的最长前缀，让已打完的短词在续打更长的词时仍可见
        if (merged.size < CANDIDATE_LIMIT) {
            for (p in plain.length - 1 downTo 1) {
                val exact = database.queryByDigitsExact(plain.substring(0, p), 16)
                    .filter { (word, pinyin, _) ->
                        word.any { it in '\u4E00'..'\u9FFF' } && matchesBoundaries(pinyin, boundaries)
                    }
                if (exact.isNotEmpty()) {
                    exact.forEach { (word, _, freq) ->
                        merged[word] = (merged[word] ?: 0) + freq
                    }
                    break
                }
            }
        }

        return merged.entries
            .map { Candidate(it.key, it.value) }
            .sortedWith(
                // 词频降序；同频短词优先（打完整音节时二字词排在四字成语前）
                compareByDescending<Candidate> { it.frequency }.thenBy { it.text.length }
            )
            .take(CANDIDATE_LIMIT)
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
        if (full.isNotEmpty()) return full.distinct().take(limit)
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
            for (j in (maxOf(0, i - MAX_PINYIN_LEN) until i).reversed()) {
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
        private const val CANDIDATE_LIMIT = 20

        /** 拼音最长字母数（zhuang/chuang = 6） */
        private const val MAX_PINYIN_LEN = 6

        /** 拼音回显每个位置保留的分段数上限 */
        private const val MAX_DISPLAY_SPLITS = 8
    }
}
