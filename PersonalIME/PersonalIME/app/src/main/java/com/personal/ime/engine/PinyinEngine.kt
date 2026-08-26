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

        // 生成所有可能的拼音分段组合
        val possiblePinyins = generateValidPinyinCombinations(digits)

        // 查询数据库
        val candidates = mutableMapOf<String, Int>()
        possiblePinyins.forEach { pinyin ->
            database.queryWords(pinyin, 10).forEach { (word, freq) ->
                candidates[word] = (candidates[word] ?: 0) + freq
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
     * 例如：74732 -> qi(74) + ge(43) + ?(2) 或 shi(743) + ge(43) + ?(2)
     */
    private fun generateValidPinyinCombinations(digits: String): List<String> {
        if (digits.isEmpty()) return listOf("")

        val results = mutableListOf<String>()

        // 尝试所有可能的分段方式
        for (endIndex in 1..digits.length) {
            val segment = digits.substring(0, endIndex)
            val possibleLetters = generateLetterCombinations(segment)

            // 检查是否有有效拼音
            val validPinyinMatches = possibleLetters.filter { it in validPinyins }

            if (validPinyinMatches.isNotEmpty()) {
                if (endIndex == digits.length) {
                    // 整个数字序列是一个拼音
                    results.addAll(validPinyinMatches)
                } else {
                    // 递归处理剩余部分
                    val restCombinations = generateValidPinyinCombinations(digits.substring(endIndex))
                    validPinyinMatches.forEach { pinyin ->
                        if (restCombinations.isEmpty()) {
                            results.add(pinyin)
                        } else {
                            restCombinations.forEach { rest ->
                                results.add(pinyin + rest)
                            }
                        }
                    }
                }
            }
        }

        return results.distinct()
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
}
