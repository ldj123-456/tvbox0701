# EC6108 Android 4.4 hardware HEVC diagnostic

This is a **standalone diagnostic APK**, not a replacement for the original HGTV; no content catalog, no Hongguo API calls, no authentication and no actual drama playback. It uses locally generated original, 12-second, 24fps clips to isolate hardware support from network/stream issues.

Checks:
- MediaCodecList HEVC/H264 decoder names; Google/Android decoders are identified as likely software.
- Native Android MediaCodec 720p HEVC test selects a **vendor decoder explicitly**; no silent fallback to software. Decodes an actual 12s HEVC sample to Surface, logging rendered frames, time lag and errors.
- Same test for native vendor H264 decoder as a baseline.
- LibVLC HEVC 480P/720P software decoders, and LibVLC HEVC 720P hardware-attempt mode, so you can compare. VLC's hardware-attempt can automatically fallback to CPU and cannot prove hardware on its own.

APK is separate appId `dev.redguo.hevcdiagnostic`, API19, ARM32. It does not modify the original HGTV 1.2.2.2 or VLC v4 app. The clips are synthetic and must not be confused with an actual encoded content stream; 12-second success does not guarantee all dramas' continuous playback. User should photograph full report and results after each test.

Source and workflow live only on branch `hgtv-hevc-hardware-diagnostic`; original private APK not uploaded here.
