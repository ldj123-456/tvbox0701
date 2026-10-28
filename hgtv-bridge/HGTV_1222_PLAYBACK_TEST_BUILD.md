# HGTV 1.2.2.2 · Original-API LibVLC Fallback Test

## Verified locally on 2026-10-28
A **modified, separately signed experimental HGTV 1.2.2.2 APK** was built locally from the user's original APK (SHA-256 `192d22df90cd6d77205fc425e5d0b5679bd6b8c79782444018158682c9579ccf`).

Test APK SHA-256: `9b1d1dc6b876beec291d836644879a5a1a674d87ce0df2f893ce694d52c9235f`, approximately 24.2 MB. It is delivered only as a chat attachment, not uploaded to this public GitHub repository.

### Key point: original content API is unchanged
All dramas, search results, episode metadata, device identity, authorization, signed requests, stream selection, CDN requests, processed video bytes, and catalog UI remain original HGTV 1.2.2.2 logic. **No independent content API or hard-coded drama catalog was added.**

The injection uses:
- Original `E0.p1` prepared media.
- Original `H0.s` MP4 random-access reader and its original media processing.
- `HgtvOriginalMediaSource` bridges original `H0.s` by reflection, with exact *original* Smali class names (`E0/p1`, `H0/s`, `o0/q`).
- `LoopbackRangeServer` exposes already-readable local video bytes on `127.0.0.1` with a random token; no relay Mac/server and no separate CDN fetcher.
- `HgtvLibVlcAdapter` uses LibVLC 3.5.1 in software mode.

### Exactly five original Smali hooks
1. `C0/u0.K0(MainActivity,Throwable)`: at method start call `HgtvPlaybackFallback.maybeStart(...)`. Return early only if error `x.w0.a==4005` and original `MainActivity.c3` is prepared.
2. `C0/u0.H0(MainActivity,...)`: close prior VLC overlay before the original HGTV loads another episode.
3. `C0/u0.I(MainActivity)`: use fallback playback position when active; otherwise keep the original progress path.
4. `MainActivity.dispatchKeyEvent`: forward remote keys only when fallback player is active; otherwise preserve original behavior.
5. `MainActivity.onPause`: close fallback on backgrounding.

Original APK `AndroidManifest.xml` package is switched from `com.hgtv.star` to `com.hgtv.star.vlcprobe`, provider authority similarly updated, and display label changed to `HGTV 播放兼容测试`, to avoid replacing the working original HGTV.

### DEX/so assembly
- `classes.dex`: original APK classes re-assembled with only the five hook changes; still contains 2,674 classes.
- `classes2.dex`: LibVLC classes from the **previous device-tested v0.4 APK**, with 32 duplicate AndroidX/support classes removed; 1,331 classes.
- `classes3.dex`: this branch's newly compiled Java backend incl. `HgtvOriginalMediaSource` and `HgtvPlaybackFallback`; 13 classes.
- `lib/armeabi-v7a`: `libvlc.so`, `libvlcjni.so`, `libc++_shared.so`.
- Verified all three DEX class sets **do not overlap**.
- Apktool 3.0.3 rebuilt modified Smali successfully; combined ZIP integrity, decoded Manifest, and V1 JAR signature verification succeeded.
- **No Android device install or actual authorized drama playback has yet been performed**. A successful build only proves static package consistency.

### TV trial
1. Preserve original HGTV. Install experimental `HGTV_1.2.2.2_VLC_Fallback_TEST.apk` separately.
2. Use the **original HGTV homepage and search** to locate a drama, enter episodes normally, then play an episode that used to return 4005.
3. If fallback starts, a temporary full-screen VLC layer appears. Remote OK toggles playback, left/right seek ten seconds, up/down switch episodes using the **original HGTV episode handler**, Back leaves playback.
4. Capture an image of any error. If possible, `adb logcat -s HGTV-LibVLC` captures Java fallback errors. Never share account credentials or private media authorization data.

This is **not** a finished release: the fallback overlay does not yet reuse HGTV's full HUD/danmaku, and real streaming, long-play, seek, stability and automatic-next must be verified on EC6108V9U.

## Repository scope
Only source-level backend and documentation are kept on this isolated branch; the user-owned original APK and resulting derivative binary are **not** pushed to a public GitHub branch.
