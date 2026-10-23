import {
  ScheduleTask,
  TaskProgress,
  CustomTask,
  DailyExecutionScoreUi,
  PreparationAdherenceUi,
  PreparationHealth,
  DayScorePoint,
} from "./types";
import { compareDateStrings, addDaysToDateStr } from "./dateUtils";

/**
 * Calculates daily execution score and points for a given date.
 */
export function calculateDailyScore(
  targetDateStr: string,
  currentDateStr: string,
  dayTasks: ScheduleTask[],
  progressMap: Map<string, TaskProgress>,
  customTasks: CustomTask[] = []
): DailyExecutionScoreUi {
  const isToday = targetDateStr === currentDateStr;
  const isPast = compareDateStrings(targetDateStr, currentDateStr) < 0;

  let points = 0;

  for (const task of dayTasks) {
    const isComplete = progressMap.get(task.id)?.isCompleted === true;
    if (task.isSundayActivity) {
      if (isComplete) points += 10;
    } else {
      if (isComplete) {
        points += 10;
      } else if (isPast) {
        points -= 2; // Missed penalty on past days
      }
    }
  }

  // Boosters
  const revKey = `BOOSTER_REV_${targetDateStr}`;
  const psKey = `BOOSTER_PS_${targetDateStr}`;
  if (progressMap.get(revKey)?.isCompleted === true) points += 5;
  if (progressMap.get(psKey)?.isCompleted === true) points += 5;

  // Custom Tasks
  for (const ct of customTasks) {
    if (ct.isCompleted) points += ct.pointsValue;
  }

  const totalScheduled = dayTasks.length;
  const completedCount = dayTasks.filter((t) => progressMap.get(t.id)?.isCompleted === true).length;
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
 */
export function calculateAdherenceAndForecast(
  currentDateStr: string,
  allTasks: ScheduleTask[],
  allProgress: TaskProgress[],
  allCustomTasks: CustomTask[] = [],
  plannedEndDateStr: string = "2027-09-28"
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
    const progress = progressMap.get(task.id);
    if (progress?.isCompleted) {
      if (progress.completionType === "ON_TIME" || !progress.completionType) {
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
      customByDate.get(d) || []
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

  // Preparation Health
  let health: PreparationHealth = "ON_TRACK";
  let healthReason = "Preparation is on schedule. Keep the steady momentum.";

  if (currentBacklog > 5 || onTimeAdherencePercent < 60) {
    health = "BEHIND";
    healthReason = `You have ${currentBacklog} overdue lectures. Prioritize Sunday catch-up to recover.`;
  } else if (currentBacklog > 0 || onTimeAdherencePercent < 80) {
    health = "AT_RISK";
    healthReason = `You have ${currentBacklog} missed lecture(s). Reschedule into upcoming buffer days.`;
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
    totalPointsAllTime,
    projectedCompletionDate,
    daysDifference: Math.round(daysDiff),
    isAheadOrOnTrack,
    confidenceScore: Math.min(100, Math.max(30, onTimeAdherencePercent)),
    historicalPoints: historicalPoints.slice(-14), // Last 14 days
  };
}
