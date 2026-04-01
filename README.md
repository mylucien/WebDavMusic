# WebDAV Music 🎵

> **WebDAV 多账户音乐播放器 · 安卓电视 + 车机双适配**  
> 支持遥控器 D-pad · 车载旋钮 · 触控屏 · 无需下载在线流媒体播放

---

## ✨ 功能特性

| 功能 | 说明 |
|------|------|
| 🌐 **WebDAV 多账户** | 同时管理多个 NAS / 云盘账户，独立凭据 |
| ▶️ **在线流媒体** | 无需下载，直接播放，ExoPlayer 驱动 |
| 🔍 **一键扫描** | 自动递归扫描，生成播放列表 |
| 📺 **TV / 盒子适配** | Leanback UI，D-pad & 遥控器完整支持 |
| 🚗 **车机适配** | AAOS Car App Library，旋钮 + 触控 |
| 📱 **手机 / 平板** | 同一 APK，自适应布局 |
| 🎵 **格式支持** | MP3 · FLAC · WAV · AAC · M4A · OPUS |
| 🌙 **深色模式** | 自动跟随系统，Material You 主题 |

---

## 📦 格式支持

```
MP3  ·  FLAC  ·  WAV  ·  AAC  ·  M4A  ·  OPUS
```

---

## 🚀 快速开始

### 1. Fork 并自动编译

1. **Fork** 本仓库到你的 GitHub 账户
2. 进入 **Actions** 标签页，启用 Workflows
3. 推送任意提交，GitHub Actions 自动编译 APK
4. 在 Actions → 对应的 Run → **Artifacts** 中下载 APK

### 2. 本地编译

```bash
git clone https://github.com/YOUR_USERNAME/WebDavMusic.git
cd WebDavMusic
./gradlew assembleDebug
# APK 在 app/build/outputs/apk/debug/
```

### 3. 发布正式版

打 tag 自动触发签名 + 发布流程（需配置 Secrets）：

```bash
git tag v1.0.0
git push origin v1.0.0
```

---

## ⚙️ 配置签名（可选，发正式版用）

在 GitHub 仓库 → **Settings → Secrets and variables → Actions** 中添加：

| Secret 名称 | 说明 |
|-------------|------|
| `SIGNING_KEY` | keystore 文件的 base64 编码 |
| `KEY_ALIAS` | keystore alias |
| `KEY_STORE_PASSWORD` | keystore 密码 |
| `KEY_PASSWORD` | key 密码 |

生成 keystore：
```bash
keytool -genkey -v -keystore release.jks -alias music -keyalg RSA -keysize 2048 -validity 10000
# 然后 base64 编码
base64 -w 0 release.jks
```

---

## 📱 使用方法

### 添加 WebDAV 账户

1. 打开 App → **账户** 标签
2. 点击 ➕ 按钮
3. 填写：
   - **账户名称**：如 `家庭 NAS`
   - **WebDAV 地址**：如 `http://192.168.1.100:5005/dav`
   - **用户名 / 密码**（可选）
4. 点击 **测试连接** 验证
5. 点击 **保存**

### 扫描音乐

- 首页点击 **🔍 扫描所有账户** 一键扫描
- 或在账户列表中针对单个账户点击 **扫描**
- 扫描完成后自动生成播放列表

### 播放

- **音乐库** → 选择歌曲 / 专辑 / 艺术家
- 遥控器：D-pad 导航，OK 键播放，媒体键控制
- 车机：旋钮选择，触控播放

---

## 🏗️ 技术架构

```
com.webdavmusic
├── data/
│   ├── model/          # 数据模型 (Song, Account, Playlist)
│   ├── webdav/         # WebDAV 客户端 (Sardine + OkHttp)
│   ├── repository/     # 数据仓库层
│   └── AppDatabase.kt  # Room 数据库
├── player/
│   ├── PlaybackService.kt   # Media3 后台播放服务
│   └── PlayerController.kt  # MediaController 封装
├── ui/
│   ├── MainActivity.kt      # 主 Activity，键盘/遥控适配
│   ├── home/                # 首页
│   ├── library/             # 音乐库浏览
│   ├── player/              # 正在播放
│   ├── settings/            # 账户管理
│   └── car/                 # AAOS 车机界面
└── di/
    └── AppModule.kt         # Hilt 依赖注入
```

### 技术栈

| 组件 | 库 |
|------|----|
| 播放引擎 | Media3 ExoPlayer |
| WebDAV 客户端 | Sardine-Android + OkHttp |
| 数据库 | Room |
| 依赖注入 | Hilt |
| TV UI | Leanback |
| 车机 UI | Car App Library |
| 导航 | Navigation Component |
| 图片 | Glide |

---

## 🔧 常见问题

**Q: 连接 NAS 失败？**  
A: 确认 WebDAV 服务已开启，地址格式为 `http://IP:PORT/path`，非 HTTPS 需要 App 允许明文流量（已配置）。

**Q: FLAC 无法播放？**  
A: ExoPlayer 原生支持 FLAC，确认 URL 可访问，检查账户密码是否正确。

**Q: 车机上看不到 App？**  
A: 需要 Android Automotive OS 的车机，普通 Android Auto 不支持（需另行适配）。

**Q: 扫描很慢？**  
A: 首次扫描需要遍历所有目录，大型音乐库可能需要几分钟。后续增量扫描更快。

---

## 📄 License

MIT License · 自由使用，欢迎贡献

---

## 🔩 首次 Clone 后初始化

由于 `gradle-wrapper.jar` 为二进制文件无法直接提交（或需用 Git LFS），  
克隆后运行以下脚本自动下载：

```bash
./setup.sh
```

或手动执行：
```bash
gradle wrapper --gradle-version 8.4
```

