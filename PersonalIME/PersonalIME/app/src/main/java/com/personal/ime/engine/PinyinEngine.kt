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

    data class Candidate(val text: String, val frequency: Int)

    fun inputT9(digits: String): List<Candidate> {
        if (digits.isEmpty()) return emptyList()

        // Generate all possible pinyin combinations from T9 digits
        val possiblePinyins = generatePinyinCombinations(digits)

        // Query database for each pinyin
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

    private fun generatePinyinCombinations(digits: String): List<String> {
        if (digits.isEmpty()) return listOf("")

        val result = mutableListOf<String>()
        val firstDigit = digits[0]
        val letters = t9Map[firstDigit] ?: return emptyList()

        val restCombinations = generatePinyinCombinations(digits.substring(1))

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
