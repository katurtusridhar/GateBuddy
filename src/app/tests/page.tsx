"use client";

import React from "react";
import { AppShell } from "@/components/layout/AppShell";
import { useApp } from "@/lib/store";
import { CyberCard } from "@/components/ui/CyberCard";
import { ClipboardList, Smartphone, BookOpen } from "lucide-react";

export default function TestsPage() {
  const { assessments } = useApp();

  const totalTests = assessments.length;
  const avgPercentage =
    totalTests > 0
      ? Math.round(assessments.reduce((sum, a) => sum + a.percentage, 0) / totalTests)
      : 0;

  return (
    <AppShell>
      <div className="mx-auto max-w-7xl space-y-6">
        {/* Header */}
        <div className="flex flex-col justify-between gap-4 sm:flex-row sm:items-center">
          <div>
            <h1 className="text-2xl font-black text-white">Tests &amp; PYQ Mistake Notebook</h1>
            <p className="text-xs text-slate-400">Weekly active recall tests, mistake logs, and revision follow-up tasks</p>
          </div>

          {/* Read-Only App Mirror Indicator */}
          <div className="flex items-center gap-2 rounded-xl border border-sky-500/40 bg-sky-950/40 px-3.5 py-2 text-xs font-semibold text-sky-300 shadow-[0_0_15px_rgba(14,165,233,0.15)]">
            <Smartphone className="h-4 w-4 text-sky-400 shrink-0" />
            <span>Mirrored from Android App (Read-Only)</span>
          </div>
        </div>

        {/* Read-Only Informational Banner */}
        <div className="flex items-center gap-3 rounded-xl border border-slate-700/60 bg-[#151F32]/80 px-4 py-3 text-xs text-slate-300">
          <BookOpen className="h-4 w-4 text-sky-400 shrink-0" />
          <span>
            Test entries, marks, and Mistake Notebook logs are recorded exclusively in the <strong>GATEBuddy Android App</strong>. This web dashboard functions as a secure real-time read-only mirror.
          </span>
        </div>

        {/* Stats Row */}
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
          <CyberCard glow="sky">
            <span className="text-xs font-semibold text-slate-400 uppercase">Total Tests Taken</span>
            <p className="mt-2 text-3xl font-extrabold text-white">{totalTests}</p>
            <p className="mt-1 text-xs text-slate-400">Weekly tests &amp; PYQ mocks recorded in App</p>
          </CyberCard>

          <CyberCard glow="emerald">
            <span className="text-xs font-semibold text-slate-400 uppercase">Average Score</span>
            <p className="mt-2 text-3xl font-extrabold text-gate-emerald">{avgPercentage}%</p>
            <p className="mt-1 text-xs text-slate-400">Aggregate accuracy across all assessments</p>
          </CyberCard>

          <CyberCard glow="purple">
            <span className="text-xs font-semibold text-slate-400 uppercase">Mistake Notebook Entries</span>
            <p className="mt-2 text-3xl font-extrabold text-gate-purple">
              {assessments.filter((a) => a.mistakesAnalysis).length}
            </p>
            <p className="mt-1 text-xs text-slate-400">Logged concept doubts &amp; error patterns</p>
          </CyberCard>
        </div>

        {/* Tests List */}
        <div className="space-y-4">
          <h2 className="text-lg font-bold text-white">Assessment History ({totalTests})</h2>

          {assessments.length === 0 ? (
            <CyberCard glow="none" className="py-12 text-center text-xs text-slate-400">
              <ClipboardList className="mx-auto h-10 w-10 text-slate-600" />
              <p className="mt-3 text-sm font-semibold text-slate-300">No Assessments Logged Yet</p>
              <p className="mt-1 text-slate-500 max-w-sm mx-auto">
                Record your weekly test scores and mistake notes in your GATEBuddy Android App to mirror them here in real time.
              </p>
            </CyberCard>
          ) : (
            <div className="space-y-3">
              {assessments.map((a) => (
                <CyberCard key={a.id} glow={a.percentage >= 70 ? "emerald" : "amber"}>
                  <div className="flex flex-col justify-between gap-3 sm:flex-row sm:items-start">
                    <div>
                      <div className="flex items-center gap-2">
                        <span className="rounded bg-sky-500/20 px-2 py-0.5 text-[10px] font-bold text-gate-sky">
                          {a.subjectName}
                        </span>
                        <span className="text-sm font-bold text-white">{a.testName}</span>
                      </div>
                      <p className="mt-1 text-xs text-slate-400">
                        Date: {a.testDate} {a.topic ? `• Topic: ${a.topic}` : ""}
                      </p>
                    </div>

                    <div className="text-right">
                      <span className="text-2xl font-black text-white">{a.percentage}%</span>
                      <p className="text-[11px] text-slate-400">
                        {a.marksObtained} / {a.maxMarks} marks
                      </p>
                    </div>
                  </div>

                  {a.mistakesAnalysis && (
                    <div className="mt-3 rounded-xl border border-slate-800 bg-slate-900/60 p-3 text-xs text-slate-300">
                      <strong className="text-rose-400">Mistake Analysis:</strong> {a.mistakesAnalysis}
                    </div>
                  )}

                  {a.followUpTasks && (
                    <div className="mt-2 text-xs text-slate-400">
                      <strong className="text-gate-sky">Follow-up:</strong> {a.followUpTasks}
                    </div>
                  )}
                </CyberCard>
              ))}
            </div>
          )}
        </div>
      </div>
    </AppShell>
  );
}
