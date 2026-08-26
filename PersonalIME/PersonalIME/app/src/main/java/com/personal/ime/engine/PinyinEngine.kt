package com.personal.ime.engine

import com.personal.ime.data.DictionaryDatabase

class PinyinEngine(private val database: DictionaryDatabase) {

    // T9 key mapping: digit -> possible letters
    private val t9Map = mapOf(
        '2' to listOf('a', 'b', 'c'),
        '3' to listOf('d', 'e', 'f'),
        '4' to listOf('g', 'h', 'i'),
        '5' to listOf('j', 'k', 'l'),
        '6' to listOf('m', 'n', 'o'),
        '7' to listOf('p', 'q', 'r', 's'),
        '8' to listOf('t', 'u', 'v'),
        '9' to listOf('w', 'x', 'y', 'z')
    )

    // Reverse mapping: letter -> digit
    private val letterToDigit = mutableMapOf<Char, Char>().apply {
        t9Map.forEach { (digit, letters) ->
            letters.forEach { letter ->
                put(letter, digit)
            }
        }
    }

    // 有效拼音列表（用于 T9 匹配）
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

    data class Candidate(val text: String, val frequency: Int)

    fun inputT9(digits: String): List<Candidate> {
        if (digits.isEmpty()) return emptyList()

        // 生成所有可能的拼音分段组合（已限制规模）
        val possiblePinyins = generateValidPinyinCombinations(digits).take(MAX_QUERY_PINYINS)

        // 查询数据库，只保留中文词条（避免 LIKE 前缀匹配把英文科技词汇捞进中文候选）
        val candidates = mutableMapOf<String, Int>()
        possiblePinyins.forEach { pinyin ->
            database.queryWords(pinyin, 8).forEach { (word, freq) ->
                if (word.any { it in '\u4E00'..'\u9FFF' }) {
                    candidates[word] = (candidates[word] ?: 0) + freq
                }
            }
        }

        return candidates.entries
            .map { Candidate(it.key, it.value) }
            .sortedByDescending { it.frequency }
            .take(20)
    }

    fun inputFullPinyin(pinyin: String): List<Candidate> {
        if (pinyin.isEmpty()) return emptyList()

        return database.queryWords(pinyin.lowercase(), 20)
            .map { Candidate(it.first, it.second) }
    }

    /**
     * 生成有效的拼音组合（支持多音节词的分段匹配）
     * 例如：74732 -> qi(74) + ge(43) + a(2)
     *
     * 使用动态规划代替指数级递归：
     * - 拼音最长 6 个字母（如 zhuang/chuang），分段长度超过 6 直接跳过
     * - 每个位置的组合数封顶，避免长数字串时计算量爆炸卡死键盘
     */
    private fun generateValidPinyinCombinations(digits: String): List<String> {
        val n = digits.length
        // dp[i] = digits[0 until i) 的所有有效拼音拼接结果
        val dp = Array(n + 1) { mutableListOf<String>() }
        dp[0].add("")

        for (i in 1..n) {
            for (j in maxOf(0, i - MAX_PINYIN_LEN) until i) {
                if (dp[j].isEmpty()) continue
                val segment = digits.substring(j, i)
                val matches = generateLetterCombinations(segment).filter { it in validPinyins }
                if (matches.isEmpty()) continue

                for (prefix in dp[j]) {
                    for (pinyin in matches) {
                        if (dp[i].size >= MAX_COMBINATIONS) break
                        dp[i].add(prefix + pinyin)
                    }
                    if (dp[i].size >= MAX_COMBINATIONS) break
                }
            }
        }

        return dp[n].distinct()
    }

    /**
     * 根据数字序列生成所有可能的字母组合
     */
    private fun generateLetterCombinations(digits: String): List<String> {
        if (digits.isEmpty()) return listOf("")

        val result = mutableListOf<String>()
        val firstDigit = digits[0]
        val letters = t9Map[firstDigit] ?: return emptyList()

        val restCombinations = generateLetterCombinations(digits.substring(1))

        letters.forEach { letter ->
            if (restCombinations.isEmpty()) {
                result.add(letter.toString())
            } else {
                restCombinations.forEach { rest ->
                    result.add(letter + rest)
                }
            }
        }

        return result
    }

    fun pinyinToDigits(pinyin: String): String {
        return pinyin.lowercase().map { letterToDigit[it] ?: it }.joinToString("")
    }

    fun incrementFrequency(pinyin: String, word: String) {
        database.incrementFrequency(pinyin, word)
    }

    fun addWord(pinyin: String, word: String) {
        database.insertWord(pinyin, word)
    }

    companion object {
        /** 拼音最长字母数（zhuang/chuang = 6） */
        private const val MAX_PINYIN_LEN = 6

        /** 单个位置的拼音组合数上限，防止长输入时计算量爆炸 */
        private const val MAX_COMBINATIONS = 200

        /** 每次查询的拼音组合数上限 */
        private const val MAX_QUERY_PINYINS = 60
    }
}
