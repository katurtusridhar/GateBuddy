"use client";

import React, { useState } from "react";
import { TaskItemUi, CandidateDateSlot } from "@/lib/types";
import { Calendar, AlertCircle, X, Check, Info } from "lucide-react";
import clsx from "clsx";

interface RescheduleModalProps {
  task: TaskItemUi;
  candidateSlots: CandidateDateSlot[];
  onDismiss: () => void;
  onConfirm: (dateStr: string) => Promise<{ success: boolean; error?: string }>;
}

export function RescheduleModal({ task, candidateSlots, onDismiss, onConfirm }: RescheduleModalProps) {
  const [selectedDate, setSelectedDate] = useState<string>(task.recoveryDate || candidateSlots[0]?.date || "");
  const [customDate, setCustomDate] = useState<string>("");
  const [isCustom, setIsCustom] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const activeDate = isCustom ? customDate.trim() : selectedDate;

  // Validation function
  const getValidationError = (dateStr: string): string | null => {
    if (!dateStr) return "Please choose or enter a date.";
    if (!/^\d{4}-\d{2}-\d{2}$/.test(dateStr)) return "Invalid format. Use YYYY-MM-DD.";
    
    const slot = candidateSlots.find((s) => s.date === dateStr);
    if (slot && slot.currentCount >= 2 && task.recoveryDate !== dateStr) {
      return `Rule 1 Violation: ${dateStr} already has 2 recovery lectures.`;
    }
    const hasSameSubject = slot?.existingSubjects.some(
      (s) => s.toLowerCase().trim() === task.subject.toLowerCase().trim()
    );
    if (hasSameSubject && task.recoveryDate !== dateStr) {
      return `Rule 2 Violation: A lecture for '${task.subject}' is already scheduled on ${dateStr}.`;
    }
    return null;
  };

  const currentRuleError = getValidationError(activeDate);

  const handleConfirm = async () => {
    if (currentRuleError) {
      setErrorMessage(currentRuleError);
      return;
    }
    setIsSubmitting(true);
    setErrorMessage(null);
    const res = await onConfirm(activeDate);
    if (!res.success) {
      setErrorMessage(res.error || "Failed to reschedule.");
      setIsSubmitting(false);
    } else {
      onDismiss();
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/75 p-4 backdrop-blur-md">
      <div className="relative w-full max-w-lg rounded-2xl border border-slate-700 bg-gate-card p-6 shadow-2xl">
        {/* Header */}
        <div className="flex items-center justify-between border-b border-slate-800 pb-4">
          <div className="flex items-center gap-2.5">
            <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-amber-500/10 text-amber-400">
              <Calendar className="h-5 w-5" />
            </div>
            <div>
              <h2 className="text-lg font-bold text-white">Reschedule Backlog Lecture</h2>
              <p className="text-xs text-slate-400">Rules: Max 2/day • No duplicate subjects per day</p>
            </div>
          </div>
          <button onClick={onDismiss} className="rounded-lg p-1.5 text-slate-400 hover:bg-slate-800 hover:text-white">
            <X className="h-5 w-5" />
          </button>
        </div>

        {/* Task Details */}
        <div className="mt-4 rounded-xl border border-slate-800 bg-slate-900/60 p-3.5">
          <div className="flex items-center gap-2">
            <span className="rounded bg-sky-500/20 px-2 py-0.5 text-[10px] font-bold text-gate-sky">
              {task.subjectCode}
            </span>
            <span className="text-sm font-semibold text-white">{task.title}</span>
          </div>
          <p className="mt-1 text-xs text-slate-400">Original Scheduled Date: {task.originalDate}</p>
        </div>

        {/* Rule Reminder */}
        <div className="mt-4 flex items-start gap-2 rounded-xl border border-emerald-500/30 bg-emerald-500/10 p-3 text-xs text-emerald-300">
          <Info className="mt-0.5 h-4 w-4 shrink-0 text-emerald-400" />
          <span>
            <strong>Fundamental Rules Enforced:</strong> 1) Maximum 2 lectures per day. 2) Two subjects cannot be the same on the same day. All previous schedules remain completely intact.
          </span>
        </div>

        {/* Error Alert */}
        {(errorMessage || currentRuleError) && (
          <div className="mt-3 flex items-start gap-2 rounded-xl border border-rose-500/40 bg-rose-500/10 p-3 text-xs text-rose-300">
            <AlertCircle className="mt-0.5 h-4 w-4 shrink-0 text-rose-400" />
            <span>{errorMessage || currentRuleError}</span>
          </div>
        )}

        {/* Candidate Dates List */}
        <div className="mt-4">
          <label className="text-xs font-semibold uppercase tracking-wider text-slate-400">
            Available Sundays & Buffer Days
          </label>
          <div className="mt-2 grid max-h-52 grid-cols-1 gap-2 overflow-y-auto pr-1">
            {candidateSlots.slice(0, 8).map((slot) => {
              const isSelected = !isCustom && selectedDate === slot.date;
              const isFull = slot.currentCount >= 2 && task.recoveryDate !== slot.date;
              const isSameSubj = slot.existingSubjects.some(
                (s) => s.toLowerCase().trim() === task.subject.toLowerCase().trim()
              ) && task.recoveryDate !== slot.date;
              const isBlocked = isFull || isSameSubj;

              return (
                <button
                  key={slot.date}
                  type="button"
                  disabled={isBlocked}
                  onClick={() => {
                    setIsCustom(false);
                    setSelectedDate(slot.date);
                  }}
                  className={clsx(
                    "flex items-center justify-between rounded-xl border p-3 text-left transition-all",
                    isSelected
                      ? "border-gate-sky bg-gate-sky/15 text-white"
                      : isBlocked
                      ? "cursor-not-allowed border-slate-800/80 bg-slate-900/30 text-slate-500 opacity-60"
                      : "border-slate-800 bg-slate-900/70 text-slate-300 hover:border-slate-700 hover:bg-slate-800"
                  )}
                >
                  <div>
                    <span className="text-xs font-bold">{slot.date}</span>
                    <span className="ml-2 text-[11px] text-slate-400">
                      ({slot.currentCount}/2 slots filled)
                    </span>
                    {isSameSubj && (
                      <p className="text-[10px] text-amber-400">Rule 2: {task.subject} already scheduled</p>
                    )}
                    {isFull && <p className="text-[10px] text-rose-400">Rule 1: Day capacity full (2/2)</p>}
                  </div>
                  {isSelected && <Check className="h-4 w-4 text-gate-sky" />}
                </button>
              );
            })}
          </div>
        </div>

        {/* Custom Date Input */}
        <div className="mt-4">
          <label className="text-xs font-semibold uppercase tracking-wider text-slate-400">
            Or Choose Custom Date (YYYY-MM-DD)
          </label>
          <div className="mt-1.5 flex gap-2">
            <input
              type="text"
              placeholder="e.g. 2026-11-04"
              value={customDate}
              onChange={(e) => {
                setCustomDate(e.target.value);
                setIsCustom(true);
              }}
              className="w-full rounded-xl border border-slate-700 bg-slate-900/90 px-3 py-2 text-xs text-white placeholder-slate-500 focus:border-gate-sky focus:outline-none"
            />
          </div>
        </div>

        {/* Action Buttons */}
        <div className="mt-6 flex justify-end gap-3 border-t border-slate-800 pt-4">
          <button
            onClick={onDismiss}
            className="rounded-xl px-4 py-2 text-xs font-semibold text-slate-400 hover:bg-slate-800 hover:text-white"
          >
            Cancel
          </button>
          <button
            onClick={handleConfirm}
            disabled={isSubmitting || Boolean(currentRuleError)}
            className={clsx(
              "rounded-xl px-5 py-2 text-xs font-bold text-slate-950 transition-all",
              Boolean(currentRuleError) || isSubmitting
                ? "cursor-not-allowed bg-slate-700 text-slate-400"
                : "bg-gate-sky hover:bg-sky-400 shadow-glowSky"
            )}
          >
            {isSubmitting ? "Assigning..." : "Confirm Reschedule"}
          </button>
        </div>
      </div>
    </div>
  );
}
