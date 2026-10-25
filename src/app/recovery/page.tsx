"use client";

import React, { useMemo } from "react";
import { AppShell } from "@/components/layout/AppShell";
import { useApp } from "@/lib/store";
import { CyberCard } from "@/components/ui/CyberCard";
import { TaskCard } from "@/components/ui/TaskCard";
import { CheckCircle2, Smartphone } from "lucide-react";

export default function RecoveryCenterPage() {
  const {
    activeBacklogs,
    recoveries,
    candidateSlots,
  } = useApp();

  const activeAssignedRecoveries = useMemo(() => {
    return recoveries.filter(
      (r) => (r.status === "ACTIVE" || r.status === "MANUAL_CONFIRMED") && !r.isCompleted
    );
  }, [recoveries]);

  return (
    <AppShell>
      <div className="mx-auto max-w-7xl space-y-6">
        {/* Header Banner */}
        <div className="flex flex-col justify-between gap-4 rounded-2xl border border-slate-800 bg-gradient-to-r from-gate-card via-gate-cardElevated to-slate-900 p-6 shadow-xl sm:flex-row sm:items-center">
          <div>
            <div className="flex items-center gap-2">
              <span className="text-xs font-bold tracking-widest text-amber-400 uppercase">
                RECOVERY STATUS MIRROR
              </span>
              <span className="rounded-full bg-amber-500/20 px-2 py-0.5 text-[10px] font-bold text-amber-400">
                READ-ONLY MIRROR
              </span>
            </div>
            <h1 className="mt-1 text-2xl font-black text-white sm:text-3xl">Backlog Recovery Command</h1>
            <p className="mt-1 text-xs text-slate-400">
              Mirror view of Rule 1 (max 2/day) and Rule 2 (unique subjects) recovery assignments synced from your Android App.
            </p>
          </div>

          <div className="flex items-center gap-2 rounded-xl border border-amber-500/30 bg-amber-500/10 px-4 py-2 text-xs font-semibold text-amber-300">
            <Smartphone className="h-4 w-4" />
            <span>Managed via GATEBuddy Android App</span>
          </div>
        </div>

        {/* Stats Row */}
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
          <CyberCard glow={activeBacklogs.length > 0 ? "rose" : "emerald"}>
            <span className="text-xs font-semibold text-slate-400 uppercase">Unrescheduled Backlogs</span>
            <p className="mt-2 text-3xl font-extrabold text-white">{activeBacklogs.length}</p>
            <p className="mt-1 text-xs text-slate-400">
              {activeBacklogs.length === 0 ? "No backlogs pending! You are 100% on track." : "Requires assignment via Android App."}
            </p>
          </CyberCard>

          <CyberCard glow="amber">
            <span className="text-xs font-semibold text-slate-400 uppercase">Scheduled Catch-Ups</span>
            <p className="mt-2 text-3xl font-extrabold text-amber-400">{activeAssignedRecoveries.length}</p>
            <p className="mt-1 text-xs text-slate-400">Assigned to upcoming Sundays &amp; buffers</p>
          </CyberCard>

          <CyberCard glow="sky">
            <span className="text-xs font-semibold text-slate-400 uppercase">Available Recovery Slots</span>
            <p className="mt-2 text-3xl font-extrabold text-gate-sky">
              {candidateSlots.filter((s) => !s.isFull).length * 2}
            </p>
            <p className="mt-1 text-xs text-slate-400">Capacity across next 16 Sundays</p>
          </CyberCard>
        </div>

        {/* Main 2-Col Content */}
        <div className="grid grid-cols-1 gap-6 lg:grid-cols-12">
          {/* Left: Pending Backlogs (7 cols) */}
          <div className="space-y-4 lg:col-span-7">
            <div className="flex items-center justify-between">
              <h2 className="text-lg font-bold text-white">Pending Backlogs ({activeBacklogs.length})</h2>
              {activeBacklogs.length > 0 && (
                <span className="text-xs font-medium text-slate-400 italic">
                  Reschedule via Android Companion App
                </span>
              )}
            </div>

            {activeBacklogs.length === 0 ? (
              <CyberCard glow="emerald" className="py-12 text-center">
                <CheckCircle2 className="mx-auto h-12 w-12 text-emerald-400" />
                <h3 className="mt-3 text-base font-bold text-white">All Clear! No Pending Backlogs</h3>
                <p className="mt-1 text-xs text-slate-400">
                  You have cleared all past backlogs. Continue following the daily schedule.
                </p>
              </CyberCard>
            ) : (
              <div className="space-y-3">
                {activeBacklogs.map((task) => (
                  <TaskCard
                    key={task.id}
                    task={task}
                  />
                ))}
              </div>
            )}
          </div>

          {/* Right: Upcoming Slot Availability (5 cols) */}
          <div className="space-y-4 lg:col-span-5">
            <CyberCard glow="none">
              <h3 className="text-sm font-bold text-white">Upcoming Sunday &amp; Buffer Capacity</h3>
              <p className="text-xs text-slate-400">Real-time Rule 1 &amp; Rule 2 slot availability</p>

              <div className="mt-4 space-y-2.5 max-h-[500px] overflow-y-auto pr-1">
                {candidateSlots.slice(0, 10).map((slot) => (
                  <div
                    key={slot.date}
                    className="flex items-center justify-between rounded-xl border border-slate-800 bg-slate-900/60 p-3"
                  >
                    <div>
                      <span className="text-xs font-bold text-white">{slot.date}</span>
                      <div className="mt-0.5 text-[11px] text-slate-400">
                        {slot.existingSubjects.length > 0
                          ? `Booked: ${slot.existingSubjects.join(", ")}`
                          : "Empty capacity available"}
                      </div>
                    </div>
                    <span
                      className={`rounded-md px-2 py-0.5 text-[10px] font-bold ${
                        slot.isFull
                          ? "bg-rose-500/20 text-rose-400"
                          : slot.currentCount === 1
                          ? "bg-amber-500/20 text-amber-400"
                          : "bg-emerald-500/20 text-emerald-400"
                      }`}
                    >
                      {slot.currentCount}/2 Filled
                    </span>
                  </div>
                ))}
              </div>
            </CyberCard>
          </div>
        </div>
      </div>
    </AppShell>
  );
}
