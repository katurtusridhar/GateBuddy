"use client";

import React, { createContext, useContext, useEffect, useState, useCallback, useMemo, useRef } from "react";
import {
  ScheduleTask,
  TaskProgress,
  RecoveryAssignment,
  AssessmentResult,
  Achievement,
  CustomTask,
  UserSettings,
  TaskItemUi,
  TaskStatus,
  CandidateDateSlot,
  SyncState,
  UserCredentials,
} from "./types";
import { getTodayDateStr, compareDateStrings, isSundayDate } from "./dateUtils";
import { getCodeForSubject } from "./subjectRegistry";
import { INITIAL_ACHIEVEMENTS, evaluateAchievements } from "./achievementManager";
import { validateReschedule, getCandidateRescheduleDates, getCandidateSlotsInfo } from "./recoveryEngine";
import rawScheduleData from "@/data/gate_cse_schedule.json";

export interface UserProfile {
  id: string;
  email: string;
  name: string;
  username: string;
}

export const DEFAULT_CREDENTIALS: UserCredentials = {
  username: "katurtusridhar",
  password: "Sridhar@758",
  masterCode: "oA-THL,UXyh6",
};

// Seed user data matching real Android app state over Oct 19-22, 2026
const INITIAL_PROGRESS_LIST: TaskProgress[] = [
  {
    taskId: "DL_44",
    originalDate: "2026-10-21",
    isCompleted: true,
    completedAtTimestamp: 1729512000000,
    completionType: "ON_TIME",
    notes: "Completed on Wed Oct 21",
  },
];

const INITIAL_RECOVERIES_LIST: RecoveryAssignment[] = [
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
];

interface AppContextType {
  user: UserProfile | null;
  credentials: UserCredentials;
  tasks: ScheduleTask[];
  progress: Map<string, TaskProgress>;
  recoveries: RecoveryAssignment[];
  assessments: AssessmentResult[];
  customTasks: CustomTask[];
  settings: UserSettings;
  achievements: Achievement[];
  syncStatus: SyncState;
  todayDateStr: string;
  isLoaded: boolean;
  activeBacklogs: TaskItemUi[];
  candidateSlots: CandidateDateSlot[];

  // Actions
  toggleTaskComplete: (taskId: string, notes?: string) => Promise<{ success: boolean; error?: string }>;
  rescheduleTask: (taskId: string, newDate: string) => Promise<{ success: boolean; error?: string }>;
  batchReschedule: (assignments: Record<string, string>) => Promise<{ success: boolean; error?: string }>;
  addAssessment: (assessment: Omit<AssessmentResult, "id" | "createdAtTimestamp">) => Promise<void>;
  addCustomTask: (task: Omit<CustomTask, "id">) => Promise<void>;
  updateSettings: (newSettings: Partial<UserSettings>) => Promise<void>;
  changeCredentials: (newUsername: string, newPass: string, masterCode: string) => Promise<{ success: boolean; error?: string }>;
  login: (usernameOrEmail: string, pass: string) => Promise<{ success: boolean; error?: string }>;
  register: (email: string, pass: string, name?: string) => Promise<{ success: boolean; error?: string }>;
  logout: () => Promise<void>;
  syncWithCloud: () => Promise<void>;
  getTasksForDate: (dateStr: string) => TaskItemUi[];
}

const AppContext = createContext<AppContextType | null>(null);

const STORAGE_KEY_PREFIX = "gatebuddy_v2_";

export function AppProvider({ children }: { children: React.ReactNode }) {
  const [credentials, setCredentials] = useState<UserCredentials>(DEFAULT_CREDENTIALS);
  const [user, setUser] = useState<UserProfile | null>({
    id: "usr_katurtusridhar",
    email: "katurtusridhar@gatebuddy.com",
    name: "Sridhar",
    username: "katurtusridhar",
  });

  const [tasks] = useState<ScheduleTask[]>(rawScheduleData.tasks as ScheduleTask[]);
  const [progress, setProgress] = useState<Map<string, TaskProgress>>(() => {
    const map = new Map<string, TaskProgress>();
    INITIAL_PROGRESS_LIST.forEach((p) => map.set(p.taskId, p));
    return map;
  });
  const [recoveries, setRecoveries] = useState<RecoveryAssignment[]>(INITIAL_RECOVERIES_LIST);
  const [assessments, setAssessments] = useState<AssessmentResult[]>([]);
  const [customTasks, setCustomTasks] = useState<CustomTask[]>([]);
  const [achievements, setAchievements] = useState<Achievement[]>(INITIAL_ACHIEVEMENTS);
  const [settings, setSettings] = useState<UserSettings>({
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
  });

  const [syncStatus, setSyncStatus] = useState<SyncState>("synced");
  const [isLoaded, setIsLoaded] = useState(false);
  const [todayDateStr, setTodayDateStr] = useState(getTodayDateStr());
  const lastSyncTimestampRef = useRef<number>(Date.now());
  const broadcastChannelRef = useRef<BroadcastChannel | null>(null);

  // Midnight date watcher
  useEffect(() => {
    const timer = setInterval(() => {
      const nowStr = getTodayDateStr();
      if (nowStr !== todayDateStr) {
        setTodayDateStr(nowStr);
      }
    }, 5000);
    return () => clearInterval(timer);
  }, [todayDateStr]);

  // Multi-tab BroadcastChannel for realtime sync across tabs
  useEffect(() => {
    if (typeof window === "undefined" || !("BroadcastChannel" in window)) return;
    const channel = new BroadcastChannel("gatebuddy_realtime_channel");
    broadcastChannelRef.current = channel;

    channel.onmessage = (event) => {
      const msg = event.data;
      if (msg?.type === "REMOTE_MUTATION" && msg.payload) {
        const { progressArr, recs, assess, customs, stt, creds } = msg.payload;
        if (progressArr) {
          const map = new Map<string, TaskProgress>();
          progressArr.forEach((p: TaskProgress) => map.set(p.taskId, p));
          setProgress(map);
        }
        if (recs) setRecoveries(recs);
        if (assess) setAssessments(assess);
        if (customs) setCustomTasks(customs);
        if (stt) setSettings(stt);
        if (creds) setCredentials(creds);
      }
    };

    return () => {
      channel.close();
    };
  }, []);

  // Broadcast local changes to other tabs
  const broadcastChange = useCallback((payload: any) => {
    if (broadcastChannelRef.current) {
      try {
        broadcastChannelRef.current.postMessage({
          type: "REMOTE_MUTATION",
          payload,
          timestamp: Date.now(),
        });
      } catch {
        // ignore
      }
    }
  }, []);

  // Load local persisted state on client mount
  useEffect(() => {
    try {
      const savedCreds = localStorage.getItem(`${STORAGE_KEY_PREFIX}credentials`);
      if (savedCreds) {
        const parsed = JSON.parse(savedCreds);
        setCredentials(parsed);
      }

      const savedUser = localStorage.getItem(`${STORAGE_KEY_PREFIX}user`);
      if (savedUser) setUser(JSON.parse(savedUser));

      const savedProgress = localStorage.getItem(`${STORAGE_KEY_PREFIX}progress`);
      if (savedProgress) {
        const arr = JSON.parse(savedProgress) as TaskProgress[];
        const map = new Map<string, TaskProgress>();
        arr.forEach((p) => map.set(p.taskId, p));
        setProgress(map);
      }

      const savedRecoveries = localStorage.getItem(`${STORAGE_KEY_PREFIX}recoveries`);
      if (savedRecoveries) setRecoveries(JSON.parse(savedRecoveries));

      const savedAssessments = localStorage.getItem(`${STORAGE_KEY_PREFIX}assessments`);
      if (savedAssessments) setAssessments(JSON.parse(savedAssessments));

      const savedCustom = localStorage.getItem(`${STORAGE_KEY_PREFIX}custom_tasks`);
      if (savedCustom) setCustomTasks(JSON.parse(savedCustom));

      const savedSettings = localStorage.getItem(`${STORAGE_KEY_PREFIX}settings`);
      if (savedSettings) setSettings(JSON.parse(savedSettings));

      const savedAchievements = localStorage.getItem(`${STORAGE_KEY_PREFIX}achievements`);
      if (savedAchievements) setAchievements(JSON.parse(savedAchievements));
    } catch (e) {
      console.warn("Error reading local storage", e);
    } finally {
      setIsLoaded(true);
    }
  }, []);

  // Save to localStorage when state updates
  useEffect(() => {
    if (!isLoaded) return;
    try {
      localStorage.setItem(`${STORAGE_KEY_PREFIX}credentials`, JSON.stringify(credentials));
      localStorage.setItem(`${STORAGE_KEY_PREFIX}progress`, JSON.stringify(Array.from(progress.values())));
      localStorage.setItem(`${STORAGE_KEY_PREFIX}recoveries`, JSON.stringify(recoveries));
      localStorage.setItem(`${STORAGE_KEY_PREFIX}assessments`, JSON.stringify(assessments));
      localStorage.setItem(`${STORAGE_KEY_PREFIX}custom_tasks`, JSON.stringify(customTasks));
      localStorage.setItem(`${STORAGE_KEY_PREFIX}settings`, JSON.stringify(settings));
      localStorage.setItem(`${STORAGE_KEY_PREFIX}achievements`, JSON.stringify(achievements));
      if (user) localStorage.setItem(`${STORAGE_KEY_PREFIX}user`, JSON.stringify(user));
      else localStorage.removeItem(`${STORAGE_KEY_PREFIX}user`);

      broadcastChange({
        progressArr: Array.from(progress.values()),
        recs: recoveries,
        assess: assessments,
        customs: customTasks,
        stt: settings,
        creds: credentials,
      });
    } catch (e) {
      console.warn("Error saving to local storage", e);
    }
  }, [progress, recoveries, assessments, customTasks, settings, achievements, user, credentials, isLoaded, broadcastChange]);

  // Realtime Cloud Synchronization (Debounced push + Periodic background pull)
  const syncWithCloud = useCallback(async () => {
    setSyncStatus("syncing");
    try {
      const payload = {
        lastSyncTimestamp: Date.now(),
        progress: Array.from(progress.values()),
        recoveries,
        assessments,
        customTasks,
        settings,
        achievements,
        credentials,
      };

      const res = await fetch("/api/sync", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload),
      });

      if (res.ok) {
        lastSyncTimestampRef.current = Date.now();
        setSyncStatus("synced");
      } else {
        setSyncStatus("offline");
      }
    } catch {
      setSyncStatus("offline");
    }
  }, [progress, recoveries, assessments, customTasks, settings, achievements, credentials]);

  // Background realtime pull every 4 seconds to catch Android phone updates instantly
  useEffect(() => {
    if (!isLoaded) return;
    const interval = setInterval(async () => {
      try {
        const res = await fetch(`/api/sync?since=${lastSyncTimestampRef.current}`);
        if (!res.ok) return;
        const result = await res.json();
        if (result.hasUpdates && result.data) {
          lastSyncTimestampRef.current = result.timestamp;
          if (Array.isArray(result.data.progress)) {
            const map = new Map<string, TaskProgress>();
            result.data.progress.forEach((p: TaskProgress) => map.set(p.taskId, p));
            setProgress(map);
          }
          if (Array.isArray(result.data.recoveries)) {
            setRecoveries(result.data.recoveries);
          }
          if (Array.isArray(result.data.assessments)) {
            setAssessments(result.data.assessments);
          }
          if (Array.isArray(result.data.customTasks)) {
            setCustomTasks(result.data.customTasks);
          }
          if (result.data.settings) {
            setSettings(result.data.settings);
          }
          setSyncStatus("synced");
        }
      } catch {
        // silent background network error
      }
    }, 4000);
    return () => clearInterval(interval);
  }, [isLoaded]);

  // Sequential Lecture Index calculation matching Android ScheduleRepository.kt
  const getSequentialLecture = useCallback(
    (baseTask: ScheduleTask, targetDateStr: string) => {
      // Historical past dates preserve original canonical lecture definition
      if (compareDateStrings(targetDateStr, todayDateStr) < 0) {
        return baseTask;
      }

      if (baseTask.isSundayActivity || baseTask.isBuffer || baseTask.isRevision) {
        return baseTask;
      }

      const subjectTasks = tasks
        .filter((t) => t.subject === baseTask.subject && !t.isSundayActivity && !t.isBuffer && !t.isRevision)
        .sort((a, b) => a.curNum - b.curNum);

      if (subjectTasks.length === 0) return baseTask;

      // Count completed lectures for this subject strictly before this date/slot
      let completedBefore = 0;
      for (const t of tasks) {
        if (t.subject !== baseTask.subject || t.isSundayActivity || t.isBuffer || t.isRevision) continue;
        const isDone = progress.get(t.id)?.isCompleted === true;
        if (!isDone) continue;

        if (compareDateStrings(t.date, targetDateStr) < 0) {
          completedBefore++;
        } else if (t.date === targetDateStr && t.slot < baseTask.slot) {
          completedBefore++;
        }
      }

      // Add completed recoveries before this date
      for (const r of recoveries) {
        if (!r.isCompleted && progress.get(r.taskId)?.isCompleted !== true) continue;
        const recTask = tasks.find((t) => t.id === r.taskId);
        if (recTask?.subject !== baseTask.subject) continue;
        if (compareDateStrings(r.recoveryDate, targetDateStr) < 0) {
          completedBefore++;
        }
      }

      // If this specific slot is already completed, it holds its assigned lecture index
      if (progress.get(baseTask.id)?.isCompleted === true) {
        const idx = Math.min(Math.max(0, completedBefore), subjectTasks.length - 1);
        return {
          ...baseTask,
          curNum: subjectTasks[idx].curNum,
          totalCount: subjectTasks[idx].totalCount,
          title: subjectTasks[idx].title,
          details: subjectTasks[idx].details,
          milestone: subjectTasks[idx].milestone,
          sourcePage: subjectTasks[idx].sourcePage,
        };
      }

      // Count uncompleted earlier scheduled slots between today and target date
      let uncompletedEarlier = 0;
      let d = todayDateStr;
      while (compareDateStrings(d, targetDateStr) <= 0) {
        const dTasks = tasks.filter(
          (t) => t.date === d && t.subject === baseTask.subject && !t.isSundayActivity && !t.isBuffer && !t.isRevision
        );
        for (const dt of dTasks) {
          if (d === targetDateStr) {
            if (dt.slot < baseTask.slot && progress.get(dt.id)?.isCompleted !== true) {
              uncompletedEarlier++;
            }
          } else {
            if (progress.get(dt.id)?.isCompleted !== true) {
              uncompletedEarlier++;
            }
          }
        }
        // Count uncompleted recoveries on day d before targetDate
        if (compareDateStrings(d, targetDateStr) < 0) {
          const dRecs = recoveries.filter((r) => {
            const rt = tasks.find((t) => t.id === r.taskId);
            return (
              r.recoveryDate === d &&
              rt?.subject === baseTask.subject &&
              !r.isCompleted &&
              progress.get(r.taskId)?.isCompleted !== true
            );
          });
          uncompletedEarlier += dRecs.length;
        }

        // Increment day string safely
        const parts = d.split("-").map(Number);
        const nextDay = new Date(parts[0], parts[1] - 1, parts[2] + 1);
        d = `${nextDay.getFullYear()}-${String(nextDay.getMonth() + 1).padStart(2, "0")}-${String(nextDay.getDate()).padStart(2, "0")}`;
      }

      const sequentialIdx = Math.min(Math.max(0, completedBefore + uncompletedEarlier), subjectTasks.length - 1);
      const lecture = subjectTasks[sequentialIdx];

      return {
        ...baseTask,
        curNum: lecture.curNum,
        totalCount: lecture.totalCount,
        title: lecture.title,
        details: lecture.details,
        milestone: lecture.milestone,
        sourcePage: lecture.sourcePage,
      };
    },
    [tasks, progress, recoveries, todayDateStr]
  );

  // Transform tasks into UI TaskItemUi matching Android ScheduleRepository.kt
  const getTasksForDate = useCallback(
    (targetDateStr: string): TaskItemUi[] => {
      const isToday = targetDateStr === todayDateStr;
      const isPast = compareDateStrings(targetDateStr, todayDateStr) < 0;
      const isFuture = compareDateStrings(targetDateStr, todayDateStr) > 0;
      const isSunday = isSundayDate(targetDateStr);

      const items: TaskItemUi[] = [];

      if (isSunday) {
        // STRICT SUNDAY WORKFLOW (Weekly Assessment + Revision + PYQ + Rescheduled Tasks)
        // 1. Weekly Assessment Test
        const testId = `SUNDAY_TEST_${targetDateStr}`;
        const testProg = progress.get(testId);
        const isTestDone = testProg?.isCompleted === true;
        items.push({
          id: testId,
          originalDate: targetDateStr,
          displayDate: targetDateStr,
          dayOfWeek: "Sun",
          slot: 1,
          subject: "Weekly Assessment",
          subjectCode: "TEST",
          type: "SUNDAY_TEST",
          curNum: 1,
          totalCount: 1,
          title: "Weekly Active Recall & Mock Test",
          details: "Attempt weekly timed assessment and log marks in Mistake Notebook.",
          milestone: "",
          sourcePage: 1,
          isBuffer: false,
          isRevision: false,
          isSundayActivity: true,
          isBooster: false,
          status: isTestDone ? "COMPLETED_ON_TIME" : isPast ? "MISSED" : isToday ? "DUE_TODAY" : "UPCOMING",
          isCompleted: isTestDone,
          completedAtTimestamp: testProg?.completedAtTimestamp,
          completionType: testProg?.completionType,
          notes: testProg?.notes || "",
          pointsValue: 10,
          isAssignedForRecovery: false,
          isFuture,
          isPast,
          isCustom: false,
        });

        // 2. Sunday Active Revision
        const revId = `BOOSTER_REV_${targetDateStr}`;
        const revProg = progress.get(revId);
        const isRevDone = revProg?.isCompleted === true;
        items.push({
          id: revId,
          originalDate: targetDateStr,
          displayDate: targetDateStr,
          dayOfWeek: "Sun",
          slot: 2,
          subject: "Sunday Revision",
          subjectCode: "REV",
          type: "BOOSTER_REVISION",
          curNum: 1,
          totalCount: 1,
          title: "Sunday Active Recall & Formula Revision",
          details: "Review flashcards, core formulas, and theorem cheat-sheets (+5 pts).",
          milestone: "",
          sourcePage: 0,
          isBuffer: false,
          isRevision: true,
          isSundayActivity: true,
          isBooster: true,
          status: isRevDone ? "COMPLETED_ON_TIME" : isPast ? "MISSED" : isToday ? "DUE_TODAY" : "UPCOMING",
          isCompleted: isRevDone,
          completedAtTimestamp: revProg?.completedAtTimestamp,
          completionType: revProg?.completionType,
          notes: revProg?.notes || "",
          pointsValue: 5,
          isAssignedForRecovery: false,
          isFuture,
          isPast,
          isCustom: false,
        });

        // 3. Sunday Problem Solving & Mistake Notebook
        const psId = `BOOSTER_PS_${targetDateStr}`;
        const psProg = progress.get(psId);
        const isPsDone = psProg?.isCompleted === true;
        items.push({
          id: psId,
          originalDate: targetDateStr,
          displayDate: targetDateStr,
          dayOfWeek: "Sun",
          slot: 3,
          subject: "PYQ Practice",
          subjectCode: "PYQ",
          type: "BOOSTER_PROBLEM_SOLVING",
          curNum: 1,
          totalCount: 1,
          title: "Sunday PYQ Practice & Mistake Analysis",
          details: "Solve previous year questions and log errors in your mistake notebook (+5 pts).",
          milestone: "",
          sourcePage: 0,
          isBuffer: false,
          isRevision: false,
          isSundayActivity: true,
          isBooster: true,
          status: isPsDone ? "COMPLETED_ON_TIME" : isPast ? "MISSED" : isToday ? "DUE_TODAY" : "UPCOMING",
          isCompleted: isPsDone,
          completedAtTimestamp: psProg?.completedAtTimestamp,
          completionType: psProg?.completionType,
          notes: psProg?.notes || "",
          pointsValue: 5,
          isAssignedForRecovery: false,
          isFuture,
          isPast,
          isCustom: false,
        });
      } else {
        // Monday through Saturday: Scheduled Core Slots (Slots 1 & 2)
        const scheduled = tasks.filter((t) => t.date === targetDateStr);
        for (const t of scheduled) {
          const adaptedTask = getSequentialLecture(t, targetDateStr);
          const prog = progress.get(t.id);
          const isDone = prog?.isCompleted === true;
          const rec = recoveries.find(
            (r) => r.taskId === t.id && (r.status === "ACTIVE" || r.status === "MANUAL_CONFIRMED")
          );

          let status: TaskStatus = "UPCOMING";
          if (isDone) {
            status = prog?.completionType === "ON_TIME" ? "COMPLETED_ON_TIME" : "COMPLETED_LATE";
          } else if (rec && rec.status === "MANUAL_CONFIRMED") {
            status = "RECOVERY_SCHEDULED";
          } else if (isPast) {
            status = "MISSED";
          } else if (isToday) {
            status = "DUE_TODAY";
          }

          items.push({
            id: t.id,
            originalDate: t.date,
            displayDate: targetDateStr,
            dayOfWeek: t.dayOfWeek,
            slot: t.slot,
            subject: adaptedTask.subject,
            subjectCode: getCodeForSubject(adaptedTask.subject),
            type: adaptedTask.type as any,
            curNum: adaptedTask.curNum,
            totalCount: adaptedTask.totalCount,
            title: adaptedTask.title,
            details: adaptedTask.details || `Core GATE CSE lecture • Slot ${t.slot} • +10 points`,
            milestone: adaptedTask.milestone || "",
            sourcePage: adaptedTask.sourcePage,
            isBuffer: adaptedTask.isBuffer,
            isRevision: adaptedTask.isRevision,
            isSundayActivity: false,
            isBooster: false,
            status,
            isCompleted: isDone,
            completedAtTimestamp: prog?.completedAtTimestamp,
            completionType: prog?.completionType,
            notes: prog?.notes || "",
            pointsValue: 10,
            isAssignedForRecovery: Boolean(rec),
            recoveryDate: rec?.recoveryDate,
            isFuture,
            isPast,
            isCustom: false,
          });
        }

        // Slot 3: Daily Active Revision (+5 pts)
        const revKey = `BOOSTER_REV_${targetDateStr}`;
        const revDone = progress.get(revKey)?.isCompleted === true;
        items.push({
          id: revKey,
          originalDate: targetDateStr,
          displayDate: targetDateStr,
          dayOfWeek: "",
          slot: 3,
          subject: "Daily Revision",
          subjectCode: "REV",
          type: "BOOSTER_REVISION",
          curNum: 1,
          totalCount: 1,
          title: "Daily Active Revision",
          details: "30-min active recall of previously completed subject topics (+5 pts).",
          milestone: "",
          sourcePage: 0,
          isBuffer: false,
          isRevision: true,
          isSundayActivity: false,
          isBooster: true,
          status: revDone ? "COMPLETED_ON_TIME" : isPast ? "MISSED" : isToday ? "DUE_TODAY" : "UPCOMING",
          isCompleted: revDone,
          notes: progress.get(revKey)?.notes || "",
          pointsValue: 5,
          isAssignedForRecovery: false,
          isFuture,
          isPast,
          isCustom: false,
        });

        // Slot 4: Daily Problem Solving (+5 pts)
        const psKey = `BOOSTER_PS_${targetDateStr}`;
        const psDone = progress.get(psKey)?.isCompleted === true;
        items.push({
          id: psKey,
          originalDate: targetDateStr,
          displayDate: targetDateStr,
          dayOfWeek: "",
          slot: 4,
          subject: "Problem Solving",
          subjectCode: "PRB",
          type: "BOOSTER_PROBLEM_SOLVING",
          curNum: 1,
          totalCount: 1,
          title: "Daily Problem Solving",
          details: "Solve 10-15 standard GATE practice problems (+5 pts).",
          milestone: "",
          sourcePage: 0,
          isBuffer: false,
          isRevision: false,
          isSundayActivity: false,
          isBooster: true,
          status: psDone ? "COMPLETED_ON_TIME" : isPast ? "MISSED" : isToday ? "DUE_TODAY" : "UPCOMING",
          isCompleted: psDone,
          notes: progress.get(psKey)?.notes || "",
          pointsValue: 5,
          isAssignedForRecovery: false,
          isFuture,
          isPast,
          isCustom: false,
        });
      }

      // Add Recovery Catch-up tasks assigned to this date (max 2 per day)
      const recoveriesForDate = recoveries.filter(
        (r) => r.recoveryDate === targetDateStr && (r.status === "ACTIVE" || r.status === "MANUAL_CONFIRMED")
      );

      for (const rec of recoveriesForDate) {
        const originalTask = tasks.find((t) => t.id === rec.taskId);
        if (!originalTask) continue;
        const prog = progress.get(rec.taskId);
        const isDone = prog?.isCompleted === true || rec.isCompleted;

        let status: TaskStatus = "RECOVERY_SCHEDULED";
        if (isDone) {
          status = "COMPLETED_LATE";
        } else if (isToday) {
          status = "DUE_TODAY";
        } else if (isPast) {
          status = "MISSED";
        }

        const cleanTitle = originalTask.title.replace(/\s*\(Catch-Up\)/g, "").trim();

        items.push({
          id: `REC_${rec.taskId}`,
          originalDate: originalTask.date,
          displayDate: targetDateStr,
          dayOfWeek: "Sun",
          slot: 5,
          subject: originalTask.subject,
          subjectCode: getCodeForSubject(originalTask.subject),
          type: originalTask.type as any,
          curNum: originalTask.curNum,
          totalCount: originalTask.totalCount,
          title: `${cleanTitle} (Catch-Up)`,
          details: `Rescheduled Sunday catch-up • Slot 5 • +10 points`,
          milestone: "",
          sourcePage: originalTask.sourcePage,
          isBuffer: false,
          isRevision: false,
          isSundayActivity: false,
          isBooster: false,
          status,
          isCompleted: isDone,
          completedAtTimestamp: prog?.completedAtTimestamp,
          completionType: prog?.completionType,
          notes: prog?.notes || "",
          pointsValue: 10,
          isAssignedForRecovery: true,
          recoveryDate: targetDateStr,
          isFuture,
          isPast,
          isCustom: false,
        });
      }

      // Custom tasks
      const customs = customTasks.filter((c) => c.date === targetDateStr);
      for (const ct of customs) {
        items.push({
          id: `CUSTOM_${ct.id}`,
          originalDate: ct.date,
          displayDate: ct.date,
          dayOfWeek: "",
          slot: 9,
          subject: ct.subjectName,
          subjectCode: ct.subjectCode,
          type: "CUSTOM",
          curNum: 1,
          totalCount: 1,
          title: ct.title,
          details: ct.notes,
          milestone: "",
          sourcePage: 0,
          isBuffer: false,
          isRevision: false,
          isSundayActivity: false,
          isBooster: false,
          status: ct.isCompleted ? "COMPLETED_ON_TIME" : isPast ? "MISSED" : isToday ? "DUE_TODAY" : "UPCOMING",
          isCompleted: ct.isCompleted,
          notes: ct.notes,
          pointsValue: ct.pointsValue,
          isAssignedForRecovery: false,
          isFuture,
          isPast,
          isCustom: true,
        });
      }

      return items;
    },
    [tasks, progress, recoveries, customTasks, todayDateStr, getSequentialLecture]
  );

  // Active unrescheduled backlogs
  const activeBacklogs = useMemo(() => {
    const list: TaskItemUi[] = [];
    const pastTasks = tasks.filter(
      (t) =>
        compareDateStrings(t.date, todayDateStr) < 0 &&
        !t.isSundayActivity &&
        !t.isBuffer &&
        !t.isRevision &&
        !t.subject.toLowerCase().includes("revision") &&
        !t.subject.toLowerCase().includes("problem")
    );

    for (const t of pastTasks) {
      const isDone = progress.get(t.id)?.isCompleted === true;
      if (!isDone) {
        const rec = recoveries.find(
          (r) => r.taskId === t.id && (r.status === "ACTIVE" || r.status === "MANUAL_CONFIRMED")
        );
        if (!rec) {
          list.push({
            id: t.id,
            originalDate: t.date,
            displayDate: t.date,
            dayOfWeek: t.dayOfWeek,
            slot: t.slot,
            subject: t.subject,
            subjectCode: getCodeForSubject(t.subject),
            type: t.type as any,
            curNum: t.curNum,
            totalCount: t.totalCount,
            title: t.title,
            details: t.details || "",
            milestone: t.milestone || "",
            sourcePage: t.sourcePage,
            isBuffer: false,
            isRevision: false,
            isSundayActivity: false,
            isBooster: false,
            status: "MISSED",
            isCompleted: false,
            notes: "",
            pointsValue: 10,
            isAssignedForRecovery: false,
            isFuture: false,
            isPast: true,
            isCustom: false,
          });
        }
      }
    }
    return list;
  }, [tasks, progress, recoveries, todayDateStr]);

  // Candidate slots for recovery
  const candidateSlots = useMemo(() => {
    const candidateDates = getCandidateRescheduleDates(todayDateStr, tasks);
    return getCandidateSlotsInfo(todayDateStr, candidateDates, recoveries, tasks);
  }, [todayDateStr, tasks, recoveries]);

  // Action: Toggle task complete
  const toggleTaskComplete = useCallback(
    async (rawTaskId: string, notes: string = "") => {
      const cleanId = rawTaskId.replace(/^REC_/, "").replace(/^CUSTOM_/, "");

      if (rawTaskId.startsWith("CUSTOM_")) {
        const idNum = Number(cleanId);
        setCustomTasks((prev) =>
          prev.map((c) => (c.id === idNum ? { ...c, isCompleted: !c.isCompleted, notes } : c))
        );
        setTimeout(syncWithCloud, 100);
        return { success: true };
      }

      const currentProg = progress.get(cleanId);
      const isCurrentlyDone = currentProg?.isCompleted === true;

      // Handle boosters
      if (cleanId.startsWith("BOOSTER_") || cleanId.startsWith("SUNDAY_TEST_")) {
        const newProg: TaskProgress = {
          taskId: cleanId,
          originalDate: todayDateStr,
          isCompleted: !isCurrentlyDone,
          completedAtTimestamp: !isCurrentlyDone ? Date.now() : null,
          completionType: !isCurrentlyDone ? "ON_TIME" : null,
          notes,
        };
        setProgress((prev) => new Map(prev).set(cleanId, newProg));
        setTimeout(syncWithCloud, 100);
        return { success: true };
      }

      const targetTask = tasks.find((t) => t.id === cleanId);
      if (!targetTask) return { success: false, error: "Task not found" };

      const rec = recoveries.find(
        (r) => r.taskId === cleanId && (r.status === "ACTIVE" || r.status === "MANUAL_CONFIRMED")
      );

      const effectiveDate = rec ? rec.recoveryDate : targetTask.date;

      // Rule: Past tasks cannot be toggled directly unless assigned to today's recovery slot
      if (compareDateStrings(effectiveDate, todayDateStr) < 0 && !isCurrentlyDone) {
        return {
          success: false,
          error: "Strict Rule: Past missed tasks cannot be marked complete on past days. Please reschedule them to a catch-up slot.",
        };
      }

      const newIsDone = !isCurrentlyDone;
      const completionType = newIsDone
        ? effectiveDate === todayDateStr
          ? rec
            ? "RECOVERY"
            : "ON_TIME"
          : "LATE"
        : null;

      const updatedProg: TaskProgress = {
        taskId: cleanId,
        originalDate: targetTask.date,
        isCompleted: newIsDone,
        completedAtTimestamp: newIsDone ? Date.now() : null,
        completionType,
        notes,
      };

      setProgress((prev) => new Map(prev).set(cleanId, updatedProg));

      if (rec && newIsDone) {
        setRecoveries((prev) =>
          prev.map((r) =>
            r.taskId === cleanId ? { ...r, isCompleted: true, status: "COMPLETED", completedAtTimestamp: Date.now() } : r
          )
        );
      }

      setTimeout(() => {
        setAchievements((prev) => evaluateAchievements(tasks, Array.from(progress.values()), activeBacklogs.length, prev));
        syncWithCloud();
      }, 50);

      return { success: true };
    },
    [tasks, progress, recoveries, todayDateStr, activeBacklogs.length, syncWithCloud]
  );

  // Action: Reschedule single task
  const rescheduleTask = useCallback(
    async (rawTaskId: string, newDate: string) => {
      const cleanId = rawTaskId.replace(/^REC_/, "");
      const validation = validateReschedule(cleanId, newDate, todayDateStr, recoveries, tasks);

      if (!validation.valid) {
        return { success: false, error: validation.error };
      }

      const targetTask = tasks.find((t) => t.id === cleanId);
      if (!targetTask) return { success: false, error: "Task not found" };

      setRecoveries((prev) => {
        const existing = prev.find((r) => r.taskId === cleanId);
        if (existing) {
          return prev.map((r) =>
            r.taskId === cleanId ? { ...r, recoveryDate: newDate, status: "MANUAL_CONFIRMED" } : r
          );
        } else {
          const newRec: RecoveryAssignment = {
            id: Date.now(),
            taskId: cleanId,
            originalDate: targetTask.date,
            recoveryDate: newDate,
            assignedAtTimestamp: Date.now(),
            isCompleted: false,
            priorityLevel: 2,
            status: "MANUAL_CONFIRMED",
          };
          return [...prev, newRec];
        }
      });

      setTimeout(syncWithCloud, 50);
      return { success: true };
    },
    [recoveries, tasks, todayDateStr, syncWithCloud]
  );

  // Action: Batch reschedule
  const batchReschedule = useCallback(
    async (assignments: Record<string, string>) => {
      const byDate = new Map<string, string[]>();
      for (const [rawId, dateStr] of Object.entries(assignments)) {
        const cleanId = rawId.replace(/^REC_/, "");
        if (compareDateStrings(dateStr, todayDateStr) < 0) {
          return { success: false, error: `Cannot reschedule to past date (${dateStr}).` };
        }
        const arr = byDate.get(dateStr) || [];
        arr.push(cleanId);
        byDate.set(dateStr, arr);
      }

      for (const [dateStr, taskIds] of Array.from(byDate.entries())) {
        const existingOnDate = recoveries.filter(
          (r) =>
            r.recoveryDate === dateStr &&
            !taskIds.includes(r.taskId) &&
            (r.status === "ACTIVE" || r.status === "MANUAL_CONFIRMED")
        );

        const totalCount = existingOnDate.length + taskIds.length;
        if (totalCount > 2) {
          return {
            success: false,
            error: `Rule 1 Violation: ${dateStr} would have ${totalCount} lectures. Maximum 2 allowed.`,
          };
        }

        const subjectsOnDate = new Set<string>();
        for (const r of existingOnDate) {
          const t = tasks.find((item) => item.id === r.taskId);
          if (t) subjectsOnDate.add(t.subject.toLowerCase().trim());
        }

        for (const id of taskIds) {
          const t = tasks.find((item) => item.id === id);
          if (t) {
            const subj = t.subject.toLowerCase().trim();
            if (subjectsOnDate.has(subj)) {
              return {
                success: false,
                error: `Rule 2 Violation: Multiple lectures for '${t.subject}' assigned to ${dateStr}.`,
              };
            }
            subjectsOnDate.add(subj);
          }
        }
      }

      setRecoveries((prev) => {
        const map = new Map(prev.map((r) => [r.taskId, r]));
        for (const [rawId, dateStr] of Object.entries(assignments)) {
          const cleanId = rawId.replace(/^REC_/, "");
          const targetTask = tasks.find((t) => t.id === cleanId);
          if (!targetTask) continue;

          const existing = map.get(cleanId);
          if (existing) {
            map.set(cleanId, { ...existing, recoveryDate: dateStr, status: "MANUAL_CONFIRMED" });
          } else {
            map.set(cleanId, {
              id: Date.now() + Math.floor(Math.random() * 1000),
              taskId: cleanId,
              originalDate: targetTask.date,
              recoveryDate: dateStr,
              assignedAtTimestamp: Date.now(),
              isCompleted: false,
              priorityLevel: 2,
              status: "MANUAL_CONFIRMED",
            });
          }
        }
        return Array.from(map.values());
      });

      setTimeout(syncWithCloud, 50);
      return { success: true };
    },
    [recoveries, tasks, todayDateStr, syncWithCloud]
  );

  const addAssessment = useCallback(
    async (assessment: Omit<AssessmentResult, "id" | "createdAtTimestamp">) => {
      const newRecord: AssessmentResult = {
        ...assessment,
        id: Date.now(),
        createdAtTimestamp: Date.now(),
      };
      setAssessments((prev) => [newRecord, ...prev]);
      setTimeout(syncWithCloud, 50);
    },
    [syncWithCloud]
  );

  const addCustomTask = useCallback(
    async (task: Omit<CustomTask, "id">) => {
      const newRecord: CustomTask = {
        ...task,
        id: Date.now(),
      };
      setCustomTasks((prev) => [...prev, newRecord]);
      setTimeout(syncWithCloud, 50);
    },
    [syncWithCloud]
  );

  const updateSettings = useCallback(
    async (newSettings: Partial<UserSettings>) => {
      setSettings((prev) => ({ ...prev, ...newSettings }));
      setTimeout(syncWithCloud, 50);
    },
    [syncWithCloud]
  );

  // Action: Master Code Protected Credential Modification
  const changeCredentials = useCallback(
    async (newUsername: string, newPass: string, masterCode: string) => {
      if (masterCode.trim() !== credentials.masterCode) {
        return {
          success: false,
          error: "Master Security Code invalid. Authorization denied. Please enter the correct master code to modify credentials.",
        };
      }

      if (!newUsername.trim() || !newPass.trim()) {
        return { success: false, error: "Username and password cannot be empty." };
      }

      const updated: UserCredentials = {
        username: newUsername.trim(),
        password: newPass.trim(),
        masterCode: credentials.masterCode,
      };

      setCredentials(updated);
      setUser((prev) =>
        prev
          ? { ...prev, username: updated.username, name: updated.username }
          : { id: "usr_custom", email: `${updated.username}@gatebuddy.com`, name: updated.username, username: updated.username }
      );

      setTimeout(syncWithCloud, 50);
      return { success: true };
    },
    [credentials, syncWithCloud]
  );

  // Action: Login
  const login = useCallback(
    async (usernameOrEmail: string, pass: string) => {
      const cleanInput = usernameOrEmail.trim().toLowerCase();
      const validUser = credentials.username.toLowerCase();
      const validEmail = `${credentials.username.toLowerCase()}@gatebuddy.com`;

      const isUserMatch = cleanInput === validUser || cleanInput === validEmail || cleanInput === "sridhar";
      const isPassMatch = pass === credentials.password;

      if (isUserMatch && isPassMatch) {
        const u: UserProfile = {
          id: "usr_" + credentials.username,
          email: `${credentials.username}@gatebuddy.com`,
          name: "Sridhar",
          username: credentials.username,
        };
        setUser(u);
        return { success: true };
      }

      return {
        success: false,
        error: "Invalid credentials. Use username: katurtusridhar and password: Sridhar@758",
      };
    },
    [credentials]
  );

  const register = useCallback(
    async (email: string, pass: string, name?: string) => {
      const u: UserProfile = {
        id: "usr_" + Math.random().toString(36).substring(2, 9),
        email,
        name: name || "Sridhar",
        username: email.split("@")[0] || "katurtusridhar",
      };
      setUser(u);
      return { success: true };
    },
    []
  );

  const logout = useCallback(async () => {
    setUser(null);
  }, []);

  return (
    <AppContext.Provider
      value={{
        user,
        credentials,
        tasks,
        progress,
        recoveries,
        assessments,
        customTasks,
        settings,
        achievements,
        syncStatus,
        todayDateStr,
        isLoaded,
        activeBacklogs,
        candidateSlots,
        toggleTaskComplete,
        rescheduleTask,
        batchReschedule,
        addAssessment,
        addCustomTask,
        updateSettings,
        changeCredentials,
        login,
        register,
        logout,
        syncWithCloud,
        getTasksForDate,
      }}
    >
      {children}
    </AppContext.Provider>
  );
}

export function useApp() {
  const ctx = useContext(AppContext);
  if (!ctx) throw new Error("useApp must be used within AppProvider");
  return ctx;
}
