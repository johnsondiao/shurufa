package com.personal.ime.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File

/**
 * 随包分发的**只读**基础词库（语言先验）。
 *
 * 架构变化（相对旧版）：
 *  旧版把 40 万条词条在**设备首次启动时**逐行 INSERT 进 dictionary.db，
 *  再用 8 个补丁方法 + 15 个魔法档位常量去调排序。升级路径（8→16）因此极复杂，
 *  且任何词库更新都要在设备上重跑一遍导入。
 *
 *  新版把词库的构建整个搬到**离线流水线**（`_tools/dict/build_base_words.py`），
 *  产出即可用的 SQLite 文件，随 APK 分发；设备端只做一次文件拷贝
 *  （安装/版本/挂载逻辑见 [AssetDatabase]）。
 *
 *  收益：
 *    1. 消除设备端 40 万行导入与 warmUp 等待
 *    2. 词库可整包热更新（换 asset 文件 + 版本号），不再需要升级迁移脚本
 *    3. base（只读）与用户数据（可写）分文件，用户数据可独立重置，互不影响
 *
 * 版本管理：`assets/dict/base_words.version` 里的字符串与本地已装版本比对，
 * 不一致就重新拷贝。流水线改动词库后需要把这个版本号 +1。
 */
class BaseDictionary(appContext: Context) {

    private val asset = AssetDatabase(
        appContext,
        ASSET_DB, ASSET_VERSION,
        INSTALLED_DB, INSTALLED_VERSION,
        ALIAS
    )

    /** 本地已安装的基础词库文件（未安装时为将要安装到的路径） */
    val databaseFile: File get() = asset.databaseFile

    /** 必须在**后台线程**调用（约 25 MB 拷贝）。@return true 表示已安装且版本匹配 */
    fun ensureInstalled(): Boolean = asset.ensureInstalled()

    /** 把基础词库挂到已打开的连接上。SQLite 的 ATTACH 是连接级的，重复 ATTACH 会报错。 */
    fun attach(db: SQLiteDatabase) = asset.attach(db)

    companion object {
        /** ATTACH 别名：SQL 里用 `base.base_words` 引用 */
        const val ALIAS = "base"
        const val TABLE = "base_words"

        private const val ASSET_DB = "dict/base_words.db"
        private const val ASSET_VERSION = "dict/base_words.version"
        private const val INSTALLED_DB = "base_words.db"
        private const val INSTALLED_VERSION = "base_words.version.txt"
    }
}
