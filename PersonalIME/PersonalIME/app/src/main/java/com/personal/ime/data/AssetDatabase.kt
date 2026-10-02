package com.personal.ime.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File

/**
 * 随包分发的**只读**资产库：安装（assets → filesDir）与挂载（ATTACH）。
 *
 * 为什么抽出来：基础词库与语言模型是同一类东西——离线构建好、随包分发、只读挂载，
 * 差别只在文件名与别名。两处各写一遍「拷贝 / 版本比对 / 原子改名」，迟早只改一处，
 * 而这类逻辑出错的表现是「词库静默变旧」，最难排查。
 *
 * 安装策略（幂等）：
 *   1. 比对 assets 里的版本号与本地已装版本号，一致且文件非空 → 直接返回
 *   2. 否则拷贝到 `*.tmp`，**改名**为正式文件（避免中途被杀进程留下半个库）
 *   3. 最后写版本号文件
 *
 * ⚠️ 拷贝是几十 MB 的磁盘 IO，必须在后台线程调用。
 */
class AssetDatabase(
    private val appContext: Context,
    private val assetPath: String,
    private val versionAssetPath: String,
    private val installedName: String,
    private val installedVersionName: String,
    /** SQL 里引用为 `<alias>.<table>` */
    val alias: String
) {

    private val installDir: File get() = File(appContext.filesDir, INSTALL_DIR)

    /** 本地已安装的库文件（未安装时为将要安装到的路径） */
    val databaseFile: File get() = File(installDir, installedName)

    private val versionFile: File get() = File(installDir, installedVersionName)

    /** @return true 表示已就位且版本一致 */
    fun ensureInstalled(): Boolean {
        val wanted = readAssetVersion()
        if (wanted.isEmpty()) return false

        val installed = if (versionFile.exists()) versionFile.readText().trim() else ""
        if (installed == wanted && databaseFile.exists() && databaseFile.length() > 0) return true

        installDir.mkdirs()
        val tmp = File(installDir, "$installedName.tmp")
        return try {
            appContext.assets.open(assetPath).use { input ->
                tmp.outputStream().use { output -> input.copyTo(output, DEFAULT_BUFFER_SIZE * 8) }
            }
            if (databaseFile.exists()) databaseFile.delete()
            if (!tmp.renameTo(databaseFile)) {
                tmp.copyTo(databaseFile, overwrite = true)
                tmp.delete()
            }
            versionFile.writeText(wanted)
            true
        } catch (e: Exception) {
            tmp.delete()
            false
        }
    }

    /** 挂到已打开的连接上。SQLite 的 ATTACH 是连接级状态。 */
    fun attach(db: SQLiteDatabase) {
        if (!databaseFile.exists()) return
        db.execSQL("ATTACH DATABASE ? AS $alias", arrayOf(databaseFile.absolutePath))
    }

    private fun readAssetVersion(): String =
        try {
            appContext.assets.open(versionAssetPath).bufferedReader().use { it.readText().trim() }
        } catch (e: Exception) {
            ""
        }

    companion object {
        private const val INSTALL_DIR = "dict"
    }
}
