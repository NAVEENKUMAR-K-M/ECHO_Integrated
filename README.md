# ECHO (integrated)

One Android app combining the two earlier projects. Package `com.echo.hearing`, min SDK 29.

| Tab | What it does | Came from |
|---|---|---|
| **Screening** | Consent, headphone check, noise check, practice, Hughson-Westlake pure-tone screening, results/audiogram | `Echo_Poc` (Compose, AudioTrack) |
| **Live monitor** | Mic → Oboe DSP loop → earbuds: noise gate, adaptive denoiser, 4-band compression, per-ear EQ, A/B speech log | `ECHO` (Oboe C++) |

Navigation is a two-item bottom bar. On the Monitor tab the start/stop controls, device pickers and
volume trim are always visible. Everything else sits in collapsed sections: noise and speech processing,
hearing profile, audiogram record, and the speech A/B log.

## How they connect

- **One microphone, one output.** Screening and monitoring both use them, so switching tab or leaving the app stops the monitor.
- **Import latest screening** (Monitor → Audiogram record) copies saved screening thresholds into the reference audiogram fields. They are uncalibrated digital levels, stored for reference only, and never change a gain.
- Settings persist in the same `echo_profile` preferences the standalone monitor used.

## Build

JDK 17+ (Android Studio's bundled JBR works), Android SDK 35, NDK 28.2.13676358, CMake 3.22.1.

```bash
./gradlew assembleDebug testDebugUnitTest
```

APK: `app/build/outputs/apk/debug/app-debug.apk`. The debug build uses the id `com.echo.hearing.debug`.

## Layout

```
app/src/main/java/com/echo/hearing/
  ui/            EchoApp (tabs), HearingScreeningApp, view model
  monitor/       MonitorScreen, MonitorViewModel, NativeAudioEngine (JNI)
  audio/ noise/ device/ domain/ data/   screening engine
app/src/main/cpp/                       Oboe DSP engine
docs/                                   architecture, validation plan, audio technical sheets
web-prototype/                          original browser POC (npm test / npm start)
```

## Caveats

This is an engineering prototype, not a hearing aid or a diagnostic device. Output levels are digital (dBFS), not
calibrated SPL. Latency is not measured end to end. See `docs/` for the full limits.
