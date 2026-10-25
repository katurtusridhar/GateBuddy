"use client";

import React, { useMemo } from "react";
import { AppShell } from "@/components/layout/AppShell";
import { useApp } from "@/lib/store";
import { CyberCard } from "@/components/ui/CyberCard";
import { calculateAdherenceAndForecast } from "@/lib/analyticsEngine";
import {
  AreaChart,
  Area,
  XAxis,
  YAxis,
  Tooltip,
  ResponsiveContainer,
} from "recharts";
import clsx from "clsx";

export default function AnalyticsPage() {
  const { tasks, progress, customTasks, todayDateStr, settings, assessments } = useApp();

  const adherence = useMemo(() => {
    return calculateAdherenceAndForecast(
      todayDateStr,
      tasks,
      Array.from(progress.values()),
      customTasks,
      settings.targetExamDate,
      assessments
    );
  }, [todayDateStr, tasks, progress, customTasks, settings.targetExamDate, assessments]);

  return (
    <AppShell>
      <div className="mx-auto max-w-7xl space-y-6">
        <div>
          <span className="text-[10px] font-bold tracking-widest text-gate-sky uppercase">
            SRIDHAR&apos;S PREPARATION SCORECARD
          </span>
          <h1 className="mt-1 text-2xl font-black text-white">Analytics &amp; Forecasting</h1>
          <p className="text-xs text-slate-400">
            Authoritative preparation adherence, point trends, and completion forecast.
          </p>
        </div>

        {/* Top Health Card */}
        <CyberCard
          glow={adherence.health === "ON_TRACK" ? "emerald" : adherence.health === "AT_RISK" ? "amber" : "rose"}
        >
          <div className="flex flex-col justify-between gap-4 sm:flex-row sm:items-center">
            <div>
              <div className="flex items-center gap-2">
                <span className="text-xs font-bold text-slate-400 uppercase">Preparation Health Status:</span>
                <span
                  className={clsx(
                    "rounded-md border px-2 py-0.5 text-xs font-black uppercase",
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
              <p className="mt-2 text-sm text-slate-200">{adherence.healthReason}</p>
            </div>

            <div className="flex items-center gap-6 text-right">
              <div>
                <span className="text-[10px] font-semibold text-slate-400 uppercase">On-Time Rate</span>
                <p className="text-2xl font-black text-white">{adherence.onTimeAdherencePercent}%</p>
              </div>
              <div>
                <span className="text-[10px] font-semibold text-slate-400 uppercase">Confidence</span>
                <p className="text-2xl font-black text-gate-sky">{adherence.confidenceScore}%</p>
              </div>
            </div>
          </div>
        </CyberCard>

        {/* Adherence Rates Row */}
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <CyberCard glow="sky">
            <span className="text-[11px] font-semibold text-slate-400 uppercase">7-Day Avg Score</span>
            <p className="mt-2 text-3xl font-extrabold text-white">{adherence.averageDailyPoints} pts</p>
            <p className="mt-1 text-[11px] text-slate-400">Rolling daily score ({adherence.sevenDayAdherencePercent}% adherence)</p>
          </CyberCard>

          <CyberCard glow="purple">
            <span className="text-[11px] font-semibold text-slate-400 uppercase">28-Day Adherence</span>
            <p className="mt-2 text-3xl font-extrabold text-gate-purple">{adherence.twentyEightDayAdherencePercent}%</p>
            <p className="mt-1 text-[11px] text-slate-400">Rolling 4-week consistency</p>
          </CyberCard>

          <CyberCard glow="emerald">
            <span className="text-[11px] font-semibold text-slate-400 uppercase">Total Points Earned</span>
            <p className="mt-2 text-3xl font-extrabold text-gate-emerald">{adherence.totalPointsAllTime}</p>
            <p className="mt-1 text-[11px] text-slate-400">All-time study point score</p>
          </CyberCard>

          <CyberCard glow={adherence.currentBacklogCount > 0 ? "rose" : "emerald"}>
            <span className="text-[11px] font-semibold text-slate-400 uppercase">Active Backlog</span>
            <p className="mt-2 text-3xl font-extrabold text-white">{adherence.currentBacklogCount}</p>
            <p className="mt-1 text-[11px] text-slate-400">Overdue lectures requiring catch-up</p>
          </CyberCard>
        </div>

        {/* 14-Day Point Timeline Chart */}
        <CyberCard glow="none">
          <div className="flex items-center justify-between border-b border-slate-800 pb-3">
            <div>
              <h3 className="text-sm font-bold text-white">Daily Points History (Last 14 Days)</h3>
              <p className="text-xs text-slate-400">Earned points across lectures, boosters &amp; custom tasks</p>
            </div>
            <span className="text-xs font-bold text-gate-sky">Target: 30 pts/day</span>
          </div>

          <div className="mt-6 h-64 w-full">
            <ResponsiveContainer width="100%" height="100%">
              <AreaChart data={adherence.historicalPoints}>
                <defs>
                  <linearGradient id="pointGradient" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="#38BDF8" stopOpacity={0.4} />
                    <stop offset="95%" stopColor="#38BDF8" stopOpacity={0.0} />
                  </linearGradient>
                </defs>
                <XAxis dataKey="date" stroke="#64748B" fontSize={10} tickLine={false} />
                <YAxis stroke="#64748B" fontSize={10} tickLine={false} />
                <Tooltip
                  contentStyle={{ backgroundColor: "#0F172A", borderColor: "#334155", borderRadius: 8, fontSize: 12 }}
                />
                <Area type="monotone" dataKey="points" stroke="#38BDF8" strokeWidth={2.5} fill="url(#pointGradient)" />
              </AreaChart>
            </ResponsiveContainer>
          </div>
        </CyberCard>

        {/* Completion Trajectory Forecast */}
        <CyberCard glow="sky">
          <div className="flex items-start justify-between">
            <div>
              <span className="text-[10px] font-bold text-gate-sky uppercase">SYLLABUS FORECAST</span>
              <h3 className="mt-1 text-base font-bold text-white">Estimated Syllabus Completion</h3>
              <p className="mt-1 text-xs text-slate-400">
                Baseline Planned Completion Date: <strong>Sep 28, 2027</strong>
              </p>
            </div>
            <div className="text-right">
              <span className="text-lg font-black text-emerald-400">{adherence.projectedCompletionDate}</span>
              <p className="text-xs text-slate-400">
                {adherence.daysDifference <= 0 ? "Ahead of Schedule" : `+${adherence.daysDifference} Days Delay`}
              </p>
            </div>
          </div>
        </CyberCard>
      </div>
    </AppShell>
  );
}
