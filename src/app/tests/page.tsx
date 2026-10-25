"use client";

import React, { useState, useMemo } from "react";
import { AppShell } from "@/components/layout/AppShell";
import { useApp } from "@/lib/store";
import { CyberCard } from "@/components/ui/CyberCard";
import {
  ClipboardList,
  Smartphone,
  BookOpen,
  CheckCircle2,
  TrendingUp,
  Layers,
  Sparkles,
  Trophy,
  Target,
  BarChart3,
  Calendar,
  AlertCircle,
  HelpCircle,
} from "lucide-react";
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
  ReferenceLine,
  Legend,
} from "recharts";
import clsx from "clsx";

// Custom glassmorphic tooltip for Test charts
function CustomTestTooltip({ active, payload, label }: any) {
  if (active && payload && payload.length) {
    const data = payload[0].payload;
    const score = payload[0].value;
    const isMastery = score >= 75;
    const isPassing = score >= 60;

    return (
      <div className="rounded-xl border border-sky-500/40 bg-[#0a1122]/95 p-3.5 shadow-[0_0_25px_rgba(14,165,233,0.3)] backdrop-blur-md text-xs">
        <div className="flex items-center justify-between gap-3 border-b border-slate-800 pb-2 mb-2">
          <span className="font-bold text-white">{data.name || label}</span>
          <span
            className={clsx(
              "rounded px-1.5 py-0.5 text-[10px] font-black uppercase",
              isMastery
                ? "bg-emerald-500/20 text-emerald-400 border border-emerald-500/30"
                : isPassing
                ? "bg-amber-500/20 text-amber-400 border border-amber-500/30"
                : "bg-rose-500/20 text-rose-400 border border-rose-500/30"
            )}
          >
            {isMastery ? "Target Met" : isPassing ? "Moderate" : "Needs Review"}
          </span>
        </div>
        <div className="space-y-1">
          <div className="flex justify-between gap-4">
            <span className="text-slate-400">Score:</span>
            <span className="font-extrabold text-cyan-300">{score}%</span>
          </div>
          {data.marks !== undefined && (
            <div className="flex justify-between gap-4">
              <span className="text-slate-400">Marks:</span>
              <span className="font-semibold text-slate-200">
                {data.marks} / {data.maxMarks}
              </span>
            </div>
          )}
          {data.subject && (
            <div className="flex justify-between gap-4">
              <span className="text-slate-400">Subject:</span>
              <span className="font-semibold text-gate-sky">{data.subject}</span>
            </div>
          )}
        </div>
      </div>
    );
  }
  return null;
}

// Custom glassmorphic tooltip for DPP charts
function CustomDppTooltip({ active, payload, label }: any) {
  if (active && payload && payload.length) {
    const data = payload[0].payload;
    const accuracy = data.accuracy;
    const isHigh = accuracy >= 80;

    return (
      <div className="rounded-xl border border-emerald-500/40 bg-[#0a151b]/95 p-3.5 shadow-[0_0_25px_rgba(16,185,129,0.3)] backdrop-blur-md text-xs">
        <div className="flex items-center justify-between gap-3 border-b border-slate-800 pb-2 mb-2">
          <span className="font-bold text-white">Date: {label}</span>
          <span
            className={clsx(
              "rounded px-1.5 py-0.5 text-[10px] font-black uppercase",
              isHigh
                ? "bg-emerald-500/20 text-emerald-400 border border-emerald-500/30"
                : "bg-amber-500/20 text-amber-400 border border-amber-500/30"
            )}
          >
            {isHigh ? "Mastery (>=80%)" : "Practice More"}
          </span>
        </div>
        <div className="space-y-1">
          <div className="flex justify-between gap-4">
            <span className="text-slate-400">Accuracy:</span>
            <span className="font-extrabold text-emerald-400">{accuracy}%</span>
          </div>
          <div className="flex justify-between gap-4">
            <span className="text-slate-400">Solved Correctly:</span>
            <span className="font-semibold text-emerald-300">
              {data.correct} / {data.total}
            </span>
          </div>
          {data.subject && (
            <div className="flex justify-between gap-4">
              <span className="text-slate-400">Subject:</span>
              <span className="font-semibold text-gate-sky">{data.subject}</span>
            </div>
          )}
        </div>
      </div>
    );
  }
  return null;
}

export default function TestsAndDppPage() {
  const { assessments, dppList } = useApp();
  const [activeTab, setActiveTab] = useState<"TESTS" | "DPP">("TESTS");
  const [testChartMode, setTestChartMode] = useState<"PROGRESSION" | "SUBJECT" | "VOLUME">("PROGRESSION");
  const [dppChartMode, setDppChartMode] = useState<"ACCURACY" | "STACKED" | "SUBJECT">("ACCURACY");

  // Tests metrics
  const totalTests = assessments.length;
  const avgTestScore =
    totalTests > 0
      ? Math.round(assessments.reduce((sum, a) => sum + a.percentage, 0) / totalTests)
      : 0;
  const highestTestScore =
    totalTests > 0 ? Math.max(...assessments.map((a) => a.percentage)) : 0;
  const mistakeLogsCount = assessments.filter(
    (a) => a.mistakesAnalysis && a.mistakesAnalysis.trim().length > 0
  ).length;

  // Chronologically sorted test chart data
  const testChartData = useMemo(() => {
    return [...assessments]
      .sort((a, b) => a.testDate.localeCompare(b.testDate))
      .map((a) => ({
        date: a.testDate.length > 5 ? a.testDate.substring(5) : a.testDate,
        fullDate: a.testDate,
        score: a.percentage,
        marks: a.marksObtained,
        maxMarks: a.maxMarks,
        subject: a.subjectName,
        name: `${a.subjectName} (${a.testName})`,
      }));
  }, [assessments]);

  // Subject-wise aggregated test performance
  const subjectTestData = useMemo(() => {
    const map = new Map<string, { total: number; sum: number; count: number }>();
    assessments.forEach((a) => {
      const sub = a.subjectName || "OTHER";
      const entry = map.get(sub) || { total: 0, sum: 0, count: 0 };
      entry.sum += a.percentage;
      entry.count += 1;
      map.set(sub, entry);
    });
    return Array.from(map.entries()).map(([sub, data]) => ({
      subject: sub,
      avgScore: Math.round(data.sum / data.count),
      testsCount: data.count,
    }));
  }, [assessments]);

  // DPP metrics
  const totalDpp = dppList.length;
  const totalQuestionsSolved = dppList.reduce((sum, d) => sum + d.totalQuestions, 0);
  const totalQuestionsCorrect = dppList.reduce((sum, d) => sum + d.questionsCorrect, 0);
  const avgDppAccuracy =
    totalQuestionsSolved > 0
      ? Math.round((totalQuestionsCorrect / totalQuestionsSolved) * 100)
      : 0;

  // Chronologically sorted DPP chart data
  const dppChartData = useMemo(() => {
    return [...dppList]
      .sort((a, b) => a.date.localeCompare(b.date))
      .map((d) => ({
        date: d.date.length > 5 ? d.date.substring(5) : d.date,
        fullDate: d.date,
        accuracy: d.accuracyPercentage,
        correct: d.questionsCorrect,
        incorrect: Math.max(0, d.totalQuestions - d.questionsCorrect),
        total: d.totalQuestions,
        subject: d.subjectCode,
      }));
  }, [dppList]);

  // Subject-wise DPP performance
  const subjectDppData = useMemo(() => {
    const map = new Map<string, { correct: number; total: number; count: number }>();
    dppList.forEach((d) => {
      const sub = d.subjectCode || "OTHER";
      const entry = map.get(sub) || { correct: 0, total: 0, count: 0 };
      entry.correct += d.questionsCorrect;
      entry.total += d.totalQuestions;
      entry.count += 1;
      map.set(sub, entry);
    });
    return Array.from(map.entries()).map(([sub, data]) => ({
      subject: sub,
      accuracy: data.total > 0 ? Math.round((data.correct / data.total) * 100) : 0,
      totalQuestions: data.total,
      sessions: data.count,
    }));
  }, [dppList]);

  return (
    <AppShell>
      <div className="mx-auto max-w-7xl space-y-6">
        {/* Header */}
        <div className="flex flex-col justify-between gap-4 sm:flex-row sm:items-center">
          <div>
            <div className="flex items-center gap-2">
              <span className="text-[10px] font-bold tracking-widest text-gate-sky uppercase">
                EXAM READINESS TELEMETRY
              </span>
              <span className="rounded bg-sky-500/20 px-2 py-0.5 text-[10px] font-bold text-sky-400">
                LIVE
              </span>
            </div>
            <h1 className="mt-1 text-2xl font-black text-white">Tests &amp; DPP Analytics</h1>
            <p className="text-xs text-slate-400">
              Interactive high-precision trajectory curves, benchmark tracking, and mistake diagnostics
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
            Test entries, marks, and DPP problem sets are logged exclusively in your <strong>GATEBuddy Android App</strong>. This dashboard provides deep graphical analytics and diagnostic insights in real time.
          </span>
        </div>

        {/* Primary Tab Switcher */}
        <div className="flex rounded-xl border border-slate-800 bg-[#0c1220] p-1.5 max-w-md shadow-inner">
          <button
            type="button"
            onClick={() => setActiveTab("TESTS")}
            className={clsx(
              "flex-1 flex items-center justify-center gap-2 rounded-lg py-2 text-xs font-bold transition-all",
              activeTab === "TESTS"
                ? "bg-sky-500/20 text-gate-sky border border-sky-500/30 shadow-[0_0_15px_rgba(14,165,233,0.3)]"
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
                ? "bg-emerald-500/20 text-gate-emerald border border-emerald-500/30 shadow-[0_0_15px_rgba(16,185,129,0.3)]"
                : "text-slate-400 hover:text-slate-200"
            )}
          >
            <Layers className="h-3.5 w-3.5" />
            <span>Daily Practice (DPP) ({totalDpp})</span>
          </button>
        </div>

        {/* ========================================================================= */}
        {/* TAB 1: TESTS & PYQ MOCKS */}
        {/* ========================================================================= */}
        {activeTab === "TESTS" && (
          <div className="space-y-6">
            {/* Hero Stat Cards */}
            <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
              <CyberCard glow="sky">
                <div className="flex items-center justify-between">
                  <span className="text-[10px] sm:text-xs font-semibold text-slate-400 uppercase">
                    Tests Logged
                  </span>
                  <Trophy className="h-4 w-4 text-sky-400" />
                </div>
                <p className="mt-1 sm:mt-2 text-2xl sm:text-3xl font-extrabold text-white">{totalTests}</p>
                <div className="mt-1 flex items-center gap-1.5 text-[10px] text-sky-300">
                  <Sparkles className="h-3 w-3" />
                  <span>Sunday Mocks &amp; PYQs</span>
                </div>
              </CyberCard>

              <CyberCard glow={avgTestScore >= 75 ? "emerald" : avgTestScore >= 60 ? "amber" : "rose"}>
                <div className="flex items-center justify-between">
                  <span className="text-[10px] sm:text-xs font-semibold text-slate-400 uppercase">
                    Average Score
                  </span>
                  <Target className="h-4 w-4 text-gate-emerald" />
                </div>
                <p className="mt-1 sm:mt-2 text-2xl sm:text-3xl font-extrabold text-gate-emerald">
                  {avgTestScore}%
                </p>
                <div className="mt-1 text-[10px] text-slate-400">
                  {avgTestScore >= 75 ? "GATE Rank Target Met" : "Target: >75% average"}
                </div>
              </CyberCard>

              <CyberCard glow="amber">
                <div className="flex items-center justify-between">
                  <span className="text-[10px] sm:text-xs font-semibold text-slate-400 uppercase">
                    Peak Mock Score
                  </span>
                  <Sparkles className="h-4 w-4 text-amber-400" />
                </div>
                <p className="mt-1 sm:mt-2 text-2xl sm:text-3xl font-extrabold text-amber-400">
                  {highestTestScore}%
                </p>
                <div className="mt-1 text-[10px] text-slate-400">All-time highest mark</div>
              </CyberCard>

              <CyberCard glow="purple">
                <div className="flex items-center justify-between">
                  <span className="text-[10px] sm:text-xs font-semibold text-slate-400 uppercase">
                    Mistake Notebook
                  </span>
                  <ClipboardList className="h-4 w-4 text-gate-purple" />
                </div>
                <p className="mt-1 sm:mt-2 text-2xl sm:text-3xl font-extrabold text-gate-purple">
                  {mistakeLogsCount}
                </p>
                <div className="mt-1 text-[10px] text-slate-400">Analyzed concept gaps</div>
              </CyberCard>
            </div>

            {/* Interactive Graph Card */}
            <CyberCard glow="sky" className="p-4 sm:p-6">
              <div className="mb-4 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between border-b border-slate-800/80 pb-3">
                <div>
                  <h3 className="text-sm font-bold text-white flex items-center gap-2">
                    <span>Performance Trajectory Analytics</span>
                    <span className="rounded bg-sky-500/20 px-2 py-0.5 text-[10px] font-bold text-sky-400">
                      BÉZIER SMOOTHED
                    </span>
                  </h3>
                  <p className="text-[11px] text-slate-400 mt-0.5">
                    Benchmark comparison against top GATE percentiles (75%+ threshold)
                  </p>
                </div>

                {/* Sub-tab view toggles */}
                <div className="flex rounded-lg border border-slate-800 bg-[#091122] p-1 self-start sm:self-auto">
                  <button
                    type="button"
                    onClick={() => setTestChartMode("PROGRESSION")}
                    className={clsx(
                      "flex items-center gap-1.5 rounded px-2.5 py-1 text-[11px] font-semibold transition-all",
                      testChartMode === "PROGRESSION"
                        ? "bg-sky-500 text-slate-950 font-bold shadow-md shadow-sky-500/20"
                        : "text-slate-400 hover:text-white"
                    )}
                  >
                    <TrendingUp className="h-3 w-3" />
                    <span>Progression</span>
                  </button>
                  <button
                    type="button"
                    onClick={() => setTestChartMode("SUBJECT")}
                    className={clsx(
                      "flex items-center gap-1.5 rounded px-2.5 py-1 text-[11px] font-semibold transition-all",
                      testChartMode === "SUBJECT"
                        ? "bg-sky-500 text-slate-950 font-bold shadow-md shadow-sky-500/20"
                        : "text-slate-400 hover:text-white"
                    )}
                  >
                    <BarChart3 className="h-3 w-3" />
                    <span>By Subject</span>
                  </button>
                  <button
                    type="button"
                    onClick={() => setTestChartMode("VOLUME")}
                    className={clsx(
                      "flex items-center gap-1.5 rounded px-2.5 py-1 text-[11px] font-semibold transition-all",
                      testChartMode === "VOLUME"
                        ? "bg-sky-500 text-slate-950 font-bold shadow-md shadow-sky-500/20"
                        : "text-slate-400 hover:text-white"
                    )}
                  >
                    <Layers className="h-3 w-3" />
                    <span>Marks vs Max</span>
                  </button>
                </div>
              </div>

              {/* Chart Rendering */}
              {testChartData.length > 0 ? (
                <div className="h-64 sm:h-72 w-full pt-2">
                  <ResponsiveContainer width="100%" height="100%">
                    {testChartMode === "PROGRESSION" ? (
                      <AreaChart data={testChartData} margin={{ top: 10, right: 10, left: -20, bottom: 0 }}>
                        <defs>
                          <linearGradient id="scoreAreaGrad" x1="0" y1="0" x2="0" y2="1">
                            <stop offset="5%" stopColor="#06b6d4" stopOpacity={0.4} />
                            <stop offset="50%" stopColor="#0284c7" stopOpacity={0.15} />
                            <stop offset="95%" stopColor="#0284c7" stopOpacity={0.0} />
                          </linearGradient>
                        </defs>
                        <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" />
                        <XAxis dataKey="date" stroke="#64748b" fontSize={11} tickLine={false} />
                        <YAxis stroke="#64748b" fontSize={11} domain={[0, 100]} tickLine={false} />
                        <Tooltip content={<CustomTestTooltip />} />
                        <ReferenceLine
                          y={75}
                          stroke="#10b981"
                          strokeDasharray="4 4"
                          strokeWidth={1.5}
                          label={{
                            value: "75% Top GATE Target",
                            fill: "#10b981",
                            fontSize: 10,
                            position: "insideTopRight",
                          }}
                        />
                        <Area
                          type="monotone"
                          dataKey="score"
                          stroke="#38bdf8"
                          strokeWidth={2.5}
                          fillOpacity={1}
                          fill="url(#scoreAreaGrad)"
                          dot={{ r: 4, fill: "#0a0f1d", stroke: "#38bdf8", strokeWidth: 2 }}
                          activeDot={{ r: 6, fill: "#38bdf8", stroke: "#fff", strokeWidth: 2 }}
                        />
                      </AreaChart>
                    ) : testChartMode === "SUBJECT" ? (
                      <BarChart data={subjectTestData} margin={{ top: 10, right: 10, left: -20, bottom: 0 }}>
                        <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" />
                        <XAxis dataKey="subject" stroke="#64748b" fontSize={11} tickLine={false} />
                        <YAxis stroke="#64748b" fontSize={11} domain={[0, 100]} tickLine={false} />
                        <Tooltip
                          contentStyle={{
                            backgroundColor: "#0d1527",
                            borderColor: "#1e293b",
                            borderRadius: "8px",
                            fontSize: "12px",
                            color: "#fff",
                          }}
                          formatter={(val: any) => [`${val}%`, "Avg Score"]}
                        />
                        <ReferenceLine y={75} stroke="#10b981" strokeDasharray="4 4" strokeWidth={1.5} />
                        <Bar dataKey="avgScore" fill="#38bdf8" radius={[6, 6, 0, 0]} />
                      </BarChart>
                    ) : (
                      <BarChart data={testChartData} margin={{ top: 10, right: 10, left: -20, bottom: 0 }}>
                        <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" />
                        <XAxis dataKey="date" stroke="#64748b" fontSize={11} tickLine={false} />
                        <YAxis stroke="#64748b" fontSize={11} tickLine={false} />
                        <Tooltip content={<CustomTestTooltip />} />
                        <Legend wrapperStyle={{ fontSize: "11px", paddingTop: "8px" }} />
                        <Bar dataKey="marks" name="Marks Obtained" fill="#38bdf8" radius={[4, 4, 0, 0]} />
                        <Bar dataKey="maxMarks" name="Max Marks" fill="#1e293b" radius={[4, 4, 0, 0]} />
                      </BarChart>
                    )}
                  </ResponsiveContainer>
                </div>
              ) : (
                <div className="flex h-52 flex-col items-center justify-center text-center text-xs text-slate-400">
                  <TrendingUp className="h-9 w-9 text-slate-600 mb-2 animate-pulse" />
                  <p className="font-semibold text-slate-300">No Assessment Data Yet</p>
                  <span className="mt-1 text-slate-500 max-w-sm">
                    Complete weekly mock tests and log your scores in the GATEBuddy Android App to visualize your score progression here.
                  </span>
                </div>
              )}
            </CyberCard>

            {/* Assessment History Cards */}
            <div className="space-y-3">
              <div className="flex items-center justify-between">
                <h3 className="text-base font-bold text-white flex items-center gap-2">
                  <span>Logged Assessment History</span>
                  <span className="rounded-full bg-slate-800 px-2 py-0.5 text-xs text-slate-300 font-semibold">
                    {totalTests}
                  </span>
                </h3>
              </div>

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
                    <CyberCard key={a.id} glow={a.percentage >= 75 ? "emerald" : a.percentage >= 60 ? "amber" : "rose"}>
                      <div className="flex flex-col justify-between gap-3 sm:flex-row sm:items-start">
                        <div>
                          <div className="flex items-center gap-2 flex-wrap">
                            <span className="rounded bg-sky-500/20 px-2 py-0.5 text-[10px] font-bold text-gate-sky border border-sky-500/30">
                              {a.subjectName}
                            </span>
                            <span className="text-sm font-bold text-white">{a.testName}</span>
                            <span
                              className={clsx(
                                "rounded px-1.5 py-0.2 text-[10px] font-bold",
                                a.percentage >= 75
                                  ? "text-emerald-400 bg-emerald-500/10"
                                  : a.percentage >= 60
                                  ? "text-amber-400 bg-amber-500/10"
                                  : "text-rose-400 bg-rose-500/10"
                              )}
                            >
                              {a.percentage >= 75 ? "GATE Benchmark Met" : a.percentage >= 60 ? "Average" : "Low Score"}
                            </span>
                          </div>
                          <p className="mt-1.5 text-xs text-slate-400 flex items-center gap-2">
                            <Calendar className="h-3.5 w-3.5 text-slate-500" />
                            <span>{a.testDate}</span>
                            {a.topic && <span>• Topic: {a.topic}</span>}
                          </p>
                        </div>

                        <div className="text-left sm:text-right">
                          <span
                            className={clsx(
                              "text-2xl font-black",
                              a.percentage >= 75 ? "text-gate-emerald" : a.percentage >= 60 ? "text-amber-400" : "text-rose-400"
                            )}
                          >
                            {a.percentage}%
                          </span>
                          <p className="text-[11px] text-slate-400 font-semibold">
                            {a.marksObtained} / {a.maxMarks} marks
                          </p>
                        </div>
                      </div>

                      {a.mistakesAnalysis && (
                        <div className="mt-3 rounded-xl border border-rose-900/40 bg-rose-950/20 p-3 text-xs text-slate-300">
                          <strong className="text-rose-400 flex items-center gap-1.5 mb-1">
                            <AlertCircle className="h-3.5 w-3.5" />
                            <span>Mistake Analysis &amp; Concept Gap:</span>
                          </strong>
                          <span>{a.mistakesAnalysis}</span>
                        </div>
                      )}

                      {a.followUpTasks && (
                        <div className="mt-2 text-xs text-slate-400 flex items-center gap-1.5">
                          <strong className="text-gate-sky">Follow-up:</strong>
                          <span>{a.followUpTasks}</span>
                        </div>
                      )}
                    </CyberCard>
                  ))}
                </div>
              )}
            </div>
          </div>
        )}

        {/* ========================================================================= */}
        {/* TAB 2: DAILY PRACTICE PROBLEMS (DPP) */}
        {/* ========================================================================= */}
        {activeTab === "DPP" && (
          <div className="space-y-6">
            {/* DPP Hero Stat Cards */}
            <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
              <CyberCard glow="emerald">
                <div className="flex items-center justify-between">
                  <span className="text-[10px] sm:text-xs font-semibold text-slate-400 uppercase">
                    DPP Sessions
                  </span>
                  <Layers className="h-4 w-4 text-gate-emerald" />
                </div>
                <p className="mt-1 sm:mt-2 text-2xl sm:text-3xl font-extrabold text-white">{totalDpp}</p>
                <div className="mt-1 text-[10px] text-gate-emerald font-semibold">Logged problem sets</div>
              </CyberCard>

              <CyberCard glow="sky">
                <div className="flex items-center justify-between">
                  <span className="text-[10px] sm:text-xs font-semibold text-slate-400 uppercase">
                    Total Solved
                  </span>
                  <HelpCircle className="h-4 w-4 text-gate-sky" />
                </div>
                <p className="mt-1 sm:mt-2 text-2xl sm:text-3xl font-extrabold text-gate-sky">
                  {totalQuestionsSolved}
                </p>
                <div className="mt-1 text-[10px] text-slate-400">Total problem attempts</div>
              </CyberCard>

              <CyberCard glow="emerald">
                <div className="flex items-center justify-between">
                  <span className="text-[10px] sm:text-xs font-semibold text-slate-400 uppercase">
                    Correct Solutions
                  </span>
                  <CheckCircle2 className="h-4 w-4 text-gate-emerald" />
                </div>
                <p className="mt-1 sm:mt-2 text-2xl sm:text-3xl font-extrabold text-gate-emerald">
                  {totalQuestionsCorrect}
                </p>
                <div className="mt-1 text-[10px] text-slate-400">High-precision solutions</div>
              </CyberCard>

              <CyberCard glow={avgDppAccuracy >= 80 ? "emerald" : "amber"}>
                <div className="flex items-center justify-between">
                  <span className="text-[10px] sm:text-xs font-semibold text-slate-400 uppercase">
                    Aggregate Accuracy
                  </span>
                  <Target className="h-4 w-4 text-gate-purple" />
                </div>
                <p className="mt-1 sm:mt-2 text-2xl sm:text-3xl font-extrabold text-gate-purple">
                  {avgDppAccuracy}%
                </p>
                <div className="mt-1 text-[10px] text-slate-400">
                  {avgDppAccuracy >= 80 ? "Accuracy Benchmark Met" : "Target: >80% accuracy"}
                </div>
              </CyberCard>
            </div>

            {/* Interactive DPP Graph Card */}
            <CyberCard glow="emerald" className="p-4 sm:p-6">
              <div className="mb-4 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between border-b border-slate-800/80 pb-3">
                <div>
                  <h3 className="text-sm font-bold text-white flex items-center gap-2">
                    <span>Problem Solving Precision &amp; Volume</span>
                    <span className="rounded bg-emerald-500/20 px-2 py-0.5 text-[10px] font-bold text-gate-emerald">
                      ACCURACY TELEMETRY
                    </span>
                  </h3>
                  <p className="text-[11px] text-slate-400 mt-0.5">
                    Daily problem volume, solve ratios, and accuracy rate across practice sessions
                  </p>
                </div>

                {/* Sub-tab view toggles */}
                <div className="flex rounded-lg border border-slate-800 bg-[#091515] p-1 self-start sm:self-auto">
                  <button
                    type="button"
                    onClick={() => setDppChartMode("ACCURACY")}
                    className={clsx(
                      "flex items-center gap-1.5 rounded px-2.5 py-1 text-[11px] font-semibold transition-all",
                      dppChartMode === "ACCURACY"
                        ? "bg-emerald-500 text-slate-950 font-bold shadow-md shadow-emerald-500/20"
                        : "text-slate-400 hover:text-white"
                    )}
                  >
                    <TrendingUp className="h-3 w-3" />
                    <span>Accuracy Curve</span>
                  </button>
                  <button
                    type="button"
                    onClick={() => setDppChartMode("STACKED")}
                    className={clsx(
                      "flex items-center gap-1.5 rounded px-2.5 py-1 text-[11px] font-semibold transition-all",
                      dppChartMode === "STACKED"
                        ? "bg-emerald-500 text-slate-950 font-bold shadow-md shadow-emerald-500/20"
                        : "text-slate-400 hover:text-white"
                    )}
                  >
                    <BarChart3 className="h-3 w-3" />
                    <span>Solved vs Missed</span>
                  </button>
                  <button
                    type="button"
                    onClick={() => setDppChartMode("SUBJECT")}
                    className={clsx(
                      "flex items-center gap-1.5 rounded px-2.5 py-1 text-[11px] font-semibold transition-all",
                      dppChartMode === "SUBJECT"
                        ? "bg-emerald-500 text-slate-950 font-bold shadow-md shadow-emerald-500/20"
                        : "text-slate-400 hover:text-white"
                    )}
                  >
                    <Layers className="h-3 w-3" />
                    <span>By Subject</span>
                  </button>
                </div>
              </div>

              {/* Chart Rendering */}
              {dppChartData.length > 0 ? (
                <div className="h-64 sm:h-72 w-full pt-2">
                  <ResponsiveContainer width="100%" height="100%">
                    {dppChartMode === "ACCURACY" ? (
                      <AreaChart data={dppChartData} margin={{ top: 10, right: 10, left: -20, bottom: 0 }}>
                        <defs>
                          <linearGradient id="dppAreaGrad" x1="0" y1="0" x2="0" y2="1">
                            <stop offset="5%" stopColor="#10b981" stopOpacity={0.4} />
                            <stop offset="50%" stopColor="#059669" stopOpacity={0.15} />
                            <stop offset="95%" stopColor="#059669" stopOpacity={0.0} />
                          </linearGradient>
                        </defs>
                        <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" />
                        <XAxis dataKey="date" stroke="#64748b" fontSize={11} tickLine={false} />
                        <YAxis stroke="#64748b" fontSize={11} domain={[0, 100]} tickLine={false} />
                        <Tooltip content={<CustomDppTooltip />} />
                        <ReferenceLine
                          y={80}
                          stroke="#06b6d4"
                          strokeDasharray="4 4"
                          strokeWidth={1.5}
                          label={{
                            value: "80% Mastery Benchmark",
                            fill: "#06b6d4",
                            fontSize: 10,
                            position: "insideTopRight",
                          }}
                        />
                        <Area
                          type="monotone"
                          dataKey="accuracy"
                          stroke="#10b981"
                          strokeWidth={2.5}
                          fillOpacity={1}
                          fill="url(#dppAreaGrad)"
                          dot={{ r: 4, fill: "#0a151b", stroke: "#10b981", strokeWidth: 2 }}
                          activeDot={{ r: 6, fill: "#10b981", stroke: "#fff", strokeWidth: 2 }}
                        />
                      </AreaChart>
                    ) : dppChartMode === "STACKED" ? (
                      <BarChart data={dppChartData} margin={{ top: 10, right: 10, left: -20, bottom: 0 }}>
                        <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" />
                        <XAxis dataKey="date" stroke="#64748b" fontSize={11} tickLine={false} />
                        <YAxis stroke="#64748b" fontSize={11} tickLine={false} />
                        <Tooltip content={<CustomDppTooltip />} />
                        <Legend wrapperStyle={{ fontSize: "11px", paddingTop: "8px" }} />
                        <Bar dataKey="correct" name="Correct Solutions" stackId="a" fill="#10b981" radius={[0, 0, 0, 0]} />
                        <Bar dataKey="incorrect" name="Missed / Inaccurate" stackId="a" fill="#f43f5e" radius={[4, 4, 0, 0]} />
                      </BarChart>
                    ) : (
                      <BarChart data={subjectDppData} margin={{ top: 10, right: 10, left: -20, bottom: 0 }}>
                        <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" />
                        <XAxis dataKey="subject" stroke="#64748b" fontSize={11} tickLine={false} />
                        <YAxis stroke="#64748b" fontSize={11} domain={[0, 100]} tickLine={false} />
                        <Tooltip
                          contentStyle={{
                            backgroundColor: "#0d1527",
                            borderColor: "#1e293b",
                            borderRadius: "8px",
                            fontSize: "12px",
                            color: "#fff",
                          }}
                          formatter={(val: any) => [`${val}%`, "Accuracy"]}
                        />
                        <ReferenceLine y={80} stroke="#06b6d4" strokeDasharray="4 4" strokeWidth={1.5} />
                        <Bar dataKey="accuracy" fill="#10b981" radius={[6, 6, 0, 0]} />
                      </BarChart>
                    )}
                  </ResponsiveContainer>
                </div>
              ) : (
                <div className="flex h-52 flex-col items-center justify-center text-center text-xs text-slate-400">
                  <Layers className="h-9 w-9 text-slate-600 mb-2 animate-pulse" />
                  <p className="font-semibold text-slate-300">No DPP Sessions Logged Yet</p>
                  <span className="mt-1 text-slate-500 max-w-sm">
                    Log your daily practice problem counts in the GATEBuddy Android App under the Tests &amp; DPP tab to track your precision metrics here.
                  </span>
                </div>
              )}
            </CyberCard>

            {/* DPP Records List */}
            <div className="space-y-3">
              <div className="flex items-center justify-between">
                <h3 className="text-base font-bold text-white flex items-center gap-2">
                  <span>Logged DPP Sessions</span>
                  <span className="rounded-full bg-slate-800 px-2 py-0.5 text-xs text-slate-300 font-semibold">
                    {totalDpp}
                  </span>
                </h3>
              </div>

              {dppList.length === 0 ? (
                <CyberCard glow="none" className="py-10 text-center text-xs text-slate-400">
                  <Layers className="mx-auto h-9 w-9 text-slate-600" />
                  <p className="mt-3 text-sm font-semibold text-slate-300">No DPP Sessions Logged Yet</p>
                  <p className="mt-1 text-slate-500 max-w-sm mx-auto">
                    Record your daily problem sets and accuracy in your GATEBuddy Android App to mirror them here.
                  </p>
                </CyberCard>
              ) : (
                <div className="space-y-3">
                  {dppList.map((d) => (
                    <CyberCard key={d.id} glow={d.accuracyPercentage >= 80 ? "emerald" : "amber"}>
                      <div className="flex flex-col justify-between gap-3 sm:flex-row sm:items-start">
                        <div>
                          <div className="flex items-center gap-2 flex-wrap">
                            <span className="rounded bg-emerald-500/20 px-2 py-0.5 text-[10px] font-bold text-gate-emerald border border-emerald-500/30">
                              {d.subjectCode}
                            </span>
                            <span className="text-sm font-bold text-white">
                              {d.questionsCorrect} of {d.totalQuestions} Solved Correctly
                            </span>
                            <span
                              className={clsx(
                                "rounded px-1.5 py-0.2 text-[10px] font-bold",
                                d.accuracyPercentage >= 80
                                  ? "text-emerald-400 bg-emerald-500/10"
                                  : "text-amber-400 bg-amber-500/10"
                              )}
                            >
                              {d.accuracyPercentage >= 80 ? "High Precision" : "Moderate"}
                            </span>
                          </div>
                          <p className="mt-1.5 text-xs text-slate-400 flex items-center gap-2">
                            <Calendar className="h-3.5 w-3.5 text-slate-500" />
                            <span>{d.date}</span>
                          </p>
                        </div>

                        <div className="text-left sm:text-right">
                          <span
                            className={clsx(
                              "text-2xl font-black",
                              d.accuracyPercentage >= 80 ? "text-gate-emerald" : "text-amber-400"
                            )}
                          >
                            {d.accuracyPercentage}%
                          </span>
                          <p className="text-[11px] text-slate-400 font-semibold">Accuracy Rate</p>
                        </div>
                      </div>

                      {d.notes && d.notes.trim().length > 0 && (
                        <div className="mt-3 rounded-xl border border-slate-800 bg-slate-900/60 p-3 text-xs text-slate-300">
                          <strong className="text-gate-sky">Session Notes:</strong> {d.notes}
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
