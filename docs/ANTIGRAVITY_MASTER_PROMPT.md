# ECHO Hearing-Screening POC — Antigravity Master Prompt

You are a senior cross-platform mobile engineer, audio-systems engineer, test architect, and privacy-conscious product engineer. Build the first proof of concept (POC) for ECHO: a mobile-first, self-administered **preliminary hearing-screening** application for Android and iOS.

## First action: inspect before coding
1. Inspect the existing repository, package manager, app framework, tests, linting, and project conventions. Do not overwrite a working project or replace existing decisions without explaining why.
2. If the repository is empty, use React Native + TypeScript with an Expo development build / prebuild-compatible architecture. Do not rely on Expo Go for low-level audio capabilities that require native APIs. Keep platform integrations behind adapters.
3. Read `README.md` and the reference POC files if provided. The browser POC demonstrates domain logic only; it is not clinical or acoustic validation.
4. Create a short implementation plan and a module/test checklist, then execute it in small verifiable steps. Do not stop after producing a plan.

## Non-negotiable product and safety boundaries
- This is a software POC for preliminary screening, not a diagnostic medical device.
- Never claim clinically valid hearing thresholds unless a specific phone + headphone output path has a documented calibration profile and the complete system has been validated against an appropriate clinical reference.
- Never label arbitrary app volume, PCM amplitude, or simulated dB steps as dB HL or dB SPL.
- Do not infer a non-response from a trial that overlaps environmental noise, an audio-route interruption, app suspension, or sensor failure.
- Keep screening thresholds, exposure-protection policy, and future hearing-assistance processing as separate concerns. This POC implements only the screening workflow.
- Default to local processing and do not store raw microphone recordings. Store only noise estimates, timestamps, sensor-health states and reason codes unless the user explicitly consents to a separately justified diagnostic capture.
- Do not silently invent clinical acceptance criteria. Put protocol-specific values behind a configuration interface and mark any unreviewed values as provisional. Identify decisions requiring audiologist review.

## Target user flow
1. Welcome, intended-use explanation, limitations, privacy notice and consent.
2. Device and audio-route preflight.
3. Supported headphone / calibration profile check. If the configuration is unsupported or uncalibrated, do not present calibrated threshold output. Offer a clearly labelled simulation/demo mode or stop with an explanation.
4. Microphone permission and sensor-availability check.
5. Quiet-environment precheck.
6. Explain the test and provide response training / consistency check.
7. Test one ear and frequency at a time, only where the hardware can route sound to the intended ear and the supported configuration is documented.
8. Present a tone, collect a response, validate trial quality, and update the threshold search only for valid trials.
9. If disturbance is detected, automatically pause measurement, flag an overlapping trial as contaminated, show “Noise may affect this test. Move to a quieter place, then recheck,” and resume only after the noise gate passes a stable recovery window.
10. Produce a screening summary with quality flags, limitations, and appropriate advice to seek professional assessment when indicated. Never display “diagnosis.”

## Required modules (separate files, explicit interfaces)
1. `onboarding/ConsentScreen`: intended use, consent, instructions and limitations.
2. `platform/PlatformAudioAdapter`: platform capability discovery, audio route changes, interruptions, lifecycle and permissions. Return explicit capability status; do not assume identical Android/iOS capabilities.
3. `calibration/CalibrationRegistry`: known device/headphone configuration and calibration-profile version. POC may use fixture profiles only; unsupported configurations must be marked unsupported.
4. `audio/StimulusEngine`: tone-generation interface, frequency, duration, envelope, output route and cleanup. Begin with a fake/simulated adapter. Implement actual native audio only after documenting API feasibility and supported hardware. Keep audio playback separate from UI sounds.
5. `noise/NoiseMonitor`: microphone source interface, sensor health, windowed noise estimate, confidence and `VALID | SUSPECT | INVALID | UNAVAILABLE` status. Do not claim calibrated SPL from uncalibrated mic input. Use a fake adapter for tests.
6. `test/SessionStateMachine`: `PRECHECK`, `READY`, `PRESENT`, `RESPONSE`, `EVALUATE`, `PAUSED_NOISE`, `PAUSED_DEVICE`, `RETRY`, `COMPLETE`, `ABORTED` with explicit allowed transitions.
7. `test/ThresholdProtocol`: modified Hughson–Westlake-style staircase as the initial *provisional* baseline, with configurable start level, descending and ascending step sizes, per-ear/per-frequency response history, reversal/acceptance rules, maximum trial guard, and clear protocol version. Do not claim the implementation fully conforms to clinical standards without expert review.
8. `test/TrialValidityGuard`: validate microphone status, overlap with tone, audio-route changes, app backgrounding, missing responses and sensor faults. Invalid trial never changes threshold estimates and must be retried or safely terminated.
9. `results/ResultRepository`: typed local persistence of session ID, target ear/frequency, response events, protocol version, device profile, calibration version, noise quality states, invalid-trial reasons, timestamps and quality flags. Avoid raw audio by default.
10. `results/ScreeningSummary`: explain screening-only results and uncertainty; distinguish “not measured / unsupported” from normal hearing.
11. `diagnostics/EventLog`: privacy-minimized structured event log and crash-safe state recovery.

## Noise interruption requirements
- The detector must not assume that any headphone microphone is suitable. Some are voice-call/ANC microphones with directionality or noise suppression. Document source, placement, sample rate and limitations per supported device.
- Prefer a validated microphone route and characterize it against an independent reference measurement before claiming real-world noise thresholds.
- If the sensor is unavailable or unreliable, show the limitation and do not claim automatic disturbance detection; use a conservative fallback or stop the test.
- If status is `SUSPECT` or `INVALID` during a stimulus, pause the test. Mark the overlapping trial contaminated, do not feed it to the threshold algorithm, preserve valid prior trials, and retry after the user relocates.
- Resume only when status is `VALID` for a configurable recovery window. Use hysteresis / persistence to avoid repeated pause-resume flapping. The actual duration and thresholds must be validated, not guessed as clinical facts.
- Record only state, noise estimate, confidence, timestamp and reason code by default—not raw room audio.

## Threshold algorithm requirements
- Implement the baseline as a pure, deterministic domain module independent of the UI and hardware.
- Use configurable protocol parameters and per-ear/per-frequency state. A commonly used staircase starting point is 10 dB down after a “heard” response and 5 dB up after a “not heard” response, but label the protocol provisional and require audiologist review before clinical use.
- Require a documented acceptance rule, response consistency checks, max-trial/timeout handling, and invalid-trial retry logic. Do not accept an estimate from insufficient valid responses.
- Store the entire valid response history and protocol version for reproducibility.
- Bayesian adaptive threshold estimation is a later comparison experiment, not a replacement before benchmark tests exist.

## Initial POC scope
- Build all screens and domain modules using fake audio/noise adapters first.
- Provide a developer-only simulation panel that can inject “noise begins during stimulus,” “sensor unavailable,” “headphones disconnected,” “app backgrounded,” “heard,” and “not heard” events.
- Clearly label all simulated levels and thresholds as simulation data, not dB HL.
- Target conventional frequencies up to 8 kHz only as a future supported range; actual frequency availability must be limited to calibrated and validated hardware profiles. Do not promise extended high frequencies in this POC.
- No accounts, cloud sync, payments, advertising, exposure-monitoring features, personalized safety limits, or hearing-assistance hardware in this first scope.

## Testing requirements — run tests, do not just write them
Create and run unit tests for:
1. Valid and invalid precheck paths.
2. Unsupported device / missing calibration / unavailable microphone.
3. Correct state-machine transitions and rejection of illegal transitions.
4. The provisional staircase step rules and boundary handling.
5. Per-ear and per-frequency result isolation.
6. Noise during a tone causes pause and contamination.
7. Contaminated trials do not update threshold estimates or become “not heard.”
8. Resume blocked until noise status is valid and recovery window is stable.
9. Headphone disconnect / app interruption pause and safe resume.
10. Response inconsistency, timeout and max-trial guards.
11. Persistence round trip and recovery after app interruption.
12. No raw audio persisted by default.
13. Simulation results always carry a `SIMULATION_ONLY` / `UNVALIDATED` flag.

Then run the full test suite, lint/typecheck and available build checks. Fix failures before reporting completion. If a native build cannot run in this environment, state exactly what could and could not be verified; do not claim success without command output.

## Deliverables
- Working runnable POC with concise setup instructions.
- Modular source tree with typed interfaces and dependency injection for fake/native adapters.
- Automated tests and actual test output summary.
- `ARCHITECTURE.md` showing module responsibilities, data flow, state machine, failure modes, and native integration gaps.
- `DEVICE_CAPABILITY_MATRIX.md` with Android/iOS audio/microphone/route/lifecycle capabilities, evidence links, and unknowns. Do not fill unknowns with guesses.
- `VALIDATION_PLAN.md` covering calibration, noise detector evaluation, comparison with conventional clinical PTA, repeatability, sensitivity/specificity and clinical review.
- A list of known limitations and the next smallest build step.

## Work style
- First inspect, then plan briefly, then implement each module and run its tests.
- Use explicit TypeScript types, small pure functions, dependency injection, deterministic fake adapters and meaningful errors.
- Avoid a giant single-file implementation, speculative APIs, unnecessary dependencies, and mocked production claims.
- When an API capability is uncertain, research the official Android/iOS documentation, record the source and test a minimal spike before relying on it.
- After implementation, report: modules completed, tests actually run and passed/failed, commands used, build status, unresolved hardware/clinical questions, and how to launch the POC.

Start now by inspecting the repository and executing the implementation. Do not merely return another plan or prompt.
