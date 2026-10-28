# HGTV 1.2.2.2 VLC v3 — regression fix (2026-10-28)

## Device user report
- v1 could sometimes render actual drama video, but picture was misaligned / black intermittently, and A/V synchronization unstable.
- v2 detailed drama and episodes still arrived using HGTV's original content API, but user reported all tested dramas went black while audio played, and a second audio instance restarted over the first.
- HGTV homepage settings crash was already present in official 1.2.2.2; this was not introduced or fixed by v3.

## Actual v3 changes
- In the **original APK Smali** `C0/u0.K0(MainActivity,Throwable)`, after `HgtvPlaybackFallback.maybeStart` returns true (a verified decoder 4005 fallback), cancel HGTV P retry Runnable, call `C0/g1.b(MainActivity)` to cancel Q retries, then pause the original ExoPlayer with `Lx/e.f()`, stop with `Lx/G.X()`, and remove queued items with `Lx/G.O(0,MAX_INT)`. Do not return early before this cleanup.
- In `C0/u0.P(MainActivity)`, skip the old ExoPlayer periodic/retry task only while the VLC fallback reports an active playback position. Original code runs normally otherwise.
- The v2 new-video-layout listener, aggressive decoder flags, and dynamic SurfaceView resize are **reverted**. The v3 `HgtvLibVlcAdapter` is based on v1's real-device video-rendering code, with only optional millisecond audio-offset diagnostics retained.
- Existing HGTV search, episode lists, metadata, official/nonofficial content interface, media URL selection, video-byte reader `E0.p1 / H0.s`, authorization and original HTTPS code are unchanged.
- APK package remains `com.hgtv.star.vlcprobe2` and uses the **same v2 test signing key**. Apktool metadata versionCode 58, versionName `1.2.2.2-vlc3`; it can update v2 without removing the original app.

## Build and static verification
- Original HGTV Smali rebuilt by Apktool 3.0.3.
- `classes.dex` original 2,674 classes with only two additional safe-control hooks; `classes2.dex` video runtime 1,331 classes, `classes3.dex` bridge 13 classes. **0 class-name overlap**.
- ARM32 `libvlc.so` and `libvlcjni.so` reused unchanged from v2.
- ZIP structural verification and JAR V1 self-signed test signature verification completed; both v2/v3 key fingerprint: `26:19:CE:88:BE:53:71:04:54:30:51:E5:DE:38:2E:A2:B5:CB:30:27:D0:40:D5:FA:8E:AF:B9:44:DB:C0:58:67`.
- Local privately delivered APK `HGTV_1.2.2.2_VLC_Fix_v3.apk`, 24,183,109 bytes, SHA-256 `763342d27a2b447daeb21ba86e40f3a0dd6780d828c28fc80275da1cfea42e46`. **Not uploaded to the public GitHub repository.**
- The Android library/DEX itself compiled successfully in [GitHub Actions run 40624625931](https://github.com/ldj123-456/tvbox0701/actions/runs/40624625931).

## Cautions
- **Real-device validation of v3 is still pending**; it is not yet verified that overlapping sound or missing picture is fixed.
- v1 restoration can bring back left-justified portrait video; this test prioritizes picture output and exclusive playback before a separate centering fix.
- The inherited settings-page crash is NOT fixed. Need stack trace to localize without breaking source/TV UI.
- For compatibility with this slow ARMv7 device, check 480p first, then 720p; full 1080p software decoding is unverified.
- The original TV app remains separately installed. This branch is isolated from `main` and learning-system files.
