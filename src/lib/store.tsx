"use client";

import React, { createContext, useContext, useEffect, useState, useCallback, useMemo, useRef } from "react";
import {
  ScheduleTask,
  TaskProgress,
  RecoveryAssignment,
  AssessmentResult,
  DppRecord,
  Achievement,
  CustomTask,
  UserSettings,
  TaskItemUi,
  TaskStatus,
  CandidateDateSlot,
  SyncState,
  UserCredentials,
} from "./types";
import { getTodayDateStr, compareDateStrings, isSundayDate, formatDateStr } from "./dateUtils";
import { getCodeForSubject } from "./subjectRegistry";
import { INITIAL_ACHIEVEMENTS } from "./achievementManager";
import { getCandidateRescheduleDates, getCandidateSlotsInfo } from "./recoveryEngine";
import rawScheduleData from "@/data/gate_cse_schedule.json";

export interface UserProfile {
  id: string;
  email: string;
  name: string;
  username: string;
}

export const DEFAULT_CREDENTIALS: UserCredentials = {
  username: "katurtusridhar",
};

export function matchScheduleTask(task: ScheduleTask, queryId: string | undefined): boolean {
  if (!queryId || !task) return false;
  const clean = queryId.replace(/^REC_/, "").trim();
  if (task.id === clean) return true;
  const codeId = `${getCodeForSubject(task.subject)}_${task.curNum}`;
  return codeId === clean;
}

export function findMatchingScheduleTask(tasks: ScheduleTask[], queryId: string | undefined): ScheduleTask | undefined {
  if (!queryId) return undefined;
  const clean = queryId.replace(/^REC_/, "").trim();
  return tasks.find((t) => matchScheduleTask(t, clean));
}

// Authoritative mirror: Pure state driven strictly from Android Companion sync & local storage
const INITIAL_PROGRESS_LIST: TaskProgress[] = [];
const INITIAL_RECOVERIES_LIST: RecoveryAssignment[] = [];

interface AppContextType {
  user: UserProfile | null;
  credentials: UserCredentials;
  tasks: ScheduleTask[];
  progress: Map<string, TaskProgress>;
  recoveries: RecoveryAssignment[];
  assessments: AssessmentResult[];
  dppList: DppRecord[];
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

const STORAGE_KEY_PREFIX = "gatebuddy_v4_";

export function hasValidAuthCookie(): boolean {
  if (typeof document === "undefined") return false;
  const match = document.cookie.match(/(?:^|;\s*)gatebuddy_auth=([^;]+)/);
  if (!match || !match[1]) return false;
  const val = decodeURIComponent(match[1].trim());
  return val.length > 0 && val !== '""' && val !== "deleted";
}

export function AppProvider({ children }: { children: React.ReactNode }) {
  const [credentials, setCredentials] = useState<UserCredentials>(DEFAULT_CREDENTIALS);
  const [user, setUser] = useState<UserProfile | null>(() => {
    if (typeof window !== "undefined") {
      try {
        if (!hasValidAuthCookie()) return null;
        const saved = localStorage.getItem(`${STORAGE_KEY_PREFIX}user`);
        if (saved) return JSON.parse(saved);
        return {
          id: "usr_katurtusridhar",
          email: "katurtusridhar@gatebuddy.com",
          name: "Sridhar",
          username: "katurtusridhar",
        };
      } catch {}
    }
    return null;
  });

  const [tasks] = useState<ScheduleTask[]>(rawScheduleData.tasks as ScheduleTask[]);
  const [progress, setProgress] = useState<Map<string, TaskProgress>>(() => {
    const map = new Map<string, TaskProgress>();
    INITIAL_PROGRESS_LIST.forEach((p) => map.set(p.taskId, p));
    return map;
  });
  const [recoveries, setRecoveries] = useState<RecoveryAssignment[]>(INITIAL_RECOVERIES_LIST);
  const [assessments, setAssessments] = useState<AssessmentResult[]>([]);
  const [dppList, setDppList] = useState<DppRecord[]>([]);
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

  // Synchronous authoritative state refs to eliminate stale closure bugs completely
  const progressRef = useRef<Map<string, TaskProgress>>(progress);
  const recoveriesRef = useRef<RecoveryAssignment[]>(recoveries);
  const customTasksRef = useRef<CustomTask[]>(customTasks);
  const assessmentsRef = useRef<AssessmentResult[]>(assessments);
  const dppListRef = useRef<DppRecord[]>(dppList);
  const achievementsRef = useRef<Achievement[]>(achievements);
  const settingsRef = useRef<UserSettings>(settings);
  const credentialsRef = useRef<UserCredentials>(credentials);

  // Keep refs synchronized with React state
  useEffect(() => { progressRef.current = progress; }, [progress]);
  useEffect(() => { recoveriesRef.current = recoveries; }, [recoveries]);
  useEffect(() => { customTasksRef.current = customTasks; }, [customTasks]);
  useEffect(() => { assessmentsRef.current = assessments; }, [assessments]);
  useEffect(() => { dppListRef.current = dppList; }, [dppList]);
  useEffect(() => { achievementsRef.current = achievements; }, [achievements]);
  useEffect(() => { settingsRef.current = settings; }, [settings]);
  useEffect(() => { credentialsRef.current = credentials; }, [credentials]);

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
        const { progressArr, recs, assess, dpps, customs, stt, creds } = msg.payload;
        if (progressArr) {
          const map = new Map<string, TaskProgress>();
          progressArr.forEach((p: TaskProgress) => map.set(p.taskId, p));
          setProgress(map);
        }
        if (recs) setRecoveries(recs);
        if (assess) setAssessments(assess);
        if (dpps) setDppList(dpps);
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
      // Clear any legacy auto-saved user keys from earlier builds
      localStorage.removeItem("gatebuddy_v2_user");
      localStorage.removeItem("gatebuddy_v2_credentials");
      localStorage.removeItem("gatebuddy_user");

      const hasAuth = hasValidAuthCookie();
      if (!hasAuth) {
        setUser(null);
        localStorage.removeItem(`${STORAGE_KEY_PREFIX}user`);
      } else {
        const savedUser = localStorage.getItem(`${STORAGE_KEY_PREFIX}user`);
        if (savedUser) {
          try {
            setUser(JSON.parse(savedUser));
          } catch {
            const fallbackUser: UserProfile = {
              id: "usr_katurtusridhar",
              email: "katurtusridhar@gatebuddy.com",
              name: "Sridhar",
              username: "katurtusridhar",
            };
            setUser(fallbackUser);
            localStorage.setItem(`${STORAGE_KEY_PREFIX}user`, JSON.stringify(fallbackUser));
          }
        } else {
          const fallbackUser: UserProfile = {
            id: "usr_katurtusridhar",
            email: "katurtusridhar@gatebuddy.com",
            name: "Sridhar",
            username: "katurtusridhar",
          };
          setUser(fallbackUser);
          localStorage.setItem(`${STORAGE_KEY_PREFIX}user`, JSON.stringify(fallbackUser));
        }

        // Asynchronously revalidate session with server
        fetch("/api/auth/me")
          .then((res) => (res.ok ? res.json() : null))
          .then((data) => {
            if (data?.success && data?.user) {
              setUser(data.user);
              try {
                localStorage.setItem(`${STORAGE_KEY_PREFIX}user`, JSON.stringify(data.user));
              } catch {}
            }
          })
          .catch(() => {});
      }

      const savedCreds = localStorage.getItem(`${STORAGE_KEY_PREFIX}credentials`);
      if (savedCreds) {
        const parsed = JSON.parse(savedCreds);
        setCredentials(parsed);
      }

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

      const savedDpp = localStorage.getItem(`${STORAGE_KEY_PREFIX}dpp_records`);
      if (savedDpp) setDppList(JSON.parse(savedDpp));

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
      localStorage.setItem(`${STORAGE_KEY_PREFIX}dpp_records`, JSON.stringify(dppList));
      localStorage.setItem(`${STORAGE_KEY_PREFIX}custom_tasks`, JSON.stringify(customTasks));
      localStorage.setItem(`${STORAGE_KEY_PREFIX}settings`, JSON.stringify(settings));
      localStorage.setItem(`${STORAGE_KEY_PREFIX}achievements`, JSON.stringify(achievements));
      if (user) localStorage.setItem(`${STORAGE_KEY_PREFIX}user`, JSON.stringify(user));
      else localStorage.removeItem(`${STORAGE_KEY_PREFIX}user`);

      broadcastChange({
        progressArr: Array.from(progress.values()),
        recs: recoveries,
        assess: assessments,
        dpps: dppList,
        customs: customTasks,
        stt: settings,
        creds: credentials,
      });
    } catch (e) {
      console.warn("Error saving to local storage", e);
    }
  }, [progress, recoveries, assessments, dppList, customTasks, settings, achievements, user, credentials, isLoaded, broadcastChange]);

  // Unidirectional App -> Website Sync:
  // The website acts as a pure read-only mirror/replica of the Android App.
  // The website NEVER pushes task progress or recovery modifications to the server.
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  const syncWithCloud = useCallback(async (..._args: any[]) => {
    // Strictly no-op: Task progress and recoveries are authored exclusively on the Android App.
  }, []);

  // Initial pull on mount to fetch authoritative App state from cloud immediately
  useEffect(() => {
    if (!isLoaded) return;
    let isCancelled = false;
    (async () => {
      try {
        const res = await fetch("/api/sync?since=0");
        if (!res.ok || isCancelled) return;
        const result = await res.json();
        if (result.data) {
          lastSyncTimestampRef.current = result.timestamp || Date.now();
          if (Array.isArray(result.data.progress)) {
            const map = new Map<string, TaskProgress>();
            result.data.progress.forEach((p: TaskProgress) => {
              map.set(p.taskId, p);
            });
            progressRef.current = map;
            setProgress(map);
          }
          if (Array.isArray(result.data.recoveries)) {
            recoveriesRef.current = result.data.recoveries;
            setRecoveries(result.data.recoveries);
          }
          if (Array.isArray(result.data.assessments)) {
            setAssessments(result.data.assessments);
            assessmentsRef.current = result.data.assessments;
          }

          if (Array.isArray(result.data.dppRecords)) {
            setDppList(result.data.dppRecords);
            dppListRef.current = result.data.dppRecords;
            try {
              localStorage.setItem(`${STORAGE_KEY_PREFIX}dpp_records`, JSON.stringify(result.data.dppRecords));
            } catch {}
          }
          if (Array.isArray(result.data.customTasks) && result.data.customTasks.length > 0) {
            setCustomTasks(result.data.customTasks);
            customTasksRef.current = result.data.customTasks;
          }
          if (result.data.settings) {
            setSettings(result.data.settings);
            settingsRef.current = result.data.settings;
          }
          if (Array.isArray(result.data.achievements) && result.data.achievements.length > 0) {
            setAchievements(result.data.achievements);
            achievementsRef.current = result.data.achievements;
          }
          setSyncStatus("synced");
        }
      } catch (e) {
        console.warn("Initial sync pull error", e);
      }
    })();
    return () => {
      isCancelled = true;
    };
  }, [isLoaded]);

  // Background realtime pull every 3 seconds to catch Android phone updates instantly
  useEffect(() => {
    if (!isLoaded) return;

    const pullAuthoritativeSync = async (forceFull: boolean = false) => {
      try {
        const query = forceFull ? "/api/sync?since=0" : `/api/sync?since=${lastSyncTimestampRef.current}`;
        const res = await fetch(query);
        if (!res.ok) return;
        const result = await res.json();
        if ((forceFull || result.hasUpdates) && result.data) {
          lastSyncTimestampRef.current = result.timestamp || Date.now();

          if (Array.isArray(result.data.progress)) {
            const map = new Map<string, TaskProgress>();
            result.data.progress.forEach((p: TaskProgress) => {
              map.set(p.taskId, p);
            });
            progressRef.current = map;
            setProgress(map);
            try {
              localStorage.setItem(`${STORAGE_KEY_PREFIX}progress`, JSON.stringify(Array.from(map.values())));
            } catch {}
          }

          if (Array.isArray(result.data.recoveries)) {
            const nextRecs = result.data.recoveries;
            recoveriesRef.current = nextRecs;
            setRecoveries(nextRecs);
            try {
              localStorage.setItem(`${STORAGE_KEY_PREFIX}recoveries`, JSON.stringify(nextRecs));
            } catch {}
          }

          if (Array.isArray(result.data.assessments)) {
            setAssessments(result.data.assessments);
            assessmentsRef.current = result.data.assessments;
          }

          if (Array.isArray(result.data.dppRecords)) {
            setDppList(result.data.dppRecords);
            dppListRef.current = result.data.dppRecords;
            try {
              localStorage.setItem(`${STORAGE_KEY_PREFIX}dpp_records`, JSON.stringify(result.data.dppRecords));
            } catch {}
          }

          if (Array.isArray(result.data.customTasks)) {
            setCustomTasks(result.data.customTasks);
            customTasksRef.current = result.data.customTasks;
          }

          if (result.data.settings) {
            setSettings(result.data.settings);
            settingsRef.current = result.data.settings;
          }

          if (Array.isArray(result.data.achievements)) {
            setAchievements((prev) => {
              const map = new Map(prev.map((a) => [a.code, a]));
              let changed = false;
              result.data.achievements.forEach((remoteAch: Achievement) => {
                const local = map.get(remoteAch.code);
                if (local) {
                  if (remoteAch.isUnlocked && !local.isUnlocked) {
                    map.set(remoteAch.code, {
                      ...local,
                      isUnlocked: true,
                      unlockedAtTimestamp: remoteAch.unlockedAtTimestamp || Date.now(),
                    });
                    changed = true;
                  }
                } else {
                  map.set(remoteAch.code, remoteAch);
                  changed = true;
                }
              });
              if (changed) {
                const nextAchs = Array.from(map.values());
                achievementsRef.current = nextAchs;
                return nextAchs;
              }
              return prev;
            });
          }

          setSyncStatus("synced");
        }
      } catch {
        // silent background network error
      }
    };

    const interval = setInterval(() => pullAuthoritativeSync(false), 3000);
    const onFocus = () => pullAuthoritativeSync(true);
    const onVisibility = () => {
      if (document.visibilityState === "visible") pullAuthoritativeSync(true);
    };

    window.addEventListener("focus", onFocus);
    document.addEventListener("visibilitychange", onVisibility);

    return () => {
      clearInterval(interval);
      window.removeEventListener("focus", onFocus);
      document.removeEventListener("visibilitychange", onVisibility);
    };
  }, [isLoaded]);

  // Canonical ordered core lectures for Track 1 (Slot 1) and Track 2 (Slot 2)
  const track1Lectures = useMemo(() => {
    return tasks
      .filter((t) => (t.type === "RECORDED" || t.type === "LIVE") && t.slot === 1)
      .sort((a, b) => compareDateStrings(a.date, b.date));
  }, [tasks]);

  const track2Lectures = useMemo(() => {
    return tasks
      .filter((t) => (t.type === "RECORDED" || t.type === "LIVE") && t.slot === 2)
      .sort((a, b) => compareDateStrings(a.date, b.date));
  }, [tasks]);

  const getTrackForSubject = useCallback(
    (subject: string) => {
      if (track1Lectures.some((t) => t.subject === subject)) return 1;
      if (track2Lectures.some((t) => t.subject === subject)) return 2;
      const clean = (subject || "").toLowerCase();
      if (
        clean.includes("digital") ||
        clean.includes("dbms") ||
        clean.includes("c prog") ||
        clean.includes("fundamental of c") ||
        clean.includes("data struct") ||
        clean.includes("engg math") ||
        clean.includes("linear algebra") ||
        clean.includes("probability") ||
        clean.includes("calculus") ||
        clean.includes("general aptitude") ||
        clean.includes("toc") ||
        clean.includes("compiler")
      ) {
        return 1;
      }
      return 2;
    },
    [track1Lectures, track2Lectures]
  );

  // Sequential Lecture Index calculation matching Android ScheduleRepository.kt
  const getSequentialLecture = useCallback(
    (baseTask: ScheduleTask, targetDateStr: string, isRecovery: boolean = false, recoveryOrderIndex: number = 0) => {
      // Historical past dates preserve original canonical lecture definition
      if (compareDateStrings(targetDateStr, todayDateStr) < 0) {
        return baseTask;
      }

      if (baseTask.isSundayActivity || baseTask.isBuffer || baseTask.isRevision) {
        return baseTask;
      }

      const trackNum = isRecovery
        ? getTrackForSubject(baseTask.subject)
        : baseTask.slot === 1 || baseTask.slot === 2
        ? baseTask.slot
        : getTrackForSubject(baseTask.subject);

      const canonicalList = trackNum === 1 ? track1Lectures : track2Lectures;
      if (canonicalList.length === 0) return baseTask;

      // 1. Calculate how many lectures for this track were COMPLETED strictly before targetDateStr
      let completedBefore = 0;
      for (let i = 0; i < canonicalList.length; i++) {
        const t = canonicalList[i];
        const codeId = `${getCodeForSubject(t.subject)}_${t.curNum}`;
        if (progress.get(t.id)?.isCompleted === true || progress.get(codeId)?.isCompleted === true) {
          completedBefore++;
        }
      }

      const codeSlotId = `${getCodeForSubject(baseTask.subject)}_${baseTask.curNum}`;
      const isThisSlotCompleted =
        progress.get(baseTask.id)?.isCompleted === true || progress.get(codeSlotId)?.isCompleted === true;
      if (isThisSlotCompleted) {
        const idx = Math.min(Math.max(0, completedBefore - 1), canonicalList.length - 1);
        const lecture = canonicalList[idx];
        return {
          ...baseTask,
          subject: lecture.subject,
          curNum: lecture.curNum,
          totalCount: lecture.totalCount,
          title: isRecovery ? `${lecture.subject} — Lec ${lecture.curNum}/${lecture.totalCount}` : lecture.title,
          details: isRecovery ? "Rescheduled Sunday catch-up • Slot 5 • +10 points" : lecture.details,
          milestone: lecture.milestone,
          sourcePage: lecture.sourcePage,
        };
      }

      // Count uncompleted earlier scheduled slots between today and target date
      let uncompletedEarlier = 0;
      let d = todayDateStr;
      while (compareDateStrings(d, targetDateStr) <= 0) {
        const dCoreTask = tasks.find(
          (t) => t.date === d && t.slot === trackNum && (t.type === "RECORDED" || t.type === "LIVE")
        );
        if (dCoreTask) {
          if (d === targetDateStr) {
            if (isRecovery) {
              if (progress.get(dCoreTask.id)?.isCompleted !== true) {
                uncompletedEarlier++;
              }
            }
          } else {
            if (progress.get(dCoreTask.id)?.isCompleted !== true) {
              uncompletedEarlier++;
            }
          }
        }

        // Count uncompleted recoveries on day d belonging to this track
        const dRecs = recoveries.filter((r) => {
          const rt = findMatchingScheduleTask(tasks, r.taskId);
          const sub = rt?.subject || "";
          const isDone =
            r.isCompleted ||
            progress.get(r.taskId)?.isCompleted === true ||
            (rt && (progress.get(rt.id)?.isCompleted === true || progress.get(`${getCodeForSubject(rt.subject)}_${rt.curNum}`)?.isCompleted === true));
          return (
            r.recoveryDate === d &&
            getTrackForSubject(sub) === trackNum &&
            !isDone
          );
        });

        if (compareDateStrings(d, targetDateStr) < 0) {
          uncompletedEarlier += dRecs.length;
        } else if (d === targetDateStr && isRecovery) {
          uncompletedEarlier += recoveryOrderIndex;
        }

        const parts = d.split("-").map(Number);
        const nextDay = new Date(parts[0], parts[1] - 1, parts[2] + 1);
        d = `${nextDay.getFullYear()}-${String(nextDay.getMonth() + 1).padStart(2, "0")}-${String(nextDay.getDate()).padStart(2, "0")}`;
      }

      const sequentialIdx = Math.min(Math.max(0, completedBefore + uncompletedEarlier), canonicalList.length - 1);
      const lecture = canonicalList[sequentialIdx];

      return {
        ...baseTask,
        subject: lecture.subject,
        curNum: lecture.curNum,
        totalCount: lecture.totalCount,
        title: isRecovery ? `${lecture.subject} — Lec ${lecture.curNum}/${lecture.totalCount}` : lecture.title,
        details: isRecovery ? "Rescheduled Sunday catch-up • Slot 5 • +10 points" : lecture.details,
        milestone: lecture.milestone,
        sourcePage: lecture.sourcePage,
      };
    },
    [tasks, progress, recoveries, todayDateStr, track1Lectures, track2Lectures, getTrackForSubject]
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
          pointsValue: 20,
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
          const codeId = `${getCodeForSubject(t.subject)}_${t.curNum}`;
          const prog = progress.get(t.id) || progress.get(codeId);
          const isDone = prog?.isCompleted === true;
          const rec = recoveries.find(
            (r) =>
              (r.taskId === t.id || r.taskId === codeId) &&
              (r.status === "ACTIVE" || r.status === "MANUAL_CONFIRMED" || r.status === "COMPLETED")
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
      // Strictly sequential lecture adaptation matching Android ScheduleRepository.kt
      const recoveriesForDate = recoveries.filter(
        (r) =>
          r.recoveryDate === targetDateStr &&
          (r.status === "ACTIVE" || r.status === "MANUAL_CONFIRMED" || r.status === "COMPLETED")
      );

      // Parity guarantee: If any past task was completed as a recovery on this date in progress,
      // ensure it appears in recoveriesForDate so today's missions match the companion app exactly
      progress.forEach((prog, pTaskId) => {
        if (prog.isCompleted && (prog.completionType === "RECOVERY" || prog.completionType === "RECOVERY_LATE")) {
          const compDate = prog.completedAtTimestamp ? formatDateStr(prog.completedAtTimestamp) : targetDateStr;
          if (compDate === targetDateStr) {
            const alreadyPresent = recoveriesForDate.some(
              (r) =>
                r.taskId === pTaskId ||
                findMatchingScheduleTask(tasks, r.taskId)?.id === findMatchingScheduleTask(tasks, pTaskId)?.id
            );
            if (!alreadyPresent) {
              const matched = findMatchingScheduleTask(tasks, pTaskId);
              if (matched) {
                recoveriesForDate.push({
                  id: -1,
                  taskId: matched.id,
                  originalDate: matched.date,
                  recoveryDate: targetDateStr,
                  assignedAtTimestamp: prog.completedAtTimestamp || Date.now(),
                  isCompleted: true,
                  priorityLevel: 1,
                  status: "COMPLETED",
                });
              }
            }
          }
        }
      });

      // Sort recovery tasks predictably: slot 1 (Track 1 / DL) before slot 2 (Track 2 / CN)
      recoveriesForDate.sort((a, b) => {
        const taskA = findMatchingScheduleTask(tasks, a.taskId);
        const taskB = findMatchingScheduleTask(tasks, b.taskId);
        return (taskA?.slot || 0) - (taskB?.slot || 0);
      });

      for (let rIdx = 0; rIdx < recoveriesForDate.length; rIdx++) {
        const rec = recoveriesForDate[rIdx];
        const originalTask = findMatchingScheduleTask(tasks, rec.taskId);
        if (!originalTask) continue;
        const codeId = `${getCodeForSubject(originalTask.subject)}_${originalTask.curNum}`;
        const prog = progress.get(rec.taskId) || progress.get(originalTask.id) || progress.get(codeId);
        const isDone = prog?.isCompleted === true || rec.isCompleted;

        // Count earlier recoveries for the same subject on this same day
        const earlierRecCount = recoveriesForDate.slice(0, rIdx).filter((other) => {
          const ot = findMatchingScheduleTask(tasks, other.taskId);
          return getTrackForSubject(ot?.subject || "") === getTrackForSubject(originalTask.subject);
        }).length;

        const adaptedTask = getSequentialLecture(originalTask, targetDateStr, true, earlierRecCount);

        let status: TaskStatus = "RECOVERY_SCHEDULED";
        if (isDone) {
          status = "COMPLETED_LATE";
        } else if (isToday) {
          status = "DUE_TODAY";
        } else if (isPast) {
          status = "MISSED";
        }

        const cleanTitle = adaptedTask.title.replace(/\s*\(Catch-Up\)/g, "").trim();

        items.push({
          id: `REC_${rec.taskId}`,
          originalDate: originalTask.date,
          displayDate: targetDateStr,
          dayOfWeek: "Sun",
          slot: 5,
          subject: adaptedTask.subject,
          subjectCode: getCodeForSubject(adaptedTask.subject),
          type: originalTask.type as any,
          curNum: adaptedTask.curNum,
          totalCount: adaptedTask.totalCount,
          title: `${cleanTitle} (Catch-Up)`,
          details: `Rescheduled Sunday catch-up • Slot 5 • +10 points`,
          milestone: adaptedTask.milestone || "",
          sourcePage: adaptedTask.sourcePage,
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
    [tasks, progress, recoveries, customTasks, todayDateStr, getSequentialLecture, getTrackForSubject]
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
        (t.type === "RECORDED" || t.type === "LIVE") &&
        !t.subject.toLowerCase().includes("revision") &&
        !t.subject.toLowerCase().includes("problem") &&
        !t.title.toLowerCase().includes("revision") &&
        !t.title.toLowerCase().includes("problem solving")
    );

    for (const t of pastTasks) {
      const codeId = `${getCodeForSubject(t.subject)}_${t.curNum}`;
      const isDone = progress.get(t.id)?.isCompleted === true || progress.get(codeId)?.isCompleted === true;
      if (!isDone) {
        const rec = recoveries.find(
          (r) =>
            (r.taskId === t.id || r.taskId === codeId) &&
            (r.status === "ACTIVE" || r.status === "MANUAL_CONFIRMED")
        );
        const hasValidFutureRecovery = Boolean(
          rec && compareDateStrings(rec.recoveryDate, todayDateStr) >= 0
        );
        if (!hasValidFutureRecovery) {
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

  // Unidirectional sync model: Task marking and unmarking is exclusively handled in the Android App.
  // The website operates as a pure read-only mirror/replica and has zero authority to mark or unmark tasks.
  const toggleTaskComplete = useCallback(
    async (..._args: any[]) => {
      void _args;
      return {
        success: false,
        error: "Task marking and unmarking is only possible through the GATEBuddy Android app.",
      };
    },
    []
  );

  // Action: Reschedule single task (Read-Only Mirror - Exclusively managed in Android App)
  const rescheduleTask = useCallback(
    async (..._args: any[]) => {
      void _args;
      return {
        success: false,
        error: "Lecture rescheduling is only possible through the GATEBuddy Android companion app.",
      };
    },
    []
  );

  // Action: Batch reschedule (Read-Only Mirror - Exclusively managed in Android App)
  const batchReschedule = useCallback(
    async (..._args: any[]) => {
      void _args;
      return {
        success: false,
        error: "Batch rescheduling is only possible through the GATEBuddy Android companion app.",
      };
    },
    []
  );

  // Unidirectional App -> Website Sync Model:
  // Assessments and mistake logs are recorded exclusively in the Android Companion App.
  // The website operates strictly as a read-only mirror.
  const addAssessment = useCallback(
    async (..._args: any[]) => {
      void _args;
      console.warn("Website is in read-only mirror mode. Assessments must be recorded in the Android app.");
      return;
    },
    []
  );

  const addCustomTask = useCallback(
    async (task: Omit<CustomTask, "id">) => {
      const newRecord: CustomTask = {
        ...task,
        id: Date.now(),
      };
      const nextCustom = [...customTasksRef.current, newRecord];
      customTasksRef.current = nextCustom;
      setCustomTasks(nextCustom);
      syncWithCloud(undefined, undefined, nextCustom);
    },
    [syncWithCloud]
  );

  const updateSettings = useCallback(
    async (newSettings: Partial<UserSettings>) => {
      const updated = { ...settingsRef.current, ...newSettings };
      settingsRef.current = updated;
      setSettings(updated);
      syncWithCloud();
    },
    [syncWithCloud]
  );

  // Action: Master Code Protected Credential Modification
  const changeCredentials = useCallback(
    async (newUsername: string, newPass: string, masterCode: string) => {
      try {
        const response = await fetch("/api/auth/reset-credentials", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            newUsername: newUsername.trim(),
            newPassword: newPass ? newPass.trim() : undefined,
            masterCode: masterCode.trim(),
          }),
        });

        const data = await response.json();
        if (!response.ok || !data.success) {
          return {
            success: false,
            error: data.error || "Master Security Code invalid. Authorization denied.",
          };
        }

        const effectiveUsername = data.username || newUsername.trim();
        const updated: UserCredentials = {
          username: effectiveUsername,
        };

        credentialsRef.current = updated;
        setCredentials(updated);
        setUser((prev) =>
          prev
            ? { ...prev, username: updated.username, name: updated.username }
            : { id: "usr_" + updated.username, email: `${updated.username}@gatebuddy.com`, name: updated.username, username: updated.username }
        );

        syncWithCloud();
        return { success: true };
      } catch (err: any) {
        return {
          success: false,
          error: err?.message || "Failed to contact server to update credentials.",
        };
      }
    },
    [syncWithCloud]
  );

  // Action: Login
  const login = useCallback(
    async (usernameOrEmail: string, pass: string) => {
      const cleanInput = (usernameOrEmail || "").trim();
      const cleanPass = (pass || "").trim();

      try {
        const res = await fetch("/api/auth/login", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ username: cleanInput, password: cleanPass }),
        });

        const data = await res.json();
        if (res.ok && data.success && data.user) {
          setUser(data.user);
          try {
            localStorage.setItem(`${STORAGE_KEY_PREFIX}user`, JSON.stringify(data.user));
          } catch {}
          return { success: true };
        }

        return {
          success: false,
          error: data.error || "Invalid username or password. Please try again.",
        };
      } catch {
        return {
          success: false,
          error: "Connection error during login. Please try again.",
        };
      }
    },
    []
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
      try {
        if (typeof document !== "undefined") {
          document.cookie = `gatebuddy_auth=${encodeURIComponent(u.username)}; path=/; max-age=2592000; SameSite=Lax`;
        }
        localStorage.setItem(`${STORAGE_KEY_PREFIX}user`, JSON.stringify(u));
      } catch {}
      return { success: true };
    },
    []
  );

  const logout = useCallback(async () => {
    setUser(null);
    try {
      if (typeof document !== "undefined") {
        document.cookie = "gatebuddy_auth=; path=/; max-age=0";
      }
      localStorage.removeItem(`${STORAGE_KEY_PREFIX}user`);
      localStorage.removeItem("gatebuddy_v2_user");
      await fetch("/api/auth/logout", { method: "POST" });
    } catch {}
    window.location.href = "/login";
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
        dppList,
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
