# RedGuo Android 4.4 v0.7 — trusted root CA update

Targets: Android 4.4.2 / API 19 / ARM32 / Huawei EC6108V9U.

## Confirmed device tests
- v0.4: LibVLC software playback succeeded for original 3-second H.264/HEVC 360p and HEVC 720p videos.
- v0.5: system TLS negotiation failed (SSLv3 alert handshake failure).
- v0.6: modern Conscrypt TLS negotiation advanced to normal PKIX certificate validation but failed with "Trust anchor for certification path not found." This identifies insufficient/mismatched trusted CA roots on this firmware or missing intermediate certificates, not a media codec problem.

## Changes in v0.7
- Keep Conscrypt 2.5.2 + SNI + TLSv1.2/1.3.
- Load root CA certificates from the unmodified, dated public Mozilla store (2026-10-20, 118 PEM root certificates). Mirror source: `Chia-Network/mozilla-ca/cacert.pem`, with upstream description at https://curl.se/docs/caextract.html.
- Combine Mozilla CA roots with those exposed by the AndroidCAStore if available, using normal `TrustManagerFactory` and PKIX validation.
- Never use trust-all, disable hostname verification, or silently accept a certificate error.
- On TLS success, show negotiated protocol and cipher. On errors, display specific trust chain or page parsing error.
- APK is a diagnostic build for public Hongguo directory/search/episode IDs; it is NOT yet a real-video Hongguo player.
- The bundled Mozilla certificate file remains under Mozilla Public License 2.0; see https://www.mozilla.org/MPL/2.0/.

## Functional test
1. Uninstall v0.6 if debug signatures differ, then install v0.7.
2. Select 红果官网目录（联网）.
3. If successful, select a real drama and verify its complete episode list; then test 搜索.
4. If failure remains, capture the error and 完整日志. If the page says HTTP 403 or 404, TLS repair worked but site access/URLs or site parsing require a separate fix.

No modifications to tvbox0701 main branch, existing HGTV or 影视仓. No server dependency.
