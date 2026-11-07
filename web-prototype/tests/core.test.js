import test from 'node:test';
import assert from 'node:assert/strict';
import { createSession, precheck, beginTrial, applyNoiseAssessment, resumeAfterNoise, recordResponse, acceptThreshold, SessionState } from '../src/core.js';

test('precheck blocks unsupported device and missing calibration',()=>{
 let s=createSession(); let r=precheck(s,{deviceSupported:false,calibrationAvailable:true,noiseStatus:'VALID'}); assert.equal(r.session.state,SessionState.ABORTED); assert.ok(r.session.qualityFlags.includes('UNSUPPORTED_DEVICE'));
 r=precheck(s,{deviceSupported:true,calibrationAvailable:false,noiseStatus:'VALID'}); assert.equal(r.session.state,SessionState.ABORTED); assert.ok(r.session.qualityFlags.includes('NO_VALID_CALIBRATION'));
});
test('precheck pauses if noise is not valid or trusted sensor is unavailable',()=>{
 const s=createSession(); assert.equal(precheck(s,{deviceSupported:true,calibrationAvailable:true,noiseStatus:'INVALID'}).session.state,SessionState.PAUSED_NOISE);
 assert.equal(precheck(s,{deviceSupported:true,calibrationAvailable:true,noiseStatus:'VALID',sensorAvailable:false}).session.state,SessionState.PAUSED_NOISE);
});
test('disturbance overlapping stimulus pauses and marks trial contaminated',()=>{
 let s=createSession(); s=precheck(s,{deviceSupported:true,calibrationAvailable:true,noiseStatus:'VALID'}).session; s=beginTrial(s);
 const r=applyNoiseAssessment(s,'INVALID',{overlapsStimulus:true}); assert.equal(r.session.state,SessionState.PAUSED_NOISE); assert.equal(r.session.contaminatedTrials.length,1); assert.equal(r.session.noisePauseCount,1);
});
test('cannot resume until environment is valid and stable',()=>{
 let s=createSession(); s=precheck(s,{deviceSupported:true,calibrationAvailable:true,noiseStatus:'VALID'}).session; s=applyNoiseAssessment(s,'SUSPECT').session;
 s=resumeAfterNoise(s,{assessment:'INVALID',stableForRecoveryWindow:true}); assert.equal(s.state,SessionState.PAUSED_NOISE);
 s=resumeAfterNoise(s,{assessment:'VALID',stableForRecoveryWindow:false}); assert.equal(s.state,SessionState.PAUSED_NOISE);
 s=resumeAfterNoise(s,{assessment:'VALID',stableForRecoveryWindow:true}); assert.equal(s.state,SessionState.READY);
});
test('invalid trial is not interpreted as a no-response',()=>{
 let s=createSession(); s=precheck(s,{deviceSupported:true,calibrationAvailable:true,noiseStatus:'VALID'}).session; s=beginTrial(s);
 s=recordResponse(s,false,{trialValid:false}); assert.equal(s.state,SessionState.RETRY); assert.equal(s.responseHistory.length,0); assert.ok(s.qualityFlags.includes('INVALID_TRIAL_NOT_USED'));
});
test('valid responses move the simulated staircase down 10 dB when heard and up 5 dB when not heard',()=>{
 let s=createSession(); s=precheck(s,{deviceSupported:true,calibrationAvailable:true,noiseStatus:'VALID'}).session; s=beginTrial(s); s=recordResponse(s,true); assert.equal(s.currentLevelDb,30);
 s.state=SessionState.READY; s=beginTrial(s); s=recordResponse(s,false); assert.equal(s.currentLevelDb,35);
});
test('threshold evaluation cannot happen before response evaluation state',()=>{const s=createSession();assert.throws(()=>acceptThreshold(s),/Cannot evaluate threshold/)});
test('simulated output is explicitly marked unvalidated',()=>{
 let s=createSession({frequenciesHz:[1000],ears:['left']});s=precheck(s,{deviceSupported:true,calibrationAvailable:true,noiseStatus:'VALID'}).session;
 s=beginTrial(s);s=recordResponse(s,true);s=acceptThreshold(s,{minimumAscendingResponses:1});assert.equal(s.results.length,1);assert.equal(s.results[0].confidence,'UNVALIDATED');
});
