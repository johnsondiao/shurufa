# PersonalIME 编译和安装指南

## 项目概述

PersonalIME 是一个功能完整的个人安卓输入法应用，使用 Kotlin 开发，支持9键拼音输入、中英混输、计算机术语词库等特性。

## 环境要求

### 必需软件
1. **Android Studio** (Hedgehog 2023.1.1 或更高版本)
   - 下载地址：https://developer.android.com/studio
   
2. **JDK 17**
   - Android Studio 自带 JDK，或单独安装
   
3. **Android SDK**
   - 通过 Android Studio 的 SDK Manager 安装
   - 需要 API 34 (Android 14)

4. **Gradle 8.5**
   - 项目已包含 Gradle Wrapper，会自动下载

### 系统要求
- **操作系统**：Windows 10/11, macOS 10.14+, Linux
- **内存**：至少 8GB RAM（推荐 16GB）
- **磁盘空间**：至少 10GB 可用空间

## 编译步骤

### 方法一：使用 Android Studio（推荐）

#### 1. 打开项目
```bash
# 启动 Android Studio
# 选择 "Open an existing project"
# 浏览到 /data/PersonalIME 目录
# 点击 "OK"
```

#### 2. 等待 Gradle 同步
- Android Studio 会自动下载依赖
- 首次同步可能需要几分钟
- 确保底部状态栏显示 "BUILD SUCCESSFUL"

#### 3. 编译 Debug 版本
```
菜单：Build -> Build Bundle(s) / APK(s) -> Build APK(s)
```

编译完成后，APK 文件位于：
```
/data/PersonalIME/app/build/outputs/apk/debug/app-debug.apk
```

#### 4. 编译 Release 版本（可选）
```
菜单：Build -> Generate Signed Bundle / APK
选择 "APK"
创建或选择密钥库
选择 "release"
点击 "Finish"
```

### 方法二：使用命令行

#### 1. 进入项目目录
```bash
cd /data/PersonalIME
```

#### 2. 赋予 gradlew 执行权限
```bash
chmod +x gradlew
```

#### 3. 编译 Debug 版本
```bash
./gradlew assembleDebug
```

编译成功后，APK 位于：
```
app/build/outputs/apk/debug/app-debug.apk
```

#### 4. 清理构建
```bash
./gradlew clean
```

#### 5. 查看构建任务
```bash
./gradlew tasks
```

## 安装到设备

### 准备工作
1. **启用开发者选项**
   - 手机：设置 -> 关于手机
   - 连续点击"版本号"7次
   - 返回设置，找到"开发者选项"

2. **启用 USB 调试**
   - 设置 -> 开发者选项
   - 开启"USB 调试"

3. **连接设备**
   - 使用 USB 数据线连接手机和电脑
   - 手机上弹出授权对话框时，点击"允许"

### 方法一：ADB 安装（推荐）

```bash
# 验证设备连接
adb devices

# 应该看到类似输出：
# List of devices attached
# XXXXXXXXXXXX    device

# 安装 APK
adb install app/build/outputs/apk/debug/app-debug.apk

# 如果已安装旧版本，使用 -r 参数覆盖安装
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 方法二：手动安装

1. **传输 APK 到手机**
   ```bash
   # 通过 USB
   adb push app/build/outputs/apk/debug/app-debug.apk /sdcard/Download/
   
   # 或通过其他方式（邮件、云盘、蓝牙等）
   ```

2. **在手机上安装**
   - 打开文件管理器
   - 找到 APK 文件（通常在 Download 目录）
   - 点击 APK 文件
   - 如果提示"未知来源"，允许安装
   - 点击"安装"

## 启用输入法

### 1. 启用 PersonalIME
```
安装完成后，打开 PersonalIME 应用
点击"启用输入法"按钮
在系统设置中找到"PersonalIME"
打开开关启用
```

### 2. 选择 PersonalIME 为当前输入法
```
返回 PersonalIME 应用
点击"选择输入法"按钮
在弹出的输入法列表中选择"PersonalIME"
```

### 3. 开始使用
```
打开任意应用（如微信、备忘录）
点击输入框
PersonalIME 键盘会自动弹出
```

## 常见问题

### Q1: Gradle 同步失败
**解决方案**：
- 检查网络连接
- 在 Android Studio 中：File -> Invalidate Caches / Restart
- 删除项目根目录的 `.gradle` 文件夹，重新同步

### Q2: 编译失败 "SDK location not found"
**解决方案**：
- 在项目根目录创建 `local.properties` 文件
- 添加内容：`sdk.dir=/path/to/Android/sdk`
- 或在 Android Studio 中：File -> Project Structure -> SDK Location

### Q3: 安装失败 "INSTALL_FAILED_UPDATE_INCOMPATIBLE"
**解决方案**：
```bash
# 先卸载旧版本
adb uninstall com.personal.ime

# 再安装新版本
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Q4: 输入法不显示
**解决方案**：
- 重启手机
- 重新启用 PersonalIME
- 检查是否在密码输入框（某些应用会禁用第三方输入法）

### Q5: 键盘高度不合适
**解决方案**：
- 打开 PersonalIME 设置
- 调整"键盘高度"滑块
- 实时预览效果

## 性能优化

### 编译加速
```bash
# 启用 Gradle 守护进程
echo "org.gradle.daemon=true" >> gradle.properties

# 并行编译
echo "org.gradle.parallel=true" >> gradle.properties

# 启用构建缓存
echo "org.gradle.caching=true" >> gradle.properties
```

### 内存优化
```bash
# 增加 Gradle 堆内存
echo "org.gradle.jvmargs=-Xmx2048m" >> gradle.properties
```

## 调试技巧

### 查看日志
```bash
# 实时查看 PersonalIME 日志
adb logcat | grep -i "PersonalIME"

# 查看特定级别的日志
adb logcat *:E | grep -i "PersonalIME"
```

### 远程调试
1. 在 Android Studio 中打开项目
2. 连接设备
3. 点击 "Run" -> "Run 'app'"
4. 选择目标设备
5. 应用会自动安装并启动

## 版本更新

### 更新版本号
编辑 `app/build.gradle.kts`：
```kotlin
android {
    defaultConfig {
        versionCode = 2  // 递增
        versionName = "1.0.1"  // 更新版本号
    }
}
```

### 生成新版本 APK
```bash
./gradlew clean assembleDebug
```

## 备份和恢复

### 备份词库
```bash
# 需要 root 权限
adb shell
su
cp /data/data/com.personal.ime/databases/dictionary.db /sdcard/
exit
adb pull /sdcard/dictionary.db
```

### 恢复词库
```bash
adb push dictionary.db /sdcard/
adb shell
su
cp /sdcard/dictionary.db /data/data/com.personal.ime/databases/
chmod 660 /data/data/com.personal.ime/databases/dictionary.db
exit
```

## 技术支持

如遇到问题，请检查：
1. Android Studio 版本是否为最新
2. Gradle 版本是否匹配（8.5）
3. Kotlin 版本是否正确（1.9.22）
4. 依赖库是否完整下载

## 项目结构说明

```
PersonalIME/
├── app/                          # 主应用模块
│   ├── src/main/
│   │   ├── java/                 # Kotlin 源代码
│   │   ├── res/                  # 资源文件
│   │   └── AndroidManifest.xml   # 应用清单
│   └── build.gradle.kts          # 模块构建配置
├── gradle/                       # Gradle 配置
├── build.gradle.kts              # 项目构建配置
├── settings.gradle.kts           # 项目设置
├── gradlew                       # Gradle 包装脚本
└── README.md                     # 项目说明
```

## 下一步

编译安装完成后，您可以：
1. 熟悉9键拼音输入方式
2. 调整键盘设置（高度、大小、主题等）
3. 开启隐私模式保护敏感输入
4. 使用剪贴板管理功能
5. 根据需要扩展词库

祝您使用愉快！
