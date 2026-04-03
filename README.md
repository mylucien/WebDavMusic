# WebDAV Music v2 🎵

> WebDAV 多账户 + 本地音乐播放器 · 安卓电视 + 车机双适配  
> 遥控器 D-pad · 车载旋钮 · 触控屏 · 无需下载在线流媒体

---

## ✨ v2 新增 / 修复

### 🔴 修复的崩溃 Bug

| # | 问题 | 修复方式 |
|---|------|---------|
| 1 | **添加账户闪退** — `Flow.collect` 在 `suspend fun` 中永不返回 | 改为 `.first()` 快照 |
| 2 | **Song ID 碰撞** — `hashCode()` 产生负数和重复 | 改为前缀字符串确保唯一 |
| 3 | **Room 枚举序列化崩溃** — `AudioFormat`/`SongSource` 枚举无 `@TypeConverters` | 添加 `Converters` 类 |
| 4 | **MediaController 连接崩溃** — `await()` 在非协程上下文 | 改为 `addListener(directExecutor())` |
| 5 | **数据库升级崩溃** — 无 Migration 导致旧用户数据丢失 | 添加 `MIGRATION_1_2` |
| 6 | **WebDAV URL 崩溃** — 不带协议前缀的地址 | `normalizeUrl()` 自动补全 `http://` |
| 7 | **认证凭据泄露到日志** — 密码拼入 URL | 改为 OkHttp `Authorization` Header |
| 8 | **每次扫描重复建播放列表** | 先查询再决定插入或更新 |

### 🟢 新功能

- **本地音乐** — `MediaStore` 扫描设备存储，自动权限请求
- **收藏功能** — 数据库级 `isFavorite`，一键切换 ❤️
- **加入队列** — 不打断当前播放，追加到列表末尾
- **TV/车机 UI 完全重设计** — 详见下方

---

## 🖥️ UI 设计原则

### 严格执行 1-2 次点击原则

```
所有核心功能触达路径：
▶ 播放      → 点击歌曲行（1次）
⏸ 暂停      → 点击底部栏播放键（1次）
⏭ 切歌      → 点击底部栏下一首（1次）
❤ 收藏      → 点击歌曲行收藏键（1次）
➕ 加队列    → 点击歌曲行队列键（1次）
🔍 搜索      → 顶部搜索框直接输入（1次）
📂 切分类    → 左侧导航栏（1次）
🎛 完整播放器 → 点击底部播放条（1次）
⚙ 添加账户  → 左侧「账户与设置」（1次）→ 对话框
```

### TV 遥控器适配

- 所有可交互元素 `focusable=true`，`focusableInTouchMode=false`
- D-pad **左右导航**：左侧导航栏 ↔ 右侧列表
- 焦点时有**明显紫色描边**（`2dp stroke #6750A4`）
- 媒体键直接控制播放，无需进入播放器
- 旋钮 `AXIS_SCROLL` 控制进度条

### 布局结构

```
┌──────────────┬─────────────────────────────────┐
│ 左侧导航栏   │  顶部：标题 + 搜索框            │
│              │  ─────────────────────────────  │
│ 🎵 所有歌曲  │                                 │
│ 📱 本地音乐  │  歌曲列表（每行含播放/收藏/队列）│
│ 💿 专辑      │                                 │
│ 🎤 艺术家    │                                 │
│ ❤️ 收藏      │                                 │
│ 📋 播放列表  │                                 │
│              │                             [+] │
│ ⚙️ 账户设置  ├─────────────────────────────────┤
│ [+ 添加账户] │ 底部播放条（始终可见）            │
└──────────────┴─────────────────────────────────┘
```

---

## 🚀 快速开始

### GitHub Actions 自动编译

```bash
git clone <your-fork>
cd WebDavMusic
git push  # Actions 自动触发，在 Artifacts 下载 APK
```

### 本地编译

```bash
./setup.sh         # 下载 gradle wrapper（如果缺失）
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/
```

---

## ⚙️ 使用说明

### 添加 WebDAV 账户
1. 左侧导航 → **⚙️ 账户与设置**
2. 点击 **+ 添加账户**
3. 填写地址、用户名、密码
4. 点击 **测试连接** 验证
5. **保存** → 点击 **🔍 扫描全部**

### 本地音乐
1. 左侧 → **📱 本地音乐**
2. 右下角 **扫描**按钮（首次需授权存储权限）

### 支持格式
```
MP3  ·  FLAC  ·  WAV  ·  AAC  ·  M4A  ·  OPUS
```

---

## 🔧 签名发布（打 tag 自动触发）

```bash
git tag v2.0.0
git push origin v2.0.0
```

Secrets 配置（Settings → Secrets → Actions）：

| 名称 | 说明 |
|------|------|
| `SIGNING_KEY` | keystore base64 |
| `KEY_ALIAS` | alias |
| `KEY_STORE_PASSWORD` | store 密码 |
| `KEY_PASSWORD` | key 密码 |

---

## 🏗️ 技术架构

| 层 | 技术 |
|----|------|
| 播放 | Media3 ExoPlayer + MediaSession |
| WebDAV | Sardine-Android + OkHttp (Auth Interceptor) |
| 本地扫描 | MediaStore ContentProvider |
| 数据库 | Room + TypeConverters |
| DI | Hilt |
| 车机 | Car App Library |
| TV | Leanback focus + custom selector drawables |
| 图片 | Glide |

MIT License
