# RedGuo Android 4.4 v0.8 — PEM certificate parser fix

Target: Huawei EC6108V9U / Android 4.4.2 / API 19 / ARM32. Independent diagnostic APK, not yet a complete Hongguo player.

## Device-grounded results
- v0.4 LibVLC software decoded H.264 360p, HEVC 360p and HEVC 720p three-second test clips.
- v0.5 failed on legacy SSL handshake.
- v0.6 progressed to PKIX trust-anchor failure with bundled Conscrypt.
- v0.7 installed and opened, but logged "现代受信任根证书加载不完整：0；尚未发起TLS连接". The dated Mozilla PEM asset contains 118 certificates, but Android 4.4 CertificateFactory.generateCertificates(bundle) returned an empty collection.

## v0.8 changes
- Streams the Mozilla CA bundle line-by-line and splits on BEGIN/END CERTIFICATE markers, ignoring comments and metadata outside cert blocks.
- Base64-decodes each X.509 certificate into DER, then passes each separately to the platform CertificateFactory.generateCertificate(ByteArrayInputStream).
- Counts PEM markers and parsed certificates; rejects incomplete or malformed collections. This is not a bypass: standard PKIX checks, TLS SNI, and HTTPS hostname verification remain intact.
- CI checks the 118 certificates **inside the built APK**, individually with OpenSSL, as well as API19/ARM32 JNI libs and APK signing.
- UI shows v0.8 and retains detailed network diagnostics.

## TV test
1. Install the APK from the GitHub Actions artifact. Existing debug APK might require uninstall due to ephemeral signing.
2. Click 红果官网目录（联网）.
3. If success, check a real drama's episode list and search by name.
4. If failed, capture the full dialog; key statuses: root count, TLS negotiation, HTTP status, or catalog JSON parsing.

Official website directory access is separate from authenticated playback and DRM. This version does not claim to play full Hongguo dramas.
