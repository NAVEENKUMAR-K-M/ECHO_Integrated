import { createSession, precheck, beginTrial, applyNoiseAssessment, resumeAfterNoise, recordResponse, acceptThreshold, currentTarget, sessionSummary, SessionState } from './core.js';

let session = createSession();
let simulatedNoise = 'VALID';
let deviceSupported = true;
let calibrationAvailable = true;
const $ = (id) => document.getElementById(id);
const logEl = $('log');
function log(message) { const li = document.createElement('li'); li.textContent = `${new Date().toLocaleTimeString()} — ${message}`; logEl.prepend(li); }
function render() {
  $('state').textContent = session.state;
  $('target').textContent = session.frequencyIndex < session.frequenciesHz.length ? `${currentTarget(session).ear.toUpperCase()} ear · ${currentTarget(session).frequencyHz} Hz · ${session.currentLevelDb} simulated dB step` : 'No active target';
  $('counts').textContent = `${session.results.length}/${session.ears.length * session.frequenciesHz.length} targets recorded`;
  $('pauseCount').textContent = String(session.noisePauseCount);
  $('result').textContent = JSON.stringify(sessionSummary(session), null, 2);
  $('begin').disabled = ![SessionState.READY, SessionState.RETRY].includes(session.state);
  $('heard').disabled = session.state !== SessionState.PRESENT;
  $('notHeard').disabled = session.state !== SessionState.PRESENT;
  $('accept').disabled = session.state !== SessionState.EVALUATE;
  $('resume').disabled = session.state !== SessionState.PAUSED_NOISE;
}
$('precheck').addEventListener('click', () => {
  const out = precheck(session, { deviceSupported, calibrationAvailable, noiseStatus: simulatedNoise, sensorAvailable: $('sensorAvailable').checked });
  session = out.session; log(`Precheck: ${out.action}`); render();
});
$('begin').addEventListener('click', () => { session = beginTrial(session); log('Simulated tone trial started'); render(); });
function respond(heard) { session = recordResponse(session, heard); log(`Response recorded: ${heard ? 'heard' : 'not heard'} at simulated level`); render(); }
$('heard').addEventListener('click', () => respond(true));
$('notHeard').addEventListener('click', () => respond(false));
$('accept').addEventListener('click', () => { session = acceptThreshold(session); log('Threshold estimate accepted by demo rules (not clinical)'); render(); });
$('injectNoise').addEventListener('click', () => {
  const out = applyNoiseAssessment(session, 'INVALID', { overlapsStimulus: session.state === SessionState.PRESENT }); session = out.session; simulatedNoise = 'INVALID'; log('Injected disturbance: measurement paused; overlapping trial marked contaminated'); render();
});
$('quiet').addEventListener('click', () => { simulatedNoise = 'VALID'; log('Simulated environment now passes the noise gate'); render(); });
$('resume').addEventListener('click', () => { session = resumeAfterNoise(session, { assessment: simulatedNoise, stableForRecoveryWindow: $('stable').checked }); log(session.state === SessionState.PAUSED_NOISE ? 'Resume denied: noise gate not stable' : `Resumed into ${session.state}`); render(); });
$('restart').addEventListener('click', () => { session = createSession(); simulatedNoise = 'VALID'; log('New simulation session created'); render(); });
$('deviceSupported').addEventListener('change', (e) => { deviceSupported = e.target.checked; });
$('calibrationAvailable').addEventListener('change', (e) => { calibrationAvailable = e.target.checked; });
render();
