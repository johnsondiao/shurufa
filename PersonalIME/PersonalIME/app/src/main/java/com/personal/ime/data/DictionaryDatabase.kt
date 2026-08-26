package com.personal.ime.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DictionaryDatabase(context: Context) : SQLiteOpenHelper(context, "dictionary.db", null, 1) {

    companion object {
        private const val TABLE_WORDS = "words"
        private const val COL_PINYIN = "pinyin"
        private const val COL_WORD = "word"
        private const val COL_FREQ = "frequency"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE $TABLE_WORDS (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_PINYIN TEXT NOT NULL,
                $COL_WORD TEXT NOT NULL,
                $COL_FREQ INTEGER DEFAULT 1,
                UNIQUE($COL_PINYIN, $COL_WORD)
            )
        """)

        // Insert built-in tech terms
        insertTechTerms(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_WORDS")
        onCreate(db)
    }

    private fun insertTechTerms(db: SQLiteDatabase) {
        val techTerms = mapOf(
            "python" to "Python",
            "api" to "API",
            "sdk" to "SDK",
            "http" to "HTTP",
            "docker" to "Docker",
            "git" to "Git",
            "java" to "Java",
            "kotlin" to "Kotlin",
            "javascript" to "JavaScript",
            "typescript" to "TypeScript",
            "react" to "React",
            "vue" to "Vue",
            "angular" to "Angular",
            "nodejs" to "Node.js",
            "npm" to "npm",
            "yarn" to "yarn",
            "webpack" to "Webpack",
            "linux" to "Linux",
            "windows" to "Windows",
            "macos" to "macOS",
            "ios" to "iOS",
            "android" to "Android",
            "sql" to "SQL",
            "nosql" to "NoSQL",
            "mongodb" to "MongoDB",
            "mysql" to "MySQL",
            "postgresql" to "PostgreSQL",
            "redis" to "Redis",
            "aws" to "AWS",
            "azure" to "Azure",
            "gcp" to "GCP",
            "kubernetes" to "Kubernetes",
            "k8s" to "k8s",
            "devops" to "DevOps",
            "ci" to "CI",
            "cd" to "CD",
            "json" to "JSON",
            "xml" to "XML",
            "yaml" to "YAML",
            "html" to "HTML",
            "css" to "CSS",
            "sass" to "Sass",
            "less" to "Less",
            "bootstrap" to "Bootstrap",
            "tailwind" to "Tailwind",
            "flutter" to "Flutter",
            "dart" to "Dart",
            "swift" to "Swift",
            "rust" to "Rust",
            "golang" to "Golang",
            "php" to "PHP",
            "ruby" to "Ruby",
            "rails" to "Rails",
            "django" to "Django",
            "flask" to "Flask",
            "spring" to "Spring",
            "hibernate" to "Hibernate",
            "tensorflow" to "TensorFlow",
            "pytorch" to "PyTorch",
            "keras" to "Keras",
            "numpy" to "NumPy",
            "pandas" to "Pandas",
            "scipy" to "SciPy",
            "matplotlib" to "Matplotlib",
            "jupyter" to "Jupyter",
            "vscode" to "VSCode",
            "intellij" to "IntelliJ",
            "eclipse" to "Eclipse",
            "androidstudio" to "Android Studio",
            "xcode" to "Xcode",
            "github" to "GitHub",
            "gitlab" to "GitLab",
            "bitbucket" to "Bitbucket",
            "stackoverflow" to "StackOverflow",
            "dockerhub" to "DockerHub",
            "nginx" to "Nginx",
            "apache" to "Apache",
            "tomcat" to "Tomcat",
            "graphql" to "GraphQL",
            "rest" to "REST",
            "grpc" to "gRPC",
            "websocket" to "WebSocket",
            "jwt" to "JWT",
            "oauth" to "OAuth",
            "ssl" to "SSL",
            "tls" to "TLS",
            "https" to "HTTPS",
            "tcp" to "TCP",
            "udp" to "UDP",
            "ip" to "IP",
            "dns" to "DNS",
            "cdn" to "CDN",
            "api" to "API",
            "rpc" to "RPC",
            "ide" to "IDE",
            "vcs" to "VCS",
            "scm" to "SCM"
        )

        techTerms.forEach { (pinyin, word) ->
            val values = ContentValues().apply {
                put(COL_PINYIN, pinyin.lowercase())
                put(COL_WORD, word)
                put(COL_FREQ, 100) // High frequency for tech terms
            }
            db.insert(TABLE_WORDS, null, values)
        }

        // Insert some common Chinese words
        val commonWords = listOf(
            "zhong" to "中",
            "guo" to "国",
            "ren" to "人",
            "da" to "大",
            "xiao" to "小",
            "shang" to "上",
            "xia" to "下",
            "zuo" to "左",
            "you" to "右",
            "qian" to "前",
            "hou" to "后",
            "tian" to "天",
            "di" to "地",
            "ri" to "日",
            "yue" to "月",
            "nian" to "年",
            "shi" to "是",
            "de" to "的",
            "le" to "了",
            "wo" to "我",
            "ni" to "你",
            "ta" to "他",
            "men" to "们",
            "zhe" to "这",
            "na" to "那",
            "ge" to "个",
            "shi" to "时",
            "jian" to "间",
            "xue" to "学",
            "sheng" to "生",
            "gong" to "工",
            "zuo" to "作",
            "cheng" to "程",
            "xu" to "序",
            "yuan" to "员",
            "kai" to "开",
            "fa" to "发",
            "ce" to "测",
            "shi" to "试",
            "bu" to "部",
            "shu" to "数",
            "ju" to "据",
            "ku" to "库",
            "biao" to "表",
            "fu" to "服",
            "wu" to "务",
            "qi" to "器",
            "wang" to "网",
            "luo" to "络",
            "an" to "安",
            "quan" to "全",
            "fang" to "防",
            "hu" to "护",
            "mi" to "密",
            "ma" to "码",
            "jian" to "键",
            "pan" to "盘",
            "shu" to "输",
            "ru" to "入",
            "fa" to "法",
            "wen" to "文",
            "ben" to "本",
            "zi" to "字",
            "fu" to "符",
            "hao" to "好",
            "hen" to "很",
            "hao" to "号",
            "ma" to "吗",
            "ne" to "呢",
            "ba" to "吧",
            "a" to "啊",
            "o" to "哦",
            "en" to "嗯"
        )

        commonWords.forEach { (pinyin, word) ->
            val values = ContentValues().apply {
                put(COL_PINYIN, pinyin.lowercase())
                put(COL_WORD, word)
                put(COL_FREQ, 50)
            }
            db.insert(TABLE_WORDS, null, values)
        }
    }

    fun queryWords(pinyin: String, limit: Int = 20): List<Pair<String, Int>> {
        val words = mutableListOf<Pair<String, Int>>()
        val db = readableDatabase

        val cursor = db.query(
            TABLE_WORDS,
            arrayOf(COL_WORD, COL_FREQ),
            "$COL_PINYIN LIKE ?",
            arrayOf("$pinyin%"),
            null, null,
            "$COL_FREQ DESC",
            limit.toString()
        )

        cursor.use {
            while (it.moveToNext()) {
                val word = it.getString(0)
                val freq = it.getInt(1)
                words.add(word to freq)
            }
        }

        return words
    }

    fun incrementFrequency(pinyin: String, word: String) {
        val db = writableDatabase
        db.execSQL(
            "UPDATE $TABLE_WORDS SET $COL_FREQ = $COL_FREQ + 1 WHERE $COL_PINYIN = ? AND $COL_WORD = ?",
            arrayOf(pinyin, word)
        )
    }

    fun insertWord(pinyin: String, word: String, frequency: Int = 1) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_PINYIN, pinyin.lowercase())
            put(COL_WORD, word)
            put(COL_FREQ, frequency)
        }
        db.insertWithOnConflict(TABLE_WORDS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }
}
