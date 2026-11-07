# ECHO Audiometric & Acoustic Validation Plan

This validation plan outlines the formal acoustic, algorithmic, and clinical experimental protocols required to transition ECHO from an uncalibrated proof of concept into a validated, regulatory-compliant hearing-screening system.

---

## 1. Stage 1: Acoustic Hardware Calibration Protocol

### Objective
Establish an empirical, reproducible transfer function mapping ECHO's digital stimulus amplitude (**dBFS**) to physical sound pressure level (**dB SPL**) and reference equivalent threshold sound pressure level (**dB HL**) for supported hardware combinations.

```mermaid
graph LR
    Phone[Android Device + ECHO App] -->|Fixed Media Volume 70%| Headphone[Target Headset]
    Headphone -->|Acoustic Coupling| EarSim[IEC 60318-1 / 60318-4 Artificial Ear]
    EarSim --> Preamp[Conditioning Preamplifier]
    Preamp --> Analyzer[Type-1 Acoustic Analyzer / B&K PULSE]
    Analyzer --> CalTable[Calibration Lookup Table RETSPL]
```

### Equipment & Environment
- **Acoustic Coupler**: IEC 60318-1 ear simulator (for supra-aural headphones) and IEC 60318-4 occluded ear simulator (for insert earphones).
- **Measurement Microphone**: 1/2-inch pressure-field microphone calibrated with a Class 1 acoustic calibrator (94.0 dB SPL at 1000 Hz, IEC 60942).
- **Test Environment**: Full acoustic hemi-anechoic or double-walled audiometric chamber (ambient noise < 20 dBA SPL).

### Calibration Procedure
1. Fix Android media volume to a designated baseline level (e.g., 70% or full 100%).
2. Synthesize pure tones in ECHO at 250, 500, 1000, 2000, 4000, and 8000 Hz across a range of digital attenuations (0 dBFS down to -70 dBFS in 5 dB steps).
3. Measure physical dB SPL in the artificial ear coupler for both Left and Right transducers.
4. Verify linearity: check that a 10 dB digital drop in dBFS corresponds to a $10.0 \pm 0.5\text{ dB}$ reduction in physical acoustic SPL.
5. Apply Reference Equivalent Threshold Sound Pressure Levels (**RETSPL**, ISO 389-1 for circumaural / ISO 389-2 for insert earphones) to compute the dB HL conversion offset:
   $$\text{Level}_{\text{dB HL}} = \text{Measured}_{\text{dB SPL}} - \text{RETSPL}(f)$$
6. Generate a signed `CalibrationProfile.json` containing the empirical lookup table and device serial/model identifiers.

---

## 2. Stage 2: Environmental Noise Detector Verification

### Objective
Quantify the sensitivity, specificity, and false-alarm rate of the environmental noise monitoring subsystem.

### Methodology
1. Place the test device inside an acoustic chamber with a calibrated omnidirectional loudspeaker array.
2. Play calibrated background soundscapes at controlled sound levels:
   - Quiet baseline: 25–35 dBA (IEC 60645-1 allowable room noise).
   - Moderate noise: 40–50 dBA (typical living room / office).
   - Loud disturbance: 55–75 dBA (traffic, speech, HVAC transients, television).
3. Record the ECHO noise detector state (`VALID`, `SUSPECT`, `INVALID`) alongside the physical sound level meter reading.
4. **Acceptance Criteria**:
   - In quiet conditions (< 35 dBA): False pause rate $< 2\%$.
   - In disruptive conditions (> 50 dBA): Disturbance detection rate $> 98\%$ within 150 ms.
   - Hysteresis recovery gate: Zero premature resumptions before 3 consecutive seconds of verified quiet.

---

## 3. Stage 3: Clinical Pure Tone Audiometry (PTA) Concordance Study

### Objective
Evaluate the diagnostic accuracy and concordance of ECHO's self-administered screening results against gold-standard clinical pure tone audiometry conducted by a certified audiologist.

### Cohort Design
- **Sample Size**: $N = 120$ participants (240 ears).
  - Cohort A (Normal Hearing): 40 participants (thresholds $\le 20\text{ dB HL}$).
  - Cohort B (Mild to Moderate Hearing Loss): 50 participants (thresholds $25\text{--}55\text{ dB HL}$).
  - Cohort C (Older Adults / Asymmetric Hearing): 30 participants.
- **Inclusion Criteria**: Adults aged 18–80 capable of following mobile instructions.
- **Exclusion Criteria**: Active ear infections, draining ears, or cognitive impairment preventing informed consent.

### Protocol
1. Each participant receives an otoscopic inspection to rule out cerumen impaction.
2. Standard diagnostic Pure Tone Air Conduction Audiometry (PTA) administered by a licensed audiologist in a certified sound-isolated booth (ANSI S3.1 / ISO 8253-1) using calibrated audiometer equipment.
3. Participant performs ECHO self-administered screening on an Android device with calibrated test headphones in a quiet room setting.
4. **Statistical Analysis**:
   - **Correlation**: Pearson's $r$ and Spearman's $\rho$ between clinical thresholds and ECHO thresholds at 500, 1000, 2000, and 4000 Hz.
   - **Bland-Altman Analysis**: Mean difference (bias) and 95% limits of agreement ($\pm 1.96\ \text{SD}$). Clinical concordance target: Mean bias $< 3\text{ dB}$, with $90\%$ of points within $\pm 10\text{ dB}$.
   - **Screening Classification Accuracy**: Sensitivity and Specificity for detecting hearing impairment defined as pure-tone average $> 25\text{ dB HL}$. Clinical benchmark target: Sensitivity $\ge 90\%$, Specificity $\ge 85\%$.

---

## 4. Stage 4: Test-Retest Reliability Protocol

### Objective
Verify that ECHO delivers stable, repeatable threshold indicators across multiple sessions without significant learning or fatigue bias.

### Protocol
1. Administer the screening twice to 50 subjects with a 30-minute rest interval between trials.
2. Calculate:
   - **Intraclass Correlation Coefficient (ICC)** across frequencies (target: $\text{ICC} > 0.85$).
   - **Absolute Difference**: Percentage of retest thresholds within $\pm 5\text{ dB}$ (target: $> 85\%$).
   - **Standard Error of Measurement (SEM)**.

---

## 5. Regulatory, Compliance & Ethical Governance

```
                    REGULATORY PATHWAY OVERVIEW
                    
  [Early Research POC] ───► [Wellness / Screening Tool] ───► [Software as a Medical Device]
     • Non-clinical           • Enforcement discretion          • FDA 510(k) Class II
     • Uncalibrated dBFS      • Clear disclaimers               • ISO 13485 Quality System
     • Simulation panel       • No diagnostic claims            • IEC 62304 Medical Software
```

1. **Current POC Classification**:
   - **Research Demonstration Tool**: Explicitly non-medical. Does not diagnose or claim clinical equivalency. Disclaimers are prominently displayed before testing.
2. **Medical Device Classification Requirements (Target State)**:
   - **FDA (USA)**: Audiometers and hearing screening apps fall under 21 CFR 874.1050 (Audiometer) as Class II medical devices requiring a 510(k) premarket notification.
   - **EU MDR (Europe)**: Software providing health assessments and screening classifications qualifies as Class IIa under Rule 11 of EU Medical Device Regulation 2017/745.
3. **Required Standards for Commercial Release**:
   - **IEC 60645-1**: Electroacoustics – Audiometric equipment (Type 4 screening audiometers).
   - **ISO 14971**: Application of risk management to medical devices.
   - **IEC 62304**: Medical device software – Software life cycle processes.
   - **HIPAA & GDPR**: Privacy and local data residency for audiometric health records.
