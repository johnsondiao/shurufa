package com.personal.ime.engine

import com.personal.ime.data.DictionaryDatabase
import com.personal.ime.util.EnglishWords

/**
 * 英文预测引擎：根据已输入的前缀预判英文单词。
 *
 * 得分规则：
 * - 内置词表：按常用度（索引）赋基础分，越靠前越常用
 * - 数据库（内置科技词汇 + 用户学习记录）：叠加更高权重，越用越靠前
 */
class EnglishEngine(private val database: DictionaryDatabase) {

    fun predict(prefix: String, limit: Int = 10): List<String> {
        if (prefix.length < 2 || !database.isReady) return emptyList()

        val lower = prefix.lowercase()
        val scores = HashMap<String, Long>()

        // 内置词表：索引越小越常用
        EnglishWords.WORDS.forEachIndexed { index, word ->
            if (word.startsWith(lower)) {
                scores[word] = (scores[word] ?: 0L) + (BUILTIN_BASE - index)
            }
        }

        // 数据库：科技词汇与用户学习的词，按词频加权（只取纯英文单词）
        database.queryWords(lower, 30)
            .filter { (word, _) -> word.isNotEmpty() && word.all { it in 'a'..'z' || it in 'A'..'Z' } }
            .forEach { (word, freq) ->
                val key = word.lowercase()
                scores[key] = (scores[key] ?: 0L) + DB_BASE + freq
            }

        return scores.entries
            .sortedWith(
                compareByDescending<Map.Entry<String, Long>> { it.value }
                    .thenBy { it.key.length }
            )
            .map { it.key }
            .take(limit)
    }

    /** 记录用户上屏的英文单词，提升其后续预测排名 */
    fun learn(word: String) {
        database.learnEnglishWord(word)
    }

    companion object {
        private const val BUILTIN_BASE = 100_000L
        private const val DB_BASE = 1_000_000L
    }
}
