"use client";

import React, { useState, useMemo } from "react";
import { AppShell } from "@/components/layout/AppShell";
import { useApp } from "@/lib/store";
import { TaskCard } from "@/components/ui/TaskCard";
import { ProgressRing } from "@/components/ui/ProgressRing";
import { CyberCard } from "@/components/ui/CyberCard";
import { RescheduleModal } from "@/components/modals/RescheduleModal";
import { BatchBacklogModal } from "@/components/modals/BatchBacklogModal";
import { calculateDailyScore, calculateAdherenceAndForecast } from "@/lib/analyticsEngine";
import { TaskItemUi } from "@/lib/types";
import { getCandidateRescheduleDates } from "@/lib/recoveryEngine";
import {
  Flame,
  ShieldAlert,
  ArrowRight,
  Target,
} from "lucide-react";
import Link from "next/link";
import clsx from "clsx";

export default function DashboardPage() {
  const {
    user,
    todayDateStr,
    getTasksForDate,
    toggleTaskComplete,
    rescheduleTask,
    batchReschedule,
    activeBacklogs,
    candidateSlots,
    tasks,
    progress,
    customTasks,
    settings,
  } = useApp();

  const [taskToReschedule, setTaskToReschedule] = useState<TaskItemUi | null>(null);
  const [showBatchModal, setShowBatchModal] = useState(false);

  const todayTasks = useMemo(() => getTasksForDate(todayDateStr), [getTasksForDate, todayDateStr]);

  const candidateDates = useMemo(
    () => getCandidateRescheduleDates(todayDateStr, tasks),
    [todayDateStr, tasks]
  );

  const dailyScore = useMemo(
    () =>
      calculateDailyScore(
        todayDateStr,
        todayDateStr,
        tasks.filter((t) => t.date === todayDateStr),
        progress,
        customTasks
      ),
    [todayDateStr, tasks, progress, customTasks]
  );

  const adherence = useMemo(
    () =>
      calculateAdherenceAndForecast(
        todayDateStr,
        tasks,
        Array.from(progress.values()),
        customTasks,
        settings.targetExamDate
      ),
    [todayDateStr, tasks, progress, customTasks, settings.targetExamDate]
  );

  const totalTodayTasks = todayTasks.length;
  const completedTodayTasks = todayTasks.filter((t) => t.isCompleted).length;
  const completionPercent =
    totalTodayTasks > 0 ? Math.round((completedTodayTasks / totalTodayTasks) * 100) : 0;

  return (
    <AppShell>
      <div className="mx-auto max-w-7xl space-y-6">
        {/* Top Welcome Banner */}
        <div className="flex flex-col justify-between gap-4 rounded-2xl border border-slate-800 bg-gradient-to-r from-gate-card via-gate-cardElevated to-slate-900 p-6 shadow-xl lg:flex-row lg:items-center">
          <div>
            <div className="flex items-center gap-2">
              <span className="text-xs font-bold tracking-widest text-gate-sky uppercase">
                GATE CSE COMMAND CENTER
              </span>
              <span className="rounded-full bg-emerald-500/20 px-2 py-0.5 text-[10px] font-bold text-emerald-400">
                ACTIVE
              </span>
            </div>
            <h1 className="mt-1 text-2xl font-black text-white sm:text-3xl">
              Hello, {user?.name || "Sridhar"}!
            </h1>
            <p className="mt-1 text-xs text-slate-400">
              Candidate Target: <strong>IIT Bombay CSE (AIR &lt; 100)</strong> • Maintain strict consistency today.
            </p>
          </div>

          <div className="flex flex-wrap items-center gap-3">
            <div className="flex items-center gap-2.5 rounded-xl border border-amber-500/30 bg-amber-500/10 px-4 py-2 text-xs font-bold text-amber-300">
              <Flame className="h-4 w-4 text-amber-400" />
              <span>Current Streak: 7 Days</span>
            </div>
            <div className="flex items-center gap-2.5 rounded-xl border border-sky-500/30 bg-sky-500/10 px-4 py-2 text-xs font-bold text-gate-sky">
              <Target className="h-4 w-4" />
              <span>Score Today: +{dailyScore.dailyPoints} pts</span>
            </div>
          </div>
        </div>

        {/* Backlog Restriction Alert if backlogs exist */}
        {activeBacklogs.length > 0 && (
          <div className="flex flex-col items-start justify-between gap-4 rounded-2xl border border-rose-500/60 bg-gradient-to-r from-rose-950/40 via-gate-card to-gate-card p-5 shadow-glowRose sm:flex-row sm:items-center">
            <div className="flex items-center gap-3.5">
              <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-rose-500/20 text-rose-400">
                <ShieldAlert className="h-6 w-6" />
              </div>
              <div>
                <div className="flex items-center gap-2">
                  <span className="rounded bg-rose-500/20 px-2 py-0.5 text-[10px] font-black uppercase text-rose-400">
                    BACKLOG RESTRICTION
                  </span>
                  <span className="text-xs font-bold text-white">
                    {activeBacklogs.length} Unrescheduled Lecture(s)
                  </span>
                </div>
                <p className="mt-0.5 text-xs text-slate-300">
                  Direct task tracking is restricted until missed lectures are rescheduled into available Sundays or buffer slots.
                </p>
              </div>
            </div>

            <button
              onClick={() => setShowBatchModal(true)}
              className="flex shrink-0 items-center gap-2 rounded-xl bg-rose-500 px-5 py-2.5 text-xs font-bold text-white shadow-glowRose transition hover:bg-rose-600"
            >
              <span>Reschedule & Unlock</span>
              <ArrowRight className="h-4 w-4" />
            </button>
          </div>
        )}

        {/* Core Daily Grid */}
        <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
          {/* Left 2 Cols: Today's Tasks */}
          <div className="space-y-4 lg:col-span-2">
            <div className="flex items-center justify-between">
              <div>
                <h2 className="text-lg font-bold text-white">Today&apos;s Missions</h2>
                <p className="text-xs text-slate-400">{todayTasks.length} scheduled activities for today</p>
              </div>
              <Link
                href="/calendar"
                className="flex items-center gap-1 text-xs font-semibold text-gate-sky hover:underline"
              >
                <span>View Full Calendar</span>
                <ArrowRight className="h-3.5 w-3.5" />
              </Link>
            </div>

            <div className="space-y-3">
              {todayTasks.map((task) => (
                <TaskCard
                  key={task.id}
                  task={task}
                  onToggleComplete={async (notes) => toggleTaskComplete(task.id, notes)}
                  onOpenReschedule={() => setTaskToReschedule(task)}
                />
              ))}
            </div>
          </div>

          {/* Right Col: Performance & Preparation Health */}
          <div className="space-y-6">
            {/* Daily Execution Ring */}
            <CyberCard glow="sky">
              <h3 className="text-sm font-bold text-white">Daily Target Execution</h3>
              <p className="text-xs text-slate-400">Core lectures & boosters completion</p>

              <div className="mt-6 flex items-center justify-around">
                <ProgressRing
                  progressPercent={completionPercent}
                  completed={completedTodayTasks}
                  total={totalTodayTasks}
                  size={120}
                  strokeWidth={10}
                />
                <div className="space-y-2">
                  <div>
                    <span className="text-[10px] font-semibold text-slate-400 uppercase">Points Earned</span>
                    <p className="text-xl font-black text-gate-sky">+{dailyScore.dailyPoints} pts</p>
                  </div>
                  <div>
                    <span className="text-[10px] font-semibold text-slate-400 uppercase">Total Syllabus</span>
                    <p className="text-sm font-bold text-white">{adherence.completedTasksCount} / 690</p>
                  </div>
                </div>
              </div>

              <div className="mt-4 rounded-xl border border-slate-800 bg-slate-900/60 p-3 text-xs text-slate-300">
                {dailyScore.statusDescription}
              </div>
            </CyberCard>

            {/* Preparation Health Card */}
            <CyberCard
              glow={adherence.health === "ON_TRACK" ? "emerald" : adherence.health === "AT_RISK" ? "amber" : "rose"}
            >
              <div className="flex items-center justify-between">
                <span className="text-xs font-bold text-slate-400 uppercase">Preparation Health</span>
                <span
                  className={clsx(
                    "rounded-md border px-2 py-0.5 text-[10px] font-black uppercase",
                    adherence.health === "ON_TRACK"
                      ? "border-emerald-500/40 bg-emerald-500/10 text-emerald-400"
                      : adherence.health === "AT_RISK"
                      ? "border-amber-500/40 bg-amber-500/10 text-amber-400"
                      : "border-rose-500/40 bg-rose-500/10 text-rose-400"
                  )}
                >
                  {adherence.health.replace("_", " ")}
                </span>
              </div>

              <p className="mt-3 text-xs text-slate-300">{adherence.healthReason}</p>

              <div className="mt-4 space-y-2.5 border-t border-slate-800/80 pt-3 text-xs">
                <div className="flex justify-between">
                  <span className="text-slate-400">7-Day Adherence:</span>
                  <span className="font-bold text-white">{adherence.sevenDayAdherencePercent}%</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-slate-400">28-Day Adherence:</span>
                  <span className="font-bold text-white">{adherence.twentyEightDayAdherencePercent}%</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-slate-400">Active Backlogs:</span>
                  <span className="font-bold text-rose-400">{adherence.currentBacklogCount} lectures</span>
                </div>
              </div>

              <Link
                href="/analytics"
                className="mt-4 flex items-center justify-center gap-1.5 rounded-xl border border-slate-700 bg-slate-900/80 py-2 text-xs font-semibold text-slate-300 transition hover:border-gate-sky hover:text-white"
              >
                <span>Full Analytics Scorecard</span>
                <ArrowRight className="h-3.5 w-3.5" />
              </Link>
            </CyberCard>
          </div>
        </div>

        {/* Modal: Single Task Reschedule */}
        {taskToReschedule && (
          <RescheduleModal
            task={taskToReschedule}
            candidateSlots={candidateSlots}
            onDismiss={() => setTaskToReschedule(null)}
            onConfirm={async (newDate) => rescheduleTask(taskToReschedule.id, newDate)}
          />
        )}

        {/* Modal: Batch Backlog Reschedule */}
        {showBatchModal && (
          <BatchBacklogModal
            backlogs={activeBacklogs}
            candidateDates={candidateDates}
            candidateSlots={candidateSlots}
            onDismiss={() => setShowBatchModal(false)}
            onConfirmAll={async (assignments) => batchReschedule(assignments)}
          />
        )}
      </div>
    </AppShell>
  );
}
