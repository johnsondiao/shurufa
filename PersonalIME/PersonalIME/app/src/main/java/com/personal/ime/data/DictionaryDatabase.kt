package com.personal.ime.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.ln
import kotlin.math.pow

/**
 * 词库数据层（v1，用户库 `ime_user.db` + 挂载只读基础库 `base_words.db` + 语言模型 `bigram.db`）
 *
 * ## 相对旧版的结构性变化
 *
 * 旧版把两件语义完全不同的事压进同一个 `words.frequency` 整数：
 *   ①「这个词在汉语里有多常用」——语言先验，只读，随词库更新
 *   ②「这个用户有多喜欢它」——个人偏好，可写，会衰减
 * 后果：15 个魔法档位常量互相竞争、5 张补丁表越加越多、升级迁移越来越复杂、
 * 用户数据无法独立重置、词库无法独立更新。
 *
 * 新版拆成三张表：
 *   base_words（在 base_words.db，只读，随包分发）—— 连续 logp
 *   user_words（本文件，可写）—— 用户主动添加，**不衰减**
 *   user_pref （本文件，可写）—— 系统学出的偏好，**指数衰减**
 *
 * 另有一个独立只读的**字符级 bigram**（bigram.db，见 [LanguageModel]），
 * 给整句候选提供词间上下文，不做词频，只做「哪个字接哪个字更顺」。
 *
 * ## 分数单位
 * 所有分数统一为 **毫纳特（natural log × 1000）的整数**，越大越优。
 * 基础词 logp 范围约 -26000 ~ -3000（离线流水线产出）。
 * 用户偏好通过 [PREF_DOMINANCE] 直接压过基础档，而不是去调一个"不撞档的魔数"。
 *
 * ## 为什么不用 WAL
 * ATTACH 是**连接级**状态，而启用 WAL 会让 Android 使用连接池，
 * 池中其它连接看不到 `base` / `lm` 别名，查询会报 `no such table: base.base_words`。
 * 未启用 WAL 时连接池大小为 1，ATTACH 安全。用户库写入量极小（每次上屏一条 upsert），
 * 不需要 WAL 的并发能力。
 */
class DictionaryDatabase(private val appContext: Context) :
    SQLiteOpenHelper(appContext, DB_NAME, null, DB_VERSION) {

    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val baseDictionary = BaseDictionary(appContext)
    private val languageModel = LanguageModel(appContext)

    @Volatile
    private var ready = false

    /** base 别名是否已挂到当前连接上 */
    @Volatile
    private var baseAttached = false

    /** lm 别名是否已挂到当前连接上 */
    @Volatile
    private var lmAttached = false

    val isReady: Boolean get() = ready

    /** 语言模型是否已载入内存（未就绪时整句排序退化为纯词频） */
    val isLanguageModelReady: Boolean get() = languageModel.isLoaded

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        // 刻意不调用 enableWriteAheadLogging()，原因见类注释（ATTACH 与连接池互斥）
        baseAttached = false
        lmAttached = false
    }

    override fun onCreate(db: SQLiteDatabase) {
        // 用户主动添加的词：优先级极高、持久、不随时间衰减
        db.execSQL("""
            CREATE TABLE $TABLE_USER_WORDS (
                pinyin TEXT NOT NULL,
                word   TEXT NOT NULL,
                digits TEXT NOT NULL,
                logp   INTEGER NOT NULL,
                added_at INTEGER NOT NULL,
                PRIMARY KEY (digits, word)
            )
        """)
        db.execSQL("CREATE INDEX idx_user_words_word ON $TABLE_USER_WORDS($COL_WORD)")

        // 系统学出的偏好：带时间戳，读取时按 Δt 指数衰减
        db.execSQL("""
            CREATE TABLE $TABLE_USER_PREF (
                scope  TEXT NOT NULL,
                key    TEXT NOT NULL,
                digits TEXT NOT NULL,
                cnt    INTEGER NOT NULL,
                t_last INTEGER NOT NULL,
                PRIMARY KEY (scope, key, digits)
            )
        """)
        db.execSQL("CREATE INDEX idx_user_pref_digits ON $TABLE_USER_PREF(digits)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // v1 为初版。基础词库与用户库已分离，词库升级不再需要动用户数据，
        // 因此这里是空的——旧版那 8 段 "if (oldVersion < N)" 迁移链随之消失。
    }

    /**
     * 后台预热：安装基础词库（首次约 25 MB 拷贝）+ 挂载，再载入语言模型。
     * 不在这里做任何数据导入——词库是离线构建好的成品文件。
     */
    fun warmUp() {
        try {
            if (!baseDictionary.ensureInstalled()) {
                ready = false
                return
            }
            attachIfNeeded(writableDatabase)
            ready = true
        } catch (e: Exception) {
            ready = false
            return
        }
        // 语言模型是**增强项**：缺失或读取失败就退化为纯词频排序，不影响基础输入
        try {
            if (languageModel.ensureInstalled()) {
                val database = writableDatabase
                languageModel.attach(database)
                languageModel.load(database)
            }
        } catch (e: Exception) {
            // 保持未加载状态即可：boundaryScore 会返回 null，DP 自动跳过 bigram 项
        }
    }

    private fun attachIfNeeded(db: SQLiteDatabase) {
        if (!baseAttached) {
            try {
                baseDictionary.attach(db)
            } catch (e: SQLiteException) {
                // 已挂载（重复 ATTACH 会抛错）——视为成功
            }
            baseAttached = true
        }
        if (!lmAttached) {
            try {
                languageModel.attach(db)
            } catch (e: SQLiteException) {
                // 同上；也可能是语言模型资产缺失，此时 attach 内部已直接返回
            }
            lmAttached = true
        }
    }

    /** 统一的取库入口：保证 base 已挂载 */
    private fun db(): SQLiteDatabase {
        val database = writableDatabase
        attachIfNeeded(database)
        return database
    }

    // ──────────────────────────────────────────────────────────── 查询

    /** 数字序列精确匹配（恰好打完） */
    fun queryExact(digits: String, limit: Int): List<WordEntry> =
        query("$COL_DIGITS = ?", arrayOf(digits), digits, limit)

    /** 数字序列前缀匹配（续打：输入 64426 也能命中更长的词） */
    fun queryPrefix(digits: String, limit: Int): List<WordEntry> =
        query("$COL_DIGITS GLOB ?", arrayOf(digits + "*"), digits, limit)

    /**
     * 精确匹配 + 拼音前缀过滤（用户在拼音选择列点了某个读法）。
     * 拼音过滤在内存里做：数字组通常只有几十条，不值当为它单独建索引
     * （旧版有一个 (pinyin, freq) 索引，为省约 10 MB 已去掉）。
     */
    fun queryExactAndPinyin(digits: String, pinyinPrefix: String, limit: Int): List<WordEntry> =
        query("$COL_DIGITS = ?", arrayOf(digits), digits, limit * 3)
            .filter { it.pinyin.replace("'", "").startsWith(pinyinPrefix) }
            .take(limit)

    /** 联想：以已上屏词为前缀的更长词条（走 base 的 word 索引） */
    fun queryWordsByPrefix(prefix: String, limit: Int): List<WordEntry> {
        val out = ArrayList<WordEntry>(limit)
        val sql = """
            SELECT $COL_WORD, $COL_PINYIN, $COL_LOGP, $COL_FLAGS FROM ${BaseDictionary.ALIAS}.${BaseDictionary.TABLE}
            WHERE $COL_WORD GLOB ? AND $COL_WORD != ?
            ORDER BY $COL_LOGP DESC LIMIT ?
        """
        db().rawQuery(sql, arrayOf(prefix + "*", prefix, limit.toString())).use { c ->
            while (c.moveToNext()) out.add(c.toEntry())
        }
        mergeUserWords(out, prefix)
        return out.sortedByDescending { it.score }.take(limit)
    }

    /**
     * 英文预测：以 prefix 开头的用户词条，返回 (词, 权重)。
     *
     * 基础词库不含拉丁词条（离线流水线只收中文），所以英文候选只来自用户学习
     * （[learnEnglishWord] 写入 user_words）。权重用"越近上屏越靠前"的序号，
     * 因为 user_words 是 REPLACE 语义、不累计次数——最近用过的排前面即可。
     */
    fun queryWords(prefix: String, limit: Int): List<Pair<String, Int>> {
        val out = ArrayList<Pair<String, Int>>(limit)
        db().rawQuery(
            "SELECT $COL_WORD FROM $TABLE_USER_WORDS " +
                "WHERE $COL_WORD GLOB ? AND $COL_WORD GLOB '[a-zA-Z]*' " +
                "ORDER BY $COL_ADDED_AT DESC LIMIT ?",
            arrayOf(prefix.lowercase() + "*", limit.toString())
        ).use { c ->
            var rank = 0
            while (c.moveToNext()) {
                out.add(c.getString(0) to (limit - rank).coerceAtLeast(1))
                rank++
            }
        }
        return out
    }

    /**
     * 拼音精确匹配的最高分词条（拼音选择器做"读法成词"加成用）。
     * 走 digits 主键 + 内存过滤拼音，不需要 pinyin 索引。
     */
    fun topWordByPinyinExact(pinyin: String): WordEntry? {
        val digits = toDigits(pinyin)
        return queryExact(digits, 60).firstOrNull { it.pinyin == pinyin }
    }

    // ──────────────────────────────────────────────────────────── 语言模型

    /**
     * 词间边界分（毫纳特，越大越优）：上一词末字 → 下一词首字 的上下文偏好。
     *
     * @return null 表示语言模型未就绪，或后字不在模型字符集内（拉丁/生僻字）。
     *         调用方必须**跳过**该项，不能记 0 分——记 0 等于认为"这个搭接很常见"，
     *         会在模型缺失时反而偏爱长路径。
     */
    fun bigramScore(prev: Char, next: Char): Int? = languageModel.boundaryScore(prev, next)

    private fun query(where: String, args: Array<String>, digits: String, limit: Int): List<WordEntry> {
        val out = ArrayList<WordEntry>(limit)
        val sql = """
            SELECT $COL_WORD, $COL_PINYIN, $COL_LOGP, $COL_FLAGS
            FROM ${BaseDictionary.ALIAS}.${BaseDictionary.TABLE}
            WHERE $where ORDER BY $COL_LOGP DESC LIMIT ?
        """
        db().rawQuery(sql, args + limit.toString()).use { c ->
            while (c.moveToNext()) out.add(c.toEntry())
        }
        mergeUserWords(out, digits)
        return out.sortedByDescending { it.score }.take(limit)
    }

    /**
     * 合并用户数据：用户添加的词条 + 用户偏好加成。
     * 必须在内存里做——若把 user_pref 写进 SQL 的 ORDER BY，
     * 低分但被用户偏好的词会在 LIMIT 之前就被截掉，偏好永远不生效。
     */
    private fun mergeUserWords(out: MutableList<WordEntry>, digitsPattern: String) {
        val database = db()
        val now = System.currentTimeMillis() / 1000L

        // 1) 用户主动添加的词：不衰减，直接给高分
        val likeArg = if (digitsPattern.endsWith("*")) digitsPattern else "$digitsPattern*"
        val fromUser = ArrayList<WordEntry>()
        database.rawQuery(
            "SELECT $COL_WORD, $COL_PINYIN, $COL_LOGP, $COL_DIGITS FROM $TABLE_USER_WORDS WHERE $COL_DIGITS GLOB ?",
            arrayOf(likeArg)
        ).use { c ->
            while (c.moveToNext()) {
                if (c.getString(3).startsWith(digitsPattern)) {
                    fromUser.add(WordEntry(c.getString(0), c.getString(1), c.getInt(2), 0))
                }
            }
        }
        // 2) 用户偏好：按 Δt 指数衰减后转成压制性加成
        val prefBonus = HashMap<String, Int>()
        database.rawQuery(
            "SELECT key, cnt, t_last FROM $TABLE_USER_PREF WHERE scope = ? AND $COL_DIGITS GLOB ?",
            arrayOf(SCOPE_WORD, likeArg)
        ).use { c ->
            while (c.moveToNext()) {
                val weight = preferenceWeight(c.getLong(1), c.getLong(2), now)
                if (weight >= PREF_MIN_WEIGHT) {
                    prefBonus[c.getString(0)] = PREF_DOMINANCE
                }
            }
        }

        if (fromUser.isEmpty() && prefBonus.isEmpty()) return

        val byWord = HashMap<String, WordEntry>()
        for (e in out) byWord[e.word] = e
        for (e in fromUser) {
            val prev = byWord[e.word]
            if (prev == null || e.score > prev.score) byWord[e.word] = e
        }
        out.clear()
        out.addAll(byWord.values.map { e ->
            val bonus = prefBonus[e.word] ?: 0
            if (bonus == 0) e else e.copy(score = e.score + bonus)
        })
    }

    /**
     * 偏好权重（纳特）：`ln(1+cnt) · 2^(-Δt/半衰期)`
     * - `ln(1+cnt)` 让"反复选择"比"偶尔误选"更稳
     * - 指数项让旧习惯自然退场：半衰期 7 天，约 3 周后低于阈值自动失宠
     */
    private fun preferenceWeight(cnt: Long, tLast: Long, now: Long): Double {
        if (cnt <= 0) return 0.0
        val deltaDays = ((now - tLast).coerceAtLeast(0L)) / 86400.0
        return ln(1.0 + cnt) * 0.5.pow(deltaDays / PREF_HALF_LIFE_DAYS)
    }

    // ──────────────────────────────────────────────────────────── 学习

    /**
     * 用户选中某词：记一次偏好（衰减型）。
     * 只按 (word, digits) 记账，不再"按拼音更新"——旧版按拼音更新会把用户
     * 没选中的同音词一起顶上去（选了「你好」却把「昵好」宠坏）。
     */
    fun bumpPreference(pinyin: String, word: String) {
        val digits = toDigits(pinyin)
        ioScope.launch {
            val now = System.currentTimeMillis() / 1000L
            db().execSQL(
                "INSERT INTO $TABLE_USER_PREF(scope, key, digits, cnt, t_last) VALUES(?,?,?,1,?) " +
                    "ON CONFLICT(scope, key, digits) DO UPDATE SET cnt = cnt + 1, t_last = ?",
                arrayOf(SCOPE_WORD, word, digits, now, now)
            )
        }
    }

    /**
     * 用户主动添加的词（或整句上屏时学出的组合词）：
     * 写入 user_words，优先级高且**不衰减**——与"系统学出的偏好"是两种东西。
     */
    fun addUserWord(pinyin: String, word: String) {
        val pinyinKey = pinyin.lowercase()
        val digits = toDigits(pinyinKey)
        ioScope.launch {
            val values = ContentValues().apply {
                put(COL_PINYIN, pinyinKey)
                put(COL_WORD, word)
                put(COL_DIGITS, digits)
                put(COL_LOGP, USER_WORD_LOGP)
                put(COL_ADDED_AT, System.currentTimeMillis() / 1000L)
            }
            db().insertWithOnConflict(TABLE_USER_WORDS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    /** 学习英文单词：仅接受纯拉丁字母（用户库新增条目） */
    fun learnEnglishWord(word: String) {
        val lower = word.lowercase()
        if (lower.isEmpty() || !lower.all { it in 'a'..'z' }) return
        addUserWord(lower, word)
    }

    /** 清空全部用户数据（不影响基础词库）——旧版只能靠"清除应用数据"才能做到 */
    fun resetUserData() {
        ioScope.launch {
            val database = db()
            database.execSQL("DELETE FROM $TABLE_USER_WORDS")
            database.execSQL("DELETE FROM $TABLE_USER_PREF")
        }
    }

    // ──────────────────────────────────────────────────────────── 工具

    private fun Cursor.toEntry() = WordEntry(getString(0), getString(1), getInt(2), getInt(3))

    companion object {
        private const val DB_NAME = "ime_user.db"
        private const val DB_VERSION = 1

        private const val TABLE_USER_WORDS = "user_words"
        private const val TABLE_USER_PREF = "user_pref"
        private const val COL_PINYIN = "pinyin"
        private const val COL_WORD = "word"
        private const val COL_DIGITS = "digits"
        private const val COL_LOGP = "logp"
        private const val COL_FLAGS = "flags"
        private const val COL_ADDED_AT = "added_at"

        private const val SCOPE_WORD = "word"

        /** 用户添加词的初始 logp：高于绝大多数基础词，保证立即可见 */
        private const val USER_WORD_LOGP = -2000

        /**
         * 偏好压制加成（毫纳特）。基础词 logp 范围约 -26000 ~ -3000，
         * 取 40000 保证被偏好的词**一定**压过任何基础词，无需去猜同音组里的最大分。
         * 这是"随使用越来越懂你"的实现方式，而衰减负责让它自然退场。
         */
        private const val PREF_DOMINANCE = 40000

        /** 偏好权重阈值（纳特）：低于此值视为已遗忘 */
        private const val PREF_MIN_WEIGHT = 0.15

        /** 偏好半衰期（天）：约 3 周后一次误选自然失宠 */
        private const val PREF_HALF_LIFE_DAYS = 7.0

        /** 拼音字母 → T9 数字（v 是 ü 的键入形式，与 u 同键） */
        private val LETTER_TO_DIGIT = mapOf(
            'a' to '2', 'b' to '2', 'c' to '2',
            'd' to '3', 'e' to '3', 'f' to '3',
            'g' to '4', 'h' to '4', 'i' to '4',
            'j' to '5', 'k' to '5', 'l' to '5',
            'm' to '6', 'n' to '6', 'o' to '6',
            'p' to '7', 'q' to '7', 'r' to '7', 's' to '7',
            't' to '8', 'u' to '8', 'v' to '8',
            'w' to '9', 'x' to '9', 'y' to '9', 'z' to '9'
        )

        /** 拼音/英文单词 → T9 数字序列（忽略音节分隔符） */
        fun toDigits(text: String): String =
            text.lowercase().filter { it != '\'' }
                .map { LETTER_TO_DIGIT[it] ?: it }.joinToString("")
    }
}

/**
 * 词库返回的一条候选。
 * @param score 毫纳特（自然对数 × 1000）：基础 logp + 用户偏好加成，越大越优
 * @param flags 位标记：1=单字 4=领域词 8=拉丁词 16=口语词（由离线流水线写入）
 */
data class WordEntry(
    val word: String,
    val pinyin: String,
    val score: Int,
    val flags: Int = 0
)
