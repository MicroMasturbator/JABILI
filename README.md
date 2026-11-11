# JABILI

[中文](#中文) | [English](#english)

---

## 中文

JABILI 是一个 Android 原生客户端，用原生界面浏览视频站，并用自带播放器接管播放。站点相关代码已模块化，以后可以加更多站点。

### 功能

- **原生浏览**：首页列表（最近更新 / 热门 / 全新上市）、分类、搜索（带搜索历史）、排序、网格或列表布局。
- **详情页**：标题、女优（带头像）、标签、相关视频，内嵌竖屏小窗播放器，可一键全屏。
- **播放器手势**（基于 Media3 / ExoPlayer）：
  - 单击：显示或隐藏进度条和控件
  - 双击：中间暂停或播放，左右两侧快退或快进 10 秒
  - 左右滑动：拖动进度
  - 上下滑动：左侧调亮度，右侧调音量
  - 长按：按住时 2 倍速
  - 支持画中画、记住播放速度、断点续播
- **本地收藏库**：爱心、收藏、历史记录（左滑删除可撤销）、观看进度条和“已看”标记。
- **账号同步**：可在设置里登录站点账号，拉取账号里的列表。
  - App 的“爱心”对应账号的爱心，App 的“收藏”对应账号的“稍後觀看”。
  - 打开“同步收藏爱心”后，在 App 里点的爱心和收藏会同步到账号；关闭时只保存在本地。
- **标题翻译**：在设备上把日文标题机翻成中文，并标注“机翻”（ML Kit，离线）。
- **长按菜单**：爱心、收藏、复制番号、分享、在网页中打开。
- **Material 3 主题**：粉色配色，支持深色模式和动态取色。
- **Cloudflare 验证**：遇到验证时弹出页面让用户手动完成。

### 首次启动

第一次打开时需要像输密码一样输入站点地址，填对才会解锁全部内容。地址之后可以在 设置 里修改；填的地址不对时，App 只显示空页面。

### 下载

每次推送都会通过 GitHub Actions 自动构建。到 [Actions](../../actions) 页面打开最新一次成功的构建，下载 `JABILI-apk` 产物即可。APK 只包含 arm64-v8a，最低支持 Android 7.0（API 24）。

### 自行构建

需要 JDK 17 和 Android SDK（compileSdk 35）。

```bash
./gradlew assembleRelease
# 输出：app/build/outputs/apk/release/
```

### 项目结构

```
app/src/main/
├── java/com/nsfwbili/app/
│   ├── site/      站点抽象（Site、SiteRegistry、SiteLock）
│   ├── data/      PageLoader（隐藏 WebView 解析）、Room 数据库、账号同步、翻译
│   ├── player/    PlayerCore、PlayerController、PlayerGestures、PlayerActivity
│   └── ui/        列表、详情、搜索、设置等界面
└── assets/s1/     页面解析脚本（list.js、detail.js 等）
```

### 添加新站点

继承 `Site`（参考 `MainSite`），实现列表、详情和播放地址的解析；在 `assets/<站点名>/` 下放解析脚本；然后在 `SiteRegistry` 里注册。

### 免责声明

本项目仅供学习交流，与任何视频网站无关。请遵守当地法律法规，以及相关网站的使用条款。

---

## English

JABILI is a native Android client for browsing video sites. It replaces the website UI with a native interface and plays videos in its own player. The site-specific code is modular so more sites can be added.

### Features

- **Native browsing**: home lists (Latest / Popular / New Releases), categories, search with history, sorting, and grid or list layout.
- **Detail page**: title, actresses with avatars, tags, and related videos, with an inline portrait mini player and a fullscreen toggle.
- **Player gestures** (Media3 / ExoPlayer):
  - Single tap: show or hide the progress bar and controls
  - Double tap: pause or play in the middle; seek back or forward 10 s on the left or right side
  - Horizontal swipe: seek
  - Vertical swipe: brightness on the left, volume on the right
  - Long press: 2x speed while held
  - Picture-in-picture, remembered playback speed, and resume from where you left off
- **Local library**: Likes, Favourites and History (swipe to delete, with undo), plus watch-progress bars and a "watched" badge.
- **Account sync**: log in to your site account in Settings and pull the account's lists.
  - App "Like" maps to the account's like list; App "Favourite" maps to its "Watch later" list.
  - With "Sync likes & favourites" on, the likes and favourites you add in the app are also added to your account. With it off, they are stored locally only.
- **Title translation**: on-device Japanese → Chinese machine translation (ML Kit, offline), labelled "机翻" (machine-translated).
- **Long-press menu**: like, favourite, copy video code, share, and open in browser.
- **Material 3 theme**: pink palette with dark mode and dynamic colour.
- **Cloudflare checks**: when a check appears, the app opens it so you can complete it yourself.

### First launch

On first launch you enter the site address, like a password. The content unlocks only when the address is correct. You can change it later in Settings; if the address is wrong, the app shows an empty page.

### Download

Every push is built by GitHub Actions. On the [Actions](../../actions) page, open the latest successful run and download the `JABILI-apk` artifact. The APK is arm64-v8a only and requires Android 7.0 (API 24) or later.

### Build

Requires JDK 17 and the Android SDK (compileSdk 35).

```bash
./gradlew assembleRelease
# output: app/build/outputs/apk/release/
```

### Project layout

```
app/src/main/
├── java/com/nsfwbili/app/
│   ├── site/      site abstraction (Site, SiteRegistry, SiteLock)
│   ├── data/      PageLoader (hidden-WebView parsing), Room DB, account sync, translation
│   ├── player/    PlayerCore, PlayerController, PlayerGestures, PlayerActivity
│   └── ui/        list, detail, search, settings screens
└── assets/s1/     page parser scripts (list.js, detail.js, ...)
```

### Adding a site

Subclass `Site` (see `MainSite`) and implement list, detail and stream-URL parsing. Put the parser scripts under `assets/<site>/`, then register it in `SiteRegistry`.

### Disclaimer

This project is for learning and personal use only and is not affiliated with any video website. Follow your local laws and the terms of service of the sites you use.
