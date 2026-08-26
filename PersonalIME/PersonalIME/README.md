# PersonalIME - 个人安卓输入法

一个功能完整的个人安卓输入法应用，支持9键拼音输入、中英混输、计算机术语词库等特性。

## 功能特性

### 核心功能
- ✅ **9键拼音输入**：T9键盘布局，快速输入
- ✅ **渐进式拼音输入**：先打首字声母，根据候选逐步补全
- ✅ **中英混输**：无需手动切换模式
- ✅ **计算机术语词库**：内置100+常用技术术语（Python, API, SDK, Docker, Git等）
- ✅ **隐私模式**：开启后不记录词频和学习输入习惯
- ✅ **敏感场景保护**：密码框、支付页自动禁用记录

### 键盘功能
- ✅ 键盘高度调节
- ✅ 键盘左右偏移（单手模式）
- ✅ 按键大小调节
- ✅ 5款基础主题（浅色、深色、蓝色、绿色、紫色）
- ✅ 自定义背景图片（预留接口）
- ✅ 振动反馈强度调节
- ✅ 按键音调节

### 工具功能
- ✅ 剪贴板管理（最近20条）
- ✅ Emoji表情面板（预留接口）
- ✅ 常用符号快捷入口（预留接口）

## 技术架构

### 开发环境
- **语言**：Kotlin
- **最低SDK**：Android 8.0 (API 26)
- **目标SDK**：Android 14 (API 34)
- **构建工具**：Gradle 8.5
- **Kotlin版本**：1.9.22

### 项目结构
```
PersonalIME/
├── app/
│   ├── src/main/
│   │   ├── java/com/personal/ime/
│   │   │   ├── data/           # 数据层
│   │   │   │   ├── DictionaryDatabase.kt    # SQLite词库
│   │   │   │   ├── PreferencesManager.kt    # DataStore配置
│   │   │   │   └── ClipboardManager.kt      # 剪贴板管理
│   │   │   ├── engine/         # 拼音引擎
│   │   │   │   └── PinyinEngine.kt          # T9拼音转换
│   │   │   ├── service/        # 输入法服务
│   │   │   │   └── PersonalIMEService.kt    # 核心IME服务
│   │   │   ├── ui/             # 界面层
│   │   │   │   └── SettingsActivity.kt      # 设置界面
│   │   │   └── util/           # 工具类
│   │   │       ├── FeedbackManager.kt       # 振动/声音反馈
│   │   │       └── EmojiData.kt             # Emoji数据
│   │   ├── res/                # 资源文件
│   │   │   ├── layout/         # 布局文件
│   │   │   ├── xml/            # XML配置
│   │   │   ├── values/         # 字符串、颜色等
│   │   │   └── drawable/       # 图形资源
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

### 核心技术
- **InputMethodService**：Android输入法服务框架
- **SQLite**：本地词库存储
- **DataStore**：用户配置持久化
- **Coroutines**：异步处理
- **ViewBinding**：视图绑定

## 编译说明

### 前置要求
1. Android Studio Hedgehog (2023.1.1) 或更高版本
2. JDK 17
3. Android SDK (API 34)
4. Gradle 8.5

### 编译步骤

#### 方法一：使用Android Studio（推荐）

1. 打开Android Studio
2. 选择 "Open an existing project"
3. 选择 `/data/PersonalIME` 目录
4. 等待Gradle同步完成
5. 点击 "Build" -> "Build Bundle(s) / APK(s)" -> "Build APK(s)"
6. APK文件将生成在 `app/build/outputs/apk/debug/` 目录

#### 方法二：使用命令行

```bash
# 进入项目目录
cd /data/PersonalIME

# 赋予gradlew执行权限
chmod +x gradlew

# 编译debug版本
./gradlew assembleDebug

# APK文件位置
ls -lh app/build/outputs/apk/debug/app-debug.apk
```

### 生成Release版本

```bash
# 编译release版本（需要配置签名）
./gradlew assembleRelease
```

## 安装说明

### 方法一：ADB安装

```bash
# 连接设备后执行
adb install app/build/outputs/apk/debug/app-debug.apk
```

### 方法二：手动安装

1. 将APK文件传输到手机
2. 在手机上打开文件管理器
3. 找到APK文件并点击安装
4. 允许"安装未知来源应用"

### 启用输入法

1. 安装完成后，打开PersonalIME应用
2. 点击"启用输入法"按钮
3. 在系统设置中找到PersonalIME并启用
4. 返回应用，点击"选择输入法"
5. 选择PersonalIME作为当前输入法

## 使用说明

### 9键拼音输入

1. **基本输入**：按数字键输入拼音
   - 例如：输入"中"，按 9-4-6-6-4（zhong）

2. **渐进式输入**：
   - 先按首字母对应的数字键
   - 查看候选词列表
   - 点击候选词或继续输入更多字母

3. **选择候选词**：
   - 点击顶部候选栏中的词语
   - 或按空格键选择第一个候选词

4. **删除**：按⌫键删除输入

5. **空格**：确认输入或插入空格

6. **回车**：确认输入或换行

### 隐私模式

1. 打开PersonalIME设置
2. 开启"隐私模式"开关
3. 开启后不会记录词频和学习输入习惯

### 键盘设置

- **键盘高度**：调节键盘整体高度
- **按键大小**：调节按键尺寸
- **键盘偏移**：左右偏移实现单手模式
- **振动强度**：调节按键振动反馈
- **按键音量**：调节按键声音大小

### 主题切换

支持5种主题：
- 浅色主题
- 深色主题
- 蓝色主题
- 绿色主题
- 紫色主题

在设置中选择"主题设置"即可切换。

## 性能指标

- **按键响应**：< 30ms
- **候选生成**：< 50ms
- **内存占用**：< 50MB
- **包体积**：< 20MB（实际约5-8MB）

## 词库说明

### 内置词库

1. **计算机术语**（100+词）：
   - 编程语言：Python, Java, Kotlin, JavaScript, Go, Rust等
   - 框架工具：React, Vue, Django, Spring, TensorFlow等
   - 技术概念：API, SDK, HTTP, Docker, Kubernetes等
   - 开发工具：Git, VSCode, IntelliJ, Android Studio等

2. **常用中文词汇**（60+词）：
   - 基础词汇：中、国、人、大、小、上、下等
   - 数字时间：天、日、月、年等
   - 代词助词：我、你、他、的、了、吗等

### 词库扩展

词库存储在SQLite数据库中，可以通过以下方式扩展：

1. 修改 `DictionaryDatabase.kt` 中的 `insertTechTerms()` 方法
2. 添加新的词汇和拼音映射
3. 重新编译安装

## 开发计划

### 已完成
- [x] 9键拼音输入核心功能
- [x] T9键盘布局
- [x] 拼音引擎和词库
- [x] 候选词显示和选择
- [x] 隐私模式
- [x] 键盘设置（高度、大小、偏移、振动、声音）
- [x] 5款主题
- [x] 剪贴板管理
- [x] 设置界面

### 待完成
- [ ] 符号键盘完整实现
- [ ] Emoji面板完整实现
- [ ] 自定义背景图片功能
- [ ] 更丰富的词库
- [ ] 长按功能（数字、符号）
- [ ] 滑动输入
- [ ] 皮肤编辑器

## 许可证

本项目仅供个人使用和学习。

## 联系方式

如有问题或建议，欢迎反馈。

---

**注意**：这是一个个人使用的输入法应用，主要用于满足特定需求。如需在生产环境使用，建议进行充分的测试和优化。
