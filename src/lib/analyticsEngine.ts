import {
  ScheduleTask,
  TaskProgress,
  CustomTask,
  AssessmentResult,
  DailyExecutionScoreUi,
  PreparationAdherenceUi,
  PreparationHealth,
  DayScorePoint,
} from "./types";
import { compareDateStrings, addDaysToDateStr, isSundayDate } from "./dateUtils";

/**
 * Calculates daily execution score and points for a given date.
 * Evaluates Sunday test score proportionally (max 20 pts) and applies negative penalties for missed tasks.
 */
export function calculateDailyScore(
  targetDateStr: string,
  currentDateStr: string,
  dayTasks: ScheduleTask[],
  progressMap: Map<string, TaskProgress>,
  customTasks: CustomTask[] = [],
  assessments: AssessmentResult[] = []
): DailyExecutionScoreUi {
  const isToday = targetDateStr === currentDateStr;
  const isPast = compareDateStrings(targetDateStr, currentDateStr) < 0;
  const isSunday = isSundayDate(targetDateStr);

  let points = 0;
  let isTestComplete = false;
  let isRevComplete = false;
  let isPyqComplete = false;

  if (isSunday || dayTasks.some((t) => t.isSundayActivity)) {
    // 1. Sunday Test (max 20 pts based on score)
    const scheduledTest = dayTasks.find((t) => t.type === "SUNDAY_TEST" || (t.isSundayActivity && t.slot === 1));
    const testProg = progressMap.get("SUNDAY_TEST_" + targetDateStr)
      || (scheduledTest ? progressMap.get(scheduledTest.id) : undefined)
      || Array.from(progressMap.values()).find((p) => p.originalDate === targetDateStr && p.taskId.startsWith("SUNDAY_TEST_"));
    const assessment = assessments.find((a: any) => (a.testDate || a.test_date) === targetDateStr);
    isTestComplete = Boolean(testProg?.isCompleted || !!assessment);

    if (isTestComplete) {
      let pct = assessment?.percentage;
      if (pct === undefined && testProg?.notes) {
        const match = testProg.notes.match(/Marks:\s*(\d+(?:\.\d+)?)\s*\/\s*(\d+(?:\.\d+)?)/);
        if (match) {
          const scored = parseFloat(match[1]);
          const max = parseFloat(match[2]);
          pct = max > 0 ? (scored / max) * 100 : 100;
        }
      }
      if (pct === undefined) pct = 100;
      const testPts = Math.min(20, Math.max(0, Math.round((pct / 100) * 20)));
      points += testPts;
    } else if (isPast && (scheduledTest || isSunday)) {
      points -= 4; // -4 pts penalty for missed Sunday test
    }

    // 2. Sunday Revision (+5 pts)
    const revProg = progressMap.get("BOOSTER_REV_" + targetDateStr)
      || (dayTasks.find((t) => t.isRevision) ? progressMap.get(dayTasks.find((t) => t.isRevision)!.id) : undefined);
    isRevComplete = Boolean(revProg?.isCompleted);
    if (isRevComplete) {
      points += 5;
    } else if (isPast && dayTasks.some((t) => t.isRevision)) {
      points -= 1;
    }

    // 3. Sunday PYQ Practice (+5 pts)
    const scheduledPyq = dayTasks.find((t) => t.type === "SUNDAY_PYQ" || (t.isSundayActivity && t.slot === 2));
    const pyqProg = progressMap.get("BOOSTER_PS_" + targetDateStr)
      || (scheduledPyq ? progressMap.get(scheduledPyq.id) : undefined);
    isPyqComplete = Boolean(pyqProg?.isCompleted);
    if (isPyqComplete) {
      points += 5;
    } else if (isPast && (scheduledPyq || (isSunday && dayTasks.length >= 2))) {
      points -= 1;
    }

    // Non-Sunday recovery or catch-up tasks scheduled for today
    for (const task of dayTasks) {
      if (!task.isSundayActivity && task.type !== "SUNDAY_TEST" && task.type !== "SUNDAY_PYQ") {
        const cleanId = (task.id || "").replace(/^REC_/, "");
        const prog = progressMap.get(task.id) || progressMap.get(cleanId);
        const isDone = (task as any).isCompleted === true || prog?.isCompleted === true;
        if (isDone) {
          points += 10;
        } else if (isPast) {
          points -= 2;
        }
      }
    }
  } else {
    // Weekday Point System
    for (const task of dayTasks) {
      const cleanId = (task.id || "").replace(/^REC_/, "");
      const prog = progressMap.get(task.id) || progressMap.get(cleanId);
      const isDone = (task as any).isCompleted === true || prog?.isCompleted === true;
      if (isDone) {
        points += 10;
      } else if (isPast) {
        points -= 2; // Missed penalty on past days
      }
    }

    // Boosters
    const revKey = `BOOSTER_REV_${targetDateStr}`;
    const psKey = `BOOSTER_PS_${targetDateStr}`;
    if (progressMap.get(revKey)?.isCompleted === true) points += 5;
    if (progressMap.get(psKey)?.isCompleted === true) points += 5;
  }

  // Custom Tasks
  for (const ct of customTasks) {
    if (ct.isCompleted) points += ct.pointsValue;
  }

  const isSundayWorkflow = isSunday || dayTasks.some((t) => t.isSundayActivity);
  const totalScheduled = isSundayWorkflow
    ? 3 + dayTasks.filter((t) => !t.isSundayActivity && t.type !== "SUNDAY_TEST" && t.type !== "SUNDAY_PYQ").length + customTasks.length
    : dayTasks.length + customTasks.length;

  const completedCount = isSundayWorkflow
    ? (isTestComplete ? 1 : 0) +
      (isRevComplete ? 1 : 0) +
      (isPyqComplete ? 1 : 0) +
      dayTasks.filter((t) => {
        if (t.isSundayActivity || t.type === "SUNDAY_TEST" || t.type === "SUNDAY_PYQ") return false;
        const cleanId = (t.id || "").replace(/^REC_/, "");
        return (t as any).isCompleted === true || progressMap.get(t.id)?.isCompleted === true || progressMap.get(cleanId)?.isCompleted === true;
      }).length +
      customTasks.filter((c) => c.isCompleted).length
    : dayTasks.filter((t) => {
        const cleanId = (t.id || "").replace(/^REC_/, "");
        return (t as any).isCompleted === true || progressMap.get(t.id)?.isCompleted === true || progressMap.get(cleanId)?.isCompleted === true;
      }).length + customTasks.filter((c) => c.isCompleted).length;

  const scorePercent =
    totalScheduled > 0 ? Math.round((completedCount / totalScheduled) * 100) : 0;

  let statusDescription = "";
  if (isToday && completedCount === totalScheduled && totalScheduled > 0) {
    statusDescription = `All core tasks completed! +${points} pts earned 🔥`;
  } else if (isToday) {
    statusDescription = `In progress: ${completedCount} of ${totalScheduled} tasks completed (${points} pts)`;
  } else if (isPast && completedCount === totalScheduled && totalScheduled > 0) {
    statusDescription = `100% completed on time (+${points} pts)`;
  } else if (isPast && completedCount > 0) {
    statusDescription = `Partially completed (${completedCount}/${totalScheduled}) — ${points} pts`;
  } else if (isPast) {
    statusDescription = `Missed (${completedCount}/${totalScheduled}) — penalty applied (${points} pts)`;
  } else {
    statusDescription = `Upcoming scheduled day (Target: 30 pts)`;
  }

  return {
    date: targetDateStr,
    scorePercentage: scorePercent,
    dailyPoints: Math.max(0, points),
    completedScheduledTasks: completedCount,
    totalScheduledTasks: totalScheduled,
    isInProgress: isToday && completedCount < totalScheduled,
    statusDescription,
  };
}

/**
 * Calculates preparation adherence, forecast, and point trends.
 * Follows 7-day rolling average points from now on.
 */
export function calculateAdherenceAndForecast(
  currentDateStr: string,
  allTasks: ScheduleTask[],
  allProgress: TaskProgress[],
  allCustomTasks: CustomTask[] = [],
  plannedEndDateStr: string = "2027-09-28",
  assessments: AssessmentResult[] = []
): PreparationAdherenceUi {
  const progressMap = new Map<string, TaskProgress>();
  for (const p of allProgress) {
    progressMap.set(p.taskId, p);
  }

  const tasksUpToToday = allTasks.filter(
    (t) => compareDateStrings(t.date, currentDateStr) <= 0
  );

  let completedOnTime = 0;
  let completedLate = 0;
  let currentBacklog = 0;

  for (const task of tasksUpToToday) {
    const isSundayTest = task.type === "SUNDAY_TEST";
    const isSundayPyq = task.type === "SUNDAY_PYQ";
    const prog = progressMap.get(task.id);
    const isDone = isSundayTest
      ? (prog?.isCompleted || progressMap.get("SUNDAY_TEST_" + task.date)?.isCompleted || assessments.some((a: any) => (a.testDate || a.test_date) === task.date))
      : isSundayPyq
      ? (prog?.isCompleted || progressMap.get("BOOSTER_PS_" + task.date)?.isCompleted)
      : (prog?.isCompleted ?? false);

    if (isDone) {
      const completionType = prog?.completionType || progressMap.get("SUNDAY_TEST_" + task.date)?.completionType || "ON_TIME";
      if (completionType === "ON_TIME" || !completionType) {
        completedOnTime++;
      } else {
        completedLate++;
      }
    } else {
      if (compareDateStrings(task.date, currentDateStr) < 0) {
        currentBacklog++;
      }
    }
  }

  const totalPastEligible = tasksUpToToday.length;
  const onTimeAdherencePercent =
    totalPastEligible > 0 ? Math.round((completedOnTime / totalPastEligible) * 100) : 100;

  // 7-day adherence
  const sevenDaysAgoStr = addDaysToDateStr(currentDateStr, -7);
  const last7DaysTasks = allTasks.filter(
    (t) =>
      compareDateStrings(t.date, sevenDaysAgoStr) > 0 &&
      compareDateStrings(t.date, currentDateStr) <= 0
  );
  const last7DaysOnTime = last7DaysTasks.filter(
    (t) => progressMap.get(t.id)?.isCompleted && progressMap.get(t.id)?.completionType === "ON_TIME"
  ).length;
  const sevenDayAdherencePercent =
    last7DaysTasks.length > 0 ? Math.round((last7DaysOnTime / last7DaysTasks.length) * 100) : 100;

  // 28-day adherence
  const twentyEightDaysAgoStr = addDaysToDateStr(currentDateStr, -28);
  const last28DaysTasks = allTasks.filter(
    (t) =>
      compareDateStrings(t.date, twentyEightDaysAgoStr) > 0 &&
      compareDateStrings(t.date, currentDateStr) <= 0
  );
  const last28DaysOnTime = last28DaysTasks.filter(
    (t) => progressMap.get(t.id)?.isCompleted && progressMap.get(t.id)?.completionType === "ON_TIME"
  ).length;
  const twentyEightDayAdherencePercent =
    last28DaysTasks.length > 0 ? Math.round((last28DaysOnTime / last28DaysTasks.length) * 100) : 100;

  // Points history
  const tasksByDate = new Map<string, ScheduleTask[]>();
  for (const t of allTasks) {
    const arr = tasksByDate.get(t.date) || [];
    arr.push(t);
    tasksByDate.set(t.date, arr);
  }

  const customByDate = new Map<string, CustomTask[]>();
  for (const c of allCustomTasks) {
    const arr = customByDate.get(c.date) || [];
    arr.push(c);
    customByDate.set(c.date, arr);
  }

  const allDates = Array.from(tasksByDate.keys())
    .filter((d) => compareDateStrings(d, currentDateStr) <= 0)
    .sort(compareDateStrings);

  let totalPointsAllTime = 0;
  let pointsToday = 0;
  const historicalPoints: DayScorePoint[] = [];

  for (const d of allDates) {
    const score = calculateDailyScore(
      d,
      currentDateStr,
      tasksByDate.get(d) || [],
      progressMap,
      customByDate.get(d) || [],
      assessments
    );
    totalPointsAllTime += score.dailyPoints;
    if (d === currentDateStr) {
      pointsToday = score.dailyPoints;
    }
    historicalPoints.push({
      date: d,
      score: score.scorePercentage,
      points: score.dailyPoints,
      totalTasks: score.totalScheduledTasks,
      completedTasks: score.completedScheduledTasks,
    });
  }

  // 7-day rolling average points: past 7 days ending currentDateStr
  let sum7DaysPoints = 0;
  for (let i = 0; i < 7; i++) {
    const checkD = addDaysToDateStr(currentDateStr, -i);
    const score = calculateDailyScore(
      checkD,
      currentDateStr,
      tasksByDate.get(checkD) || [],
      progressMap,
      customByDate.get(checkD) || [],
      assessments
    );
    sum7DaysPoints += score.dailyPoints;
  }
  const averageDailyPoints = parseFloat((sum7DaysPoints / 7).toFixed(1));

  // Preparation Health (driven by 7-Day average & Backlog count)
  let health: PreparationHealth = "ON_TRACK";
  let healthReason = `On track: 7-day average of ${averageDailyPoints} pts/day and minimal backlog (${currentBacklog}).`;

  if (currentBacklog > 5 || (averageDailyPoints < 12 && onTimeAdherencePercent < 60)) {
    health = "BEHIND";
    healthReason = `Behind schedule: Backlog is ${currentBacklog} missed tasks and 7-day average is ${averageDailyPoints} pts/day. Buffer recovery advised.`;
  } else if (currentBacklog > 0 || averageDailyPoints < 22 || onTimeAdherencePercent < 80) {
    health = "AT_RISK";
    healthReason = `Caution: 7-day average at ${averageDailyPoints} pts/day with ${currentBacklog} overdue tasks.`;
  }

  // Projected Completion
  const completedTotal = completedOnTime + completedLate;

  // Velocity: avg lectures completed per week (assume baseline 10)
  const daysDiff = currentBacklog * 0.7; // ~0.7 days delay per backlog lecture
  const projectedCompletionDate = addDaysToDateStr(plannedEndDateStr, Math.round(daysDiff));
  const isAheadOrOnTrack = currentBacklog === 0;

  return {
    health,
    healthReason,
    onTimeAdherencePercent,
    sevenDayAdherencePercent,
    twentyEightDayAdherencePercent,
    completedTasksCount: completedTotal,
    totalEligibleTasks: totalPastEligible,
    currentBacklogCount: currentBacklog,
    dailyPointsToday: pointsToday,
    averageDailyPoints,
    totalPointsAllTime,
    projectedCompletionDate,
    daysDifference: Math.round(daysDiff),
    isAheadOrOnTrack,
    confidenceScore: Math.min(100, Math.max(30, onTimeAdherencePercent)),
    historicalPoints: historicalPoints.slice(-14), // Last 14 days
  };
}
