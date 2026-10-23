"use client";

import React, { useState, useMemo } from "react";
import { AppShell } from "@/components/layout/AppShell";
import { useApp } from "@/lib/store";
import { TaskCard } from "@/components/ui/TaskCard";
import { RescheduleModal } from "@/components/modals/RescheduleModal";
import { compareDateStrings, isSundayDate } from "@/lib/dateUtils";
import { TaskItemUi } from "@/lib/types";
import { ChevronLeft, ChevronRight } from "lucide-react";
import clsx from "clsx";

export default function CalendarPage() {
  const {
    tasks,
    progress,
    recoveries,
    todayDateStr,
    getTasksForDate,
    toggleTaskComplete,
    rescheduleTask,
    candidateSlots,
  } = useApp();

  const [currentYear, setCurrentYear] = useState(2026);
  const [currentMonth, setCurrentMonth] = useState(10); // Oct = 10
  const [selectedDateStr, setSelectedDateStr] = useState(todayDateStr);
  const [taskToReschedule, setTaskToReschedule] = useState<TaskItemUi | null>(null);

  const selectedDateTasks = useMemo(() => getTasksForDate(selectedDateStr), [getTasksForDate, selectedDateStr]);

  // Generate calendar grid for current month starting with Monday (ISO week)
  const monthData = useMemo(() => {
    const daysInMonth = new Date(currentYear, currentMonth, 0).getDate();
    // In JS: getDay() returns 0 for Sunday, 1 for Monday, etc.
    // We want Monday = 0, Tuesday = 1, ... Sunday = 6
    const firstDay = new Date(currentYear, currentMonth - 1, 1).getDay();
    const dayOfWeekOffset = (firstDay + 6) % 7;

    const cells: { dateStr: string; dayNum: number; isCurrentMonth: boolean }[] = [];

    // Preceding padding
    for (let i = 0; i < dayOfWeekOffset; i++) {
      cells.push({ dateStr: "", dayNum: 0, isCurrentMonth: false });
    }

    // Days of month
    for (let day = 1; day <= daysInMonth; day++) {
      const dateStr = `${currentYear}-${String(currentMonth).padStart(2, "0")}-${String(day).padStart(2, "0")}`;
      cells.push({ dateStr, dayNum: day, isCurrentMonth: true });
    }

    return cells;
  }, [currentYear, currentMonth]);

  const handlePrevMonth = () => {
    if (currentYear === 2026 && currentMonth <= 10) return;
    if (currentMonth === 1) {
      setCurrentMonth(12);
      setCurrentYear((y) => y - 1);
    } else {
      setCurrentMonth((m) => m - 1);
    }
  };

  const handleNextMonth = () => {
    if (currentYear === 2027 && currentMonth >= 9) return;
    if (currentMonth === 12) {
      setCurrentMonth(1);
      setCurrentYear((y) => y + 1);
    } else {
      setCurrentMonth((m) => m + 1);
    }
  };

  const monthNames = [
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December"
  ];

  const formattedSelectedHeader = useMemo(() => {
    try {
      const parts = selectedDateStr.split("-").map(Number);
      const d = new Date(parts[0], parts[1] - 1, parts[2]);
      return d.toLocaleDateString("en-US", {
        weekday: "long",
        month: "short",
        day: "numeric",
        year: "numeric",
      });
    } catch {
      return selectedDateStr;
    }
  }, [selectedDateStr]);

  return (
    <AppShell>
      <div className="mx-auto max-w-6xl space-y-6">
        {/* Month Navigation & Today Jump */}
        <div className="flex items-center justify-between rounded-2xl border border-[#26354F] bg-[#151F32] px-4 py-2.5 shadow-lg">
          <button
            onClick={handlePrevMonth}
            disabled={currentYear === 2026 && currentMonth <= 10}
            className="rounded-xl p-2 text-slate-400 hover:bg-[#1E2B45] hover:text-white disabled:opacity-30 disabled:pointer-events-none transition"
            aria-label="Previous Month"
          >
            <ChevronLeft className="h-5 w-5" />
          </button>

          <div className="flex items-center gap-3">
            <h2 className="text-base sm:text-lg font-bold text-white">
              {monthNames[currentMonth - 1]} {currentYear}
            </h2>

            {/* Quick TODAY Jump Button */}
            <button
              onClick={() => {
                setCurrentYear(2026);
                setCurrentMonth(10);
                setSelectedDateStr(todayDateStr);
              }}
              className="flex items-center gap-1.5 rounded-xl border border-sky-500/50 bg-sky-500/15 px-2.5 py-1 text-[11px] font-bold text-sky-400 transition hover:bg-sky-500/25"
            >
              <span className="h-1.5 w-1.5 rounded-full bg-sky-400 animate-pulse" />
              <span>TODAY</span>
            </button>
          </div>

          <button
            onClick={handleNextMonth}
            disabled={currentYear === 2027 && currentMonth >= 9}
            className="rounded-xl p-2 text-slate-400 hover:bg-[#1E2B45] hover:text-white disabled:opacity-30 disabled:pointer-events-none transition"
            aria-label="Next Month"
          >
            <ChevronRight className="h-5 w-5" />
          </button>
        </div>

        {/* 2-Column Responsive Layout */}
        <div className="grid grid-cols-1 gap-6 lg:grid-cols-12">
          {/* Calendar Grid & Legend (7 cols on desktop) */}
          <div className="space-y-4 lg:col-span-7">
            {/* Calendar Card */}
            <div className="rounded-2xl border border-[#26354F] bg-[#0F172A] p-4 sm:p-5 shadow-xl">
              {/* Day Headers (M T W T F S S) */}
              <div className="grid grid-cols-7 gap-1 text-center text-xs font-bold text-slate-400 pb-3 border-b border-[#26354F]/60">
                <span>M</span>
                <span>T</span>
                <span>W</span>
                <span>T</span>
                <span>F</span>
                <span>S</span>
                <span className="text-rose-400">S</span>
              </div>

              {/* Day Cells Grid */}
              <div className="grid grid-cols-7 gap-1.5 sm:gap-2 pt-3">
                {monthData.map((cell, idx) => {
                  if (!cell.isCurrentMonth) {
                    return <div key={`empty-${idx}`} className="aspect-square rounded-xl bg-transparent" />;
                  }

                  const dateTasks = tasks.filter((t) => t.date === cell.dateStr);
                  const isToday = cell.dateStr === todayDateStr;
                  const isSelected = cell.dateStr === selectedDateStr;
                  const isPast = compareDateStrings(cell.dateStr, todayDateStr) < 0;
                  const isSunday = isSundayDate(cell.dateStr);

                  const hasRec = recoveries.some(
                    (r) => r.recoveryDate === cell.dateStr && (r.status === "ACTIVE" || r.status === "MANUAL_CONFIRMED")
                  );

                  // Calculate status dot color matching Android
                  const completedCount = dateTasks.filter((t) => progress.get(t.id)?.isCompleted).length;
                  const hasTasks = dateTasks.length > 0;
                  const allDone = hasTasks && completedCount === dateTasks.length;
                  const hasMissed = isPast && hasTasks && completedCount < dateTasks.length;

                  let dotColor = "";
                  if (allDone) {
                    dotColor = "bg-emerald-400 shadow-[0_0_6px_#34d399]";
                  } else if (hasMissed) {
                    dotColor = "bg-rose-400 shadow-[0_0_6px_#f43f5e]";
                  } else if (isSunday || hasRec) {
                    dotColor = "bg-amber-400 shadow-[0_0_6px_#f59e0b]";
                  }

                  return (
                    <button
                      key={cell.dateStr}
                      type="button"
                      onClick={() => setSelectedDateStr(cell.dateStr)}
                      className={clsx(
                        "group relative aspect-square flex flex-col items-center justify-center rounded-xl border text-center transition-all duration-150",
                        isSelected
                          ? "border-sky-400 bg-sky-500/20 shadow-[0_0_12px_rgba(14,165,233,0.3)] ring-1 ring-sky-400"
                          : isToday
                          ? "border-sky-500/80 bg-[#1E2B45]"
                          : "border-[#26354F]/70 bg-[#151F32]/70 hover:border-slate-500 hover:bg-[#1E2B45]/60"
                      )}
                    >
                      <span
                        className={clsx(
                          "text-xs sm:text-sm font-bold",
                          isSelected
                            ? "text-sky-300 font-black"
                            : isToday
                            ? "text-sky-400 font-extrabold"
                            : isSunday
                            ? "text-slate-200"
                            : "text-slate-200"
                        )}
                      >
                        {cell.dayNum}
                      </span>

                      {/* Status Dot */}
                      {dotColor ? (
                        <span className={clsx("mt-1 h-1.5 w-1.5 rounded-full shrink-0", dotColor)} />
                      ) : (
                        <span className="mt-1 h-1.5 w-1.5 bg-transparent" />
                      )}
                    </button>
                  );
                })}
              </div>
            </div>

            {/* Status Legend Bar (Exact match to Android) */}
            <div className="flex flex-wrap items-center justify-between gap-2 rounded-xl border border-[#26354F] bg-[#151F32]/80 px-4 py-2.5 text-xs text-slate-400">
              <div className="flex items-center gap-1.5">
                <span className="h-2 w-2 rounded-full bg-emerald-400" />
                <span>Done</span>
              </div>
              <div className="flex items-center gap-1.5">
                <span className="h-2 w-2 rounded-full bg-rose-400" />
                <span>Missed</span>
              </div>
              <div className="flex items-center gap-1.5">
                <span className="h-2 w-2 rounded-full bg-amber-400" />
                <span>Test/Rec</span>
              </div>
              <div className="flex items-center gap-1.5">
                <span className="h-2 w-2 rounded-full bg-sky-400" />
                <span>Buffer</span>
              </div>
              <div className="flex items-center gap-1.5">
                <span className="h-2 w-2 rounded-full bg-slate-600" />
                <span>Upcoming</span>
              </div>
            </div>
          </div>

          {/* Selected Date Tasks Column (5 cols on desktop) */}
          <div className="space-y-4 lg:col-span-5">
            {/* Header: TASKS FOR THIS DATE pill + Date Title + Task Count */}
            <div className="flex items-center justify-between rounded-2xl border border-[#26354F] bg-[#0F172A] p-4 shadow-lg">
              <div>
                <span className="rounded-lg border border-sky-500/40 bg-sky-950/60 px-2 py-0.5 text-[10px] font-bold text-sky-400 tracking-wider uppercase">
                  • TASKS FOR THIS DATE
                </span>
                <h3 className="mt-1.5 text-base sm:text-lg font-bold text-white">{formattedSelectedHeader}</h3>
              </div>
              <span className="rounded-xl border border-indigo-500/40 bg-indigo-950/60 px-3 py-1 text-xs font-bold text-indigo-300">
                {selectedDateTasks.length} tasks
              </span>
            </div>

            {/* Task Cards List */}
            <div className="space-y-3">
              {selectedDateTasks.length === 0 ? (
                <div className="rounded-2xl border border-[#26354F] bg-[#0F172A] p-8 text-center text-xs text-slate-400">
                  No tasks scheduled for this date.
                </div>
              ) : (
                selectedDateTasks.map((task) => (
                  <TaskCard
                    key={task.id}
                    task={task}
                    onToggleComplete={async (notes) => toggleTaskComplete(task.id, notes)}
                    onOpenReschedule={() => setTaskToReschedule(task)}
                  />
                ))
              )}
            </div>
          </div>
        </div>

        {/* Reschedule Modal */}
        {taskToReschedule && (
          <RescheduleModal
            task={taskToReschedule}
            candidateSlots={candidateSlots}
            onDismiss={() => setTaskToReschedule(null)}
            onConfirm={async (newDate) => rescheduleTask(taskToReschedule.id, newDate)}
          />
        )}
      </div>
    </AppShell>
  );
}
