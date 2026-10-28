# HGTV 1.2.2.2 VLC v4 — Stable portrait centering on Android 4.4

## Device feedback / regression history
- v1: original HGTV catalog and real drama playback reached LibVLC; portrait video left-aligned, A/V issues.
- v2: resized SurfaceView after OnNewVideoLayout; device showed black-screen regression and overlapping audio.
- v3: restored the v1 rendering, stopped original ExoPlayer and cancelled retries during 4005 fallback. User confirms video and sound now work, but video stutters, switches to some episodes stretch the picture, and portrait material is still left-aligned.
- v4 fixes the _window geometry_ without resizing SurfaceView.

## Specific changes (only hgtv-bridge/HgtvLibVlcAdapter.java)
1. Keep Android 4.4 full-screen SurfaceView, no dynamic resize, no OnNewVideoLayoutListener.
2. Inform LibVLC's native vout of current host physical size using `IVLCVout.setWindowSize(w,h)`: pre-attach, once UI host is laid out, when `MediaPlayer.Event.Vout` fires, and on host size change. Always resend after attach even when dimensions match; an early pre-attach call might not reach native vout.
3. `setAspectRatio(null)` and `setScale(0f)` to let VLC letterbox portrait material with natural proportions; no manual stretching to 16:9.
4. Reset the remembered window size on `stop()`. The old overlay is released when the original HGTV changes episode; each new player receives a fresh viewport dimension.
5. Turn on `--drop-late-frames` and `--skip-frames` to prioritize audio timing over stale video frames on old ARMv7 software decoding. This **cannot guarantee** smooth playback if the video bitrate/resolution exceeds CPU throughput.

## External technical evidence
- https://github.com/mrmaffen/vlc-android-sdk/issues/107 — user-reported off-center VLC SurfaceView output, solved via `setWindowSize`.
- https://github.com/GeoffreyMetais/vlc-android/blob/master/libvlc/src/org/videolan/libvlc/VideoHelper.java — native VLC video helper synchronizes window size and aspect ratio.

## Integrity and deployment
- Source committed to isolated `hgtv-player-backend-api19` branch (never overwrites `main`).
- GitHub Actions run https://github.com/ldj123-456/tvbox0701/actions/runs/40626752277 passed Android Library compile and API19 DEX generation.
- Privately rebuilt **original HGTV** Smali skeleton, versionCode **59**, versionName `1.2.2.2-vlc4`, package `com.hgtv.star.vlcprobe2`, label `HGTV VLC 居中兼容 v4`.
- Original HGTV media and content logic unchanged: catalog/search/detail/episodes, session identity, original HTTPS, `E0.p1` / `H0.s` bytes, 4005 fallback hooks and exclusive playback.
- Verified the resulting APK's ZIP integrity, API19 AndroidManifest, test V1 signature, and same signing certificate as v3.
- APK size **24,183,989 bytes**; SHA256 `46a160c9dc008364083711544b08129e5771aad0391383976f1f57377c271732`.
- The private APK is delivered in this chat only. It is NOT pushed to public GitHub because it contains the user's locally provided original APK.
- **Not yet validated on physical EC6108V9U**. A successful build is not proof that portrait centering, episode-switch aspect ratio, or stutter are fully fixed. The inherited HGTV settings-page crash also remains unresolved.

## Required real-device checks
1. Update previous v3 test app (same package and signing key) without removing original HGTV.
2. Play a portrait drama; confirm image is horizontally centered and proportions are natural.
3. Switch forward/backward 3-5 episodes; confirm no stretching/left shift/double audio.
4. Observe 3-5 minutes for frame drops. If sustained stutter remains with audio normal, report video quality/resolution and CPU heat; choose a lower HGTV quality via existing authorized playback settings where accessible. Do not build an independent catalog.
