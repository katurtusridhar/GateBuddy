"use client";

import React, { useState, useMemo } from "react";
import { TaskItemUi, CandidateDateSlot } from "@/lib/types";
import { Calendar, AlertCircle, X, Check, ShieldAlert, Sparkles } from "lucide-react";
import clsx from "clsx";

interface BatchBacklogModalProps {
  backlogs: TaskItemUi[];
  candidateDates: string[];
  candidateSlots: CandidateDateSlot[];
  onDismiss: () => void;
  onConfirmAll: (assignments: Record<string, string>) => Promise<{ success: boolean; error?: string }>;
}

export function BatchBacklogModal({
  backlogs,
  candidateDates,
  candidateSlots,
  onDismiss,
  onConfirmAll,
}: BatchBacklogModalProps) {
  // Sort backlogs by subject and sequence
  const sortedBacklogs = useMemo(() => {
    return [...backlogs].sort((a, b) => {
      const cmp = a.subject.localeCompare(b.subject);
      return cmp !== 0 ? cmp : a.curNum - b.curNum;
    });
  }, [backlogs]);

  // Pre-seed assignments respecting Rule 1 (count < 2) and Rule 2 (no duplicate subjects)
  const initialAssignments = useMemo(() => {
    const map: Record<string, string> = {};
    const dateCounts = new Map<string, number>();
    const dateSubjects = new Map<string, Set<string>>();

    for (const slot of candidateSlots) {
      dateCounts.set(slot.date, slot.currentCount);
      dateSubjects.set(
        slot.date,
        new Set(slot.existingSubjects.map((s) => s.toLowerCase().trim()))
      );
    }

    const bySubject = new Map<string, TaskItemUi[]>();
    for (const b of sortedBacklogs) {
      const arr = bySubject.get(b.subject) || [];
      arr.push(b);
      bySubject.set(b.subject, arr);
    }

    for (const [subj, list] of Array.from(bySubject.entries())) {
      const subjLower = subj.toLowerCase().trim();
      let startIdx = 0;
      for (const task of list) {
        let assigned = "";
        for (let i = startIdx; i < candidateDates.length; i++) {
          const d = candidateDates[i];
          const count = dateCounts.get(d) || 0;
          const subjects = dateSubjects.get(d) || new Set<string>();

          if (count < 2 && !subjects.has(subjLower)) {
            assigned = d;
            dateCounts.set(d, count + 1);
            subjects.add(subjLower);
            dateSubjects.set(d, subjects);
            startIdx = i + 1; // Strict sequence guarantee
            break;
          }
        }
        map[task.id] = assigned || candidateDates[candidateDates.length - 1] || "";
      }
    }

    return map;
  }, [sortedBacklogs, candidateDates, candidateSlots]);

  const [assignments, setAssignments] = useState<Record<string, string>>(initialAssignments);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [customTaskId, setCustomTaskId] = useState<string | null>(null);
  const [customDateInput, setCustomDateInput] = useState("");

  const handleConfirm = async () => {
    setIsSubmitting(true);
    setErrorMessage(null);
    const res = await onConfirmAll(assignments);
    if (!res.success) {
      setErrorMessage(res.error || "Batch assignment failed.");
      setIsSubmitting(false);
    } else {
      onDismiss();
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 p-4 backdrop-blur-md">
      <div className="relative flex max-h-[90vh] w-full max-w-2xl flex-col rounded-2xl border border-slate-700 bg-gate-card p-6 shadow-2xl">
        {/* Header */}
        <div className="flex items-center justify-between border-b border-slate-800 pb-4">
          <div className="flex items-center gap-3">
            <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-rose-500/10 text-rose-400">
              <ShieldAlert className="h-6 w-6" />
            </div>
            <div>
              <h2 className="text-xl font-bold text-white">Backlog Sequential Scheduler</h2>
              <p className="text-xs text-slate-400">
                {backlogs.length} Unrescheduled Lecture(s) • Max 2/day • No duplicate subjects per day
              </p>
            </div>
          </div>
          <button onClick={onDismiss} className="rounded-lg p-1.5 text-slate-400 hover:bg-slate-800 hover:text-white">
            <X className="h-5 w-5" />
          </button>
        </div>

        {/* Rule Banner */}
        <div className="mt-4 flex items-center gap-2 rounded-xl border border-emerald-500/30 bg-emerald-500/10 p-3 text-xs text-emerald-300">
          <Sparkles className="h-4 w-4 shrink-0 text-emerald-400" />
          <span>
            Dates are automatically pre-seeded in sequence with 100% schedule preservation. You may adjust or assign custom dates.
          </span>
        </div>

        {errorMessage && (
          <div className="mt-3 flex items-start gap-2 rounded-xl border border-rose-500/40 bg-rose-500/10 p-3 text-xs text-rose-300">
            <AlertCircle className="mt-0.5 h-4 w-4 shrink-0 text-rose-400" />
            <span>{errorMessage}</span>
          </div>
        )}

        {/* Backlog List */}
        <div className="mt-4 flex-1 space-y-3 overflow-y-auto pr-1">
          {sortedBacklogs.map((task) => {
            const currentSelected = assignments[task.id] || "";
            return (
              <div
                key={task.id}
                className="rounded-xl border border-slate-800 bg-slate-900/60 p-3.5 transition hover:border-slate-700"
              >
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <div className="flex items-center gap-2">
                      <span className="rounded bg-sky-500/20 px-2 py-0.5 text-[10px] font-bold text-gate-sky">
                        {task.subjectCode}
                      </span>
                      <span className="text-xs font-bold text-white">{task.title}</span>
                    </div>
                    <p className="mt-1 text-[11px] text-slate-400">
                      Originally scheduled: {task.originalDate}
                    </p>
                  </div>
                  <div className="text-right">
                    <span className="text-xs font-bold text-amber-400">
                      Assigned: {currentSelected || "Pending"}
                    </span>
                  </div>
                </div>

                {/* Candidate Selection Chips */}
                <div className="mt-2.5 flex flex-wrap gap-1.5">
                  {candidateDates.slice(0, 4).map((d) => (
                    <button
                      key={d}
                      type="button"
                      onClick={() => setAssignments((prev) => ({ ...prev, [task.id]: d }))}
                      className={clsx(
                        "rounded-lg border px-2.5 py-1 text-[11px] font-semibold transition",
                        currentSelected === d
                          ? "border-gate-sky bg-gate-sky/20 text-white"
                          : "border-slate-800 bg-slate-950 text-slate-400 hover:border-slate-700 hover:text-slate-200"
                      )}
                    >
                      {d.slice(5)}
                    </button>
                  ))}
                  <button
                    type="button"
                    onClick={() => {
                      setCustomTaskId(task.id);
                      setCustomDateInput(currentSelected);
                    }}
                    className="flex items-center gap-1 rounded-lg border border-amber-500/40 bg-amber-500/10 px-2.5 py-1 text-[11px] font-semibold text-amber-300 hover:bg-amber-500/20"
                  >
                    <Calendar className="h-3 w-3" />
                    <span>+ Custom</span>
                  </button>
                </div>
              </div>
            );
          })}
        </div>

        {/* Custom Date Modal Inside */}
        {customTaskId && (
          <div className="absolute inset-x-6 top-1/4 rounded-2xl border border-amber-500/50 bg-slate-950 p-5 shadow-2xl">
            <h4 className="text-sm font-bold text-white">Custom Recovery Date</h4>
            <p className="text-xs text-slate-400">Enter date in YYYY-MM-DD format:</p>
            <input
              type="text"
              placeholder="e.g. 2026-11-04"
              value={customDateInput}
              onChange={(e) => setCustomDateInput(e.target.value)}
              className="mt-2 w-full rounded-xl border border-slate-700 bg-slate-900 px-3 py-2 text-xs text-white focus:border-gate-sky focus:outline-none"
            />
            <div className="mt-3 flex justify-end gap-2">
              <button
                onClick={() => setCustomTaskId(null)}
                className="rounded-lg px-3 py-1.5 text-xs text-slate-400 hover:text-white"
              >
                Cancel
              </button>
              <button
                onClick={() => {
                  if (customDateInput.trim()) {
                    setAssignments((prev) => ({ ...prev, [customTaskId]: customDateInput.trim() }));
                  }
                  setCustomTaskId(null);
                }}
                className="rounded-lg bg-gate-sky px-4 py-1.5 text-xs font-bold text-slate-950 hover:bg-sky-400"
              >
                Set Date
              </button>
            </div>
          </div>
        )}

        {/* Footer */}
        <div className="mt-4 flex items-center justify-between border-t border-slate-800 pt-4">
          <button
            onClick={onDismiss}
            className="rounded-xl px-4 py-2 text-xs font-semibold text-slate-400 hover:bg-slate-800 hover:text-white"
          >
            Cancel
          </button>
          <button
            onClick={handleConfirm}
            disabled={isSubmitting}
            className="flex items-center gap-2 rounded-xl bg-gate-sky px-5 py-2.5 text-xs font-bold text-slate-950 shadow-glowSky transition hover:bg-sky-400 disabled:opacity-50"
          >
            <Check className="h-4 w-4" />
            <span>{isSubmitting ? "Saving..." : "Confirm All & Unlock Today"}</span>
          </button>
        </div>
      </div>
    </div>
  );
}
