# HH音乐 · 网易云第三方音乐播放器（Android）

一个基于网易云接口的第三方音乐播放器，由两部分组成：

- `server/` — Node.js / Express 后端代理，封装网易云 weapi 加密签名与接口，统一输出干净的 JSON。
- `android/` — 原生 Android 客户端（Kotlin + Jetpack Compose + Media3/ExoPlayer）。

```
┌─────────────────┐      HTTP/JSON      ┌──────────────────┐     weapi 加密     ┌─────────────────┐
│  Android App    │ ───────────────────▶│  Node 代理 server │ ──────────────────▶│  网易云 music.163 │
│  Compose/ExoPlayer│◀──────────────────│  (Express)       │◀──────────────────│                 │
└─────────────────┘                     └──────────────────┘                    └─────────────────┘
```

## 功能

- 🔍 关键词搜索歌曲 / 歌手
- ▶️ 播放、暂停、上一首 / 下一首、拖动进度
- 📃 播放队列（可点选跳转）
- 🎼 实时滚动歌词（带时间轴 & 翻译）
- 🏆 排行榜 / 歌单浏览与一键播放
- 🎚 通知栏 / MediaSession 控制（锁屏可控）
- 🎨 仿网易云深色绿色主题

## 一、启动后端（必需）

后端服务负责向网易云发起 weapi 签名请求，并给 App 提供干净 JSON。

```bash
cd server
npm install
npm start          # 默认监听 http://localhost:3000
```

健康检查：

```bash
curl http://localhost:3000/api/health
```

可用接口：

| 接口 | 说明 |
|------|------|
| `GET /api/search?s=周杰伦&limit=30` | 搜索歌曲 |
| `GET /api/song/detail?ids=5257138,210049` | 歌曲详情 |
| `GET /api/song/url?id=5257138&level=exhigh` | 获取播放地址（mp3/flac） |
| `GET /api/lyric?id=5257138` | 获取歌词 |
| `GET /api/playlist/detail?id=19723756` | 歌单详情 |
| `GET /api/toplist` | 排行榜列表 |
| `POST /api/song/like`        body `{"id":123,"like":true}` | 喜欢歌曲 |

## 二、运行 Android 客户端

### 1. 配置后端地址

App 默认通过 Android 模拟器访问宿主机：

```kotlin
// android/app/src/main/java/com/hh/music/player/network/NetworkModule.kt
const val BASE_URL = "http://10.0.2.2:3000/api/"
```

- **模拟器**：保持 `10.0.2.2:3000` 即可（自动映射到电脑 localhost）。
- **真机**：改为电脑局域网 IP，例如 `http://192.168.1.100:3000/api/`，并确保手机与电脑同网段。

### 2. 用 Android Studio 打开并运行

1. 打开 Android Studio → `Open` → 选择 `D:\HHmusic\android` 目录。
2. 等待 Gradle 同步下载依赖（首次需要联网，约几分钟）。
3. 选择设备 / 模拟器，点击 ▶️ Run。

> 要求：Android Studio Hedgehog 及以上、JDK 17+、Android SDK API 34。
> 本机已检测到 JDK 21（Corretto），Android SDK 由 Android Studio 提供。

## 技术栈

**后端**：Node.js、Express、网易云 weapi 自实现加密（AES-128-CBC + RSA）。

**客户端**：
- UI：Jetpack Compose、Material 3
- 播放：Media3 / ExoPlayer + MediaSession（前台服务 + 通知栏）
- 网络：Retrofit + kotlinx.serialization + OkHttp
- 图片：Coil
- 架构：单 Activity + Navigation Compose + 手动依赖注入（AppContainer）

## 目录结构

```
D:\HHmusic
├─ server/                     后端代理
│  ├─ src/
│  │  ├─ crypto.js             weapi 加密（AES+RSA）
│  │  ├─ netease.js            网易云接口封装
│  │  └─ index.js              Express 路由（/api/*）
│  └─ package.json
└─ android/                    Android 客户端
   ├─ app/
   │  ├─ build.gradle.kts
   │  └─ src/main/
   │     ├─ AndroidManifest.xml
   │     ├─ res/               主题/图标/字符串
   │     └─ java/com/hh/music/player/
   │        ├─ MainActivity.kt / HHMusicApp.kt
   │        ├─ data/           数据模型 / Repository / AppContainer
   │        ├─ network/        Retrofit API / 网络模块
   │        ├─ playback/       PlaybackService / PlayerController
   │        └─ ui/             搜索 / 播放器 / 歌单 / 主题 / 组件
   ├─ build.gradle.kts
   ├─ settings.gradle.kts
   ├─ gradle.properties
   ├─ gradle/libs.versions.toml
   └─ gradle/wrapper/          Gradle Wrapper
```

## 免责声明

本项目仅供学习交流使用，接口与资源版权归网易云音乐所有。请勿用于商业用途。


## v1.1 新增功能

> 后端已扩展 4 个接口、客户端新增「发现 / 我的」页与本地持久化。

### 新接口（后端 `server/`）

| 接口 | 说明 |
|------|------|
| `GET /api/recommend/songs?limit=30` | 每日推荐 |
| `GET /api/recommend/playlists?limit=12` | 推荐歌单 |
| `GET /api/artist/songs?id=6452&limit=50&order=hot` | 歌手热门歌曲（order=hot/time） |
| `GET /api/new/song?limit=30` | 新歌速递 |

### Android 新功能

- 🧭 **底部导航重构**：发现 / 搜索 / 排行榜 / 我的，四大 Tab 切换
- 🔭 **发现页**：每日推荐 + 新歌速递 + 推荐歌单（卡片网格），一键播放
- ❤️ **我的**：收藏歌曲 / 最近播放 / 收藏歌单，三个子 Tab；最近可清空
- 🔖 **本地持久化**（DataStore）：收藏、最近播放（最多 50）、歌单收藏、播放模式、搜索历史全部本地保存
- 🔁 **播放模式**：顺序播放 / 单曲循环 / 随机播放，循环切换并持久化
- 🕘 **搜索历史**：空态展示历史关键词，点选即搜，可清空
- ❤️ **收藏按钮**：播放页歌曲收藏、歌单详情页收藏歌单
- 🎵 播放/切歌时自动记录到「最近播放」

### 架构图

```mermaid
flowchart LR
    A[Android UI: Compose] --> PC[PlayerController\n队列/模式/进度]
    PC --> PS[PlaybackService\nExoPlayer+MediaSession]
    A --> Repo[MusicRepository]
    Repo --> LS[LocalStore\nDataStore: 收藏/最近/历史/模式]
    Repo --> Net[Retrofit API]
    Net --> BE[Node 后端 /api/*]
    BE --> NE(网易云 weapi)
```

