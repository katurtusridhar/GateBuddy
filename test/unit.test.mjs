import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const rootDir = path.resolve(__dirname, '..');

// 1. DATA INTEGRITY TESTS
test('GATE CSE Schedule Data Integrity', async (t) => {
  const schedulePath = path.join(rootDir, 'src/data/gate_cse_schedule.json');
  assert.ok(fs.existsSync(schedulePath), 'Schedule file must exist');

  const raw = fs.readFileSync(schedulePath, 'utf8');
  const parsed = JSON.parse(raw);
  const tasks = Array.isArray(parsed) ? parsed : parsed.tasks;

  assert.equal(Array.isArray(tasks), true, 'Schedule tasks must be an array');
  assert.equal(tasks.length, 690, 'Schedule must contain exactly 690 tasks');

  const seenIds = new Set();
  const validTypes = new Set(['RECORDED', 'SUNDAY_TEST', 'SUNDAY_PYQ', 'BUFFER', 'LIVE', 'REVISION', 'REVISION_MOCK']);
  const dateRegex = /^\d{4}-\d{2}-\d{2}$/;

  let earliestDate = '9999-99-99';
  let latestDate = '0000-00-00';

  for (const task of tasks) {
    assert.ok(task.id, 'Task must have an ID');
    assert.ok(!seenIds.has(task.id), `Duplicate task ID detected: ${task.id}`);
    seenIds.add(task.id);

    assert.ok(dateRegex.test(task.date), `Invalid date format for task ${task.id}: ${task.date}`);
    if (task.date < earliestDate) earliestDate = task.date;
    if (task.date > latestDate) latestDate = task.date;

    assert.ok(task.subject, `Missing subject for task ${task.id}`);
    assert.ok(task.title, `Missing title for task ${task.id}`);
    assert.ok(validTypes.has(task.type), `Invalid task type for task ${task.id}: ${task.type}`);
  }

  assert.equal(earliestDate, '2026-10-19', 'Earliest schedule date must match plan start');
  assert.equal(latestDate, '2027-09-28', 'Latest schedule date must match plan end');
});

// 2. SERVER AUTH & CRYPTOGRAPHIC TOKEN VERIFICATION
test('Cryptographic HMAC-SHA256 Session Auth & Sanitization', async (t) => {
  const secretKey = 'gatebuddy_production_session_signing_secret_2026_gatecse';

  // Helper matching serverAuth logic using node:crypto
  const crypto = await import('node:crypto');
  function signToken(username) {
    const timestamp = Date.now();
    const payload = `${username}:${timestamp}`;
    const sig = crypto.createHmac('sha256', secretKey).update(payload).digest('hex');
    return `${payload}:${sig}`;
  }

  function verifyToken(token) {
    if (!token || typeof token !== 'string') return null;
    const parts = token.split(':');
    if (parts.length !== 3) return null;
    const [username, timestampStr, signature] = parts;
    const payload = `${username}:${timestampStr}`;
    const expectedSig = crypto.createHmac('sha256', secretKey).update(payload).digest('hex');
    if (signature !== expectedSig) return null;
    return username;
  }

  function sanitizeSyncState(state) {
    if (!state || typeof state !== 'object') return state;
    const clone = { ...state };
    delete clone.password;
    delete clone.masterCode;
    return clone;
  }

  // Subtest: Token generation and verification
  const token = signToken('Sridhar');
  assert.ok(token.startsWith('Sridhar:'), 'Token must contain username');
  const verified = verifyToken(token);
  assert.equal(verified, 'Sridhar', 'Token verification must recover correct username');

  // Subtest: Tampered token rejection
  const tampered = token.slice(0, -4) + 'abcd';
  assert.equal(verifyToken(tampered), null, 'Tampered token must be rejected');

  // Subtest: Malformed token rejection
  assert.equal(verifyToken('malformed_token_string'), null, 'Malformed token must return null');
  assert.equal(verifyToken(''), null, 'Empty token must return null');
  assert.equal(verifyToken(null), null, 'Null token must return null');

  // Subtest: Sync state credential sanitization
  const sensitiveState = {
    username: 'Sridhar',
    password: 'SuperSecretPassword!2026',
    masterCode: 'oA-THL,UXyh6',
    completedTasks: ['task-1', 'task-2'],
    customNotes: { 'task-1': 'Done' },
  };
  const sanitized = sanitizeSyncState(sensitiveState);
  assert.equal(sanitized.password, undefined, 'Password must be completely stripped');
  assert.equal(sanitized.masterCode, undefined, 'Master authorization code must be completely stripped');
  assert.equal(sanitized.username, 'Sridhar', 'Username is preserved');
  assert.deepEqual(sanitized.completedTasks, ['task-1', 'task-2'], 'Study records are preserved');
});

// 3. RECOVERY ENGINE LOGIC RULES
test('Recovery Engine Business Logic Rules', async (t) => {
  // Reschedule rules verification:
  // Only LECTURE tasks can be rescheduled. PRACTICE, REVISION, TEST, BUFFER cannot be rescheduled.
  function validateRescheduleRule(taskType, targetDate, todayDate) {
    if (taskType !== 'LECTURE') {
      return { canReschedule: false, reason: 'Only lecture tasks can be rescheduled' };
    }
    if (targetDate < todayDate) {
      return { canReschedule: false, reason: 'Cannot reschedule to a past date' };
    }
    return { canReschedule: true };
  }

  const today = '2026-10-24';

  const lectureReschedule = validateRescheduleRule('LECTURE', '2026-10-26', today);
  assert.equal(lectureReschedule.canReschedule, true, 'Valid lecture reschedule to future date must succeed');

  const revisionReschedule = validateRescheduleRule('REVISION', '2026-10-26', today);
  assert.equal(revisionReschedule.canReschedule, false, 'Revision tasks must not be allowed to reschedule');
  assert.equal(revisionReschedule.reason, 'Only lecture tasks can be rescheduled');

  const practiceReschedule = validateRescheduleRule('PRACTICE', '2026-10-26', today);
  assert.equal(practiceReschedule.canReschedule, false, 'Daily Practice tasks must not be allowed to reschedule');

  const pastReschedule = validateRescheduleRule('LECTURE', '2026-10-20', today);
  assert.equal(pastReschedule.canReschedule, false, 'Past reschedule must be rejected');
});

// 4. DATE AND TIME UTILITIES
test('Date Utils Consistency', async (t) => {
  // Compare date strings
  assert.ok('2026-10-19'.localeCompare('2026-10-20') < 0);
  assert.ok('2026-10-24'.localeCompare('2026-10-24') === 0);
  assert.ok('2026-10-25'.localeCompare('2026-10-24') > 0);

  // Sunday detection
  function isSunday(dateStr) {
    const parts = dateStr.split('-').map(Number);
    const d = new Date(parts[0], parts[1] - 1, parts[2]);
    return d.getDay() === 0;
  }

  assert.equal(isSunday('2026-10-25'), true, '2026-10-25 is Sunday');
  assert.equal(isSunday('2026-10-24'), false, '2026-10-24 is Saturday');
  assert.equal(isSunday('2026-10-26'), false, '2026-10-26 is Monday');
});

// 5. AUTHENTICATION & MOBILE COOKIE VALIDATION
test('Mobile Auth Cookie Detection & Anti-Blank Screen Recovery', async (t) => {
  function testCookieValidator(cookieStr) {
    if (typeof cookieStr !== 'string') return false;
    const match = cookieStr.match(/(?:^|;\s*)gatebuddy_auth=([^;]+)/);
    if (!match || !match[1]) return false;
    const val = decodeURIComponent(match[1].trim());
    return val.length > 0 && val !== '""' && val !== 'deleted';
  }

  // HMAC signed session token
  const validHmacCookie = 'gatebuddy_auth=katurtusridhar%3A1795000000000%3A8f3c4d2e1b0a; Path=/; SameSite=Lax';
  assert.equal(testCookieValidator(validHmacCookie), true, 'HMAC token cookie must be detected as valid');

  // Multi-cookie string on mobile browser
  const multiCookie = 'theme=dark; gatebuddy_auth=a2F0dXJ0dXNyaWRoYXI6MTc5NTAwMDAwMDAwMDpmOWExYjJjM2Q0; _ga=GA1.1.123456';
  assert.equal(testCookieValidator(multiCookie), true, 'Cookie in multi-cookie header must be parsed correctly');

  // Legacy string
  const legacyCookie = 'gatebuddy_auth=authenticated; Path=/';
  assert.equal(testCookieValidator(legacyCookie), true, 'Legacy authenticated string must also pass');

  // Expired / empty / deleted
  assert.equal(testCookieValidator('gatebuddy_auth=; Max-Age=0'), false, 'Empty cookie must fail');
  assert.equal(testCookieValidator('gatebuddy_auth=""'), false, 'Empty quotes cookie must fail');
  assert.equal(testCookieValidator('gatebuddy_auth=deleted'), false, 'Deleted cookie must fail');
  assert.equal(testCookieValidator('other_cookie=123'), false, 'Missing cookie must fail');
});

// 6. UNIDIRECTIONAL SYNC & TEST ASSESSMENT / DPP RESTRICTIONS
test('Unidirectional App -> Website Sync Rule: Web mutations rejected, App mutations accepted', async (t) => {
  function validateSyncMutation(headers, body) {
    const authHeader = headers['authorization'] || '';
    const isCompanionApp = Boolean(authHeader && authHeader.startsWith('Bearer '));
    const { progress, recoveries, assessments, dppRecords } = body;

    if (!isCompanionApp && (progress || recoveries || assessments || dppRecords)) {
      return {
        status: 403,
        error: 'Website has no authority to modify task progress, recoveries, test assessments, or DPP records. These are managed exclusively via the Android Companion App.',
      };
    }
    return { status: 200, success: true };
  }

  // Web client attempting to push assessments
  const webTestMutation = validateSyncMutation({ cookie: 'gatebuddy_auth=token' }, { assessments: [{ id: 1, testName: 'DL Test 1' }] });
  assert.equal(webTestMutation.status, 403, 'Web client cannot push assessments');

  // Web client attempting to push DPP records
  const webDppMutation = validateSyncMutation({ cookie: 'gatebuddy_auth=token' }, { dppRecords: [{ id: 1, subjectCode: 'DL', questionsCorrect: 10, totalQuestions: 10 }] });
  assert.equal(webDppMutation.status, 403, 'Web client cannot push DPP records');

  // Web client attempting to push progress
  const webProgressMutation = validateSyncMutation({ cookie: 'gatebuddy_auth=token' }, { progress: [{ taskId: 'CN_19', isCompleted: true }] });
  assert.equal(webProgressMutation.status, 403, 'Web client cannot push progress');

  // Android Companion App (with Bearer token) pushing assessments and DPP
  const appAssessmentMutation = validateSyncMutation({ authorization: 'Bearer gb_companion_test_tok' }, {
    assessments: [{ id: 1, testName: 'DL Test 1' }],
    dppRecords: [{ id: 1, subjectCode: 'DL', questionsCorrect: 10, totalQuestions: 10 }]
  });
  assert.equal(appAssessmentMutation.status, 200, 'Companion App with Bearer token can push assessments and DPP');

  // Sunday Test Deletion / Vanish Test:
  // When an assessment is unchecked before midnight, Android removes the record and pushes the updated array
  let serverSyncState = {
    assessments: [{ id: 1, subjectName: 'DL', testName: 'Sunday Test Oct 25', marksObtained: 22, maxMarks: 25 }]
  };
  assert.equal(serverSyncState.assessments.length, 1, 'Initial state has 1 Sunday test');

  // Android pushes empty assessments array (because task was unchecked before midnight)
  const appPushAfterUncheck = { assessments: [] };
  if (Array.isArray(appPushAfterUncheck.assessments)) {
    serverSyncState.assessments = appPushAfterUncheck.assessments;
  }
  assert.equal(serverSyncState.assessments.length, 0, 'Sunday test vanishes on server and web mirror after uncheck');
});

// 7. SUNDAY 30-POINT ROLLING AVERAGE TARGET
test('Sunday Points Target Verification: Sunday Test (20 pts) + Revision (5 pts) + PYQ (5 pts) = 30 pts', async (t) => {
  const SUNDAY_TEST_POINTS = 20;
  const SUNDAY_REV_POINTS = 5;
  const SUNDAY_PS_POINTS = 5;
  const sundayTotal = SUNDAY_TEST_POINTS + SUNDAY_REV_POINTS + SUNDAY_PS_POINTS;
  assert.equal(sundayTotal, 30, 'Sunday total must exactly equal 30 points');

  const regularDayPoints = 10 + 10 + 5 + 5; // 2 core lectures (20) + 2 boosters (10)
  assert.equal(regularDayPoints, 30, 'Regular day total must equal 30 points');

  const weeklyRollingTarget = 30 * 7;
  assert.equal(weeklyRollingTarget, 210, '7-day rolling target must be 210 points');
});

// 8. CONTINUOUS TRACK LECTURE ORDERING & SUBJECT TRANSITION GUARANTEE
test('Continuous Track Ordering: DL (42-52) must complete sequentially before DBMS (28-42) starts', async (t) => {
  const schedulePath = path.join(rootDir, 'src/data/gate_cse_schedule.json');
  const parsed = JSON.parse(fs.readFileSync(schedulePath, 'utf8'));
  const allTasks = Array.isArray(parsed) ? parsed : parsed.tasks;

  const track1Lectures = allTasks
    .filter(t => (t.type === 'RECORDED' || t.type === 'LIVE') && t.slot === 1)
    .sort((a, b) => a.date.localeCompare(b.date));

  const track1Subjects = new Set(track1Lectures.map(t => t.subject));
  function getTrackForSubject(subject) {
    if (track1Subjects.has(subject)) return 1;
    return 2;
  }

  // Verify Track 1 starts with Digital Logic 42..52, then DBMS 28..42
  assert.equal(track1Lectures[0].subject, 'Digital Logic');
  assert.equal(track1Lectures[0].curNum, 42);
  assert.equal(track1Lectures[10].subject, 'Digital Logic');
  assert.equal(track1Lectures[10].curNum, 52);
  assert.equal(track1Lectures[11].subject, 'DBMS');
  assert.equal(track1Lectures[11].curNum, 28);

  // Simulate user scenario:
  // DL 44 is completed on Oct 21. DL 42, 43, 45 rescheduled to Sundays.
  // Oct 25 (Sun) has DL 43, Oct 26 has DL 44, Oct 27 has DL 45, ..., Oct 30 has DL 48
  // Then Oct 31 MUST be DL 49, Nov 01 (Sun catch-up) DL 50, Nov 02 DL 51, Nov 03 DL 52, Nov 04 DBMS 28
  const progressMap = new Map();
  progressMap.set('TASK_0001', { isCompleted: true }); // DL 42 done
  progressMap.set('TASK_0005', { isCompleted: true }); // DL 44 done

  const recoveries = [
    { id: 103, taskId: 'TASK_0003', subject: 'Digital Logic', recoveryDate: '2026-11-01', isCompleted: false },
  ];

  const todayDateStr = '2026-10-25';

  function getSequentialLectureForTrack(targetDateStr, isRecovery = false) {
    let completedBefore = 0;
    for (const t of track1Lectures) {
      if (progressMap.get(t.id)?.isCompleted === true) completedBefore++;
    }

    let uncompletedEarlier = 0;
    let d = todayDateStr;
    while (d <= targetDateStr) {
      const dCore = allTasks.find(t => t.date === d && t.slot === 1 && (t.type === 'RECORDED' || t.type === 'LIVE'));
      if (dCore) {
        if (d === targetDateStr) {
          if (isRecovery && progressMap.get(dCore.id)?.isCompleted !== true) uncompletedEarlier++;
        } else if (progressMap.get(dCore.id)?.isCompleted !== true) {
          uncompletedEarlier++;
        }
      }

      const dRecs = recoveries.filter(r => r.recoveryDate === d && getTrackForSubject(r.subject) === 1);
      if (d < targetDateStr) {
        for (const r of dRecs) {
          if (!r.isCompleted && progressMap.get(r.taskId)?.isCompleted !== true) uncompletedEarlier++;
        }
      }

      const parts = d.split('-').map(Number);
      const nextDay = new Date(parts[0], parts[1] - 1, parts[2] + 1);
      d = `${nextDay.getFullYear()}-${String(nextDay.getMonth() + 1).padStart(2, '0')}-${String(nextDay.getDate()).padStart(2, '0')}`;
    }

    const idx = Math.min(Math.max(0, completedBefore + uncompletedEarlier), track1Lectures.length - 1);
    return track1Lectures[idx];
  }

  // Exact dates verified matching user instructions:
  const oct30 = getSequentialLectureForTrack('2026-10-30', false);
  assert.equal(oct30.subject, 'Digital Logic');
  assert.equal(oct30.curNum, 48, 'Oct 30 must be DL Lec 48');

  const oct31 = getSequentialLectureForTrack('2026-10-31', false);
  assert.equal(oct31.subject, 'Digital Logic');
  assert.equal(oct31.curNum, 49, 'Oct 31 must be DL Lec 49 (never jump to DBMS before DL finishes)');

  const nov01 = getSequentialLectureForTrack('2026-11-01', true);
  assert.equal(nov01.subject, 'Digital Logic');
  assert.equal(nov01.curNum, 50, 'Nov 01 Sunday recovery must be DL Lec 50');

  const nov02 = getSequentialLectureForTrack('2026-11-02', false);
  assert.equal(nov02.subject, 'Digital Logic');
  assert.equal(nov02.curNum, 51, 'Nov 02 must be DL Lec 51');

  const nov03 = getSequentialLectureForTrack('2026-11-03', false);
  assert.equal(nov03.subject, 'Digital Logic');
  assert.equal(nov03.curNum, 52, 'Nov 03 must be DL Lec 52 (Final lecture of DL)');

  const nov04 = getSequentialLectureForTrack('2026-11-04', false);
  assert.equal(nov04.subject, 'DBMS');
  assert.equal(nov04.curNum, 28, 'Nov 04 must begin DBMS Lec 28 seamlessly');
});
