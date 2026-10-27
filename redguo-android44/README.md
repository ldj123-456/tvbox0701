# RedGuo Android 4.4 codec probe (isolated development branch)
This is an independent **codec test APK only**, not yet a Hongguo-series client. Target: Android 4.4.2 / API 19 / armeabi-v7a.
The GitHub workflow generates three original 5-second test clips (H.264 360p, HEVC 360p, HEVC 720p) and attempts to compile the application with official VideoLAN LibVLC 3.5.1.
The APK uses a unique package dev.redguo.codecprobe and does not replace HGTV or any existing TVBox app.
Status is only confirmed once GitHub Actions succeeds and the resulting APK passes installation/video tests on EC6108V9U. License: see VideoLAN LibVLC LGPL terms.
