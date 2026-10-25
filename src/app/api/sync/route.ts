import { NextRequest, NextResponse } from "next/server";
import { getSupabaseServiceClient } from "@/lib/supabase/server";
import {
  getAuthorizedUser,
  resolveSupabaseUserId,
  sanitizeSyncState,
  updateServerCredentials,
  verifyMasterCode,
  serverCredentials,
} from "@/lib/serverAuth";

let globalServerVersion = 1;

// Server-side authoritative sync state
const serverSyncState = {
  lastUpdated: 0,
  serverVersion: globalServerVersion,
  progress: [] as any[],
  recoveries: [] as any[],
  assessments: [] as any[],
  dppRecords: [] as any[],
  customTasks: [] as any[],
  settings: {
    id: 1,
    targetExamDate: "2028-02-12",
    targetExamName: "GATE CSE 2028",
    dailyRecoveryLimit: 2,
    themeMode: "DARK",
    reminderEnabled: false,
    reminderSlot1Hour: 9,
    reminderSlot1Minute: 0,
    reminderSlot2Hour: 18,
    reminderSlot2Minute: 0,
    isOnboarded: true,
  },
  achievements: [] as any[],
  credentials: {
    username: serverCredentials.username,
  },
};

export async function GET(req: NextRequest) {
  // Enforce user-scoped cryptographic authentication & API authorization
  const auth = await getAuthorizedUser(req);
  if (!auth.authorized || !auth.username) {
    if (auth.status === 503) {
      return NextResponse.json(
        {
          status: "unavailable",
          code: "REVOCATION_CHECK_UNAVAILABLE",
          message: "Session revocation service unavailable. Request rejected to prevent unauthorized access.",
        },
        { status: 503 }
      );
    }
    return NextResponse.json(
      { status: "unauthorized", message: "User authorization required. Provide valid Bearer session token or auth cookie." },
      { status: 401 }
    );
  }

  const { searchParams } = new URL(req.url);
  const since = searchParams.get("since");
  const sinceTs = since ? parseInt(since, 10) : 0;
  const sinceVer = searchParams.get("version");
  const sinceVerNum = sinceVer ? parseInt(sinceVer, 10) : 0;

  let maxDbTimestamp = 0;
  const dbErrors: any[] = [];
  const dbClient = getSupabaseServiceClient();
  const isDbActive = Boolean(dbClient);

  // If Supabase is configured, fetch persistent records from PostgreSQL filtered by verified user identity
  if (isDbActive && dbClient) {
    try {
      const userRes = await resolveSupabaseUserId(dbClient, auth.username);
      if (!userRes.resolved || !userRes.userId) {
        dbErrors.push({ table: "user_profiles", err: userRes.error });
      } else {
        const userUuid = userRes.userId;
        const targetClient = userRes.dbClient || dbClient;
        const [progRes, recRes, assessRes, customRes, achRes, dppRes] = await Promise.all([
          targetClient.from("task_progress").select("*").eq("user_id", userUuid),
          targetClient.from("recovery_assignments").select("*").eq("user_id", userUuid),
          targetClient.from("assessment_results").select("*").eq("user_id", userUuid),
          targetClient.from("custom_tasks").select("*").eq("user_id", userUuid),
          (async () => {
            try {
              return await targetClient.from("user_achievements").select("*").eq("user_id", userUuid);
            } catch {
              return { data: null, error: null };
            }
          })(),
          (async () => {
            try {
              return await targetClient.from("dpp_records").select("*").eq("user_id", userUuid);
            } catch {
              return { data: null, error: null };
            }
          })(),
        ]);

      if (progRes.error) dbErrors.push({ table: "task_progress", err: progRes.error.message });
      if (recRes.error) dbErrors.push({ table: "recovery_assignments", err: recRes.error.message });
      if (assessRes.error) dbErrors.push({ table: "assessment_results", err: assessRes.error.message });
      if (customRes.error) dbErrors.push({ table: "custom_tasks", err: customRes.error.message });

      if (!progRes.error && Array.isArray(progRes.data)) {
        serverSyncState.progress = progRes.data.map((p: any) => {
          if (p.updated_at) {
            const t = new Date(p.updated_at).getTime();
            if (t > maxDbTimestamp) maxDbTimestamp = t;
          }
          return {
            taskId: p.task_id,
            originalDate: p.original_date,
            isCompleted: p.is_completed,
            completedAtTimestamp: p.completed_at ? new Date(p.completed_at).getTime() : null,
            completionType: p.completion_type,
            notes: p.notes || "",
            lastMutatedTimestamp: p.updated_at ? new Date(p.updated_at).getTime() : 0,
          };
        });
      }

      if (!recRes.error && Array.isArray(recRes.data)) {
        serverSyncState.recoveries = recRes.data.map((r: any) => {
          if (r.updated_at) {
            const t = new Date(r.updated_at).getTime();
            if (t > maxDbTimestamp) maxDbTimestamp = t;
          }
          return {
            id: r.id,
            taskId: r.task_id,
            originalDate: r.original_date,
            recoveryDate: r.recovery_date,
            assignedAtTimestamp: r.assigned_at ? new Date(r.assigned_at).getTime() : Date.now(),
            isCompleted: r.is_completed,
            priorityLevel: r.priority_level || 2,
            status: r.status,
          };
        });
      }

      if (!assessRes.error && Array.isArray(assessRes.data)) {
        serverSyncState.assessments = assessRes.data.map((a: any) => ({
          id: a.id,
          testDate: a.test_date || a.testDate,
          testName: a.test_name || a.testName,
          subjectName: a.subject_name || a.subjectName,
          marksObtained: Number(a.marks_obtained ?? a.marksObtained ?? 0),
          maxMarks: Number(a.max_marks ?? a.maxMarks ?? 50),
          percentage: Number(a.percentage ?? 0),
          topic: a.topic || "",
          mistakesAnalysis: a.mistakes_analysis || a.mistakesAnalysis || "",
          followUpTasks: a.follow_up_tasks || a.followUpTasks || "",
          createdAtTimestamp: a.created_at ? new Date(a.created_at).getTime() : Date.now(),
        }));
      }

      if (!customRes.error && Array.isArray(customRes.data)) {
        serverSyncState.customTasks = customRes.data;
      }

      if (achRes && !achRes.error && Array.isArray(achRes.data)) {
        const existingMap = new Map(serverSyncState.achievements.map((a: any) => [a.code || a.id, a]));
        for (const remoteAch of achRes.data) {
          const code = remoteAch.code;
          const ex = existingMap.get(code);
          existingMap.set(code, {
            ...(ex || {}),
            code,
            id: code,
            isUnlocked: remoteAch.is_unlocked === true,
            unlockedAtTimestamp: remoteAch.unlocked_at ? new Date(remoteAch.unlocked_at).getTime() : null,
          });
        }
        serverSyncState.achievements = Array.from(existingMap.values());
      }

      if (dppRes && !dppRes.error && Array.isArray(dppRes.data)) {
        serverSyncState.dppRecords = dppRes.data;
      }
      }

      if (maxDbTimestamp > 0) {
        serverSyncState.lastUpdated = maxDbTimestamp;
        globalServerVersion = maxDbTimestamp;
      }
    } catch {
      // Fallback to in-memory state on query failure
    }
  }

  // Calculate durable revision cursors across cold starts and independent lambda instances
  const durableTimestamp = maxDbTimestamp > 0 ? maxDbTimestamp : serverSyncState.lastUpdated;
  const durableVersion = maxDbTimestamp > 0 ? maxDbTimestamp : globalServerVersion;

  const hasUpdates =
    !since ||
    sinceTs === 0 ||
    durableTimestamp > sinceTs ||
    (sinceVerNum > 0 && durableVersion > sinceVerNum);

  return NextResponse.json({
    status: "ok",
    backendType: isDbActive ? "supabase_postgresql" : "in_memory_serverless",
    hasUpdates,
    timestamp: durableTimestamp,
    serverVersion: durableVersion,
    dbErrors: dbErrors && dbErrors.length > 0 ? dbErrors : undefined,
    data: sanitizeSyncState({
      ...serverSyncState,
      lastUpdated: durableTimestamp,
      serverVersion: durableVersion,
    }),
  });
}

export async function POST(req: NextRequest) {
  try {
    // Enforce user-scoped cryptographic authentication & API authorization
    const auth = await getAuthorizedUser(req);
    if (!auth.authorized || !auth.username) {
      if (auth.status === 503) {
        return NextResponse.json(
          {
            status: "unavailable",
            code: "REVOCATION_CHECK_UNAVAILABLE",
            message: "Session revocation service unavailable. Request rejected to prevent unauthorized access.",
          },
          { status: 503 }
        );
      }
      return NextResponse.json(
        { status: "unauthorized", message: "User authorization required. Provide valid Bearer session token or auth cookie." },
        { status: 401 }
      );
    }

    const body = await req.json();
    const { progress, recoveries, assessments, dppRecords, customTasks, settings, achievements, credentials } = body;

    // Per-user ownership check: An authenticated user cannot mutate another user's records without masterCode
    if (credentials?.username && credentials.username.trim().toLowerCase() !== auth.username.toLowerCase()) {
      if (!credentials.masterCode || !verifyMasterCode(credentials.masterCode)) {
        return NextResponse.json(
          {
            status: "forbidden",
            code: "CROSS_USER_ACCESS_DENIED",
            message: `User '${auth.username}' is not authorized to modify records for user '${credentials.username}'.`,
          },
          { status: 403 }
        );
      }
    }

    // Unidirectional App -> Website Sync Model:
    // ONLY the Android Companion App (Bearer token authenticated) has authority to mutate task progress, recoveries, assessments, and DPP records.
    // The website acts as a read-only replica and is forbidden from modifying task completions, recoveries, test assessments, or DPP records.
    const authHeader = req.headers.get("authorization");
    const isCompanionApp = Boolean(authHeader && authHeader.startsWith("Bearer "));

    if (!isCompanionApp && (progress || recoveries || assessments || dppRecords)) {
      return NextResponse.json(
        {
          status: "forbidden",
          message: "Website has no authority to modify task progress, recoveries, test assessments, or DPP records. These are managed exclusively via the Android Companion App.",
        },
        { status: 403 }
      );
    }

    const baseTimestamp = Math.max(serverSyncState.lastUpdated, globalServerVersion);
    const mutationTimestamp = Math.max(Date.now(), baseTimestamp + 1);
    globalServerVersion = mutationTimestamp;

    const dbClient = getSupabaseServiceClient();
    const isDbActive = Boolean(dbClient);
    let resolvedUserUuid: string | null = null;
    let activeTargetClient: any = dbClient;

    // If Supabase is active, resolve user identity upfront and enforce multi-tenant ownership
    if (isDbActive && dbClient) {
      const userRes = await resolveSupabaseUserId(dbClient, auth.username);
      if (!userRes.resolved || !userRes.userId) {
        return NextResponse.json(
          {
            status: "error",
            code: "SUPABASE_IDENTITY_UNRESOLVED",
            message: `Cannot persist to PostgreSQL: ${userRes.error}`,
          },
          { status: 503 }
        );
      }
      resolvedUserUuid = userRes.userId;
      activeTargetClient = userRes.dbClient || dbClient;

      // Check all incoming entities for cross-user tampering
      const entityGroups = [
        { name: "progress", items: progress },
        { name: "recoveries", items: recoveries },
        { name: "assessments", items: assessments },
        { name: "dppRecords", items: dppRecords },
        { name: "customTasks", items: customTasks },
        { name: "achievements", items: achievements },
      ];

      for (const group of entityGroups) {
        if (Array.isArray(group.items)) {
          for (const item of group.items) {
            const itemUser = item?.userId || item?.user_id;
            if (itemUser && typeof itemUser === "string" && itemUser.trim()) {
              const clean = itemUser.trim();
              if (clean !== resolvedUserUuid && clean.toLowerCase() !== auth.username.toLowerCase()) {
                return NextResponse.json(
                  {
                    status: "forbidden",
                    code: "CROSS_USER_ACCESS_DENIED",
                    message: `Cross-user violation in '${group.name}': Record targets user '${clean}', but session belongs to '${auth.username}'.`,
                  },
                  { status: 403 }
                );
              }
            }
          }
        }
      }
    }

    // Apply authoritative merge for progress with deterministic conflict resolution
    if (Array.isArray(progress)) {
      const existingMap = new Map(serverSyncState.progress.map((p) => [p.taskId, p]));
      for (const p of progress) {
        const existing = existingMap.get(p.taskId);
        const incomingIsCompleted = p.isCompleted === true || p.completed === true;
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
          ? p.completedAtTimestamp || existing?.completedAtTimestamp || mutationTimestamp
          : null;

        existingMap.set(p.taskId, {
          ...(existing || {}),
          ...p,
          isCompleted: incomingIsCompleted,
          completedAtTimestamp: effectiveCompletedAt,
          completionType: incomingIsCompleted ? (p.completionType || existing?.completionType || "ON_TIME") : null,
          notes: p.notes !== undefined ? p.notes : (existing?.notes || ""),
          lastMutatedTimestamp: incomingMutatedAt || mutationTimestamp,
          updatedAt: new Date(mutationTimestamp).toISOString(),
        });
      }
      serverSyncState.progress = Array.from(existingMap.values());
    }

    // Apply idempotent merge for recoveries
    if (Array.isArray(recoveries)) {
      const existingMap = new Map(serverSyncState.recoveries.map((r) => [r.taskId, r]));
      for (const r of recoveries) {
        existingMap.set(r.taskId, { ...r, updatedAt: new Date(mutationTimestamp).toISOString() });
      }
      serverSyncState.recoveries = Array.from(existingMap.values());
    }

    // Assessments (authoritative replace from Android Companion App)
    if (Array.isArray(assessments)) {
      serverSyncState.assessments = assessments;
    }

    // DPP Records (authoritative replace from Android Companion App)
    if (Array.isArray(dppRecords)) {
      serverSyncState.dppRecords = dppRecords;
    }

    if (Array.isArray(customTasks)) {
      serverSyncState.customTasks = customTasks;
    }

    if (settings) {
      serverSyncState.settings = settings;
    }

    if (Array.isArray(achievements)) {
      const existingMap = new Map(serverSyncState.achievements.map((a: any) => [a.code || a.id, a]));
      for (const a of achievements) {
        const code = a.code || a.id;
        const existing = existingMap.get(code);
        const isUnlocked = a.isUnlocked === true || existing?.isUnlocked === true;
        const unlockedAtTimestamp =
          a.isUnlocked && a.unlockedAtTimestamp
            ? a.unlockedAtTimestamp
            : existing?.unlockedAtTimestamp || (isUnlocked ? mutationTimestamp : null);
        existingMap.set(code, {
          ...(existing || {}),
          ...a,
          id: code,
          code,
          isUnlocked,
          unlockedAtTimestamp,
        });
      }
      serverSyncState.achievements = Array.from(existingMap.values());
    }

    if (credentials) {
      if (credentials.username && credentials.password && credentials.masterCode) {
        updateServerCredentials(credentials.username, credentials.password, credentials.masterCode);
      }
      serverSyncState.credentials = { username: credentials.username || serverCredentials.username };
    }

    serverSyncState.lastUpdated = mutationTimestamp;
    serverSyncState.serverVersion = globalServerVersion;

    // If Supabase is configured, persist all modified entities to PostgreSQL
    if (isDbActive && dbClient && resolvedUserUuid) {
      const dbErrors: any[] = [];
      const userUuid = resolvedUserUuid;

      try {
        if (Array.isArray(progress) && progress.length > 0) {
          const rows = progress.map((p) => {
            const isDone = p.isCompleted === true || p.completed === true;
            const origDate =
              p.originalDate ||
              p.original_date ||
              (p.taskId && p.taskId.includes("_") ? p.taskId.split("_").pop() : new Date().toISOString().slice(0, 10));
            return {
              user_id: userUuid,
              task_id: p.taskId,
              original_date: origDate,
              is_completed: isDone,
              completed_at: isDone
                ? p.completedAtTimestamp
                  ? new Date(p.completedAtTimestamp).toISOString()
                  : p.completedAt || new Date().toISOString()
                : null,
              completion_type: isDone ? p.completionType || "ON_TIME" : null,
              notes: p.notes || "",
              updated_at: new Date(mutationTimestamp).toISOString(),
            };
          });
          const { error } = await activeTargetClient.from("task_progress").upsert(rows, { onConflict: "user_id,task_id" });
          if (error) {
            console.warn("Supabase task_progress upsert error:", error.message);
            dbErrors.push({ table: "task_progress", error: error.message });
          }
        }

        if (Array.isArray(recoveries) && recoveries.length > 0) {
          const rows = recoveries.map((r) => {
            const isDone = r.isCompleted === true || r.status === "completed";
            const origDate = r.originalDate || r.original_date || new Date().toISOString().slice(0, 10);
            const recDate = r.recoveryDate || r.assignedDate || r.recovery_date || new Date().toISOString().slice(0, 10);
            return {
              user_id: userUuid,
              task_id: r.taskId,
              original_date: origDate,
              recovery_date: recDate,
              is_completed: isDone,
              priority_level: r.priorityLevel || 2,
              status: r.status || (isDone ? "completed" : "pending"),
              updated_at: new Date(mutationTimestamp).toISOString(),
            };
          });
          const { error } = await activeTargetClient.from("recovery_assignments").upsert(rows, { onConflict: "user_id,task_id" });
          if (error) {
            console.warn("Supabase recovery_assignments upsert error:", error.message);
            dbErrors.push({ table: "recovery_assignments", error: error.message });
          }
        }

        if (Array.isArray(achievements) && achievements.length > 0) {
          const rows = achievements
            .filter((a: any) => a.isUnlocked === true)
            .map((a: any) => ({
              user_id: userUuid,
              code: a.code || a.id,
              is_unlocked: true,
              unlocked_at: a.unlockedAtTimestamp ? new Date(a.unlockedAtTimestamp).toISOString() : new Date().toISOString(),
            }));
          if (rows.length > 0) {
            const { error } = await activeTargetClient.from("user_achievements").upsert(rows, { onConflict: "user_id,code" });
            if (error) {
              console.warn("Supabase user_achievements upsert error:", error.message);
              dbErrors.push({ table: "user_achievements", error: error.message });
            }
          }
        }

        if (Array.isArray(assessments)) {
          await activeTargetClient.from("assessment_results").delete().eq("user_id", userUuid);
          if (assessments.length > 0) {
            const rows = assessments.map((a: any) => ({
              user_id: userUuid,
              test_date: a.testDate,
              test_name: a.testName,
              subject_name: a.subjectName,
              marks_obtained: a.marksObtained,
              max_marks: a.maxMarks,
              percentage: a.percentage,
              questions_attempted: a.questionsAttempted,
              questions_correct: a.questionsCorrect,
              topic: a.topic || "",
              mistakes_analysis: a.mistakesAnalysis || "",
              follow_up_tasks: a.followUpTasks || "",
              updated_at: new Date(mutationTimestamp).toISOString(),
            }));
            const { error } = await activeTargetClient.from("assessment_results").insert(rows);
            if (error) {
              console.warn("Supabase assessment_results insert error:", error.message);
              dbErrors.push({ table: "assessment_results", error: error.message });
            }
          }
        }

        if (Array.isArray(dppRecords)) {
          await activeTargetClient.from("dpp_records").delete().eq("user_id", userUuid);
          if (dppRecords.length > 0) {
            const rows = dppRecords.map((d: any) => ({
              user_id: userUuid,
              date: d.date,
              subject_code: d.subjectCode || d.subject_code || "GATE",
              subject_name: d.subjectName || d.subject_name || "General",
              topic: d.topic || "",
              total_questions: Number(d.totalQuestions || d.total_questions || 0),
              questions_correct: Number(d.questionsCorrect || d.questions_correct || 0),
              accuracy_percentage: Number(d.accuracyPercentage || d.accuracy_percentage || 0.0),
              notes: d.notes || "",
              created_at: d.createdAtTimestamp ? new Date(d.createdAtTimestamp).toISOString() : new Date().toISOString(),
              updated_at: new Date(mutationTimestamp).toISOString(),
            }));
            const { error } = await activeTargetClient.from("dpp_records").insert(rows);
            if (error) {
              console.warn("Supabase dpp_records insert error:", error.message);
              dbErrors.push({ table: "dpp_records", error: error.message });
            }
          }
        }

        if (Array.isArray(customTasks)) {
          await activeTargetClient.from("custom_tasks").delete().eq("user_id", userUuid);
          if (customTasks.length > 0) {
            const rows = customTasks.map((c: any) => ({
              user_id: userUuid,
              date: c.date,
              title: c.title,
              subject_code: c.subjectCode || "GATE",
              subject_name: c.subjectName || "General / Practice",
              is_completed: c.isCompleted === true,
              completed_at: c.completedAtTimestamp ? new Date(c.completedAtTimestamp).toISOString() : null,
              notes: c.notes || "",
              points_value: c.pointsValue || 5,
              updated_at: new Date(mutationTimestamp).toISOString(),
            }));
            const { error } = await activeTargetClient.from("custom_tasks").insert(rows);
            if (error) {
              console.warn("Supabase custom_tasks insert error:", error.message);
              dbErrors.push({ table: "custom_tasks", error: error.message });
            }
          }
        }
      } catch (dbErr: any) {
        console.warn("Supabase background upsert exception", dbErr);
        dbErrors.push({ table: "all", error: dbErr?.message || "Unknown database failure" });
      }

      // If durable persistence fails, DO NOT falsely return success
      if (dbErrors.length > 0) {
        return NextResponse.json(
          {
            status: "error",
            code: "DATABASE_PERSISTENCE_FAILED",
            message: "Failed to persist records to durable PostgreSQL storage. Operation not confirmed.",
            errors: dbErrors,
          },
          { status: 503 }
        );
      }
    }

    return NextResponse.json({
      status: "synced",
      backendType: isDbActive ? "supabase_postgresql" : "in_memory_serverless",
      timestamp: serverSyncState.lastUpdated,
      serverVersion: globalServerVersion,
      data: sanitizeSyncState(serverSyncState),
    });
  } catch (error: any) {
    return NextResponse.json(
      { status: "error", message: error?.message || "Sync failed" },
      { status: 500 }
    );
  }
}
