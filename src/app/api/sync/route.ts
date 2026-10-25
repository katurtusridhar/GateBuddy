import { NextRequest, NextResponse } from "next/server";
import { supabase, isSupabaseConfigured } from "@/lib/supabase/client";
import {
  verifyRequestAuthorization,
  sanitizeSyncState,
  updateServerCredentials,
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
  const isAuthorized = await verifyRequestAuthorization(req);
  if (!isAuthorized) {
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

  // If Supabase is configured, fetch persistent records from PostgreSQL
  if (isSupabaseConfigured) {
    try {
      const [progRes, recRes, assessRes, customRes, achRes, dppRes] = await Promise.all([
        supabase.from("task_progress").select("*"),
        supabase.from("recovery_assignments").select("*"),
        supabase.from("assessment_results").select("*"),
        supabase.from("custom_tasks").select("*"),
        (async () => {
          try {
            return await supabase.from("user_achievements").select("*");
          } catch {
            return { data: null, error: null };
          }
        })(),
        (async () => {
          try {
            return await supabase.from("dpp_records").select("*");
          } catch {
            return { data: null, error: null };
          }
        })(),
      ]);

      if (progRes.error) dbErrors.push({ table: "task_progress", err: progRes.error.message });
      if (recRes.error) dbErrors.push({ table: "recovery_assignments", err: recRes.error.message });
      if (assessRes.error) dbErrors.push({ table: "assessment_results", err: assessRes.error.message });
      if (customRes.error) dbErrors.push({ table: "custom_tasks", err: customRes.error.message });

      if (!progRes.error && progRes.data && progRes.data.length > 0) {
        serverSyncState.progress = progRes.data.map((p) => {
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

      if (!recRes.error && recRes.data && recRes.data.length > 0) {
        serverSyncState.recoveries = recRes.data.map((r) => {
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

      if (!assessRes.error && assessRes.data && assessRes.data.length > 0) {
        serverSyncState.assessments = assessRes.data;
      }

      if (!customRes.error && customRes.data && customRes.data.length > 0) {
        serverSyncState.customTasks = customRes.data;
      }

      if (achRes && !achRes.error && achRes.data && achRes.data.length > 0) {
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

      if (dppRes && !dppRes.error && dppRes.data && dppRes.data.length > 0) {
        serverSyncState.dppRecords = dppRes.data;
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
    backendType: isSupabaseConfigured ? "supabase_postgresql" : "in_memory_serverless",
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
    const isAuthorized = await verifyRequestAuthorization(req);
    if (!isAuthorized) {
      return NextResponse.json(
        { status: "unauthorized", message: "User authorization required. Provide valid Bearer session token or auth cookie." },
        { status: 401 }
      );
    }

    const body = await req.json();
    const { progress, recoveries, assessments, dppRecords, customTasks, settings, achievements, credentials } = body;

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

    // Apply authoritative merge for progress with deterministic conflict resolution
    if (Array.isArray(progress)) {
      const existingMap = new Map(serverSyncState.progress.map((p) => [p.taskId, p]));
      for (const p of progress) {
        const existing = existingMap.get(p.taskId);
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
    if (isSupabaseConfigured) {
      try {
        if (Array.isArray(progress) && progress.length > 0) {
          const rows = progress.map((p) => ({
            task_id: p.taskId,
            original_date: p.originalDate,
            is_completed: p.isCompleted === true,
            completed_at: p.isCompleted && p.completedAtTimestamp ? new Date(p.completedAtTimestamp).toISOString() : null,
            completion_type: p.isCompleted ? (p.completionType || "ON_TIME") : null,
            notes: p.notes || "",
            updated_at: new Date(mutationTimestamp).toISOString(),
          }));
          const { error } = await supabase.from("task_progress").upsert(rows, { onConflict: "task_id" });
          if (error) console.warn("Supabase task_progress upsert error:", error.message);
        }

        if (Array.isArray(recoveries) && recoveries.length > 0) {
          const rows = recoveries.map((r) => ({
            task_id: r.taskId,
            original_date: r.originalDate,
            recovery_date: r.recoveryDate,
            is_completed: r.isCompleted === true,
            priority_level: r.priorityLevel || 2,
            status: r.status,
            updated_at: new Date(mutationTimestamp).toISOString(),
          }));
          const { error } = await supabase.from("recovery_assignments").upsert(rows, { onConflict: "task_id" });
          if (error) console.warn("Supabase recovery_assignments upsert error:", error.message);
        }

        if (Array.isArray(achievements) && achievements.length > 0) {
          const rows = achievements
            .filter((a: any) => a.isUnlocked === true)
            .map((a: any) => ({
              code: a.code || a.id,
              is_unlocked: true,
              unlocked_at: a.unlockedAtTimestamp ? new Date(a.unlockedAtTimestamp).toISOString() : new Date().toISOString(),
            }));
          if (rows.length > 0) {
            try {
              await supabase.from("user_achievements").upsert(rows, { onConflict: "code" });
            } catch {
              // table may not exist yet in some environments
            }
          }
        }

        if (Array.isArray(assessments) && assessments.length > 0) {
          try {
            await supabase.from("assessment_results").upsert(assessments);
          } catch {
            // table may not exist yet in some environments
          }
        }

        if (Array.isArray(dppRecords) && dppRecords.length > 0) {
          try {
            await supabase.from("dpp_records").upsert(dppRecords);
          } catch {
            // table may not exist yet in some environments
          }
        }

        if (Array.isArray(customTasks) && customTasks.length > 0) {
          try {
            await supabase.from("custom_tasks").upsert(customTasks);
          } catch {
            // table may not exist yet in some environments
          }
        }
      } catch (dbErr) {
        console.warn("Supabase background upsert error", dbErr);
      }
    }

    return NextResponse.json({
      status: "synced",
      backendType: isSupabaseConfigured ? "supabase_postgresql" : "in_memory_serverless",
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
