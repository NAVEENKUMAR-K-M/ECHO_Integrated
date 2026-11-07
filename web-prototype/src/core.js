/**
 * ECHO POC domain logic. Simulation only: it does not generate calibrated acoustic output
 * and cannot produce clinically valid hearing thresholds.
 */
export const SessionState = Object.freeze({
  PRECHECK: 'PRECHECK', READY: 'READY', PRESENT: 'PRESENT', RESPONSE: 'RESPONSE',
  EVALUATE: 'EVALUATE', PAUSED_NOISE: 'PAUSED_NOISE', PAUSED_DEVICE: 'PAUSED_DEVICE',
  RETRY: 'RETRY', COMPLETE: 'COMPLETE', ABORTED: 'ABORTED'
});

export const DEFAULT_FREQUENCIES_HZ = [500, 1000, 2000, 4000];

export function createSession({ frequenciesHz = DEFAULT_FREQUENCIES_HZ, ears = ['left', 'right'] } = {}) {
  return {
    id: `sim-${Date.now()}`, state: SessionState.PRECHECK, frequenciesHz: [...frequenciesHz], ears: [...ears],
    earIndex: 0, frequencyIndex: 0, currentLevelDb: 40, startingLevelDb: 40,
    downStepDb: 10, upStepDb: 5, minLevelDb: 0, maxLevelDb: 80,
    trialCount: 0, validAscendingResponses: 0, heardAtLevels: [], responseHistory: [],
    results: [], contaminatedTrials: [], noisePauseCount: 0, devicePauseCount: 0,
    qualityFlags: ['SIMULATION_ONLY'], startedAt: new Date().toISOString()
  };
}

export function currentTarget(session) {
  return { ear: session.ears[session.earIndex], frequencyHz: session.frequenciesHz[session.frequencyIndex] };
}

export function precheck(session, { deviceSupported, calibrationAvailable, noiseStatus, sensorAvailable = true }) {
  if (!deviceSupported) return { session: { ...session, state: SessionState.ABORTED, qualityFlags: [...session.qualityFlags, 'UNSUPPORTED_DEVICE'] }, action: 'ABORT_UNSUPPORTED_DEVICE' };
  if (!calibrationAvailable) return { session: { ...session, state: SessionState.ABORTED, qualityFlags: [...session.qualityFlags, 'NO_VALID_CALIBRATION'] }, action: 'ABORT_NO_CALIBRATION' };
  if (!sensorAvailable) return { session: { ...session, state: SessionState.PAUSED_NOISE, qualityFlags: [...session.qualityFlags, 'NO_TRUSTED_NOISE_SENSOR'] }, action: 'PAUSE_SENSOR_UNAVAILABLE' };
  if (noiseStatus !== 'VALID') return { session: { ...session, state: SessionState.PAUSED_NOISE }, action: 'PAUSE_NOISE' };
  return { session: { ...session, state: SessionState.READY }, action: 'READY' };
}

/**
 * Start one simulated trial. A production implementation must route calibrated audio
 * through a validated audio backend; this function only advances the domain state.
 */
export function beginTrial(session) {
  if (![SessionState.READY, SessionState.RETRY, SessionState.EVALUATE].includes(session.state)) throw new Error(`Cannot begin trial from ${session.state}`);
  return { ...session, state: SessionState.PRESENT, trialCount: session.trialCount + 1 };
}

/**
 * Noise gate is deliberately conservative: SUSPECT and INVALID both suspend measurement.
 * A trial overlapped by disturbance is contaminated, never treated as a no-response.
 */
export function applyNoiseAssessment(session, assessment, { overlapsStimulus = false } = {}) {
  if (assessment === 'VALID') return { session, action: 'CONTINUE' };
  const contaminatedTrials = overlapsStimulus
    ? [...session.contaminatedTrials, { ear: currentTarget(session).ear, frequencyHz: currentTarget(session).frequencyHz, levelDb: session.currentLevelDb, reason: assessment, at: new Date().toISOString() }]
    : session.contaminatedTrials;
  return {
    session: { ...session, state: SessionState.PAUSED_NOISE, noisePauseCount: session.noisePauseCount + 1, contaminatedTrials },
    action: 'PAUSE_AND_RECHECK'
  };
}

export function resumeAfterNoise(session, { assessment, stableForRecoveryWindow }) {
  if (session.state !== SessionState.PAUSED_NOISE) throw new Error('Session is not paused for noise');
  if (assessment !== 'VALID' || !stableForRecoveryWindow) return { ...session, state: SessionState.PAUSED_NOISE };
  return { ...session, state: session.contaminatedTrials.length ? SessionState.RETRY : SessionState.READY };
}

/**
 * Simplified modified Hughson–Westlake-style staircase for POC logic tests only.
 * Clinical protocol details, masking, response checks, calibration and acceptance rules
 * must be reviewed by an audiologist before production use.
 */
export function recordResponse(session, heard, { trialValid = true } = {}) {
  if (session.state !== SessionState.PRESENT && session.state !== SessionState.RESPONSE) throw new Error(`Cannot record response from ${session.state}`);
  if (!trialValid) return { ...session, state: SessionState.RETRY, qualityFlags: [...session.qualityFlags, 'INVALID_TRIAL_NOT_USED'] };
  const target = currentTarget(session);
  const response = { ...target, levelDb: session.currentLevelDb, heard: Boolean(heard), valid: true, at: new Date().toISOString() };
  const history = [...session.responseHistory, response];
  let level = session.currentLevelDb;
  let validAscendingResponses = session.validAscendingResponses;
  let heardAtLevels = [...session.heardAtLevels];
  if (heard) {
    heardAtLevels.push(level);
    validAscendingResponses += 1;
    level = Math.max(session.minLevelDb, level - session.downStepDb);
  } else {
    level = Math.min(session.maxLevelDb, level + session.upStepDb);
    validAscendingResponses = 0;
  }
  return { ...session, state: SessionState.EVALUATE, currentLevelDb: level, validAscendingResponses, heardAtLevels, responseHistory: history };
}

export function acceptThreshold(session, { minimumAscendingResponses = 2 } = {}) {
  if (session.state !== SessionState.EVALUATE) throw new Error(`Cannot evaluate threshold from ${session.state}`);
  if (session.validAscendingResponses < minimumAscendingResponses || session.heardAtLevels.length === 0) {
    return { ...session, state: SessionState.READY };
  }
  const target = currentTarget(session);
  const estimateDb = Math.min(...session.heardAtLevels.slice(-minimumAscendingResponses));
  const result = { ...target, thresholdEstimateDb: estimateDb, method: 'HW_POC_SIMULATION', confidence: 'UNVALIDATED', acceptedAt: new Date().toISOString() };
  const results = [...session.results, result];
  const nextFrequencyIndex = session.frequencyIndex + 1;
  let next = { ...session, results, responseHistory: [], heardAtLevels: [], validAscendingResponses: 0, currentLevelDb: session.startingLevelDb };
  if (nextFrequencyIndex < session.frequenciesHz.length) return { ...next, frequencyIndex: nextFrequencyIndex, state: SessionState.READY };
  const nextEarIndex = session.earIndex + 1;
  if (nextEarIndex < session.ears.length) return { ...next, earIndex: nextEarIndex, frequencyIndex: 0, state: SessionState.READY };
  return { ...next, state: SessionState.COMPLETE };
}

export function sessionSummary(session) {
  return {
    state: session.state,
    completedTargets: session.results.length,
    plannedTargets: session.ears.length * session.frequenciesHz.length,
    pauseCount: session.noisePauseCount + session.devicePauseCount,
    contaminatedTrials: session.contaminatedTrials.length,
    qualityFlags: [...new Set(session.qualityFlags)],
    results: session.results
  };
}
