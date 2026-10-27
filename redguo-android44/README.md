# RedGuo Android 4.4 v0.5 - independent network and playback test

Target: Huawei EC6108V9U / Android 4.4.2 API 19 / ARM32 (`armeabi-v7a`). This is an independently installable **integration test APK**, not yet a complete playable Hongguo client.

## Verified in v0.4 on the real device
- LibVLC 3.5.1 software decoding completed original H.264 360p / HEVC 360p / HEVC 720p offline samples (each a few seconds).
- The system did not advertise an HEVC decoder, so LibVLC software decoding is the promising path.
- Long-duration real drama decoding is **not yet** proven.

## New in v0.5
- `HongguoCatalog.java`: fetches public metadata from `https://hongguoduanju.com/`.
- Search via public website, and full episode ID lists from `/detail?series_id=...`.
- TLS 1.2 explicitly requested for Android 4.4, with **system certificate verification still enabled**.
- Strict response size and time limits, background network calls, visible failure diagnostics; no permanent media URLs or stored credentials.
- Maintains the same v0.4 standalone LibVLC codec tests.

## Important boundaries
- Episode IDs are **not playable streams**. Selecting an episode currently reports the ID, not a fake playback success.
- No authenticated player API, DRM handling, media decryption or end-to-end Hongguo online playback has been implemented.
- This test UI is temporary; the eventual client is intended to follow the established HGTV interface without adding product features.
- The APK uses package name `dev.redguo.codecprobe` and will update this test app only. It does not overwrite HGTV or 影视仓.
- Java code is authored independently; the official website is read as a public source. LibVLC is a VideoLAN component with its own applicable licenses.

## Checkpoints on the TV
1. Install v0.5 on EC6108V9U; offline H.264 and HEVC tests must still play.
2. Select **红果官网目录（联网）**; note number of dramas returned or exact TLS error.
3. Select a drama, verify full episode count; e.g. the public page for 军需处长的账本 currently shows 46 episodes.
4. Try **剧名搜索（联网）**, then open a result's episodes.
5. Capture the diagnostics dialog if the network fails. Do not send private account tokens or playback URLs.

CI builds the APK on a separate branch, checks minimum API 19, ARM32 native libraries, sample assets and old-compatible V1 APK signature.
