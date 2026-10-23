import { NextRequest, NextResponse } from "next/server";

// Server-side in-memory store initialized with real user schedule state from Oct 19-22, 2026
const serverSyncState = {
  lastUpdated: Date.now(),
  progress: [
    {
      taskId: "DL_44",
      originalDate: "2026-10-21",
      isCompleted: true,
      completedAtTimestamp: 1729512000000,
      completionType: "ON_TIME",
      notes: "Completed on Wed Oct 21",
    },
  ] as any[],
  recoveries: [
    {
      id: 101,
      taskId: "DL_42",
      originalDate: "2026-10-19",
      recoveryDate: "2026-10-25",
      assignedAtTimestamp: 1729340000000,
      isCompleted: false,
      priorityLevel: 2,
      status: "MANUAL_CONFIRMED",
    },
    {
      id: 102,
      taskId: "CN_19",
      originalDate: "2026-10-19",
      recoveryDate: "2026-10-25",
      assignedAtTimestamp: 1729340000000,
      isCompleted: false,
      priorityLevel: 2,
      status: "MANUAL_CONFIRMED",
    },
    {
      id: 103,
      taskId: "DL_43",
      originalDate: "2026-10-20",
      recoveryDate: "2026-11-01",
      assignedAtTimestamp: 1729426000000,
      isCompleted: false,
      priorityLevel: 2,
      status: "MANUAL_CONFIRMED",
    },
    {
      id: 104,
      taskId: "CN_20",
      originalDate: "2026-10-20",
      recoveryDate: "2026-11-01",
      assignedAtTimestamp: 1729426000000,
      isCompleted: false,
      priorityLevel: 2,
      status: "MANUAL_CONFIRMED",
    },
    {
      id: 105,
      taskId: "CN_21",
      originalDate: "2026-10-21",
      recoveryDate: "2026-11-08",
      assignedAtTimestamp: 1729512000000,
      isCompleted: false,
      priorityLevel: 2,
      status: "MANUAL_CONFIRMED",
    },
    {
      id: 106,
      taskId: "DL_45",
      originalDate: "2026-10-22",
      recoveryDate: "2026-11-08",
      assignedAtTimestamp: 1729598000000,
      isCompleted: false,
      priorityLevel: 2,
      status: "MANUAL_CONFIRMED",
    },
    {
      id: 107,
      taskId: "CN_22",
      originalDate: "2026-10-22",
      recoveryDate: "2026-11-15",
      assignedAtTimestamp: 1729598000000,
      isCompleted: false,
      priorityLevel: 2,
      status: "MANUAL_CONFIRMED",
    },
  ] as any[],
  assessments: [] as any[],
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
    username: "katurtusridhar",
    password: "Sridhar@758",
    masterCode: "oA-THL,UXyh6",
  },
};

export async function GET(req: NextRequest) {
  const { searchParams } = new URL(req.url);
  const since = searchParams.get("since");
  const sinceTs = since ? parseInt(since, 10) : 0;

  return NextResponse.json({
    status: "ok",
    hasUpdates: serverSyncState.lastUpdated > sinceTs,
    timestamp: serverSyncState.lastUpdated,
    data: serverSyncState,
  });
}

export async function POST(req: NextRequest) {
  try {
    const body = await req.json();
    const { progress, recoveries, assessments, customTasks, settings, achievements, credentials } = body;

    // Apply idempotent merge for progress
    if (Array.isArray(progress)) {
      const existingMap = new Map(serverSyncState.progress.map((p) => [p.taskId, p]));
      for (const p of progress) {
        existingMap.set(p.taskId, { ...p, updatedAt: new Date().toISOString() });
      }
      serverSyncState.progress = Array.from(existingMap.values());
    }

    // Apply idempotent merge for recoveries
    if (Array.isArray(recoveries)) {
      const existingMap = new Map(serverSyncState.recoveries.map((r) => [r.taskId, r]));
      for (const r of recoveries) {
        existingMap.set(r.taskId, { ...r, updatedAt: new Date().toISOString() });
      }
      serverSyncState.recoveries = Array.from(existingMap.values());
    }

    // Assessments
    if (Array.isArray(assessments)) {
      const existingMap = new Map(serverSyncState.assessments.map((a) => [a.id || `${a.testDate}_${a.testName}`, a]));
      for (const a of assessments) {
        existingMap.set(a.id || `${a.testDate}_${a.testName}`, a);
      }
      serverSyncState.assessments = Array.from(existingMap.values());
    }

    if (Array.isArray(customTasks)) {
      serverSyncState.customTasks = customTasks;
    }

    if (settings) {
      serverSyncState.settings = settings;
    }

    if (Array.isArray(achievements)) {
      serverSyncState.achievements = achievements;
    }

    if (credentials) {
      serverSyncState.credentials = credentials;
    }

    serverSyncState.lastUpdated = Date.now();

    return NextResponse.json({
      status: "synced",
      timestamp: serverSyncState.lastUpdated,
      serverVersion: 1,
      data: serverSyncState,
    });
  } catch (error: any) {
    return NextResponse.json(
      { status: "error", message: error?.message || "Sync failed" },
      { status: 500 }
    );
  }
}
