# RedGuo Android 4.4 v0.6 — TLS compatibility test

Target device: EC6108V9U / Android 4.4.2 (API 19) / ARM32.

v0.4 on-device video tests completed H.264 360p, HEVC 360p, HEVC 720p with **LibVLC 3.5.1 software decoding**. v0.5 added public Hongguo website browsing, but its Android 4.4 system OpenSSL connection was rejected at SSL handshake, reporting SSL23_GET_SERVER_HELLO alert handshake failure.

## Changes in v0.6
- Embeds Google's Apache-2.0 Conscrypt for modern HTTPS (2.5.2, `armeabi-v7a` native library).
- Creates TLS session via app-bundled provider rather than KitKat system OpenSSL; enables only TLS 1.2 / 1.3 (when available).
- Explicit SNI for the official hostname.
- Keeps normal certificate-chain verification **and hostname verification**; no trust-all, insecure redirect or plain HTTP fallback.
- Logs successful negotiated TLS protocol and cipher or detailed network error.
- Keeps the v0.5 official public web catalogue, search and episode diagnostic actions, and original offline decoder tests.

No video signing/DRM functions or authenticated playback requests are integrated. Episode directory success must not be described as video playback success.

## TV test steps
1. Install `app-debug.apk` from the newest successful Actions artifact, using package `dev.redguo.codecprobe`. This updates only our codec-test app, not HGTV/影视仓.
2. Select `红果官网目录（联网）`.
3. If it succeeds, select a drama and inspect episodes, then search by drama name.
4. If it fails, photograph the error and `完整日志` dialog. Note `TLS 成功 TLSv1.2` vs certificate error vs HTTP response vs parse error.

Compiled on GitHub Actions. Recheck API19, ARM32, v1 signature, and Conscrypt JNI presence. Real-device results remain essential.
