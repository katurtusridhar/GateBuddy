import {
  ScheduleTask,
  RecoveryAssignment,
  CandidateDateSlot,
} from "./types";
import { compareDateStrings, addDaysToDateStr, isSundayDate } from "./dateUtils";

/**
 * Returns candidate upcoming Sundays and buffer days for recovery scheduling.
 */
export function getCandidateRescheduleDates(
  currentDateStr: string,
  allTasks: ScheduleTask[]
): string[] {
  const candidateSet = new Set<string>();

  // 1. Buffer days on or after today
  for (const t of allTasks) {
    if (t.isBuffer && compareDateStrings(t.date, currentDateStr) >= 0) {
      candidateSet.add(t.date);
    }
  }

  // 2. Next 16 Sundays
  let runner = currentDateStr;
  let sundaysFound = 0;
  for (let i = 0; i < 120 && sundaysFound < 16; i++) {
    if (isSundayDate(runner) && compareDateStrings(runner, currentDateStr) >= 0) {
      candidateSet.add(runner);
      sundaysFound++;
    }
    runner = addDaysToDateStr(runner, 1);
  }

  const sorted = Array.from(candidateSet).sort(compareDateStrings);
  return sorted;
}

/**
 * Computes slot availability for candidate recovery dates enforcing:
 * Rule 1: Max 2 lectures per day.
 * Rule 2: 2 subjects cannot be the same on the same day.
 */
export function getCandidateSlotsInfo(
  currentDateStr: string,
  candidateDates: string[],
  activeRecoveries: RecoveryAssignment[],
  allTasks: ScheduleTask[],
  targetSubject?: string
): CandidateDateSlot[] {
  const taskMap = new Map<string, ScheduleTask>();
  for (const t of allTasks) {
    taskMap.set(t.id, t);
  }

  const recoveriesByDate = new Map<string, RecoveryAssignment[]>();
  for (const r of activeRecoveries) {
    if (r.status === "ACTIVE" || r.status === "MANUAL_CONFIRMED") {
      const arr = recoveriesByDate.get(r.recoveryDate) || [];
      arr.push(r);
      recoveriesByDate.set(r.recoveryDate, arr);
    }
  }

  const targetSubjClean = targetSubject ? targetSubject.trim().toLowerCase() : null;

  return candidateDates.map((date) => {
    const list = recoveriesByDate.get(date) || [];
    const currentCount = list.length;
    const existingSubjects: string[] = [];

    for (const r of list) {
      const task = taskMap.get(r.taskId);
      if (task) {
        existingSubjects.push(task.subject);
      }
    }

    const isFull = currentCount >= 2;
    const isSameSubj = targetSubjClean
      ? existingSubjects.some((s) => s.trim().toLowerCase() === targetSubjClean)
      : false;

    return {
      date,
      currentCount,
      existingSubjects,
      isFull,
      canAcceptSubject: !isFull && !isSameSubj,
    };
  });
}

/**
 * Validates whether a target task can be assigned to newDate.
 * Enforces:
 * - Rule 1: Max 2 lectures per day
 * - Rule 2: 2 subjects cannot be the same on the same day
 * - Date must not be in the past
 */
export function validateReschedule(
  taskId: string,
  newDate: string,
  currentDateStr: string,
  activeRecoveries: RecoveryAssignment[],
  allTasks: ScheduleTask[]
): { valid: boolean; error?: string } {
  if (compareDateStrings(newDate, currentDateStr) < 0) {
    return { valid: false, error: `Cannot reschedule to past date (${newDate}).` };
  }

  const taskMap = new Map<string, ScheduleTask>();
  for (const t of allTasks) {
    taskMap.set(t.id, t);
  }

  const targetTask = taskMap.get(taskId);
  if (!targetTask) {
    return { valid: false, error: "Task not found." };
  }

  // Strict GATE Rule: Rescheduling is strictly for lectures only.
  // Never for Daily Problem Solving, Daily Revision, Boosters, or Sunday Tests.
  if (
    targetTask.isSundayActivity ||
    targetTask.isBuffer ||
    targetTask.isRevision ||
    targetTask.type === "BOOSTER_REVISION" ||
    targetTask.type === "BOOSTER_PROBLEM_SOLVING" ||
    targetTask.type === "SUNDAY_TEST" ||
    targetTask.type === "SUNDAY_PYQ" ||
    targetTask.subject.toLowerCase().includes("revision") ||
    targetTask.subject.toLowerCase().includes("problem") ||
    targetTask.title.toLowerCase().includes("revision") ||
    targetTask.title.toLowerCase().includes("problem solving")
  ) {
    return {
      valid: false,
      error: "Strict Rule: Rescheduling is strictly for core lectures only. Daily Problem Solving and Daily Revision cannot be rescheduled.",
    };
  }

  // Active recoveries on target date excluding this task itself
  const existingOnDate = activeRecoveries.filter(
    (r) =>
      r.recoveryDate === newDate &&
      r.taskId !== taskId &&
      (r.status === "ACTIVE" || r.status === "MANUAL_CONFIRMED")
  );

  // Rule 1: Max 2 lectures
  if (existingOnDate.length >= 2) {
    return {
      valid: false,
      error: `Rule 1 Violation: ${newDate} already has ${existingOnDate.length} recovery lectures (Max 2 allowed).`,
    };
  }

  // Rule 2: No duplicate subjects
  const targetSubjLower = targetTask.subject.trim().toLowerCase();
  for (const r of existingOnDate) {
    const t = taskMap.get(r.taskId);
    if (t && t.subject.trim().toLowerCase() === targetSubjLower) {
      return {
        valid: false,
        error: `Rule 2 Violation: A lecture for '${targetTask.subject}' is already scheduled on ${newDate}.`,
      };
    }
  }

  return { valid: true };
}
