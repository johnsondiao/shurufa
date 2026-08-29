package com.personal.ime.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class DictionaryDatabase(private val appContext: Context) :
    SQLiteOpenHelper(appContext, "dictionary.db", null, 9) {

    /** 词频学习等写操作放到 IO 线程，避免主线程卡顿（用户上屏每个词都会触发） */
    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** 词库是否已完成首次建库导入（未就绪时在 Service 展示提示，避免主线程阻塞） */
    @Volatile
    private var ready = false

    val isReady: Boolean
        get() = ready

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        // WAL：首次建库导入 40 万条词条期间，读请求不被写事务阻塞。
        // 注意 setWriteAheadLoggingEnabled 是隐藏 API，公开 API 只有已废弃的 enableWriteAheadLogging
        @Suppress("DEPRECATION")
        db.enableWriteAheadLogging()
    }

    companion object {
        private const val TABLE_WORDS = "words"
        private const val COL_PINYIN = "pinyin"
        private const val COL_WORD = "word"
        private const val COL_FREQ = "frequency"
        private const val COL_DIGITS = "digits"

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

        /** 拼音/英文单词 → T9 数字序列（忽略音节分隔符 '） */
        fun toDigits(text: String): String =
            text.lowercase().filter { it != '\'' }
                .map { LETTER_TO_DIGIT[it] ?: it }.joinToString("")

        /** 单字词库资产文件（《通用规范汉字表》8105 字） */
        private const val ASSET_CN_CHARS = "cn_chars.txt"

        /** 词语/成字词库资产文件（汉典成语 + 2-4 字词组，约 16 万条） */
        private const val ASSET_CN_WORDS = "cn_words.txt"

        /** 用户词保护档：用户打过的词直接跳入此档，压过所有基础档位（手编词 90/单字 85/词组 50），
         *  之后再打则在档内 +1，几次即可稳定置顶 */
        private const val USER_TIER = 95
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE $TABLE_WORDS (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_PINYIN TEXT NOT NULL,
                $COL_WORD TEXT NOT NULL,
                $COL_FREQ INTEGER DEFAULT 1,
                $COL_DIGITS TEXT NOT NULL DEFAULT '',
                UNIQUE($COL_PINYIN, $COL_WORD)
            )
        """)
        // 复合索引：digits/pinyin 前缀匹配 + frequency 降序可全程走索引，免临时排序
        // （单列 digits 索引由本复合索引前缀覆盖，不再单独建）
        db.execSQL("CREATE INDEX idx_words_digits_freq ON $TABLE_WORDS($COL_DIGITS, $COL_FREQ DESC)")
        db.execSQL("CREATE INDEX idx_words_pinyin_freq ON $TABLE_WORDS($COL_PINYIN, $COL_FREQ DESC)")
        // word 单列索引：词频学习按 word 更新，无此索引会全表扫描 40 万行卡死主线程
        db.execSQL("CREATE INDEX idx_words_word ON $TABLE_WORDS($COL_WORD)")

        // Insert built-in tech terms
        insertTechTerms(db)

        // Insert 《通用规范汉字表》单字（覆盖新华字典全部汉字）
        loadAssetWords(db, ASSET_CN_CHARS)

        // Insert 词语/成字词库（汉典成语 + 2-4 字词组）
        loadAssetWords(db, ASSET_CN_WORDS)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion >= 8) {
            // 8→9：仅补 word 列索引（词频学习按 word 更新），保留已导入词库，
            // 避免 DROP 重建导致重新导入 40 万条（加载慢）
            if (oldVersion < 9) {
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_words_word ON $TABLE_WORDS($COL_WORD)")
            }
        } else {
            db.execSQL("DROP TABLE IF EXISTS $TABLE_WORDS")
            onCreate(db)
        }
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
                put(COL_DIGITS, toDigits(pinyin))
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
            "en" to "嗯",
            "zhongguo" to "中国",
            "renmen" to "人们",
            "daxue" to "大学",
            "xiaoxue" to "小学",
            "shangwu" to "上午",
            "xiawu" to "下午",
            "zuotian" to "昨天",
            "jintian" to "今天",
            "mingtian" to "明天",
            "shijian" to "时间",
            "xuesheng" to "学生",
            "gongzuo" to "工作",
            "chengxu" to "程序",
            "kaifa" to "开发",
            "ceshi" to "测试",
            "shuju" to "数据",
            "wangluo" to "网络",
            "anquan" to "安全",
            "fanghu" to "防护",
            "mima" to "密码",
            "jianpan" to "键盘",
            "shuru" to "输入",
            "wenben" to "文本",
            "zifu" to "字符",
            "haoma" to "号码",
            "women" to "我们",
            "nimen" to "你们",
            "tamen" to "他们",
            "zhege" to "这个",
            "nage" to "那个",
            "shenme" to "什么",
            "zenme" to "怎么",
            "weishenme" to "为什么",
            "duoshao" to "多少",
            "jige" to "几个",
            "yige" to "一个",
            "liangge" to "两个",
            "henduo" to "很多",
            "yixie" to "一些",
            "meiyou" to "没有",
            "you" to "有",
            "shi" to "十",
            "bai" to "百",
            "qian" to "千",
            "wan" to "万",
            "ling" to "零",
            "yi" to "一",
            "er" to "二",
            "san" to "三",
            "si" to "四",
            "wu" to "五",
            "liu" to "六",
            "qi" to "七",
            "ba" to "八",
            "jiu" to "九",
            "dongxi" to "东西",
            "fangxiang" to "方向",
            "difang" to "地方",
            "mingzi" to "名字",
            "dianhua" to "电话",
            "diannao" to "电脑",
            "shouji" to "手机",
            "pingguo" to "苹果",
            "xiangjiao" to "香蕉",
            "chengzi" to "橙子",
            "putao" to "葡萄",
            "xigua" to "西瓜",
            "fanqie" to "番茄",
            "tudou" to "土豆",
            "luobo" to "萝卜",
            "baicai" to "白菜",
            "jidan" to "鸡蛋",
            "niunai" to "牛奶",
            "kafei" to "咖啡",
            "cha" to "茶",
            "shui" to "水",
            "mifan" to "米饭",
            "miantiao" to "面条",
            "jiaozi" to "饺子",
            "baozi" to "包子",
            "mantou" to "馒头",
            "yuebing" to "月饼",
            "zongzi" to "粽子",
            "chuntian" to "春天",
            "xiatian" to "夏天",
            "qiutian" to "秋天",
            "dongtian" to "冬天",
            "zaoshang" to "早上",
            "zhongwu" to "中午",
            "wanshang" to "晚上",
            "banye" to "半夜",
            "xianzai" to "现在",
            "yihou" to "以后",
            "yiqian" to "以前",
            "gangcai" to "刚才",
            "kuai" to "快",
            "man" to "慢",
            "zao" to "早",
            "wan" to "晚",
            "chang" to "长",
            "duan" to "短",
            "gao" to "高",
            "ai" to "矮",
            "pang" to "胖",
            "shou" to "瘦",
            "mei" to "美",
            "chou" to "丑",
            "xin" to "新",
            "jiu" to "旧",
            "gui" to "贵",
            "pianyi" to "便宜",
            "dui" to "对",
            "cuo" to "错",
            "zhen" to "真",
            "jia" to "假",
            "huai" to "坏",
            "leng" to "冷",
            "re" to "热",
            "nuan" to "暖",
            "liang" to "凉",
            "gan" to "干",
            "shi" to "湿",
            "zhong" to "重",
            "qing" to "轻",
            "ruan" to "软",
            "ying" to "硬",
            "hei" to "黑",
            "bai" to "白",
            "hong" to "红",
            "huang" to "黄",
            "lan" to "蓝",
            "lv" to "绿",
            "zi" to "紫",
            "cheng" to "橙",
            "fen" to "粉",
            "hui" to "灰",
            "zong" to "棕",
            "jin" to "金",
            "yin" to "银",
            "tong" to "铜",
            "tie" to "铁",
            "gang" to "钢",
            "shi" to "石",
            "mu" to "木",
            "huo" to "火",
            "tu" to "土",
            "shan" to "山",
            "he" to "河",
            "hai" to "海",
            "hu" to "湖",
            "jiang" to "江",
            "xi" to "溪",
            "quan" to "泉",
            "jing" to "井",
            "tian" to "田",
            "lu" to "路",
            "qiao" to "桥",
            "men" to "门",
            "chuang" to "窗",
            "qiang" to "墙",
            "wading" to "屋顶",
            "louti" to "楼梯",
            "dianti" to "电梯",
            "zoulang" to "走廊",
            "keting" to "客厅",
            "woshi" to "卧室",
            "chufang" to "厨房",
            "weishengjian" to "卫生间",
            "yushi" to "浴室",
            "yangtai" to "阳台",
            "huayuan" to "花园",
            "tingyuan" to "庭院",
            "chekui" to "车库",
            "cangku" to "仓库",
            "bangongshi" to "办公室",
            // 高频功能词与常用疑问/能愿结构
            "neng" to "能",
            "nengbu" to "能不",
            "nengbuneng" to "能不能",
            "keyi" to "可以",
            "keyima" to "可以吗",
            "buxing" to "不行",
            "shibushi" to "是不是",
            "youmeiyou" to "有没有",
            "hui" to "会",
            "huiyi" to "会议",
            "huibuhui" to "会不会",
            "yaobuyao" to "要不要",
            "xiang" to "想",
            "xiangyao" to "想要",
            "yinggai" to "应该",
            "xuyao" to "需要",
            "ganxie" to "感谢",
            "bangzhu" to "帮助",
            "wenti" to "问题",
            "jueding" to "决定",
            "kaishi" to "开始",
            "jieshu" to "结束",
            "zhunbei" to "准备",
            "jihua" to "计划",
            // “度”类高频词与常用搭配
            "du" to "度",
            "gaodu" to "高度",
            "sudu" to "速度",
            "wendu" to "温度",
            "jiaodu" to "角度",
            "shendu" to "深度",
            "yingdu" to "硬度",
            "shidu" to "湿度",
            "kuandu" to "宽度",
            "changdu" to "长度",
            "houdu" to "厚度",
            "nongdu" to "浓度",
            "qiangdu" to "强度",
            "yuedu" to "阅读",
            "gaoxing" to "高兴",
            "gaoshou" to "高手",
            "gaoji" to "高级",
            "gaosu" to "高速",
            "gaobie" to "告别",
            "gaozhi" to "告知",
            "gailv" to "概率",
            "gaibian" to "改变",
            "gaishan" to "改善",
            "gaikuang" to "概况",
            "gainian" to "概念",
            "jiaoshi" to "教室",
            "tushuguan" to "图书馆",
            "bowuguan" to "博物馆",
            "yiyuan" to "医院",
            "yaodian" to "药店",
            "chaoshi" to "超市",
            "shangdian" to "商店",
            "fandian" to "饭店",
            "jiudian" to "酒店",
            "lvguan" to "旅馆",
            "binguan" to "宾馆",
            "jichang" to "机场",
            "huochezhan" to "火车站",
            "qichezhan" to "汽车站",
            "ditiezhan" to "地铁站",
            "gongjiaozhan" to "公交车站",
            "tingchechang" to "停车场",
            "jiayouzhan" to "加油站",
            "yinhang" to "银行",
            "youju" to "邮局",
            "dianxinju" to "电信局",
            "gonganju" to "公安局",
            "xiaofangju" to "消防局",
            "zhengfu" to "政府",
            "xuexiao" to "学校",
            "zhongxue" to "中学",
            "youeryuan" to "幼儿园",
            "yanjiusheng" to "研究生",
            "boshi" to "博士",
            "shuoshi" to "硕士",
            "benke" to "本科",
            "zhuanke" to "专科",
            "tongxue" to "同学",
            "laoshi" to "老师",
            "jiaoshou" to "教授",
            "zhuren" to "主任",
            "xiaozhang" to "校长",
            "yisheng" to "医生",
            "hushi" to "护士",
            "bingren" to "病人",
            "jingcha" to "警察",
            "junren" to "军人",
            "nongmin" to "农民",
            "gongren" to "工人",
            "shangren" to "商人",
            "zuojia" to "作家",
            "huajia" to "画家",
            "yinyuejia" to "音乐家",
            "daoyan" to "导演",
            "yanyuan" to "演员",
            "geshou" to "歌手",
            "wujia" to "舞家",
            "tiyuan" to "体院",
            "jiaolian" to "教练",
            "caipan" to "裁判",
            "xuanshou" to "选手",
            "duiyou" to "队友",
            "duishou" to "对手",
            "pengyou" to "朋友",
            "tongshi" to "同事",
            "lingdao" to "领导",
            "xiaji" to "下级",
            "kehu" to "客户",
            "guke" to "顾客",
            "xiaofeizhe" to "消费者",
            "yonghu" to "用户",
            "huiyuan" to "会员",
            "fensi" to "粉丝",
            "wangyou" to "网友",
            "linju" to "邻居",
            "qinqi" to "亲戚",
            "jiaren" to "家人",
            "fumu" to "父母",
            "baba" to "爸爸",
            "mama" to "妈妈",
            "die" to "爹",
            "niang" to "娘",
            "erzi" to "儿子",
            "nv" to "女",
            "nver" to "女儿",
            "gege" to "哥哥",
            "didi" to "弟弟",
            "jiejie" to "姐姐",
            "meimei" to "妹妹",
            "yeye" to "爷爷",
            "nainai" to "奶奶",
            "waigong" to "外公",
            "waipo" to "外婆",
            "bofu" to "伯父",
            "bomu" to "伯母",
            "shushu" to "叔叔",
            "shen" to "婶",
            "jiujiu" to "舅舅",
            "jiuma" to "舅妈",
            "yima" to "姨妈",
            "yifu" to "姨父",
            "tangge" to "堂哥",
            "tangdi" to "堂弟",
            "tangjie" to "堂姐",
            "tangmei" to "堂妹",
            "biaoge" to "表哥",
            "biaodi" to "表弟",
            "biaojie" to "表姐",
            "biaomei" to "表妹",
            "zhizi" to "侄子",
            "zhinv" to "侄女",
            "waisheng" to "外甥",
            "waishengnv" to "外甥女",
            "sunzi" to "孙子",
            "sunnv" to "孙女",
            "laogong" to "老公",
            "laopo" to "老婆",
            "zhangfu" to "丈夫",
            "qizi" to "妻子",
            "nanpengyou" to "男朋友",
            "nvpengyou" to "女朋友",
            "duixiang" to "对象",
            "lianren" to "恋人",
            "airen" to "爱人",
            "peiou" to "配偶",
            "banlv" to "伴侣",
            "zhiji" to "知己",
            "miyue" to "蜜月",
            "hunyin" to "婚姻",
            "jiating" to "家庭",
            "qinzi" to "亲子",
            "jiazhang" to "家长",
            "haizi" to "孩子",
            "ertong" to "儿童",
            "yinger" to "婴儿",
            "youer" to "幼儿",
            "shaonian" to "少年",
            "qingnian" to "青年",
            "zhongnian" to "中年",
            "laonian" to "老年",
            "laoren" to "老人",
            "nianqing" to "年轻",
            "nianlao" to "年老",
            "chengnian" to "成年",
            "weichengnian" to "未成年",
            "shengri" to "生日",
            "nianling" to "年龄",
            "sui" to "岁",
            "yuesao" to "月嫂",
            "baomu" to "保姆",
            "hugong" to "护工",
            "qingjie" to "清洁",
            "xiuyuan" to "修员",
            "anmo" to "按摩",
            "meifa" to "美发",
            "meirong" to "美容",
            "jianshen" to "健身",
            "yuji" to "瑜伽",
            "youyong" to "游泳",
            "paobu" to "跑步",
            "lanqiu" to "篮球",
            "zuqiu" to "足球",
            "paiqiu" to "排球",
            "yumaoqiu" to "羽毛球",
            "pingpangqiu" to "乒乓球",
            "wangqiu" to "网球",
            "gaoerfu" to "高尔夫",
            "bingqiu" to "冰球",
            "huabing" to "滑冰",
            "huaxue" to "滑雪",
            "qiche" to "汽车",
            "zixingche" to "自行车",
            "diandongche" to "电动车",
            "motuoche" to "摩托车",
            "gonggongqiche" to "公共汽车",
            "chuzuche" to "出租车",
            "didi" to "滴滴",
            "kuaidi" to "快递",
            "waimai" to "外卖",
            "taobao" to "淘宝",
            "jingdong" to "京东",
            "pinduoduo" to "拼多多",
            "meituan" to "美团",
            "eleme" to "饿了么",
            "zhifubao" to "支付宝",
            "weixin" to "微信",
            "qq" to "QQ",
            "douyin" to "抖音",
            "kuaishou" to "快手",
            "bilibili" to "哔哩哔哩",
            "youku" to "优酷",
            "aiqiyi" to "爱奇艺",
            "tengxunshipin" to "腾讯视频",
            "wangyiyunyinyue" to "网易云音乐",
            "qqyinyue" to "QQ音乐",
            "kugou" to "酷狗",
            "kuwo" to "酷我",
            "xiami" to "虾米",
            "douban" to "豆瓣",
            "zhihu" to "知乎",
            "weibo" to "微博",
            "tieba" to "贴吧",
            "luntan" to "论坛",
            "shequ" to "社区",
            "wangzhan" to "网站",
            "yingyong" to "应用",
            "ruanjian" to "软件",
            "yingjian" to "硬件",
            "xitong" to "系统",
            "caozuoxitong" to "操作系统",
            "anzhuo" to "安卓",
            "huawei" to "华为",
            "xiaomi" to "小米",
            "oppo" to "OPPO",
            "vivo" to "VIVO",
            "sanxing" to "三星",
            "meizu" to "魅族",
            "yijia" to "一加",
            "zhenwo" to "真我",
            "hongmi" to "红米",
            "rongyao" to "荣耀"
        )

        commonWords.forEach { (pinyin, word) ->
            val values = ContentValues().apply {
                put(COL_PINYIN, pinyin.lowercase())
                put(COL_WORD, word)
                // 手编最高频词档：高于单字分层词频（55~85）与词组（46~50）
                put(COL_FREQ, 90)
                put(COL_DIGITS, toDigits(pinyin))
            }
            db.insert(TABLE_WORDS, null, values)
        }
    }

    /**
     * 从资产文件批量导入词条（每行 "拼音 词条 词频"，空格分隔）。
     * 精编词组先插入，资产词条用 CONFLICT_IGNORE 避免覆盖；事务提交保证性能。
     */
    private fun loadAssetWords(db: SQLiteDatabase, assetName: String) {
        val lines = try {
            appContext.assets.open(assetName).bufferedReader().use { it.readLines() }
        } catch (e: Exception) {
            return
        }

        // 预编译插入语句：40 万条导入时避免逐条编译，速度提升一个数量级
        val insert = db.compileStatement(
            "INSERT OR IGNORE INTO $TABLE_WORDS($COL_PINYIN, $COL_WORD, $COL_FREQ, $COL_DIGITS) VALUES(?,?,?,?)"
        )
        db.beginTransaction()
        try {
            for (line in lines) {
                val parts = line.split(' ')
                if (parts.size != 3) continue
                val pinyin = parts[0]
                val word = parts[1]
                val freq = parts[2].toIntOrNull() ?: continue
                insert.bindString(1, pinyin)
                insert.bindString(2, word)
                insert.bindLong(3, freq.toLong())
                insert.bindString(4, toDigits(pinyin))
                insert.executeInsert()
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
            insert.close()
        }
    }

    /** 后台预热：触发首次建库与资产导入，避免用户第一次按键时卡住主线程 */
    fun warmUp() {
        readableDatabase
        ready = true
    }

    fun queryWords(pinyin: String, limit: Int = 20): List<Pair<String, Int>> {
        val words = mutableListOf<Pair<String, Int>>()
        val db = readableDatabase

        // GLOB 大小写敏感，可走 BINARY 索引范围扫描；LIKE 在 BINARY 列上不会用索引
        val cursor = db.query(
            TABLE_WORDS,
            arrayOf(COL_WORD, COL_FREQ),
            "$COL_PINYIN GLOB ?",
            arrayOf(pinyin.lowercase() + "*"),
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

    /** T9 前缀匹配：查数字序列以输入数字开头的所有词条（含更长词的续打匹配）。
     *  返回 (词条, 带音节分隔的拼音, 词频)，供引擎校验强制音节边界。 */
    fun queryByDigitsPrefix(digits: String, limit: Int): List<Triple<String, String, Int>> =
        queryByDigits("$COL_DIGITS GLOB ?", "$digits*", limit)

    /** T9 精确匹配：数字序列与输入完全相等的词条 */
    fun queryByDigitsExact(digits: String, limit: Int): List<Triple<String, String, Int>> =
        queryByDigits("$COL_DIGITS = ?", digits, limit)

    /** T9 精确匹配 + 拼音前缀过滤：用户在拼音选择列选了某个读法时使用。
     *  拼音过滤下推到 DB（走 (pinyin, freq) 索引 + digits 等值双条件），
     *  避免只在内存小窗口内过滤导致该读法的字被窗口截断。 */
    fun queryByDigitsExactAndPinyin(digits: String, pinyinPrefix: String, limit: Int): List<Triple<String, String, Int>> {
        val words = mutableListOf<Triple<String, String, Int>>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_WORDS,
            arrayOf(COL_WORD, COL_PINYIN, COL_FREQ),
            "$COL_DIGITS = ? AND $COL_PINYIN GLOB ?",
            arrayOf(digits, pinyinPrefix + "*"),
            null, null,
            "$COL_FREQ DESC",
            limit.toString()
        )
        cursor.use {
            while (it.moveToNext()) {
                words.add(Triple(it.getString(0), it.getString(1), it.getInt(2)))
            }
        }
        return words
    }

    private fun queryByDigits(where: String, arg: String, limit: Int): List<Triple<String, String, Int>> {
        val words = mutableListOf<Triple<String, String, Int>>()
        val db = readableDatabase

        val cursor = db.query(
            TABLE_WORDS,
            arrayOf(COL_WORD, COL_PINYIN, COL_FREQ),
            where,
            arrayOf(arg),
            null, null,
            // 词频降序可走复合索引免排序；同频短词优先在引擎层内存排序
            "$COL_FREQ DESC",
            limit.toString()
        )

        cursor.use {
            while (it.moveToNext()) {
                words.add(Triple(it.getString(0), it.getString(1), it.getInt(2)))
            }
        }

        return words
    }

    /** 按拼音提升词频（用户选词学习）。异步执行 + pinyin 索引，不阻塞主线程。
     *  用户词保护档：词频低于 USER_TIER 时直接跳档（一次上屏即可置顶），已入档则继续 +1 */
    fun incrementFrequency(pinyin: String) {
        ioScope.launch {
            writableDatabase.execSQL(
                "UPDATE $TABLE_WORDS SET $COL_FREQ = CASE WHEN $COL_FREQ < $USER_TIER THEN $USER_TIER ELSE $COL_FREQ + 1 END WHERE $COL_PINYIN = ?",
                arrayOf(pinyin)
            )
        }
    }

    fun insertWord(pinyin: String, word: String, frequency: Int = 1) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_PINYIN, pinyin.lowercase())
            put(COL_WORD, word)
            put(COL_FREQ, frequency)
            put(COL_DIGITS, toDigits(pinyin))
        }
        db.insertWithOnConflict(TABLE_WORDS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    /**
     * 学习英文单词：已存在则词频 +1，否则新建记录。
     * 仅接受纯英文字母的单词，避免中文拼音词条被误写。
     */
    fun learnEnglishWord(word: String) {
        val lower = word.lowercase()
        if (lower.isEmpty() || !lower.all { it in 'a'..'z' }) return

        val db = writableDatabase
        db.execSQL(
            "UPDATE $TABLE_WORDS SET $COL_FREQ = $COL_FREQ + 1 WHERE $COL_PINYIN = ? AND $COL_WORD = ?",
            arrayOf(lower, word)
        )
        val values = ContentValues().apply {
            put(COL_PINYIN, lower)
            put(COL_WORD, word)
            put(COL_FREQ, 1)
            put(COL_DIGITS, toDigits(lower))
        }
        // 已存在时忽略，避免覆盖刚更新的词频
        db.insertWithOnConflict(TABLE_WORDS, null, values, SQLiteDatabase.CONFLICT_IGNORE)
    }
}
