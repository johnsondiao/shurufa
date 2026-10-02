package com.personal.ime.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase

/**
 * 字符级 bigram 语言模型（阶段 4b）。
 *
 * ## 它解决什么
 * 整句候选把数字串切成若干词条，此前只按各词的 unigram 频率求和，**完全没有上下文**，
 * 于是「说得好 / 说的好」「在哪 / 在那」这类同音歧义只能靠词频猜。
 * bigram 给**词与词的搭接处**（上一词末字 → 下一词首字）一个条件概率，
 * 让上下文参与决策。
 *
 * ## 为什么是字符级而不是词级
 * 38 万词表的两两共现极度稀疏，语料再大也估计不稳；字符级只需 ~8000 字的两两共现，
 * 同样语料下每个组合都有足够计数，剪枝量化后只有 3 MB 出头。
 *
 * ## 中心化（关键）
 * 打分时每个边界项都减去 [meanLogp]（语料上边界对数概率的期望 ≈ 负的字符熵）。
 * 不做这一步，`Σ 边界项` 会随分段数线性变负，等于塞进一个**与 bigram 强度耦合的长度惩罚**：
 * 整句候选会被单词条挤空，而且 β 这一个旋钮同时在调「上下文强度」和「偏向长词程度」，
 * 无法独立调参。中心化后边界项只表达「这个搭接比平均好多少」，长度偏好另由
 * `PinyinEngine.LM_SEG_PENALTY` 显式控制。
 *
 * ## 为什么整个载入内存
 * 表只有 13 万行，压进一个开放寻址的 long→int 表约 3 MB。而整句 DP 一次要查几千次
 * 边界分，走 SQLite 会成为单键延迟的主要来源。载入后查询就是一次数组访问。
 *
 * ## 剪枝与退避
 * 离线只保留每字 Top-N 后继；未保留的组合用精确退避
 * `ln P_uni(b) + ln(δ/(c(a)+δ))`，两项分别来自 char_uni 的 logp / norm 列，
 * 因此退避不是近似。
 */
class LanguageModel(private val appContext: Context) {

    private val asset = AssetDatabase(
        appContext,
        ASSET_DB, ASSET_VERSION,
        INSTALLED_DB, INSTALLED_VERSION,
        ALIAS
    )

    @Volatile
    private var bigramMap: LongIntMap? = null

    @Volatile
    private var charMap: HashMap<Int, Long>? = null

    @Volatile
    private var meanLogp = 0

    /** 后字不在字符集时的退避下限（已中心化），见 [boundaryScore] */
    @Volatile
    private var floorScore = -100_000

    val isLoaded: Boolean get() = bigramMap != null

    /** 必须在后台线程调用（几 MB 拷贝） */
    fun ensureInstalled(): Boolean = asset.ensureInstalled()

    fun attach(db: SQLiteDatabase) = asset.attach(db)

    /**
     * 把 bigram / char_uni 读进内存。幂等，重复调用直接返回。
     * 挂载完成后在后台调用一次即可。
     */
    fun load(db: SQLiteDatabase) {
        if (bigramMap != null) return

        db.rawQuery("SELECT value FROM $ALIAS.$TABLE_META WHERE key='mean_logp'", null).use { c ->
            if (c.moveToFirst()) meanLogp = c.getString(0).toIntOrNull() ?: 0
        }

        val chars = HashMap<Int, Long>(MAX_CHARS)
        var minLogp = Int.MAX_VALUE
        var minNorm = Int.MAX_VALUE
        db.rawQuery("SELECT cp, logp, norm FROM $ALIAS.$TABLE_CHAR", null).use { c ->
            while (c.moveToNext()) {
                val lp = c.getInt(1)
                val nm = c.getInt(2)
                if (lp < minLogp) minLogp = lp
                if (nm < minNorm) minNorm = nm
                // 高 32 位 = ln P_uni(c)，低 32 位 = ln(δ/(count(c)+δ))
                chars[c.getInt(0)] = (lp.toLong() shl 32) or (nm.toLong() and 0xFFFF_FFFFL)
            }
        }
        // 退避下限：最罕见的后字 × 最常见的前字（= 中心化前可能取到的最小值）
        floorScore = if (chars.isEmpty()) -100_000 else minLogp + minNorm - meanLogp

        val rows = db.rawQuery("SELECT COUNT(*) FROM $ALIAS.$TABLE_BIGRAM", null).use {
            if (it.moveToFirst()) it.getInt(0) else 0
        }
        // 载入即减掉中心化常数：查询热路径上少一次运算
        val map = LongIntMap(rows)
        db.rawQuery("SELECT prev, next, logp FROM $ALIAS.$TABLE_BIGRAM", null).use { c ->
            while (c.moveToNext()) {
                map.put(packKey(c.getInt(0), c.getInt(1)), c.getInt(2) - meanLogp)
            }
        }

        charMap = chars
        bigramMap = map
    }

    /**
     * 词间边界分（毫纳特，已中心化），越大越优。
     *
     * @return null 表示**前字**不在模型字符集内（拉丁字母/标点当上下文），
     *         此时是真正的"无上下文信息"，调用方应跳过该项而非记 0 分。
     */
    fun boundaryScore(prev: Char, next: Char): Int? {
        val map = bigramMap ?: return null
        val hit = map.get(packKey(prev.code, next.code))
        if (hit != LongIntMap.NOT_FOUND) return hit

        val chars = charMap ?: return null
        val nextPacked = chars[next.code]
        // 后字不在 char_uni：这不是"无信息"，而是"这个字在 4500 万字语料里几乎不出现"，
        // 是很强的稀有证据，必须给退避下限。若当成 0 分，会出现「未知」压过
        // 「已知但搭接差」的荒谬结果——实测上文为「后」时打 8426，「蜩/龆/盷」
        // （语料里几乎为零）靠 0 加成反超中心化后为 -11.7 的「天」，把「天」挤出候选。
        if (nextPacked == null) return floorScore

        val prevPacked = chars[prev.code] ?: return null
        return ((nextPacked shr 32).toInt() + prevPacked.toInt()) - meanLogp
    }

    companion object {
        const val ALIAS = "lm"
        const val TABLE_BIGRAM = "bigram"
        const val TABLE_CHAR = "char_uni"
        const val TABLE_META = "lm_meta"

        private const val ASSET_DB = "dict/bigram.db"
        private const val ASSET_VERSION = "dict/bigram.version"
        private const val INSTALLED_DB = "bigram.db"
        private const val INSTALLED_VERSION = "bigram.version.txt"

        private const val MAX_CHARS = 16384

        private fun packKey(prev: Int, next: Int): Long =
            (prev.toLong() shl 32) or (next.toLong() and 0xFFFF_FFFFL)
    }
}

/**
 * 只读的 long → int 开放寻址表（线性探测）。
 *
 * 不用 HashMap<Long, Int> 的原因：13 万条装箱后约 6 MB 且每次查询都要 allocation，
 * 而这里用两个原始数组，容量按 1.5× 取 2 的幂，实质占用约 3 MB、查询零分配。
 * 键为两字码点拼成，恒不为 0（中文字码点 ≥ 0x4E00），故 0 可作空槽哨兵。
 */
internal class LongIntMap(expected: Int) {

    companion object {
        const val NOT_FOUND = Int.MIN_VALUE
    }

    private val mask: Int
    private val keys: LongArray
    private val values: IntArray

    init {
        var capacity = 16
        val target = maxOf(16, expected * 3 / 2)
        while (capacity < target) capacity = capacity shl 1
        mask = capacity - 1
        keys = LongArray(capacity)
        values = IntArray(capacity)
    }

    fun put(key: Long, value: Int) {
        var i = spread(key) and mask
        while (true) {
            val k = keys[i]
            if (k == 0L) {
                keys[i] = key
                values[i] = value
                return
            }
            if (k == key) {
                values[i] = value
                return
            }
            i = (i + 1) and mask
        }
    }

    fun get(key: Long): Int {
        var i = spread(key) and mask
        while (true) {
            val k = keys[i]
            if (k == 0L) return NOT_FOUND
            if (k == key) return values[i]
            i = (i + 1) and mask
        }
    }

    private fun spread(key: Long): Int {
        var h = (key xor (key ushr 32)).toInt()
        h *= 0x27D4EB2D
        return h xor (h ushr 15)
    }
}
