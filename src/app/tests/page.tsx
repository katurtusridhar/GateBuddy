"use client";

import React, { useState } from "react";
import { AppShell } from "@/components/layout/AppShell";
import { useApp } from "@/lib/store";
import { CyberCard } from "@/components/ui/CyberCard";
import { ClipboardList, Smartphone, BookOpen, CheckCircle2, TrendingUp, Layers, Sparkles } from "lucide-react";
import {
  AreaChart,
  Area,
  BarChart,
  Bar,
  XAxis,
  YAxis,
  Tooltip,
  ResponsiveContainer,
  CartesianGrid,
} from "recharts";
import clsx from "clsx";

export default function TestsAndDppPage() {
  const { assessments, dppList } = useApp();
  const [activeTab, setActiveTab] = useState<"TESTS" | "DPP">("TESTS");

  // Tests metrics
  const totalTests = assessments.length;
  const avgTestScore =
    totalTests > 0
      ? Math.round(assessments.reduce((sum, a) => sum + a.percentage, 0) / totalTests)
      : 0;
  const highestTestScore =
    totalTests > 0 ? Math.max(...assessments.map((a) => a.percentage)) : 0;
  const mistakeLogsCount = assessments.filter((a) => a.mistakesAnalysis && a.mistakesAnalysis.trim().length > 0).length;

  // Chronologically sorted test chart data
  const testChartData = [...assessments]
    .sort((a, b) => a.testDate.localeCompare(b.testDate))
    .map((a) => ({
      date: a.testDate.length > 5 ? a.testDate.substring(5) : a.testDate,
      score: a.percentage,
      name: `${a.subjectName} (${a.testName})`,
    }));

  // DPP metrics
  const totalDpp = dppList.length;
  const totalQuestionsSolved = dppList.reduce((sum, d) => sum + d.totalQuestions, 0);
  const totalQuestionsCorrect = dppList.reduce((sum, d) => sum + d.questionsCorrect, 0);
  const avgDppAccuracy =
    totalQuestionsSolved > 0
      ? Math.round((totalQuestionsCorrect / totalQuestionsSolved) * 100)
      : 0;

  // Chronologically sorted DPP chart data
  const dppChartData = [...dppList]
    .sort((a, b) => a.date.localeCompare(b.date))
    .map((d) => ({
      date: d.date.length > 5 ? d.date.substring(5) : d.date,
      accuracy: d.accuracyPercentage,
      correct: d.questionsCorrect,
      total: d.totalQuestions,
      subject: d.subjectCode,
    }));

  return (
    <AppShell>
      <div className="mx-auto max-w-7xl space-y-6">
        {/* Header */}
        <div className="flex flex-col justify-between gap-4 sm:flex-row sm:items-center">
          <div>
            <div className="flex items-center gap-2">
              <span className="text-[10px] font-bold tracking-widest text-gate-sky uppercase">
                EXAM READINESS COMMAND CENTER
              </span>
              <span className="rounded bg-sky-500/20 px-2 py-0.5 text-[10px] font-bold text-sky-400">
                ACTIVE
              </span>
            </div>
            <h1 className="mt-1 text-2xl font-black text-white">Tests &amp; DPP Analytics</h1>
            <p className="text-xs text-slate-400">
              Weekly mock tests, Mistake Notebook, and Daily Practice Problems performance tracking
            </p>
          </div>

          {/* Read-Only App Mirror Indicator */}
          <div className="flex items-center gap-2 rounded-xl border border-sky-500/40 bg-sky-950/40 px-3.5 py-2 text-xs font-semibold text-sky-300 shadow-[0_0_15px_rgba(14,165,233,0.15)] shrink-0">
            <Smartphone className="h-4 w-4 text-sky-400 shrink-0" />
            <span>Mirrored from Android App (Read-Only)</span>
          </div>
        </div>

        {/* Read-Only Banner */}
        <div className="flex items-center gap-3 rounded-xl border border-slate-700/60 bg-[#151F32]/80 px-4 py-3 text-xs text-slate-300">
          <BookOpen className="h-4 w-4 text-sky-400 shrink-0" />
          <span>
            Test logs, scores, and DPP problem sessions are authored and managed directly in your <strong>GATEBuddy Android App</strong>. This portal mirrors your verified practice telemetry in real time.
          </span>
        </div>

        {/* Segmented Control Tabs */}
        <div className="flex rounded-xl border border-slate-800 bg-[#0c1220] p-1.5 max-w-md">
          <button
            type="button"
            onClick={() => setActiveTab("TESTS")}
            className={clsx(
              "flex-1 flex items-center justify-center gap-2 rounded-lg py-2 text-xs font-bold transition-all",
              activeTab === "TESTS"
                ? "bg-sky-500/20 text-gate-sky border border-sky-500/30 shadow-[0_0_12px_rgba(14,165,233,0.25)]"
                : "text-slate-400 hover:text-slate-200"
            )}
          >
            <TrendingUp className="h-3.5 w-3.5" />
            <span>Weekly Tests &amp; PYQs ({totalTests})</span>
          </button>
          <button
            type="button"
            onClick={() => setActiveTab("DPP")}
            className={clsx(
              "flex-1 flex items-center justify-center gap-2 rounded-lg py-2 text-xs font-bold transition-all",
              activeTab === "DPP"
                ? "bg-emerald-500/20 text-gate-emerald border border-emerald-500/30 shadow-[0_0_12px_rgba(16,185,129,0.25)]"
                : "text-slate-400 hover:text-slate-200"
            )}
          >
            <Layers className="h-3.5 w-3.5" />
            <span>Daily Practice (DPP) ({totalDpp})</span>
          </button>
        </div>

        {/* TAB 1: TESTS & PYQ MOCKS */}
        {activeTab === "TESTS" && (
          <div className="space-y-6">
            {/* Stats Row */}
            <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
              <CyberCard glow="sky">
                <span className="text-[10px] sm:text-xs font-semibold text-slate-400 uppercase">Tests Logged</span>
                <p className="mt-1 sm:mt-2 text-2xl sm:text-3xl font-extrabold text-white">{totalTests}</p>
                <p className="mt-0.5 text-[10px] text-slate-400">Recorded via Android App</p>
              </CyberCard>

              <CyberCard glow="emerald">
                <span className="text-[10px] sm:text-xs font-semibold text-slate-400 uppercase">Avg Score</span>
                <p className="mt-1 sm:mt-2 text-2xl sm:text-3xl font-extrabold text-gate-emerald">{avgTestScore}%</p>
                <p className="mt-0.5 text-[10px] text-slate-400">Average accuracy</p>
              </CyberCard>

              <CyberCard glow="amber">
                <span className="text-[10px] sm:text-xs font-semibold text-slate-400 uppercase">Peak Score</span>
                <p className="mt-1 sm:mt-2 text-2xl sm:text-3xl font-extrabold text-amber-400">{highestTestScore}%</p>
                <p className="mt-0.5 text-[10px] text-slate-400">Highest mock score</p>
              </CyberCard>

              <CyberCard glow="purple">
                <span className="text-[10px] sm:text-xs font-semibold text-slate-400 uppercase">Mistake Logs</span>
                <p className="mt-1 sm:mt-2 text-2xl sm:text-3xl font-extrabold text-gate-purple">{mistakeLogsCount}</p>
                <p className="mt-0.5 text-[10px] text-slate-400">Error patterns analyzed</p>
              </CyberCard>
            </div>

            {/* Test Trajectory Graph */}
            <CyberCard glow="sky">
              <div className="mb-4 flex items-center justify-between">
                <div>
                  <h3 className="text-sm font-bold text-white">Mock Test Score Trajectory</h3>
                  <p className="text-[11px] text-slate-400">Score percentage progression over test dates</p>
                </div>
                <div className="flex items-center gap-1.5 text-xs text-gate-sky">
                  <Sparkles className="h-3.5 w-3.5" />
                  <span>Target: &gt;75%</span>
                </div>
              </div>

              {testChartData.length > 0 ? (
                <div className="h-56 w-full">
                  <ResponsiveContainer width="100%" height="100%">
                    <AreaChart data={testChartData} margin={{ top: 10, right: 10, left: -20, bottom: 0 }}>
                      <defs>
                        <linearGradient id="scoreGrad" x1="0" y1="0" x2="0" y2="1">
                          <stop offset="5%" stopColor="#38bdf8" stopOpacity={0.35} />
                          <stop offset="95%" stopColor="#38bdf8" stopOpacity={0.0} />
                        </linearGradient>
                      </defs>
                      <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" />
                      <XAxis dataKey="date" stroke="#64748b" fontSize={11} tickLine={false} />
                      <YAxis stroke="#64748b" fontSize={11} domain={[0, 100]} tickLine={false} />
                      <Tooltip
                        contentStyle={{
                          backgroundColor: "#0d1527",
                          borderColor: "#1e293b",
                          borderRadius: "8px",
                          fontSize: "12px",
                          color: "#fff",
                        }}
                        formatter={(val: any) => [`${val}%`, "Score"]}
                      />
                      <Area
                        type="monotone"
                        dataKey="score"
                        stroke="#38bdf8"
                        strokeWidth={2.5}
                        fillOpacity={1}
                        fill="url(#scoreGrad)"
                      />
                    </AreaChart>
                  </ResponsiveContainer>
                </div>
              ) : (
                <div className="flex h-44 flex-col items-center justify-center text-center text-xs text-slate-400">
                  <TrendingUp className="h-8 w-8 text-slate-600 mb-2" />
                  <span>Log tests in your GATEBuddy Android App to visualize your score progression here.</span>
                </div>
              )}
            </CyberCard>

            {/* Assessment History List */}
            <div className="space-y-3">
              <h3 className="text-base font-bold text-white">Assessment History ({totalTests})</h3>

              {assessments.length === 0 ? (
                <CyberCard glow="none" className="py-10 text-center text-xs text-slate-400">
                  <ClipboardList className="mx-auto h-9 w-9 text-slate-600" />
                  <p className="mt-3 text-sm font-semibold text-slate-300">No Assessments Logged Yet</p>
                  <p className="mt-1 text-slate-500 max-w-sm mx-auto">
                    Record your weekly test marks in your GATEBuddy Android App to mirror them here in real time.
                  </p>
                </CyberCard>
              ) : (
                <div className="space-y-3">
                  {assessments.map((a) => (
                    <CyberCard key={a.id} glow={a.percentage >= 70 ? "emerald" : "amber"}>
                      <div className="flex flex-col justify-between gap-3 sm:flex-row sm:items-start">
                        <div>
                          <div className="flex items-center gap-2 flex-wrap">
                            <span className="rounded bg-sky-500/20 px-2 py-0.5 text-[10px] font-bold text-gate-sky">
                              {a.subjectName}
                            </span>
                            <span className="text-sm font-bold text-white">{a.testName}</span>
                          </div>
                          <p className="mt-1 text-xs text-slate-400">
                            Date: {a.testDate} {a.topic ? `• Topic: ${a.topic}` : ""}
                          </p>
                        </div>

                        <div className="text-left sm:text-right">
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
        )}

        {/* TAB 2: DAILY PRACTICE PROBLEMS (DPP) */}
        {activeTab === "DPP" && (
          <div className="space-y-6">
            {/* DPP Stats Row */}
            <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
              <CyberCard glow="emerald">
                <span className="text-[10px] sm:text-xs font-semibold text-slate-400 uppercase">DPP Sessions</span>
                <p className="mt-1 sm:mt-2 text-2xl sm:text-3xl font-extrabold text-white">{totalDpp}</p>
                <p className="mt-0.5 text-[10px] text-slate-400">Logged practice sets</p>
              </CyberCard>

              <CyberCard glow="sky">
                <span className="text-[10px] sm:text-xs font-semibold text-slate-400 uppercase">Questions Solved</span>
                <p className="mt-1 sm:mt-2 text-2xl sm:text-3xl font-extrabold text-gate-sky">{totalQuestionsSolved}</p>
                <p className="mt-0.5 text-[10px] text-slate-400">Total problem attempts</p>
              </CyberCard>

              <CyberCard glow="emerald">
                <span className="text-[10px] sm:text-xs font-semibold text-slate-400 uppercase">Correct Questions</span>
                <p className="mt-1 sm:mt-2 text-2xl sm:text-3xl font-extrabold text-gate-emerald">{totalQuestionsCorrect}</p>
                <p className="mt-0.5 text-[10px] text-slate-400">Accurate solutions</p>
              </CyberCard>

              <CyberCard glow="purple">
                <span className="text-[10px] sm:text-xs font-semibold text-slate-400 uppercase">Avg Accuracy</span>
                <p className="mt-1 sm:mt-2 text-2xl sm:text-3xl font-extrabold text-gate-purple">{avgDppAccuracy}%</p>
                <p className="mt-0.5 text-[10px] text-slate-400">Aggregate solve rate</p>
              </CyberCard>
            </div>

            {/* DPP Accuracy Graph */}
            <CyberCard glow="emerald">
              <div className="mb-4 flex items-center justify-between">
                <div>
                  <h3 className="text-sm font-bold text-white">Daily Practice Accuracy &amp; Volume</h3>
                  <p className="text-[11px] text-slate-400">Daily accuracy percentage and problem counts</p>
                </div>
                <div className="flex items-center gap-1.5 text-xs text-gate-emerald">
                  <CheckCircle2 className="h-3.5 w-3.5" />
                  <span>Target: &gt;80%</span>
                </div>
              </div>

              {dppChartData.length > 0 ? (
                <div className="h-56 w-full">
                  <ResponsiveContainer width="100%" height="100%">
                    <BarChart data={dppChartData} margin={{ top: 10, right: 10, left: -20, bottom: 0 }}>
                      <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" />
                      <XAxis dataKey="date" stroke="#64748b" fontSize={11} tickLine={false} />
                      <YAxis stroke="#64748b" fontSize={11} domain={[0, 100]} tickLine={false} />
                      <Tooltip
                        contentStyle={{
                          backgroundColor: "#0d1527",
                          borderColor: "#1e293b",
                          borderRadius: "8px",
                          fontSize: "12px",
                          color: "#fff",
                        }}
                        formatter={(val: any, name: any) => [
                          name === "accuracy" ? `${val}%` : val,
                          name === "accuracy" ? "Accuracy" : "Correct / Total",
                        ]}
                      />
                      <Bar dataKey="accuracy" fill="#10b981" radius={[4, 4, 0, 0]} />
                    </BarChart>
                  </ResponsiveContainer>
                </div>
              ) : (
                <div className="flex h-44 flex-col items-center justify-center text-center text-xs text-slate-400">
                  <Layers className="h-8 w-8 text-slate-600 mb-2" />
                  <span>Log DPP sessions in your GATEBuddy Android App to visualize your problem-solving metrics here.</span>
                </div>
              )}
            </CyberCard>

            {/* DPP Records List */}
            <div className="space-y-3">
              <h3 className="text-base font-bold text-white">DPP Practice History ({totalDpp})</h3>

              {dppList.length === 0 ? (
                <CyberCard glow="none" className="py-10 text-center text-xs text-slate-400">
                  <Layers className="mx-auto h-9 w-9 text-slate-600" />
                  <p className="mt-3 text-sm font-semibold text-slate-300">No DPP Sessions Logged Yet</p>
                  <p className="mt-1 text-slate-500 max-w-sm mx-auto">
                    Record your daily problem counts and accuracy in your GATEBuddy Android App under Tests &amp; DPP tab to mirror them here.
                  </p>
                </CyberCard>
              ) : (
                <div className="space-y-3">
                  {dppList.map((d) => (
                    <CyberCard key={d.id} glow={d.accuracyPercentage >= 80 ? "emerald" : "amber"}>
                      <div className="flex flex-col justify-between gap-3 sm:flex-row sm:items-start">
                        <div>
                          <div className="flex items-center gap-2 flex-wrap">
                            <span className="rounded bg-emerald-500/20 px-2 py-0.5 text-[10px] font-bold text-gate-emerald">
                              {d.subjectCode}
                            </span>
                            <span className="text-sm font-bold text-white">
                              {d.questionsCorrect} of {d.totalQuestions} Solved Correctly
                            </span>
                          </div>
                          <p className="mt-1 text-xs text-slate-400">Date: {d.date}</p>
                        </div>

                        <div className="text-left sm:text-right">
                          <span className="text-2xl font-black text-white">{d.accuracyPercentage}%</span>
                          <p className="text-[11px] text-slate-400">Accuracy Rate</p>
                        </div>
                      </div>

                      {d.notes && d.notes.trim().length > 0 && (
                        <div className="mt-3 rounded-xl border border-slate-800 bg-slate-900/60 p-3 text-xs text-slate-300">
                          <strong className="text-gate-sky">Notes:</strong> {d.notes}
                        </div>
                      )}
                    </CyberCard>
                  ))}
                </div>
              )}
            </div>
          </div>
        )}
      </div>
    </AppShell>
  );
}
