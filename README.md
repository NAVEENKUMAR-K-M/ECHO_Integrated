# ECHO (integrated)

One Android app combining the two earlier projects. Package `com.echo.hearing`, min SDK 29.

| Tab | What it does |
|---|---|
| **Test** | Consent, headphone check, noise check, practice, Hughson-Westlake pure-tone screening, results/audiogram |
| **Home** | Status and shortcuts |
| **Monitor** | Background phone-volume tracking, listening analytics, high-volume and hearing-test alerts |
| **Aid** | Hearing aid: mic → Oboe DSP loop → earbuds (noise gate, adaptive denoiser, 4-band compression, per-ear EQ, A/B speech log) |
| **Reports** | Weekly report, exposure trends, insights and history, all from data recorded on the phone |
| **Profile** | Profile, devices, notifications, privacy, data export |

## Monitor tab: phone volume tracking

The Monitor tab records the phone's **media volume setting** over time and shows listening analytics.
It uses no microphone and plays no audio.

- `volume/VolumeTrackerService` is a foreground service (`specialUse`) with a notification and a Stop
  action. It samples the volume, whether media is playing, and the output (Bluetooth, wired, USB,
  speaker) on every change and every 15 s. It resumes after a reboot or app update if it was on.
- Records are time segments in `files/volume_log.csv` on the phone, kept for 90 days. Gaps where the
  phone slept are not counted.
- Analytics: today's listening time, average/peak volume and time at 80% or more; a 24-hour volume
  timeline; 7-day listening chart; listening by output; listening sessions.
- Alerts (`volume/VolumeAlerts.kt`): a notification after 10 minutes of listening at 80% or more
  (repeated every 30 minutes), and a hearing-test suggestion when the last week's average volume on an
  output is at least 15 points above the 4 weeks before it on at least 4 separate days.
- Volume is a percentage of the phone's volume range, not a measured sound level in dB.

## Aid tab: hearing aid

- `monitor/MonitorService` is a foreground service (`microphone`) that keeps the aid running in the
  background, with a notification and a Stop action.
- It takes no audio focus, so music and video keep playing alongside it. It stops for phone and video
  calls. Output is on the media stream, so the phone's volume buttons control it.
- **Automatic fitting** (`monitor/HearingAidFitting.kt`): each new screening sets the per-ear EQ with
  the half-gain rule relative to the listener's own best threshold, capped at +6 dB per band. Each
  screening is applied once, so later manual adjustments are kept. Screening levels are uncalibrated,
  so only the differences between frequencies and ears are used.

## How they connect

- **One microphone.** Screening and the hearing aid both need it, so switching to the Test tab stops the aid.
- Hearing-aid settings persist in the `echo_profile` preferences the standalone monitor used.

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
