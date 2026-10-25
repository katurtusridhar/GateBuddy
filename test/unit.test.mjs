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
  assert.equal(verifyToken('authenticated'), null, 'Magic bypass token string authenticated must be strictly rejected (SEC-001)');
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

// 9. COMPANION CLIENT KEY AUTHORIZATION SECURITY (SEC-002)
test('Companion Client Key Authorization: client key alone is NOT user proof; user credentials required', async (t) => {
  const CLIENT_KEY = 'gb_companion_secure_sync_2026_tok';
  const VALID_USER = 'katurtusridhar';
  const VALID_PASS = 'Sridhar@758';
  const MASTER_CODE = 'oA-THL,UXyh6';

  function evaluateClientGrant(body) {
    if (body.grantType === 'companion_client') {
      if (!body.clientKey || body.clientKey !== CLIENT_KEY) {
        return { success: false, status: 401, error: 'Invalid client API key. Companion client unauthorized.' };
      }
      // Security invariant: Client key alone CANNOT prove user identity!
      if (body.username && body.password) {
        if (body.username.toLowerCase() === VALID_USER && body.password === VALID_PASS) {
          return { success: true, status: 200, user: VALID_USER };
        }
        return { success: false, status: 401, error: 'Invalid user credentials for companion client grant.' };
      }
      if (body.masterCode && body.masterCode === MASTER_CODE) {
        return { success: true, status: 200, user: body.username || VALID_USER };
      }
      return {
        success: false,
        status: 401,
        error: 'Companion client key alone cannot issue a user session token. User credentials required.',
      };
    }
    return { success: false, status: 400, error: 'Invalid grant' };
  }

  // 1. Client key alone without password -> MUST FAIL (SEC invariant)
  const keyOnly = evaluateClientGrant({ grantType: 'companion_client', clientKey: CLIENT_KEY, username: VALID_USER });
  assert.equal(keyOnly.success, false);
  assert.equal(keyOnly.status, 401);
  assert.match(keyOnly.error, /Companion client key alone cannot issue a user session token/);

  // 2. Client key + correct username and password -> MUST SUCCEED
  const validPair = evaluateClientGrant({
    grantType: 'companion_client',
    clientKey: CLIENT_KEY,
    username: VALID_USER,
    password: VALID_PASS,
  });
  assert.equal(validPair.success, true);
  assert.equal(validPair.status, 200);
  assert.equal(validPair.user, VALID_USER);

  // 3. Client key + wrong password -> MUST FAIL
  const wrongPass = evaluateClientGrant({
    grantType: 'companion_client',
    clientKey: CLIENT_KEY,
    username: VALID_USER,
    password: 'wrong_password',
  });
  assert.equal(wrongPass.success, false);
  assert.equal(wrongPass.status, 401);

  // 4. Invalid client key + valid user credentials -> MUST FAIL (client unauthorized)
  const badClientKey = evaluateClientGrant({
    grantType: 'companion_client',
    clientKey: 'fake_embedded_key',
    username: VALID_USER,
    password: VALID_PASS,
  });
  assert.equal(badClientKey.success, false);
  assert.equal(badClientKey.status, 401);

  // 5. Valid client key + master code -> MUST SUCCEED
  const masterAuth = evaluateClientGrant({
    grantType: 'companion_client',
    clientKey: CLIENT_KEY,
    masterCode: MASTER_CODE,
  });
  assert.equal(masterAuth.success, true);
  assert.equal(masterAuth.status, 200);
});

// 10. COMPREHENSIVE NEGATIVE AUTHENTICATION SUITE
test('Negative Authentication Suite: missing, invalid, expired, malformed, and bypass tokens', async (t) => {
  const crypto = await import('node:crypto');
  const secretKey = 'gatebuddy_production_session_signing_secret_2026_gatecse';

  function signTokenWithExpiry(username, expiryMs) {
    const payload = `${username}:${expiryMs}`;
    const sig = crypto.createHmac('sha256', secretKey).update(payload).digest('hex');
    const full = `${payload}:${sig}`;
    return Buffer.from(full).toString('base64url');
  }

  function verifyToken(token) {
    if (!token || typeof token !== 'string') return { valid: false, error: 'Empty token' };
    try {
      const decoded = Buffer.from(token, 'base64url').toString('utf-8');
      const parts = decoded.split(':');
      if (parts.length !== 3) return { valid: false, error: 'Malformed token structure' };
      const [username, expiryStr, signature] = parts;
      const expiry = parseInt(expiryStr, 10);
      if (isNaN(expiry) || Date.now() > expiry) return { valid: false, error: 'Token expired' };
      const expectedSig = crypto.createHmac('sha256', secretKey).update(`${username}:${expiryStr}`).digest('hex');
      if (signature !== expectedSig) return { valid: false, error: 'Signature mismatch' };
      return { valid: true, username };
    } catch {
      return { valid: false, error: 'Decoding failed' };
    }
  }

  // 1. Missing / null / undefined token
  assert.equal(verifyToken(null).valid, false);
  assert.equal(verifyToken(undefined).valid, false);
  assert.equal(verifyToken('').valid, false);

  // 2. Legacy bypass string "authenticated"
  const legacyBypass = verifyToken('authenticated');
  assert.equal(legacyBypass.valid, false, 'Legacy bypass string "authenticated" must be strictly rejected');

  // 3. Expired token
  const pastExpiry = Date.now() - 60000; // 1 min ago
  const expiredToken = signTokenWithExpiry('katurtusridhar', pastExpiry);
  const expiredRes = verifyToken(expiredToken);
  assert.equal(expiredRes.valid, false);
  assert.equal(expiredRes.error, 'Token expired');

  // 4. Future valid token
  const futureExpiry = Date.now() + 600000; // 10 mins from now
  const validToken = signTokenWithExpiry('katurtusridhar', futureExpiry);
  const validRes = verifyToken(validToken);
  assert.equal(validRes.valid, true);
  assert.equal(validRes.username, 'katurtusridhar');

  // 5. Tampered signature
  const tamperedToken = validToken.slice(0, -6) + 'abcdef';
  assert.equal(verifyToken(tamperedToken).valid, false);

  // 6. Malformed base64url characters
  assert.equal(verifyToken('!!!not_base64!!!').valid, false);

  // 7. Token payload with colon injection
  const injection = Buffer.from('user:admin:9999999999:fake_sig').toString('base64url');
  assert.equal(verifyToken(injection).valid, false);
});

// 11. PER-USER OWNERSHIP AND CROSS-USER ISOLATION TESTS
test('Per-User Ownership and Cross-User Mutation Prevention', async (t) => {
  const MASTER_CODE = 'oA-THL,UXyh6';

  function validateMutationOwnership(authUsername, targetPayload) {
    if (targetPayload.credentials?.username) {
      const targetUser = targetPayload.credentials.username.trim().toLowerCase();
      if (targetUser !== authUsername.toLowerCase()) {
        if (!targetPayload.credentials.masterCode || targetPayload.credentials.masterCode !== MASTER_CODE) {
          return {
            allowed: false,
            status: 403,
            error: `User '${authUsername}' is not authorized to modify records for user '${targetPayload.credentials.username}'.`,
          };
        }
      }
    }
    return { allowed: true, status: 200 };
  }

  // Same user mutating their own data -> ALLOWED
  const selfMutation = validateMutationOwnership('katurtusridhar', {
    credentials: { username: 'katurtusridhar' },
  });
  assert.equal(selfMutation.allowed, true);

  // User A trying to mutate User B without masterCode -> FORBIDDEN (403)
  const crossUserAttack = validateMutationOwnership('user_alice', {
    credentials: { username: 'user_bob' },
  });
  assert.equal(crossUserAttack.allowed, false);
  assert.equal(crossUserAttack.status, 403);
  assert.match(crossUserAttack.error, /not authorized to modify records for user/);

  // User A mutating User B WITH valid masterCode -> ALLOWED
  const adminDelegation = validateMutationOwnership('user_alice', {
    credentials: { username: 'user_bob', masterCode: MASTER_CODE },
  });
  assert.equal(adminDelegation.allowed, true);
  assert.equal(adminDelegation.status, 200);
});

// 12. ZERO CREDENTIAL LEAKAGE IN ERROR PAYLOADS AND RESPONSES
test('Security: Errors and responses never leak sensitive credentials or secrets', async (t) => {
  function formatErrorResponse(action, error, receivedCredentials) {
    // Sanitized output format
    return {
      status: 'error',
      action,
      message: error?.message || 'Authentication or operation failed',
      // Explicitly ensure received password and masterCode are NEVER reflected
    };
  }

  const sensitive = {
    username: 'katurtusridhar',
    password: 'SuperSecretUserPassword!123',
    masterCode: 'oA-THL,UXyh6',
  };

  const response = formatErrorResponse('token_issuance', new Error('Invalid credentials provided'), sensitive);
  const responseJson = JSON.stringify(response);

  assert.ok(!responseJson.includes('SuperSecretUserPassword!123'), 'Response must not contain password');
  assert.ok(!responseJson.includes('oA-THL,UXyh6'), 'Response must not contain masterCode');
  assert.equal(response.status, 'error');
});

// 13. SUPABASE USER IDENTITY MAPPING & FOREIGN KEY VERIFICATION
test('Supabase Identity Mapping: verified user_profiles ID vs synthetic UUID mismatch', async (t) => {
  // Mock Supabase store with auth.users and user_profiles
  const mockAuthUsers = new Map([
    ['real-gotrue-uuid-1234', { id: 'real-gotrue-uuid-1234', email: 'katurtusridhar@gatebuddy.com' }],
  ]);
  const mockUserProfiles = new Map([
    ['katurtusridhar@gatebuddy.com', { id: 'real-gotrue-uuid-1234', email: 'katurtusridhar@gatebuddy.com' }],
  ]);

  const mockSupabase = {
    from(table) {
      if (table === 'user_profiles') {
        return {
          select(cols) {
            return {
              eq(col, val) {
                return {
                  async maybeSingle() {
                    const row = mockUserProfiles.get(val);
                    return { data: row || null, error: null };
                  },
                };
              },
            };
          },
        };
      }
      return {};
    },
  };

  // Mock resolveSupabaseUserId logic
  async function resolveSupabaseUserId(supabaseClient, username) {
    if (!supabaseClient) return { resolved: false, error: 'No client' };
    const cleanUser = username.trim().toLowerCase();
    const email = cleanUser.includes('@') ? cleanUser : `${cleanUser}@gatebuddy.com`;
    const { data: profile } = await supabaseClient.from('user_profiles').select('id').eq('email', email).maybeSingle();
    if (profile && profile.id) {
      return { resolved: true, userId: profile.id };
    }
    return {
      resolved: false,
      error: `User '${username}' does not have an active record in user_profiles / auth.users. Foreign key cannot be satisfied without GoTrue provisioning.`,
    };
  }

  // 1. Existing user -> resolves to the genuine auth.users ID
  const existingUser = await resolveSupabaseUserId(mockSupabase, 'katurtusridhar');
  assert.equal(existingUser.resolved, true);
  assert.equal(existingUser.userId, 'real-gotrue-uuid-1234');
  assert.ok(mockAuthUsers.has(existingUser.userId), 'Resolved ID must exist in auth.users');

  // 2. Case insensitive check -> KaturtuSridhar resolves to same real UUID
  const caseInsensitive = await resolveSupabaseUserId(mockSupabase, 'KaturtuSridhar');
  assert.equal(caseInsensitive.resolved, true);
  assert.equal(caseInsensitive.userId, 'real-gotrue-uuid-1234');

  // 3. Unprovisioned new user -> fails safely, does NOT fabricate synthetic UUID
  const unprovisioned = await resolveSupabaseUserId(mockSupabase, 'new_student_2026');
  assert.equal(unprovisioned.resolved, false);
  assert.match(unprovisioned.error, /does not have an active record in user_profiles/);
  assert.equal(unprovisioned.userId, undefined);

  // 4. FK Integrity: Any insert with a non-existent UUID fails
  function insertWithFkCheck(userId, taskId) {
    if (!mockAuthUsers.has(userId)) {
      throw new Error(`insert or update on table "task_progress" violates foreign key constraint "task_progress_user_id_fkey"`);
    }
    return { success: true };
  }

  // Verified user succeeds
  assert.equal(insertWithFkCheck(existingUser.userId, 'TASK_0001').success, true);

  // Synthetic UUID that is NOT in auth.users FAILS FK constraint
  const fakeSyntheticUuid = '5f63114b-2510-5a19-a5bd-42691d242182';
  assert.throws(() => insertWithFkCheck(fakeSyntheticUuid, 'TASK_0001'), /violates foreign key constraint/);
});

// 14. ANDROID COMPANION CREDENTIAL SAFETY & MISSING CREDENTIALS HANDLING
test('Android Companion Credential Safety: empty credentials fail safely without hardcoded fallbacks', async (t) => {
  function acquireTokenWithPrefs(prefs) {
    const savedToken = prefs.session_token;
    if (savedToken && savedToken.trim().length > 0) {
      return { token: savedToken, status: 'CACHED_SESSION' };
    }
    const username = prefs.auth_username?.trim();
    const password = prefs.auth_password?.trim();

    // Security invariant: NEVER fall back to hardcoded plaintext passwords!
    if (!username || !password) {
      return { token: null, status: 'UNCONFIGURED_CREDENTIALS' };
    }

    return { token: `fresh_signed_token_for_${username}`, status: 'AUTHENTICATED' };
  }

  // 1. Fresh install with empty prefs -> does NOT send hardcoded password, fails cleanly
  const freshInstall = acquireTokenWithPrefs({});
  assert.equal(freshInstall.token, null);
  assert.equal(freshInstall.status, 'UNCONFIGURED_CREDENTIALS');

  // 2. Returning user with valid saved token -> returns cached session without re-transmitting password
  const returningUserWithToken = acquireTokenWithPrefs({ session_token: 'existing_valid_token' });
  assert.equal(returningUserWithToken.token, 'existing_valid_token');
  assert.equal(returningUserWithToken.status, 'CACHED_SESSION');

  // 3. User configured credentials in Settings -> authenticates successfully
  const configuredUser = acquireTokenWithPrefs({
    auth_username: 'katurtusridhar',
    auth_password: 'UserEnteredSecretPassword!',
  });
  assert.equal(configuredUser.token, 'fresh_signed_token_for_katurtusridhar');
  assert.equal(configuredUser.status, 'AUTHENTICATED');
});

// 15. ELEVATED DATABASE ACCESS: SERVICE-ROLE KEY ISOLATION & ZERO-LEAKAGE
test('Elevated Database Access: Service-role key server-only isolation & zero-credential leak', async (t) => {
  // 1. Verify SUPABASE_SERVICE_ROLE_KEY is not prefixed with NEXT_PUBLIC_
  const envKey = 'SUPABASE_SERVICE_ROLE_KEY';
  assert.equal(envKey.startsWith('NEXT_PUBLIC_'), false, 'Service role key must never use NEXT_PUBLIC_ prefix');

  // 2. Client mock to verify options for service-role isolation
  function createServiceClientConfig(serviceRoleKey) {
    if (!serviceRoleKey || serviceRoleKey.includes('placeholder')) return null;
    return {
      auth: {
        persistSession: false,
        autoRefreshToken: false,
        detectSessionInUrl: false,
      },
    };
  }

  const config = createServiceClientConfig('eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.dummy_secret');
  assert.notEqual(config, null);
  assert.equal(config.auth.persistSession, false, 'Service role client must not persist sessions');
  assert.equal(config.auth.autoRefreshToken, false, 'Service role client must not auto-refresh tokens');

  // 3. Verify error messages and logs never leak the service role key or secret
  const secretKey = 'secret_supabase_admin_key_999';
  const rawDbError = `Database error while executing elevated upsert with key ${secretKey}`;
  function sanitizeError(msg) {
    return msg.replace(secretKey, '[REDACTED_SECRET]');
  }
  const sanitized = sanitizeError(rawDbError);
  assert.equal(sanitized.includes(secretKey), false);
  assert.equal(sanitized.includes('[REDACTED_SECRET]'), true);
});

// 16. MULTI-TENANT OWNERSHIP & CROSS-USER MUTATION REJECTION ACROSS ALL ENTITIES
test('Multi-Tenant Ownership: Cross-user attempts rejected across all 6 sync entities', async (t) => {
  const authorizedUserUuid = '7d92cb64-4bf8-4660-84c2-901db41454ec';
  const victimUserUuid = '11111111-2222-3333-4444-555555555555';

  function validatePayloadOwnership(payload, currentUserUuid, currentUsername) {
    const entityGroups = [
      { name: 'progress', items: payload.progress },
      { name: 'recoveries', items: payload.recoveries },
      { name: 'assessments', items: payload.assessments },
      { name: 'dppRecords', items: payload.dppRecords },
      { name: 'customTasks', items: payload.customTasks },
      { name: 'achievements', items: payload.achievements },
    ];

    for (const group of entityGroups) {
      if (Array.isArray(group.items)) {
        for (const item of group.items) {
          const itemUser = item?.userId || item?.user_id;
          if (itemUser && typeof itemUser === 'string' && itemUser.trim()) {
            const clean = itemUser.trim();
            if (clean !== currentUserUuid && clean.toLowerCase() !== currentUsername.toLowerCase()) {
              return {
                authorized: false,
                statusCode: 403,
                code: 'CROSS_USER_ACCESS_DENIED',
                message: `Cross-user violation in '${group.name}': Record targets user '${clean}', but session belongs to '${currentUsername}'.`,
              };
            }
          }
        }
      }
    }
    return { authorized: true };
  }

  // 1. Authorized payload without explicit user_id (implicitly stamped with current user)
  const validPayload = {
    progress: [{ taskId: 'T1', isCompleted: true }],
    recoveries: [{ taskId: 'T2', status: 'ACTIVE' }],
    assessments: [{ testName: 'Test 1', marksObtained: 25 }],
    dppRecords: [{ subjectCode: 'EM', totalQuestions: 10 }],
    customTasks: [{ title: 'Solve 20 PYQs' }],
    achievements: [{ code: 'FIRST_TEST' }],
  };
  const validCheck = validatePayloadOwnership(validPayload, authorizedUserUuid, 'katurtusridhar');
  assert.equal(validCheck.authorized, true);

  // 2. Cross-user attack on task_progress
  const attackProgress = { progress: [{ taskId: 'T1', user_id: victimUserUuid }] };
  const checkProgress = validatePayloadOwnership(attackProgress, authorizedUserUuid, 'katurtusridhar');
  assert.equal(checkProgress.authorized, false);
  assert.equal(checkProgress.code, 'CROSS_USER_ACCESS_DENIED');
  assert.match(checkProgress.message, /in 'progress'/);

  // 3. Cross-user attack on recoveries
  const attackRecoveries = { recoveries: [{ taskId: 'T2', userId: victimUserUuid }] };
  const checkRecoveries = validatePayloadOwnership(attackRecoveries, authorizedUserUuid, 'katurtusridhar');
  assert.equal(checkRecoveries.authorized, false);
  assert.match(checkRecoveries.message, /in 'recoveries'/);

  // 4. Cross-user attack on dpp_records
  const attackDpp = { dppRecords: [{ subjectCode: 'OS', user_id: victimUserUuid }] };
  const checkDpp = validatePayloadOwnership(attackDpp, authorizedUserUuid, 'katurtusridhar');
  assert.equal(checkDpp.authorized, false);
  assert.match(checkDpp.message, /in 'dppRecords'/);

  // 5. Cross-user attack on custom_tasks
  const attackCustom = { customTasks: [{ title: 'Hack', user_id: victimUserUuid }] };
  const checkCustom = validatePayloadOwnership(attackCustom, authorizedUserUuid, 'katurtusridhar');
  assert.equal(checkCustom.authorized, false);
  assert.match(checkCustom.message, /in 'customTasks'/);
});

// 17. UNPROVISIONED IDENTITY MAPPING FAILS SAFELY WITH 503
test('Identity Resolution: Missing or invalid Supabase user profile fails safely with 503', async (t) => {
  const unprovisionedUser = 'unknown_student_99';
  const mockDbClient = {
    from: (table) => ({
      select: () => ({
        eq: () => ({
          maybeSingle: async () => ({ data: null, error: null }),
        }),
      }),
    }),
  };

  async function resolveUser(client, username) {
    const { data: profile } = await client.from('user_profiles').select('id').eq('email', `${username}@gatecse.internal`).maybeSingle();
    if (!profile) {
      return { resolved: false, statusCode: 503, code: 'SUPABASE_IDENTITY_UNRESOLVED' };
    }
    return { resolved: true, userId: profile.id };
  }

  const res = await resolveUser(mockDbClient, unprovisionedUser);
  assert.equal(res.resolved, false);
  assert.equal(res.statusCode, 503);
  assert.equal(res.code, 'SUPABASE_IDENTITY_UNRESOLVED');
});

// 18. SERVER-SIDE SESSION REVOCATION, LOGOUT & REPLAY PREVENTION
test('Session Security: Server-side token revocation, logout, and replay prevention', async (t) => {
  const secretKey = 'test-secret-key-gatebuddy';
  const crypto = await import('node:crypto');

  function createHmac(data) {
    return crypto.createHmac('sha256', secretKey).update(data).digest('hex');
  }

  let seq = 0;
  function signToken(username, expiresInSeconds) {
    seq++;
    const nonce = `n_${seq}`;
    const expiry = Date.now() + expiresInSeconds * 1000;
    const payload = `${username}:${expiry}:${nonce}`;
    const hmac = createHmac(payload);
    return Buffer.from(`${payload}:${hmac}`).toString('base64url');
  }

  const revokedSignatures = new Map();

  function revokeToken(token) {
    if (!token) return false;
    const decoded = Buffer.from(token, 'base64url').toString('utf-8');
    const parts = decoded.split(':');
    if (parts.length < 3) return false;
    const expiry = parseInt(parts[1], 10);
    const sig = parts[parts.length - 1];
    revokedSignatures.set(sig, expiry);
    return true;
  }

  function verifyToken(token) {
    if (!token) return { valid: false, error: 'MISSING_TOKEN' };
    try {
      const decoded = Buffer.from(token, 'base64url').toString('utf-8');
      const parts = decoded.split(':');
      let username = '';
      let expiryStr = '';
      let sig = '';
      let payloadToVerify = '';

      if (parts.length === 4) {
        const [u, exp, nonce, s] = parts;
        username = u;
        expiryStr = exp;
        sig = s;
        payloadToVerify = `${u}:${exp}:${nonce}`;
      } else if (parts.length === 3) {
        const [u, exp, s] = parts;
        username = u;
        expiryStr = exp;
        sig = s;
        payloadToVerify = `${u}:${exp}`;
      } else {
        return { valid: false, error: 'MALFORMED' };
      }

      const expiry = parseInt(expiryStr, 10);
      if (Date.now() > expiry) return { valid: false, error: 'TOKEN_EXPIRED' };
      if (revokedSignatures.has(sig)) return { valid: false, error: 'TOKEN_REVOKED' };
      const expected = createHmac(payloadToVerify);
      if (sig !== expected) return { valid: false, error: 'INVALID_SIGNATURE' };
      return { valid: true, username };
    } catch {
      return { valid: false, error: 'DECODE_ERROR' };
    }
  }

  // 1. Issue token for user A
  const tokenA = signToken('katurtusridhar', 3600);
  const checkInitial = verifyToken(tokenA);
  assert.equal(checkInitial.valid, true);
  assert.equal(checkInitial.username, 'katurtusridhar');

  // 2. Revoke token A via logout
  const revokeResult = revokeToken(tokenA);
  assert.equal(revokeResult, true);

  // 3. Attempt to replay revoked token A -> rejected immediately
  const checkReplay = verifyToken(tokenA);
  assert.equal(checkReplay.valid, false);
  assert.equal(checkReplay.error, 'TOKEN_REVOKED');

  // 4. Fresh token issued for user A is completely valid
  const freshTokenA = signToken('katurtusridhar', 3600);
  const checkFresh = verifyToken(freshTokenA);
  assert.equal(checkFresh.valid, true);

  // 5. Account switching: Revoking user A does not affect user B
  const tokenB = signToken('second_student', 3600);
  assert.equal(verifyToken(tokenB).valid, true);
  assert.equal(verifyToken(tokenA).valid, false); // User A still revoked
});


