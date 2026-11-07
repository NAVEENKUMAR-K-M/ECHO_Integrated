# ECHO Device & Acoustic Capability Matrix

This document characterizes hardware capabilities, OS audio subsystems, input/output routing, and acoustic limitations across Android and iOS. 

> [!IMPORTANT]
> **Acoustic Honesty Rule**: This document distinguishes **verified platform API facts** from **unverified acoustic/audiological assumptions**. Unknowns and hardware variations are explicitly documented and never masked with guesses.

---

## 1. Audio Output Route Capabilities

| Audio Route Type | Android Platform API | iOS Platform API | Acoustic Screening Viability | Known Latency & Timing Jitter | Uncalibrated Hazard / Unknowns |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Wired 3.5mm Headphones** | `AudioDeviceInfo.TYPE_WIRED_HEADPHONES` (API 23+) | `AVAudioSessionPortHeadphones` | **Recommended for Screening** | Very low (<15 ms) deterministic buffer delivery | Output voltage varies across DAC chips (Qualcomm WCD9385 vs MediaTek vs Cirrus Logic). Phone volume slider is not calibrated SPL. |
| **Wired 3.5mm Headset w/ Mic** | `AudioDeviceInfo.TYPE_WIRED_HEADSET` (API 23+) | `AVAudioSessionPortHeadsetMic` | **Supported for Screening** | Very low (<15 ms) | Earphone driver impedance (16Ω vs 32Ω) alters physical sound pressure. Uncalibrated without coupler. |
| **USB-C Digital Audio / Headset** | `AudioDeviceInfo.TYPE_USB_HEADSET` / `TYPE_USB_DEVICE` (API 26+) | `AVAudioSessionPortUSBAudio` | **Supported for Screening** | Low (10–25 ms) | USB DAC contains independent amplifier and DSP. Output level is completely isolated from phone internal DAC gains. |
| **Bluetooth Audio (A2DP)** | `AudioDeviceInfo.TYPE_BLUETOOTH_A2DP` (API 23+) | `AVAudioSessionPortBluetoothA2DP` | **Conditional / Caution** | Variable (100–250 ms jitter depending on codec: SBC/AAC/LDAC) | Bluetooth headphones apply internal DSP EQ, volume scaling, dynamic range compression (DRC), and active noise cancellation (ANC) that distort linear acoustic stimulus steps. |
| **Bluetooth LE Audio** | `AudioDeviceInfo.TYPE_BLE_HEADSET` (API 31+) | `AVAudioSessionPortBluetoothLE` | **Future Research Target** | Low latency (20–40 ms via LC3 codec) | Varying OEM firmware support; requires Android 13+ and compatible hardware. |
| **Built-in Speaker** | `AudioDeviceInfo.TYPE_BUILTIN_SPEAKER` | `AVAudioSessionPortBuiltInSpeaker` | **STRICTLY BLOCKED** | N/A | Cannot provide acoustic isolation between left and right ears; room acoustics and reflections invalidate screening. |

---

## 2. Microphone & Environmental Noise Sensing Capabilities

| Input Source | Platform Access API | Frequency Response & Sensitivity | Audio Processing Hazards | Viability for Noise Gating |
| :--- | :--- | :--- | :--- | :--- |
| **Primary Phone Microphone** | `AudioRecord(MediaRecorder.AudioSource.MIC)` | Omnidirectional electret/MEMS; calibrated around 300Hz–4kHz speech range | Hardware AGC (Automatic Gain Control) or acoustic echo cancellation (AEC) can distort ambient level if `VOICE_COMMUNICATION` is used. ECHO uses `AudioSource.MIC` to minimize DSP distortion. | **Verified viable for relative ambient noise threshold gating** (quiet vs loud room). Cannot report absolute dBA SPL without sound level meter calibration. |
| **Headphone Inline Microphone** | `AudioRecord(MediaRecorder.AudioSource.DEFAULT)` when headset connected | Varies wildly from cheap electret to high-end MEMS | Variable placement (hangs near collar/clothing; friction noise can produce false positive noise spikes). | **Requires empirical screening**: prone to rustle and placement artifacts. |
| **Headphone ANC / Ambient Microphones** | Proprietary vendor SDKs only (Sony Headphone SDK, Apple AirPods Private Framework) | Calibrated external feeds on select flagship models | Standard Android/iOS public audio APIs **do not expose** external ANC microphone streams. | **Currently Unavailable via public OS APIs** without dedicated OEM partnership. |

---

## 3. Audio Lifecycle, Focus & Interruption Handling

| Event | Android Subsystem Behavior | ECHO Handling & State Transition | iOS Equivalent Behavior |
| :--- | :--- | :--- | :--- |
| **Incoming Phone Call** | `AudioManager.AUDIOFOCUS_LOSS_TRANSIENT` | Immediate halt of `AudioTrack`, trial marked `INVALID_INTERRUPTED`, state transitions to `AUTOMATIC_PAUSE_DEVICE` | `AVAudioSessionInterruptionType.began` halts audio engine |
| **Headphones Unplugged** | Broadcast `ACTION_HEADSET_PLUG` / `AudioDeviceCallback` | Immediate halt of stimulus, state transitions to `AUTOMATIC_PAUSE_DEVICE`, user prompted to reconnect | `AVAudioSession.routeChangeNotification` with reason `oldDeviceUnavailable` |
| **App Sent to Background** | Activity `onStop` lifecycle event | Release `AudioTrack` and pause `NoiseMonitor`. Persist current session state to disk. Prevent sound from playing in background. | `UIApplication.didEnterBackgroundNotification` suspends audio session |
| **Transient Notification Chime** | `AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK` | ECHO halts test trial and invalidates trial to prevent alert sound contamination | `AVAudioSessionInterruptionOptionShouldResume` |

---

## 4. Acoustic Calibration & Reference Transfer

```
+-----------------------------------------------------------------------------------------+
|                               ACOUSTIC SIGNAL CHAIN                                     |
|                                                                                         |
|  [Digital Tone Generator]                                                               |
|        │                                                                                |
|        ▼ Attenuation in dBFS (-80 to 0 dBFS)                                            |
|  [Android AudioTrack / HAL]                                                             |
|        │                                                                                |
|        ▼ Master Volume Attenuation (0% to 100%)                                         |
|  [Hardware DAC & Headphone Amp]                                                         |
|        │                                                                                |
|        ▼ Output Voltage (VRMS) into load impedance (16Ω / 32Ω)                          |
|  [Headphone Transducer & Ear Coupling]                                                  |
|        │                                                                                |
|        ▼ Physical Sound Pressure Level (dB SPL) in Ear Canal                            |
|  [Tympanic Membrane / Cochlea]                                                          |
|        │                                                                                |
|        ▼ Hearing Level (dB HL) relative to ISO 389 Reference Equivalent Thresholds     |
+-----------------------------------------------------------------------------------------+
```

### Critical Audiological Distinctions:
1. **dBFS (Decibels relative to Full Scale)**:
   - A purely digital metric representing sample amplitude relative to the maximum representable 16-bit integer (32,767).
   - $0\text{ dBFS}$ is peak digital output; $-40\text{ dBFS}$ is attenuated digital output.
   - **ECHO exclusively records and displays dBFS.**
2. **dB SPL (Sound Pressure Level)**:
   - Physical acoustic energy measured in pascals ($20\ \mu\text{Pa}$ reference).
   - Depends directly on phone amplifier voltage, volume slider setting, and headphone transducer sensitivity ($dB\text{ SPL}/\text{mW}$).
3. **dB HL (Hearing Level)**:
   - Clinical audiometric scale referenced to the average hearing threshold of healthy young otologically normal adults (ISO 389-1 / ANSI S3.6 RETSPL).
   - **Treating dBFS or phone volume as dB HL without calibrated coupler measurement is medically invalid and prohibited.**

---

## 5. Verified Facts vs. Unresolved Research Questions

### Verified Engineering Facts
- Android `AudioTrack` can generate low-latency, glitch-free stereo tones with pure channel separation (left channel silent for right ear trials, right channel silent for left ear trials).
- Hann raised-cosine ramps of 20ms completely eliminate acoustic switching transients and click artifacts.
- `AudioRecord` with 16 kHz sampling and 100ms RMS analysis reliably differentiates between quiet environments (ambient < -45 dBFS) and spoken disturbance (> -32 dBFS).
- Android runtime permissions (`RECORD_AUDIO`) and physical audio routes (`AudioManager.getDevices`) can be queried reliably on Android 8.0 (API 26) through Android 15 (API 35).

### Open Research Questions & Audiological Unknowns
1. **Volume Slider Standardization**:
   - Android media volume slider has between 15 and 30 discrete steps depending on OEM. Setting a fixed media volume (e.g. 70%) does not guarantee consistent output voltage across different phone manufacturers.
   - *Investigation needed*: Can USB-C standard DACs provide fixed hardware gain control via UAC (USB Audio Class) descriptors?
2. **Bluetooth DSP Latency Jitter**:
   - Bluetooth codecs introduce 100–250 ms variable latency, which affects user reaction time latency measurement.
   - *Investigation needed*: Calibration of reaction time windows when Bluetooth routes are detected.
3. **Microphone Frequency Response Variance**:
   - MEMS microphones on budget Android devices exhibit varying low-frequency roll-off below 250 Hz.
   - *Investigation needed*: Empirical bench characterization of phone microphone transfer functions against a Type-1 sound level meter.
