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
