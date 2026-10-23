"use client";

import React, { useState } from "react";
import { TaskItemUi } from "@/lib/types";
import { Check, X as CloseIcon, Calendar, StickyNote, Lock, AlertTriangle } from "lucide-react";
import clsx from "clsx";

interface TaskCardProps {
  task: TaskItemUi;
  onToggleComplete: (notes?: string) => Promise<{ success: boolean; error?: string }>;
  onOpenReschedule?: () => void;
}

export function TaskCard({ task, onToggleComplete, onOpenReschedule }: TaskCardProps) {
  const [showNotes, setShowNotes] = useState(false);
  const [noteText, setNoteText] = useState(task.notes || "");
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const isLockedPast = task.isPast && !task.isCompleted;

  const handleToggle = async () => {
    if (isLockedPast) {
      setErrorMessage("Strict Rule: Past missed tasks cannot be marked complete on past days. Please assign them to a catch-up slot.");
      return;
    }
    setIsSubmitting(true);
    setErrorMessage(null);
    const res = await onToggleComplete(noteText);
    if (!res.success) {
      setErrorMessage(res.error || "Cannot complete task.");
    }
    setIsSubmitting(false);
  };

  const handleSaveNotes = async () => {
    await onToggleComplete(noteText);
    setShowNotes(false);
  };

  // Category badge
  const slotInfo = (() => {
    if (task.isAssignedForRecovery) {
      return { label: "CATCH-UP (+10)", bg: "bg-amber-950/70 border-amber-700/60 text-amber-300" };
    }
    if (task.type === "SUNDAY_TEST") {
      return { label: "SUNDAY TEST", bg: "bg-amber-950/70 border-amber-700/60 text-amber-300" };
    }
    if (task.isBooster) {
      return { label: "BOOSTER (+5)", bg: "bg-emerald-950/70 border-emerald-700/60 text-emerald-300" };
    }
    if (task.isCustom) {
      return { label: `CUSTOM (+${task.pointsValue})`, bg: "bg-indigo-950/70 border-indigo-700/60 text-indigo-300" };
    }
    return { label: `SLOT ${task.slot}`, bg: "bg-indigo-950/70 border-indigo-700/60 text-indigo-300" };
  })();

  // Status Badge matching Android TaskStatusBadge
  const statusInfo = (() => {
    if (task.isCompleted) {
      return { label: "DONE", dotColor: "bg-emerald-400", bg: "bg-emerald-950/50 border-emerald-500/50 text-emerald-300" };
    }
    if (task.isAssignedForRecovery || task.status === "RECOVERY_SCHEDULED") {
      return { label: "RECOVERY", dotColor: "bg-amber-400", bg: "bg-amber-950/50 border-amber-500/50 text-amber-300" };
    }
    if (task.status === "MISSED" || task.isPast) {
      return { label: "MISSED", dotColor: "bg-rose-400", bg: "bg-rose-950/50 border-rose-500/50 text-rose-300" };
    }
    if (task.status === "DUE_TODAY") {
      return { label: "TODAY", dotColor: "bg-sky-400", bg: "bg-sky-950/50 border-sky-500/50 text-sky-300" };
    }
    return { label: "UPCOMING", dotColor: "bg-slate-400", bg: "bg-slate-800/80 border-slate-700 text-slate-400" };
  })();

  const displaySubtitle = (() => {
    if (task.details && !task.details.toLowerCase().includes("lecture " + task.curNum + " of")) {
      return task.details;
    }
    if (task.isAssignedForRecovery) {
      return "Sunday catch-up slot • +10 points on completion";
    }
    return `Core GATE CSE lecture • Slot ${task.slot} • +10 points`;
  })();

  return (
    <div
      className={clsx(
        "relative rounded-2xl border transition-all duration-200 p-4 sm:p-5",
        task.isCompleted
          ? "border-emerald-500/40 bg-gradient-to-br from-[#122329] to-[#0d1820]"
          : task.isAssignedForRecovery || task.status === "RECOVERY_SCHEDULED"
          ? "border-amber-500/40 bg-gradient-to-br from-[#1b1c2b] to-[#141524]"
          : isLockedPast
          ? "border-rose-500/30 bg-gradient-to-br from-[#1b1724] to-[#12101c]"
          : "border-[#26354F] bg-gradient-to-br from-[#151F32] to-[#1E2B45] hover:border-slate-600"
      )}
    >
      {/* Header Row: Slot, Subject Pill, and Status Badge */}
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="flex flex-wrap items-center gap-2">
          {/* Category Slot Pill */}
          <span className={clsx("rounded-lg border px-2.5 py-1 text-[11px] font-bold tracking-wider", slotInfo.bg)}>
            {slotInfo.label}
          </span>

          {/* Subject Pill with Code */}
          {task.subjectCode && (
            <span className="rounded-lg border border-sky-600/40 bg-sky-950/70 px-2.5 py-1 text-[11px] font-semibold text-sky-300">
              [{task.subjectCode}] {task.subject}
            </span>
          )}
        </div>

        {/* Status Chip */}
        <span
          className={clsx(
            "flex items-center gap-1.5 rounded-full border px-2.5 py-0.5 text-[11px] font-bold",
            statusInfo.bg
          )}
        >
          <span className={clsx("h-1.5 w-1.5 rounded-full", statusInfo.dotColor)} />
          <span>{statusInfo.label}</span>
        </span>
      </div>

      {/* Title & Subtitle + Action Check Button */}
      <div className="mt-3 flex items-start justify-between gap-4">
        <div className="flex-1 min-w-0">
          <h3
            className={clsx(
              "text-[15px] font-bold tracking-tight transition-colors line-clamp-2",
              task.isCompleted ? "text-slate-300 line-through" : "text-white"
            )}
          >
            {task.title}
          </h3>
          <p className="mt-1 text-xs text-slate-400 line-clamp-2">{displaySubtitle}</p>
        </div>

        {/* Action Toggle / Status Button (36px Circle) */}
        <div className="shrink-0 pt-0.5">
          {isLockedPast ? (
            // Past Missed: Rose circle with X
            <div
              className="flex h-9 w-9 items-center justify-center rounded-full border border-rose-500/70 bg-rose-950/40 text-rose-400 shadow-[0_0_10px_rgba(244,63,94,0.2)]"
              title="Missed in past (Locked). Reschedule below."
            >
              <CloseIcon className="h-4 w-4 stroke-[2.5]" />
            </div>
          ) : task.isPast && task.isCompleted ? (
            // Past Completed: Emerald circle with check
            <div
              className="flex h-9 w-9 items-center justify-center rounded-full border border-emerald-500/70 bg-emerald-950/40 text-emerald-400 shadow-[0_0_10px_rgba(16,185,129,0.2)]"
              title="Completed in past (Locked)"
            >
              <Check className="h-4 w-4 stroke-[3]" />
            </div>
          ) : task.isFuture ? (
            // Future locked
            <div
              className="flex h-9 w-9 items-center justify-center rounded-full border border-slate-700 bg-slate-800/60 text-slate-500"
              title="Future task locked until scheduled date"
            >
              <Lock className="h-4 w-4" />
            </div>
          ) : (
            // Today: Interactive Checkbox
            <button
              onClick={handleToggle}
              disabled={isSubmitting}
              className={clsx(
                "flex h-9 w-9 items-center justify-center rounded-full border transition-all duration-200",
                task.isCompleted
                  ? "border-emerald-500 bg-emerald-500 text-slate-950 shadow-[0_0_12px_rgba(16,185,129,0.5)]"
                  : "border-slate-600 bg-slate-800/80 text-slate-400 hover:border-sky-400 hover:text-white"
              )}
              title={task.isCompleted ? "Click to uncheck" : "Click to mark done"}
            >
              {task.isCompleted && <Check className="h-4 w-4 stroke-[3]" />}
            </button>
          )}
        </div>
      </div>

      {/* Rescheduled Banner (Matching Android Screenshot with Change Button) */}
      {(task.isAssignedForRecovery || task.status === "RECOVERY_SCHEDULED") && task.recoveryDate && (
        <div className="mt-3 flex items-center justify-between rounded-xl border border-amber-500/50 bg-[#451A03]/40 px-3 py-2 text-xs font-semibold text-amber-300">
          <div className="flex items-center gap-2">
            <Calendar className="h-4 w-4 text-amber-400 shrink-0" />
            <span>
              {task.displayDate === task.recoveryDate
                ? `Catch-up originally from ${task.originalDate}`
                : `Rescheduled to: ${task.recoveryDate}`}
            </span>
          </div>

          {onOpenReschedule && (
            <button
              onClick={onOpenReschedule}
              className="rounded-lg bg-amber-500/25 px-2.5 py-1 text-[11px] font-bold text-amber-200 transition hover:bg-amber-500/40 hover:text-white"
            >
              Change
            </button>
          )}
        </div>
      )}

      {/* Reschedule Call-to-Action for Past Missed Backlog */}
      {!task.isAssignedForRecovery && task.status === "MISSED" && onOpenReschedule && (
        <div className="mt-3 flex items-center justify-between rounded-xl border border-rose-500/40 bg-rose-950/30 px-3 py-2 text-xs font-semibold text-rose-300">
          <div className="flex items-center gap-2">
            <Calendar className="h-4 w-4 text-rose-400 shrink-0" />
            <span>Backlog: Tap to assign recovery date</span>
          </div>
          <button
            onClick={onOpenReschedule}
            className="rounded-lg bg-rose-500/25 px-2.5 py-1 text-[11px] font-bold text-rose-200 transition hover:bg-rose-500/40 hover:text-white"
          >
            Reschedule
          </button>
        </div>
      )}

      {/* Error Message */}
      {errorMessage && (
        <div className="mt-3 flex items-center gap-2 rounded-xl border border-rose-500/50 bg-rose-950/50 p-2.5 text-xs text-rose-300">
          <AlertTriangle className="h-4 w-4 shrink-0 text-rose-400" />
          <span>{errorMessage}</span>
        </div>
      )}

      {/* Bottom Row: Notes Icon */}
      <div className="mt-3 flex items-center justify-between border-t border-[#26354F]/70 pt-2.5 text-xs">
        <button
          onClick={() => setShowNotes(!showNotes)}
          className="flex items-center gap-1.5 text-slate-400 hover:text-sky-400 transition"
        >
          <StickyNote className="h-3.5 w-3.5" />
          <span>{task.notes ? "View Note" : "Add Note"}</span>
        </button>

        {task.notes && !showNotes && (
          <span className="italic text-[11px] text-slate-400 truncate max-w-[200px]">&ldquo;{task.notes}&rdquo;</span>
        )}
      </div>

      {/* Notes Dialog / Inline Editor */}
      {showNotes && (
        <div className="mt-2.5 rounded-xl border border-slate-700 bg-slate-900/95 p-3">
          <textarea
            value={noteText}
            onChange={(e) => setNoteText(e.target.value)}
            placeholder="Write key formulas, lecture timestamps, doubts..."
            className="w-full resize-none rounded-lg border border-slate-800 bg-slate-950 p-2.5 text-xs text-white placeholder-slate-500 focus:border-sky-400 focus:outline-none"
            rows={3}
          />
          <div className="mt-2 flex justify-end gap-2">
            <button
              onClick={() => setShowNotes(false)}
              className="rounded-lg px-2.5 py-1 text-xs text-slate-400 hover:text-white"
            >
              Cancel
            </button>
            <button
              onClick={handleSaveNotes}
              className="rounded-lg bg-sky-500 px-3 py-1 text-xs font-bold text-slate-950 hover:bg-sky-400 transition"
            >
              Save Note
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
