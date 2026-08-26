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

    fun inputT9(digits: String): List<Candidate> {
        if (digits.isEmpty()) return emptyList()

        val merged = LinkedHashMap<String, Int>()

        // 1) 续打匹配：词条数字序列以输入开头的词（含恰好等长的词）
        database.queryByDigitsPrefix(digits, 20).forEach { (word, freq) ->
            if (word.any { it in '\u4E00'..'\u9FFF' }) {
                merged[word] = (merged[word] ?: 0) + freq
            }
        }

        // 2) 回退：从长到短找"恰好打完"的最长前缀，让已打完的短词在续打更长的词时仍可见
        //    例如词库无"能不能"时，输入 6364286364 仍应给出"能"(6364)
        if (merged.size < CANDIDATE_LIMIT) {
            for (p in digits.length - 1 downTo 1) {
                val exact = database.queryByDigitsExact(digits.substring(0, p), 10)
                    .filter { (word, _) -> word.any { it in '\u4E00'..'\u9FFF' } }
                if (exact.isNotEmpty()) {
                    exact.forEach { (word, freq) ->
                        merged[word] = (merged[word] ?: 0) + freq
                    }
                    break
                }
            }
        }

        return merged.entries
            .map { Candidate(it.key, it.value) }
            .sortedByDescending { it.frequency }
            .take(CANDIDATE_LIMIT)
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
    }
}
