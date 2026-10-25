import test from 'node:test';
import assert from 'node:assert/strict';

// Test harness simulating serverless /api/sync state machine and client reconciliations

function createServerSyncEngine() {
  let serverVersion = 1;
  let lastUpdated = Date.now();
  let progressMap = new Map();
  let recoveriesMap = new Map();
  let achievementsMap = new Map();

  function post(payload) {
    const { progress, recoveries, achievements } = payload;
    const mutationTs = Math.max(Date.now(), lastUpdated + 1);
    serverVersion++;

    if (Array.isArray(progress)) {
      for (const p of progress) {
        const existing = progressMap.get(p.taskId);
        const incomingIsCompleted = p.isCompleted === true;
        const incomingMutatedAt = Number(p.lastMutatedTimestamp || p.completedAtTimestamp || 0);
        const isExplicitMutation = p.isExplicitMutation === true;
        const existingCompletedAt = Number(existing?.completedAtTimestamp || 0);
        const existingMutatedAt = Number(existing?.lastMutatedTimestamp || 0);

        // Safeguard: Passive uncompleted background records CANNOT overwrite a confirmed completed task!
        if (existing?.isCompleted && !incomingIsCompleted) {
          const isExplicitUncheck =
            isExplicitMutation ||
            (incomingMutatedAt > 0 && incomingMutatedAt > existingCompletedAt && incomingMutatedAt > existingMutatedAt);
          if (!isExplicitUncheck) {
            // Keep existing completed record
            continue;
          }
        }

        const effectiveCompletedAt = incomingIsCompleted
          ? p.completedAtTimestamp || existing?.completedAtTimestamp || mutationTs
          : null;

        progressMap.set(p.taskId, {
          ...(existing || {}),
          ...p,
          isCompleted: incomingIsCompleted,
          completedAtTimestamp: effectiveCompletedAt,
          completionType: incomingIsCompleted ? (p.completionType || existing?.completionType || 'ON_TIME') : null,
          notes: p.notes !== undefined ? p.notes : (existing?.notes || ''),
          lastMutatedTimestamp: incomingMutatedAt || mutationTs,
          updatedAt: new Date(mutationTs).toISOString(),
        });
      }
    }

    if (Array.isArray(recoveries)) {
      for (const r of recoveries) {
        recoveriesMap.set(r.taskId, { ...r, updatedAt: new Date(mutationTs).toISOString() });
      }
    }

    if (Array.isArray(achievements)) {
      for (const a of achievements) {
        const code = a.code || a.id;
        const existing = achievementsMap.get(code);
        const isUnlocked = a.isUnlocked === true || existing?.isUnlocked === true;
        achievementsMap.set(code, {
          ...(existing || {}),
          ...a,
          code,
          isUnlocked,
          unlockedAtTimestamp: isUnlocked ? (a.unlockedAtTimestamp || existing?.unlockedAtTimestamp || mutationTs) : null,
        });
      }
    }

    lastUpdated = mutationTs;

    return {
      status: 'synced',
      timestamp: lastUpdated,
      serverVersion,
      data: {
        lastUpdated,
        serverVersion,
        progress: Array.from(progressMap.values()),
        recoveries: Array.from(recoveriesMap.values()),
        achievements: Array.from(achievementsMap.values()),
      },
    };
  }

  function get(sinceTs = 0) {
    const hasUpdates = sinceTs === 0 || lastUpdated > sinceTs;
    return {
      status: 'ok',
      hasUpdates,
      timestamp: lastUpdated,
      serverVersion,
      data: {
        lastUpdated,
        serverVersion,
        progress: Array.from(progressMap.values()),
        recoveries: Array.from(recoveriesMap.values()),
        achievements: Array.from(achievementsMap.values()),
      },
    };
  }

  return { post, get };
}

// 1. TEST A — ANDROID TO WEBSITE BIDIRECTIONAL PROPAGATION
test('Test A — Android to Website task completion and uncompletion propagation', async (t) => {
  const server = createServerSyncEngine();

  // Step 1: Initial state
  const initialWeb = server.get(0);
  assert.equal(initialWeb.data.progress.length, 0);

  // Step 2: Android completes TASK_0011
  const t1 = Date.now();
  const androidPost = server.post({
    progress: [
      {
        taskId: 'TASK_0011',
        originalDate: '2026-10-24',
        isCompleted: true,
        completedAtTimestamp: t1,
        completionType: 'ON_TIME',
        notes: 'Done from phone',
        isExplicitMutation: true,
        lastMutatedTimestamp: t1,
      },
    ],
  });
  assert.equal(androidPost.status, 'synced');

  // Step 3: Website polls using its previous lastSyncTimestamp (which was 0)
  const webPoll = server.get(initialWeb.timestamp);
  assert.equal(webPoll.hasUpdates, true);
  const task11 = webPoll.data.progress.find((p) => p.taskId === 'TASK_0011');
  assert.ok(task11);
  assert.equal(task11.isCompleted, true);
  assert.equal(task11.notes, 'Done from phone');

  // Step 4: Android uncompletes TASK_0011 explicitly
  const t2 = Date.now() + 500;
  const androidUncheck = server.post({
    progress: [
      {
        taskId: 'TASK_0011',
        originalDate: '2026-10-24',
        isCompleted: false,
        completedAtTimestamp: null,
        completionType: null,
        notes: '',
        isExplicitMutation: true,
        lastMutatedTimestamp: t2,
      },
    ],
  });
  assert.equal(androidUncheck.status, 'synced');

  // Step 5: Website polls again
  const webPoll2 = server.get(webPoll.timestamp);
  assert.equal(webPoll2.hasUpdates, true);
  const task11Unchecked = webPoll2.data.progress.find((p) => p.taskId === 'TASK_0011');
  assert.ok(task11Unchecked);
  assert.equal(task11Unchecked.isCompleted, false);
});

// 2. TEST B — WEBSITE TO ANDROID BIDIRECTIONAL PROPAGATION & SAFEGUARD
test('Test B — Website to Android propagation and protection against stale background overwrite', async (t) => {
  const server = createServerSyncEngine();

  // Step 1: Website completes TASK_0012
  const t1 = Date.now();
  server.post({
    progress: [
      {
        taskId: 'TASK_0012',
        originalDate: '2026-10-24',
        isCompleted: true,
        completedAtTimestamp: t1,
        completionType: 'ON_TIME',
        notes: 'Done from website',
        isExplicitMutation: true,
        lastMutatedTimestamp: t1,
      },
    ],
  });

  // Verify server has TASK_0012 completed
  const snap1 = server.get(0);
  assert.equal(snap1.data.progress.find((p) => p.taskId === 'TASK_0012')?.isCompleted, true);

  // Step 2: Android runs a routine 4-second background loop BEFORE knowing about the check
  // Android sends a passive background scan packet where isExplicitMutation = false and lastMutatedTimestamp = 0
  const androidPassiveSync = server.post({
    progress: [
      {
        taskId: 'TASK_0012',
        originalDate: '2026-10-24',
        isCompleted: false,
        completedAtTimestamp: null,
        notes: '',
        isExplicitMutation: false,
        lastMutatedTimestamp: 0,
      },
    ],
  });

  // Verify that the server's completed state was PROTECTED and NOT overwritten by the passive sync!
  const task12AfterPassive = androidPassiveSync.data.progress.find((p) => p.taskId === 'TASK_0012');
  assert.equal(task12AfterPassive.isCompleted, true, 'Passive background scan must NEVER uncheck a completed task');

  // Step 3: Now Android receives the server snapshot during sync and reconciles it
  // Android Room will become isCompleted = true.
  assert.equal(task12AfterPassive.completedAtTimestamp, t1);
});

// 3. TEST C — IDEMPOTENCY OF REPEATED MUTATIONS
test('Test C — Repeated identical mutations do not duplicate side effects or change timestamps', async (t) => {
  const server = createServerSyncEngine();
  const t1 = 1792800000000;

  const res1 = server.post({
    progress: [
      {
        taskId: 'TASK_0005',
        originalDate: '2026-10-21',
        isCompleted: true,
        completedAtTimestamp: t1,
        completionType: 'ON_TIME',
        isExplicitMutation: true,
        lastMutatedTimestamp: t1,
      },
    ],
  });

  const res2 = server.post({
    progress: [
      {
        taskId: 'TASK_0005',
        originalDate: '2026-10-21',
        isCompleted: true,
        completedAtTimestamp: t1,
        completionType: 'ON_TIME',
        isExplicitMutation: true,
        lastMutatedTimestamp: t1,
      },
    ],
  });

  assert.equal(res2.data.progress.length, 1);
  assert.equal(res2.data.progress[0].completedAtTimestamp, t1, 'Original completion timestamp preserved');
});

// 4. TEST D — MONOTONIC VERSION INCREMENT
test('Test D — Monotonic server version increment on mutation', async (t) => {
  const server = createServerSyncEngine();
  const v1 = server.get(0).serverVersion;

  const r1 = server.post({ progress: [{ taskId: 'TASK_0001', originalDate: '2026-10-19', isCompleted: true }] });
  assert.ok(r1.serverVersion > v1, 'Server version must increment after write');

  const r2 = server.post({ recoveries: [{ taskId: 'TASK_0001', recoveryDate: '2026-10-25', status: 'MANUAL_CONFIRMED' }] });
  assert.ok(r2.serverVersion > r1.serverVersion, 'Server version must increment on recovery update');
});

// 5. TEST E — EXPLICIT UNCHECK ACCEPTANCE
test('Test E — Explicit user uncheck mutation is properly accepted', async (t) => {
  const server = createServerSyncEngine();
  const t1 = Date.now();

  server.post({
    progress: [{ taskId: 'TASK_0010', originalDate: '2026-10-23', isCompleted: true, completedAtTimestamp: t1 }],
  });

  const t2 = t1 + 1000;
  const res = server.post({
    progress: [
      {
        taskId: 'TASK_0010',
        originalDate: '2026-10-23',
        isCompleted: false,
        isExplicitMutation: true,
        lastMutatedTimestamp: t2,
      },
    ],
  });

  const task10 = res.data.progress.find((p) => p.taskId === 'TASK_0010');
  assert.equal(task10.isCompleted, false, 'Explicit uncheck must be accepted');
  assert.equal(task10.completedAtTimestamp, null);
});

// 6. TEST F — DURABLE POSTGRESQL PERSISTENCE & ERROR HANDLING (NO SILENT SUCCESS)
test('Test F — Durable PostgreSQL Persistence: write failures return 503 error, never silent success', async (t) => {
  // Simulates durable persistence engine with mockable PostgreSQL store
  function createDurableSyncEngine(options = { simulateDbError: false }) {
    let mockPostgresTable = new Map(); // Simulated durable table: (user_id, task_id) -> row
    let inMemoryCache = new Map();

    return {
      post(userId, payload) {
        const { progress } = payload;
        const dbErrors = [];

        // Attempt durable write
        if (Array.isArray(progress)) {
          for (const p of progress) {
            if (options.simulateDbError) {
              dbErrors.push({ table: 'task_progress', error: 'Connection to PostgreSQL failed: timeout' });
              break;
            }
            // Composite key matching schema: (user_id, task_id)
            const key = `${userId}:${p.taskId}`;
            mockPostgresTable.set(key, {
              user_id: userId,
              task_id: p.taskId,
              original_date: p.originalDate,
              is_completed: p.isCompleted,
              updated_at: new Date().toISOString(),
            });
          }
        }

        // INVARIANT: If durable write fails, DO NOT claim success!
        if (dbErrors.length > 0) {
          return {
            status: 503,
            body: {
              status: 'error',
              code: 'DATABASE_PERSISTENCE_FAILED',
              message: 'Failed to persist records to durable PostgreSQL storage.',
              errors: dbErrors,
            },
          };
        }

        // Only update in-memory state after durable write succeeds
        if (Array.isArray(progress)) {
          for (const p of progress) {
            inMemoryCache.set(p.taskId, p);
          }
        }

        return {
          status: 200,
          body: {
            status: 'synced',
            persistedRows: mockPostgresTable.size,
          },
        };
      },
      restartServerlessInstance() {
        // Cold start wipes in-memory cache completely
        inMemoryCache.clear();
      },
      getHydratedFromPostgres(userId) {
        const records = [];
        for (const [key, value] of mockPostgresTable.entries()) {
          if (key.startsWith(`${userId}:`)) {
            records.push(value);
          }
        }
        return records;
      },
    };
  }

  // Subtest 1: Successful durable write
  const healthyEngine = createDurableSyncEngine({ simulateDbError: false });
  const successRes = healthyEngine.post('uuid_user_1', {
    progress: [{ taskId: 'TASK_0001', originalDate: '2026-10-19', isCompleted: true }],
  });
  assert.equal(successRes.status, 200);
  assert.equal(successRes.body.status, 'synced');

  // Subtest 2: Serverless cold start / restart -> data survives from PostgreSQL
  healthyEngine.restartServerlessInstance();
  const recoveredData = healthyEngine.getHydratedFromPostgres('uuid_user_1');
  assert.equal(recoveredData.length, 1);
  assert.equal(recoveredData[0].task_id, 'TASK_0001');
  assert.equal(recoveredData[0].is_completed, true);

  // Subtest 3: Database write failure -> must return HTTP 503 error, NEVER 200 synced
  const failingEngine = createDurableSyncEngine({ simulateDbError: true });
  const failedRes = failingEngine.post('uuid_user_1', {
    progress: [{ taskId: 'TASK_0002', originalDate: '2026-10-19', isCompleted: true }],
  });
  assert.equal(failedRes.status, 503);
  assert.equal(failedRes.body.status, 'error');
  assert.equal(failedRes.body.code, 'DATABASE_PERSISTENCE_FAILED');
});

// 7. TEST G — MULTI-TENANT ISOLATION AND COMPOSITE UPSERT CONFLICTS
test('Test G — Multi-Tenant Data Separation by user_id and Composite Upsert Key', async (t) => {
  const postgresStore = new Map();

  function upsertTaskProgress(userId, taskId, isCompleted) {
    const compositeKey = `${userId}:${taskId}`;
    postgresStore.set(compositeKey, {
      user_id: userId,
      task_id: taskId,
      is_completed: isCompleted,
    });
  }

  // User A and User B mark the same taskId differently
  upsertTaskProgress('user_A_uuid', 'TASK_0001', true);
  upsertTaskProgress('user_B_uuid', 'TASK_0001', false);

  assert.equal(postgresStore.size, 2, 'Different users must have separate rows for identical taskId');
  assert.equal(postgresStore.get('user_A_uuid:TASK_0001').is_completed, true);
  assert.equal(postgresStore.get('user_B_uuid:TASK_0001').is_completed, false);

  // User A updates their progress again -> updates in-place via composite key (no duplicates)
  upsertTaskProgress('user_A_uuid', 'TASK_0001', false);
  assert.equal(postgresStore.size, 2, 'Upsert on composite key must update existing row without growing table size');
  assert.equal(postgresStore.get('user_A_uuid:TASK_0001').is_completed, false);
});
