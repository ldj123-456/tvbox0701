# HGTV 1.2.2.2 播放层最小接入定位（静态分析记录）

## 目标及已知实机证据

- 原 APK: `HGTV_1.2.2.2.apk`，SHA-256 `192d22df90cd6d77205fc425e5d0b5679bd6b8c79782444018158682c9579ccf`，4,209,740 bytes。
- 在 Android 4.4.2 / ARMv7 设备上，原 HGTV 能打开首页、搜索、剧集详情与完整选集，但 HEVC 播放报 4005。
- 独立验证 APK 使用 VideoLAN LibVLC 3.5.1 软件模式可播放 H.264 360p / HEVC 360p / HEVC 720p 原创三秒短片。
- 此仓库的 `hgtv-bridge` Android Library (API19) 已经在 CI 中编译 AAR，Java 8 回环 Range 自测通过。
- **这些证据不意味着原 HGTV 的真实视频已经接入 LibVLC**；不能把 AAR 当成可安装 APK。

## DEX 定位——能直接从 1.2.2.2 的 classes.dex 复核

原 APK `classes.dex` 约 3.1 MB，程序类与方法明显经过 R8 混淆。以下是 **1.2.2.2 特有的混淆名**，换版后可能完全不同。

| 位置 | 静态证据 | 推断与置信度 |
| --- | --- | --- |
| `Lcom/hgtv/star/MainActivity;` | 实际入口 Activity，`onCreate`、`dispatchKeyEvent`、`onPause` | 高：原 UI/导航入口，原则上不改 |
| `LC0/u0;->P(...)`（方法 ID 780） | 同一方法的常量包括 `hgtv://episode/`、`video/mp4`，并创建 URI/媒体模型、调用 `Lx/G;`，指令长度 332 个 code unit | **高：媒体项装配候选位置**，但仍需反编译确认参数 |
| `LC0/u0;->H0(...)`（方法 ID 757） | 常量 `player`、`qualityLabel`、`playbackTitle`，大量添加 View/FrameLayout 操作 | 中高：播放器页面与 HUD 组装相关 |
| `LC0/u0;->K0(...)`（方法 ID 766） | 常量 `playback_decoder_init`、`playback_decoding`、`playback_decoder_unsupported` 等 | 高：原播放错误分流与回退候选 |
| `LE0/p1;->t(...)` | 含 `Range`、`bytes=`、`Content-Range`、`Referer` 与 `https://novel.snssdk.com/` | 高：分段 HTTP 媒体请求，不是 UI |
| `LE0/p1;->s(...)` | 含 `视频样本超出资源大小`，有 `close()` | 中高：媒体准备/索引相关 |
| `Lx/G;` | 字符串 `ExoPlayerImpl`、`seekTo ignored because an ad is playing`、`Player is accessed on the wrong thread` | 高：内含 ExoPlayer 实例逻辑（名称已混淆） |

证据仅证明符号及常量关联；尚**不能断言**所有媒体字节都是可直接播放的 MP4，不能把其中任何受保护媒体密钥带入替代代码。

## 正确的最小补丁点

1. **不改** 原 Activity UI、菜单、搜索、剧集目录、设备会话、HTTPS、原媒体授权逻辑。
2. 在原媒体项装配和错误回退附近增加 **可选的软件播放后端**：默认保持现有 ExoPlayer，出现 HEVC 解码不支持时才尝试 LibVLC。
3. 在 HGTV 已经完成正常访问控制与合法媒体处理的前提下，让原媒体读取对象实现 `LoopbackRangeServer.SeekableSource`：
   - `length()` 返回合法、已可读取媒体的长度；
   - `read(long, byte[], int, int)` 支持随机读取，尽量不预先下载整集；
   - `close()` 及时释放网络会话。
4. 使用 `HgtvLibVlcAdapter.play(source, positionMs, speed, callback)`，保持原 HUD/弹幕、遥控器、切集、进度保存逻辑。
5. 必须在真机上额外验证：长片段持续播放、1080p/高码率能力、seek 和自动连播；三秒短片只能证明解码器可工作。

## 当前阻塞条件

- `pan8664716/HGTV` 的 2026-10-28 当前公开分支和 v1.2.6 标签，仅有 README、图片和发行物，没有 README 所述 `android/app` / `android/core` 源码，也未提供明确根目录开源许可证。
- `N3urda/hongguoTV` 是技术上相近的上游，但其 `kotlin-tv` 为另一套 Android 8+ 工程，不能认定它就是 1.2.2.2 源码。
- HGTV 原 APK 经过 R8 混淆，并且原版签名私钥不可用。盲目改 DEX 并重新签名无法无损覆盖旧应用，可能造成 UI、接口、播放流程回归。
- 已在官方 [Issue #12](https://github.com/pan8664716/HGTV/issues/12#issuecomment-6457947809) 提供实机证据与可编译 AAR，并请求对应播放器初始化/媒体输入源码或维护者直接集成的支持。

## 交付状态

- **完成**：旧盒子 HEVC 软件解码短样片验证；播放器后端 AAR 实际编译；回环 Range 读取测试；原 APK 播放入口静态定位；在 HGTV 官方 Issue 协作。
- **未完成**：在 HGTV 原 APK 中集成这个后端；有授权的真实剧集端到端播放；面向用户可直接安装的最终 HGTV 修改版。

后续开发若无法取得原源码，优先继续做**本地 APK 反编译验证**，不要重建另一套网页目录/TLS/界面，也不要在未确定媒体格式与许可的情况下复制内容接口或 DRM 处理。
