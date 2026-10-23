import { ScheduleTask, TaskProgress, Achievement } from "./types";

export const INITIAL_ACHIEVEMENTS: Achievement[] = [
  { code: "FIRST_TASK", title: "First Step Forward", description: "Completed your first scheduled GATE lecture", category: "CONSISTENCY", isUnlocked: false },
  { code: "FIRST_PERFECT_DAY", title: "Flawless Execution", description: "Completed 100% of tasks scheduled for a day", category: "CONSISTENCY", isUnlocked: false },
  { code: "STREAK_7", title: "7-Day Discipline", description: "Maintained a 7-day continuous study streak", category: "CONSISTENCY", isUnlocked: false },
  { code: "STREAK_30", title: "Unbreakable Habit", description: "Maintained a 30-day continuous study streak", category: "CONSISTENCY", isUnlocked: false },
  { code: "PERFECT_SUNDAY", title: "Sunday Warrior", description: "Completed weekly test and all Sunday revision activities", category: "TESTS", isUnlocked: false },
  { code: "BACKLOG_CLEARED", title: "Zero Debt", description: "Successfully cleared all pending backlogs", category: "RECOVERY", isUnlocked: false },
  { code: "SUBJ_DIGITAL_LOGIC", title: "Digital Logic Master", description: "Completed all 52 lectures of Digital Logic", category: "MASTERY", isUnlocked: false },
  { code: "SUBJ_NETWORKS", title: "Computer Networks Pro", description: "Completed all 73 lectures of Computer Networks", category: "MASTERY", isUnlocked: false },
  { code: "SUBJ_DBMS", title: "Database Architect", description: "Completed all 42 lectures of DBMS", category: "MASTERY", isUnlocked: false },
  { code: "SUBJ_C_PROG", title: "C & Algorithms Initiate", description: "Completed all 43 lectures of C Programming", category: "MASTERY", isUnlocked: false },
  { code: "SUBJ_COA", title: "Architecture Specialist", description: "Completed all 51 lectures of COA", category: "MASTERY", isUnlocked: false },
  { code: "SUBJ_OPERATING_SYSTEMS", title: "Kernel Navigator", description: "Completed all 43 lectures of Operating Systems", category: "MASTERY", isUnlocked: false },
  { code: "REVISION_CHAMPION", title: "Active Recall Master", description: "Completed 20 daily active recall sessions", category: "CONSISTENCY", isUnlocked: false },
  { code: "FULL_SYLLABUS_COMPLETE", title: "GATE CSE Titan", description: "Completed entire 690-task syllabus schedule", category: "MASTERY", isUnlocked: false },
];

export function evaluateAchievements(
  allTasks: ScheduleTask[],
  allProgress: TaskProgress[],
  currentBacklog: number,
  existingAchievements: Achievement[] = INITIAL_ACHIEVEMENTS
): Achievement[] {
  const progressMap = new Map<string, TaskProgress>();
  for (const p of allProgress) {
    progressMap.set(p.taskId, p);
  }

  const completedTasks = allTasks.filter((t) => progressMap.get(t.id)?.isCompleted);
  const newlyEvaluated = existingAchievements.map((ach) => ({ ...ach }));

  function unlock(code: string) {
    const item = newlyEvaluated.find((a) => a.code === code);
    if (item && !item.isUnlocked) {
      item.isUnlocked = true;
      item.unlockedAtTimestamp = Date.now();
    }
  }

  // 1. First Task
  if (completedTasks.length > 0) {
    unlock("FIRST_TASK");
  }

  // 2. First Perfect Day
  const allByDate = new Map<string, ScheduleTask[]>();
  for (const t of allTasks) {
    const arr = allByDate.get(t.date) || [];
    arr.push(t);
    allByDate.set(t.date, arr);
  }
  for (const [, dayTasks] of Array.from(allByDate.entries())) {
    if (dayTasks.length > 0 && dayTasks.every((t) => progressMap.get(t.id)?.isCompleted)) {
      unlock("FIRST_PERFECT_DAY");
      break;
    }
  }

  // 3. Perfect Sunday
  for (const [, dayTasks] of Array.from(allByDate.entries())) {
    const sundayTasks = dayTasks.filter((t) => t.isSundayActivity);
    if (sundayTasks.length >= 2 && sundayTasks.every((t) => progressMap.get(t.id)?.isCompleted)) {
      unlock("PERFECT_SUNDAY");
      break;
    }
  }

  // 4. Backlog cleared
  if (completedTasks.length >= 5 && currentBacklog === 0) {
    unlock("BACKLOG_CLEARED");
  }

  // 5. Subject mastery
  const completedBySubject = new Map<string, number>();
  for (const t of completedTasks) {
    completedBySubject.set(t.subject, (completedBySubject.get(t.subject) || 0) + 1);
  }

  if ((completedBySubject.get("Digital Logic") || 0) >= 52) unlock("SUBJ_DIGITAL_LOGIC");
  if ((completedBySubject.get("Computer Network") || 0) >= 73) unlock("SUBJ_NETWORKS");
  if ((completedBySubject.get("DBMS") || 0) >= 42) unlock("SUBJ_DBMS");
  if ((completedBySubject.get("Fundamental of C + C Prog") || 0) >= 43) unlock("SUBJ_C_PROG");
  if ((completedBySubject.get("COA") || 0) >= 51) unlock("SUBJ_COA");
  if ((completedBySubject.get("Operating System") || 0) >= 43) unlock("SUBJ_OPERATING_SYSTEMS");

  // 6. Full syllabus
  if (completedTasks.length >= 690) {
    unlock("FULL_SYLLABUS_COMPLETE");
  }

  return newlyEvaluated;
}
