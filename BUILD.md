# Build WebDavMusic

本文档说明如何编译 WebDavMusic 项目。

## 环境要求

- **JDK**: 17 或更高版本
- **Android SDK**: API 34 (Android 14)
- **Gradle**: 8.4 (wrapper 已包含)
- **操作系统**: Linux / macOS / Windows

## 快速开始

### 方法 1: 使用 Android Studio (推荐)

1. 安装 [Android Studio](https://developer.android.com/studio)
2. 打开 Android Studio，选择 "Open an Existing Project"
3. 选择 WebDavMusic 项目目录
4. Android Studio 会自动下载 Gradle、Android SDK 等依赖
5. 等待同步完成后，点击 Build → Build Bundle(s) / APK(s) → Build APK(s)

### 方法 2: 命令行编译

#### Linux / macOS

```bash
# 1. 克隆项目
git clone https://github.com/mylucien/WebDavMusic.git
cd WebDavMusic

# 2. 运行设置脚本（下载 gradle wrapper）
chmod +x setup.sh
./setup.sh

# 3. 设置 JAVA_HOME (如果未设置)
export JAVA_HOME=/path/to/jdk-17

# 4. 设置 ANDROID_HOME (如果未设置)
export ANDROID_HOME=/path/to/android-sdk

# 5. 编译 Debug APK
./gradlew assembleDebug

# APK 输出位置
ls -la app/build/outputs/apk/debug/
```

#### Windows

```powershell
# 1. 克隆项目
git clone https://github.com/mylucien/WebDavMusic.git
cd WebDavMusic

# 2. 运行设置脚本
.\setup.sh

# 3. 设置环境变量
$env:JAVA_HOME="C:\Program Files\Java\jdk-17"
$env:ANDROID_HOME="C:\Users\YourName\AppData\Local\Android\Sdk"

# 4. 编译 Debug APK
.\gradlew.bat assembleDebug

# APK 输出位置
dir app\build\outputs\apk\debug\
```

### 方法 3: 使用 Docker

```bash
# 构建镜像
docker build -t webdav-music-builder -f Dockerfile.build .

# 编译 APK
docker run --rm -v $(pwd)/output:/output webdav-music-builder

# APK 会在 output/ 目录中
ls -la output/
```

## Android SDK 安装

如果尚未安装 Android SDK，可以通过以下方式安装：

### 方法 1: Android Studio

Android Studio 会自动安装和管理 SDK。

### 方法 2: 命令行工具

```bash
# 下载命令行工具
wget https://dl.google.com/android/repository/commandlinetools-linux-9477386_latest.zip
unzip commandlinetools-linux-9477386_latest.zip

# 设置环境变量
export ANDROID_HOME=/path/to/android-sdk
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/bin

# 安装必要的 SDK 组件
sdkmanager --sdk_root=$ANDROID_HOME "platforms;android-34" "build-tools;34.0.0"
```

## 编译选项

### Debug 版本

```bash
./gradlew assembleDebug
```

### Release 版本

```bash
./gradlew assembleRelease
```

### 清理构建

```bash
./gradlew clean
```

### 完整构建

```bash
./gradlew clean build
```

## 常见问题

### 1. Gradle wrapper JAR 缺失

运行 `./setup.sh` 脚本会自动下载。

### 2. SDK 未找到

确保 `ANDROID_HOME` 环境变量已正确设置。

### 3. JDK 版本不匹配

项目需要 JDK 17。检查版本：

```bash
java -version
```

### 4. 网络问题

如果下载依赖失败，可以配置镜像：

在项目根目录创建 `gradle.properties`：

```properties
systemProp.http.proxyHost=your-proxy-host
systemProp.http.proxyPort=your-proxy-port
```

## 项目结构

```
WebDavMusic/
├── app/                    # 主应用模块
│   ├── src/main/
│   │   ├── java/          # Kotlin/Java 源码
│   │   ├── res/           # 资源文件
│   │   └── AndroidManifest.xml
│   └── build.gradle       # 模块构建配置
├── gradle/                 # Gradle wrapper
├── build.gradle           # 项目构建配置
├── settings.gradle        # 项目设置
├── setup.sh               # 设置脚本
└── BUILD.md               # 本文档
```

## 技术栈

- **语言**: Kotlin 1.9.22
- **构建工具**: Gradle 8.4 + Android Gradle Plugin 8.2.2
- **依赖注入**: Hilt 2.50
- **数据库**: Room 2.6.1
- **播放器**: Media3 ExoPlayer 1.2.1
- **网络**: OkHttp 4.12.0
- **图片加载**: Glide 4.16.0

## License

MIT License
