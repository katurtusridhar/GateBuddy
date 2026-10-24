/**
 * GATE CSE Companion — Shared Type Definitions
 * Authoritative types mirroring Android Room entities and UI contracts.
 */

export type TaskType =
  | "RECORDED"
  | "LIVE"
  | "BUFFER"
  | "REVISION"
  | "SUNDAY_TEST"
  | "SUNDAY_PYQ"
  | "REVISION_MOCK"
  | "BOOSTER_REVISION"
  | "BOOSTER_PROBLEM_SOLVING"
  | "CUSTOM";

export type TaskStatus =
  | "COMPLETED_ON_TIME"
  | "COMPLETED_LATE"
  | "DUE_TODAY"
  | "MISSED"
  | "RECOVERY_SCHEDULED"
  | "UPCOMING";

export type PreparationHealth = "ON_TRACK" | "AT_RISK" | "BEHIND";

export interface ScheduleTask {
  id: string; // e.g. "TASK_0001"
  date: string; // YYYY-MM-DD
  dayOfWeek: string; // Mon, Tue, etc.
  slot: number; // 1 or 2
  subject: string;
  type: string;
  curNum: number;
  totalCount: number;
  title: string;
  details: string;
  milestone: string;
  sourcePage: number;
  isBuffer: boolean;
  isRevision: boolean;
  isSundayActivity: boolean;
}

export interface TaskProgress {
  taskId: string;
  originalDate: string;
  isCompleted: boolean;
  completedAtTimestamp?: number | null;
  completionType?: "ON_TIME" | "LATE" | "RECOVERY" | "RECOVERY_LATE" | null;
  notes: string;
  version?: number;
  updatedAt?: string;
  lastMutatedTimestamp?: number | null;
}

export interface RecoveryAssignment {
  id: number;
  taskId: string;
  originalDate: string;
  recoveryDate: string; // YYYY-MM-DD
  assignedAtTimestamp: number;
  isCompleted: boolean;
  completedAtTimestamp?: number | null;
  priorityLevel: number;
  status: "ACTIVE" | "MANUAL_CONFIRMED" | "COMPLETED" | "EXPIRED" | "CANCELLED";
  version?: number;
  updatedAt?: string;
}

export interface AssessmentResult {
  id: number;
  testDate: string;
  testName: string;
  subjectName: string;
  marksObtained: number;
  maxMarks: number;
  percentage: number;
  questionsAttempted?: number | null;
  questionsCorrect?: number | null;
  topic: string;
  mistakesAnalysis: string;
  followUpTasks: string;
  createdAtTimestamp: number;
  updatedAt?: string;
}

export interface Achievement {
  code: string;
  title: string;
  description: string;
  category: "CONSISTENCY" | "MASTERY" | "TESTS" | "RECOVERY";
  isUnlocked: boolean;
  unlockedAtTimestamp?: number | null;
}

export interface CustomTask {
  id: number;
  date: string; // YYYY-MM-DD
  title: string;
  subjectCode: string;
  subjectName: string;
  isCompleted: boolean;
  completedAtTimestamp?: number | null;
  notes: string;
  pointsValue: number;
  updatedAt?: string;
}

export interface UserSettings {
  id: number;
  targetExamDate: string; // YYYY-MM-DD
  targetExamName: string;
  dailyRecoveryLimit: number;
  themeMode: "DARK" | "LIGHT" | "SYSTEM";
  reminderEnabled: boolean;
  reminderSlot1Hour: number;
  reminderSlot1Minute: number;
  reminderSlot2Hour: number;
  reminderSlot2Minute: number;
  isOnboarded: boolean;
}

export interface CandidateDateSlot {
  date: string;
  currentCount: number;
  existingSubjects: string[];
  isFull: boolean;
  canAcceptSubject: boolean;
}

export interface SubjectInfo {
  code: string;
  name: string;
  priorCompletedLectures: number;
  totalLectures: number;
  type: string;
}

export interface SubjectUi {
  code: string;
  name: string;
  completedLectures: number;
  totalLectures: number;
  progressPercent: number;
  statusText: string;
  type: string;
  remainingLectures: number;
  overdueLectures: number;
  nextScheduledLecture?: string;
  targetCompletionDate?: string;
}

export interface DayScorePoint {
  date: string;
  score: number;
  points: number;
  totalTasks: number;
  completedTasks: number;
}

export interface DailyExecutionScoreUi {
  date: string;
  scorePercentage: number;
  dailyPoints: number;
  completedScheduledTasks: number;
  totalScheduledTasks: number;
  isInProgress: boolean;
  statusDescription: string;
}

export interface PreparationAdherenceUi {
  health: PreparationHealth;
  healthReason: string;
  onTimeAdherencePercent: number;
  sevenDayAdherencePercent: number;
  twentyEightDayAdherencePercent: number;
  completedTasksCount: number;
  totalEligibleTasks: number;
  currentBacklogCount: number;
  dailyPointsToday: number;
  totalPointsAllTime: number;
  projectedCompletionDate: string;
  daysDifference: number;
  isAheadOrOnTrack: boolean;
  confidenceScore: number;
  historicalPoints: DayScorePoint[];
}

export interface TaskItemUi {
  id: string;
  originalDate: string;
  displayDate: string;
  dayOfWeek: string;
  slot: number;
  subject: string;
  subjectCode: string;
  type: TaskType;
  curNum: number;
  totalCount: number;
  title: string;
  details: string;
  milestone: string;
  sourcePage: number;
  isBuffer: boolean;
  isRevision: boolean;
  isSundayActivity: boolean;
  isBooster: boolean;
  status: TaskStatus;
  isCompleted: boolean;
  completedAtTimestamp?: number | null;
  completionType?: string | null;
  notes: string;
  pointsValue: number;
  isAssignedForRecovery: boolean;
  recoveryDate?: string | null;
  isFuture: boolean;
  isPast: boolean;
  isCustom: boolean;
}

export interface UserCredentials {
  username: string;
  password: string;
  masterCode: string;
}

export const DEFAULT_CREDENTIALS: UserCredentials = {
  username: "katurtusridhar",
  password: "Sridhar@758",
  masterCode: "oA-THL,UXyh6",
};

export type SyncState = "synced" | "syncing" | "offline" | "error" | "pending";

export interface SyncPayload {
  lastSyncTimestamp: number;
  progress: TaskProgress[];
  recoveries: RecoveryAssignment[];
  assessments: AssessmentResult[];
  customTasks: CustomTask[];
  settings?: UserSettings;
  achievements?: Achievement[];
}
