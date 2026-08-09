# HH音乐 · 网易云第三方音乐播放器（Android）

一个基于网易云接口的第三方音乐播放器，客户端直接内建 eapi 加密（移植自 [GuitaristRin/Ncrust](https://github.com/GuitaristRin/Ncrust)），默认无需后端即可联网搜索、取歌词并播放。`server/` 保留为可选的 Node/Express weapi 代理。

```text
┌────────────────────┐   eapi 加密/直连 https   ┌────────────────────┐
│ Android App        │ ────────────────────────> │ 网易云 music.163   │
│ Compose / Media3   │ <──────────────────────── │                    │
└────────────────────┘                          └────────────────────┘
        server/ 仅作可选代理，不开也能用
```

## 功能清单（v1.6）

### 主题与外观
- 主题模式：跟随系统 / 浅色 / 深色，重启后保持。
- 主题色：云岭绿（默认）/ 夜帆蓝 / 炽阳橙。
- 动态取色：Android 12+ 可选，壁纸主题色优先于固定主题色。
- 系统栏样式、播放页背景、MiniPlayer 与弹层随主题统一切换。

### 搜索与歌手
- 关键词搜索歌曲与歌手；搜索页展示歌手结果区。
- 歌手页：热门 / 最新歌曲切换、播放全部、分页加载更多、单曲长按操作。
- v1 歌手页只提供歌曲列表，专辑 Tab 留待后续版本。

### 歌词与播放页
- 歌词行点击可跳转定位。
- 显示翻译、显示罗马音（`romalrc`）开关。
- 歌词字号：小 / 中 / 大。
- 进度滑块、倍速、定时关闭、均衡器、播放队列、离线缓存联动。

### 音乐库与下载
- 收藏歌曲、最近播放、收藏歌单。
- 本地音乐：MediaStore 扫描 + SAF 导入，快速标题/歌手筛选，失效 URI 自动清理。
- 下载页：下载中进度、已下载管理、失败重试、清空失败记录。
- 设置页：自动缓存开关、缓存上限（256MB / 512MB / 1GB / 2GB / 不限）。

## 构建与运行

### Android

从 Android Studio 打开 `android/` 并运行 `app`；也可以使用命令行：

```bash
cd android
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
.\gradlew.bat assembleRelease
```

要求：JDK 17+、Android SDK 35、Android Studio 最新稳定版。

### Server（可选）

默认 App 直连网易云，不需要启动 server。若要在本地跑 weapi 代理：

```bash
cd server
npm install
npm start        # http://localhost:3000
npm run dev      # watch 模式
npm test         # node --test tests/routes.test.js（Windows / Node 22 兼容）
```

## Server 接口

| 接口 | 说明 |
|------|------|
| `GET /api/search?s=周杰伦&limit=30` | 歌曲搜索 |
| `GET /api/artist/search?s=周杰伦&limit=30` | 歌手搜索（type=100） |
| `GET /api/artist/songs?id=6452&limit=50&order=hot` | 歌手歌曲（hot/time） |
| `GET /api/song/detail?ids=123,456` | 歌曲详情 |
| `GET /api/song/url?id=123&level=exhigh` | 播放地址 |
| `GET /api/lyric?id=123` | 歌词（lrc/tlyric/romalrc/yrc） |
| `GET /api/playlist/detail?id=19723756` | 歌单详情 |
| `GET /api/toplist` | 排行榜列表 |
| `GET /api/recommend/songs?limit=30` | 每日推荐 |
| `GET /api/recommend/playlists?limit=12` | 推荐歌单 |
| `GET /api/new/song?limit=30` | 新歌速递 |
| `POST /api/song/like` | 喜欢歌曲，body `{"id":123,"like":true}` |
| `GET /api/health` | 健康检查 |

## 技术栈

- Android：Kotlin、Jetpack Compose（Material 3 Expressive）、Navigation Compose、Media3/ExoPlayer + MediaSession。
- 数据持久化：DataStore Preferences + kotlinx.serialization。
- 网络：直接 NetEase eapi（默认）或 Retrofit + Node server（可选）。
- Server：Node.js、Express、自实现 weapi 加密（AES-128-CBC + RSA）。

## 版本记录

### v1.6（当前）
- 用户可控主题：system/light/dark、绿/蓝/橙、动态取色。
- 歌手搜索与歌手页。
- 歌词行点击跳转、翻译/罗马音开关、三档字号。
- 本地音乐标题/歌手筛选；下载页失败记录清空。
- Server 更新到 Windows/Node 22 可用的测试脚本，并新增 artist/search 接口测试。
- Android 版本号 `1.6`（versionCode 6）。

### v1.5
- 播放队列弹层：数量 / 总时长 / 上移下移 / 移顶移底 / 移除。
- 倍速、定时关闭、均衡器。
- 播放失败自动兜底与重试。

### v1.4
- 歌词滚动 O(n) 优化。
- URL 解析失败自动切下一首 / 重试一次。
- 下一首预解析、连接重试指数退避、LRU 缓存。
- Release R8 混淆 + 资源压缩。

### v1.3
- 进度、歌词、控件重组隔离与卡顿修复。
- 音乐库页、设置页（数据源 / 音质 / 进度样式）。

### v1.2
- App 直连网易云 eapi，默认无需 backend。

### v1.1
- 底部导航 / 发现页 / 我的、收藏、最近播放、播放模式、搜索历史、本地持久化。

## 免责声明

项目仅供学习交流，所有接口与资源版权归网易云音乐所有。请勿用于商业用途。
