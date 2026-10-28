# HGTV 1.2.2.2 — 最小播放器后端补丁候选

**目标不是新红果客户端，而是保留原 HGTV 已验证可用的界面、搜索、选集、设备会话、证书与内容读取流程，只增加可选的 LibVLC 软件播放后端。**

- 面向 EC6108V9U / Android 4.4.2 / API19 / ARM32。
- 已有 v0.4 实机证据：LibVLC 3.5.1 可以软解 H.264 360P、HEVC 360P / 720P 三秒样片；不等于已验证长时间剧集。
- 上游 HGTV 发布仓库（pan8664716/HGTV）仅有 README/图片/安装包，缺少与 1.2.2.2 对应的原始 Android 工程。此分支不声称已经改造原 APK。
- Issue #12 https://github.com/pan8664716/HGTV/issues/12 也有同类 HEVC 无法软解问题。

## 本仓库包含
- `LoopbackRangeServer`：Java8 / Android4.4 的回环 HTTP Range 服务，只接收 HGTV **已经授权并准备好**的可随机访问媒体字节。绑定 127.0.0.1，随机访问令牌，不从网络获取影片，不进行破解和解密。
- `HgtvLibVlcAdapter`：播放、暂停、跳转、倍速、首帧、播放结束、错误回调、切集释放。需要原 HGTV 的播放 Surface 所在容器。
- GitHub Actions：直接编译 Android Library AAR，并执行 loopback Range Java 回归测试。

## 必须满足才能真正接入 HGTV
1. 拿到 HGTV 1.2.2.2 对应源码（或维护者协作提供的播放初始化/原媒体输入接口）。**不能将 N3urda Android 8 工程当成 HGTV 1.2.2.2 的源码。**
2. 在原 HGTV 播放器入口保留 ExoPlayer 作为默认；旧设备在 HEVC 解码失败时调用这个后端。原界面 HUD/弹幕仍处在播放 Surface 上层。
3. 由 HGTV 现有、合法获授权的媒体管线实现 `LoopbackRangeServer.SeekableSource`。不能把未知加密的 CDN URL 直接当作可播放视频。
4. 在旧盒子测试至少 10–20 分钟真实可访问片段、切集、随机跳转、进度恢复、焦点、无内存泄漏。
5. 发布需核实 HGTV 源码与媒体的授权，保留 LibVLC 组件许可证声明。

## 交付
CI 产生的 `.aar` 是**可编译的播放器库**，不是可安装 APK，也不是已经能播放红果全集的成品。构建 App 时还需要添加 `org.videolan.android:libvlc-all:3.5.1` 依赖以打包 ARM32 原生库。

原 HGTV APK 代码 R8 混淆严重、源码缺失，**不建议盲目字节码注入**，容易破坏启动、媒体访问和现有可用功能。
