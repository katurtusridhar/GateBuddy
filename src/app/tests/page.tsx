"use client";

import React, { useState } from "react";
import { AppShell } from "@/components/layout/AppShell";
import { useApp } from "@/lib/store";
import { CyberCard } from "@/components/ui/CyberCard";
import { ClipboardList, Plus, X } from "lucide-react";

export default function TestsPage() {
  const { assessments, addAssessment, todayDateStr } = useApp();
  const [showAddModal, setShowAddModal] = useState(false);

  // Form states
  const [testName, setTestName] = useState("");
  const [subjectName, setSubjectName] = useState("Digital Logic");
  const [marksObtained, setMarksObtained] = useState("");
  const [maxMarks, setMaxMarks] = useState("50");
  const [topic, setTopic] = useState("");
  const [mistakesAnalysis, setMistakesAnalysis] = useState("");
  const [followUpTasks, setFollowUpTasks] = useState("");

  const totalTests = assessments.length;
  const avgPercentage =
    totalTests > 0
      ? Math.round(assessments.reduce((sum, a) => sum + a.percentage, 0) / totalTests)
      : 0;

  const handleAddTest = async (e: React.FormEvent) => {
    e.preventDefault();
    const marks = parseFloat(marksObtained);
    const max = parseFloat(maxMarks);
    if (isNaN(marks) || isNaN(max) || max <= 0) return;

    const percentage = Math.round((marks / max) * 100);

    await addAssessment({
      testDate: todayDateStr,
      testName: testName || "Weekly Test & Active Recall",
      subjectName,
      marksObtained: marks,
      maxMarks: max,
      percentage,
      topic,
      mistakesAnalysis,
      followUpTasks,
    });

    setShowAddModal(false);
    setTestName("");
    setMarksObtained("");
    setTopic("");
    setMistakesAnalysis("");
    setFollowUpTasks("");
  };

  return (
    <AppShell>
      <div className="mx-auto max-w-7xl space-y-6">
        {/* Header */}
        <div className="flex flex-col justify-between gap-4 sm:flex-row sm:items-center">
          <div>
            <h1 className="text-2xl font-black text-white">Tests &amp; PYQ Mistake Notebook</h1>
            <p className="text-xs text-slate-400">Weekly active recall tests, mistake logs, and revision follow-up tasks</p>
          </div>

          <button
            onClick={() => setShowAddModal(true)}
            className="flex items-center gap-1.5 rounded-xl bg-gate-sky px-4 py-2.5 text-xs font-bold text-slate-950 shadow-glowSky transition hover:bg-sky-400"
          >
            <Plus className="h-4 w-4" />
            <span>Record Test Result</span>
          </button>
        </div>

        {/* Stats Row */}
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
          <CyberCard glow="sky">
            <span className="text-xs font-semibold text-slate-400 uppercase">Total Tests Taken</span>
            <p className="mt-2 text-3xl font-extrabold text-white">{totalTests}</p>
            <p className="mt-1 text-xs text-slate-400">Weekly tests &amp; PYQ mocks recorded</p>
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
              <p className="mt-3 font-semibold text-slate-300">No test results recorded yet.</p>
              <p className="mt-1 text-slate-500">Record your weekly test scores and mistake notes here.</p>
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

        {/* Modal: Record New Test */}
        {showAddModal && (
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 p-4 backdrop-blur-md">
            <div className="relative w-full max-w-lg rounded-2xl border border-slate-700 bg-gate-card p-6 shadow-2xl">
              <div className="flex items-center justify-between border-b border-slate-800 pb-4">
                <h3 className="text-lg font-bold text-white">Record Weekly Test Result</h3>
                <button onClick={() => setShowAddModal(false)} className="rounded-lg p-1 text-slate-400 hover:text-white">
                  <X className="h-5 w-5" />
                </button>
              </div>

              <form onSubmit={handleAddTest} className="mt-4 space-y-3.5">
                <div>
                  <label className="text-xs font-semibold text-slate-400">Test Title</label>
                  <input
                    type="text"
                    required
                    placeholder="e.g. Sunday Test 01 (DL & CN)"
                    value={testName}
                    onChange={(e) => setTestName(e.target.value)}
                    className="mt-1 w-full rounded-xl border border-slate-700 bg-slate-900 px-3 py-2 text-xs text-white focus:border-gate-sky focus:outline-none"
                  />
                </div>

                <div>
                  <label className="text-xs font-semibold text-slate-400">Subject</label>
                  <select
                    value={subjectName}
                    onChange={(e) => setSubjectName(e.target.value)}
                    className="mt-1 w-full rounded-xl border border-slate-700 bg-slate-900 px-3 py-2 text-xs text-white focus:border-gate-sky focus:outline-none"
                  >
                    <option value="Digital Logic">Digital Logic</option>
                    <option value="Computer Network">Computer Network</option>
                    <option value="DBMS">DBMS</option>
                    <option value="Fundamental of C + C Prog">Fundamental of C + C Prog</option>
                    <option value="Operating System">Operating System</option>
                    <option value="COA">COA</option>
                    <option value="General Aptitude">General Aptitude</option>
                  </select>
                </div>

                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <label className="text-xs font-semibold text-slate-400">Marks Obtained</label>
                    <input
                      type="number"
                      step="0.5"
                      required
                      placeholder="e.g. 42"
                      value={marksObtained}
                      onChange={(e) => setMarksObtained(e.target.value)}
                      className="mt-1 w-full rounded-xl border border-slate-700 bg-slate-900 px-3 py-2 text-xs text-white focus:border-gate-sky focus:outline-none"
                    />
                  </div>
                  <div>
                    <label className="text-xs font-semibold text-slate-400">Max Marks</label>
                    <input
                      type="number"
                      required
                      placeholder="50"
                      value={maxMarks}
                      onChange={(e) => setMaxMarks(e.target.value)}
                      className="mt-1 w-full rounded-xl border border-slate-700 bg-slate-900 px-3 py-2 text-xs text-white focus:border-gate-sky focus:outline-none"
                    />
                  </div>
                </div>

                <div>
                  <label className="text-xs font-semibold text-slate-400">Subject / Topic</label>
                  <input
                    type="text"
                    placeholder="e.g. Combinational Circuits & IP Addressing"
                    value={topic}
                    onChange={(e) => setTopic(e.target.value)}
                    className="mt-1 w-full rounded-xl border border-slate-700 bg-slate-900 px-3 py-2 text-xs text-white focus:border-gate-sky focus:outline-none"
                  />
                </div>

                <div>
                  <label className="text-xs font-semibold text-slate-400">Mistake Analysis &amp; Doubts</label>
                  <textarea
                    rows={2}
                    placeholder="What questions did you miss and why?"
                    value={mistakesAnalysis}
                    onChange={(e) => setMistakesAnalysis(e.target.value)}
                    className="mt-1 w-full rounded-xl border border-slate-700 bg-slate-900 px-3 py-2 text-xs text-white focus:border-gate-sky focus:outline-none"
                  />
                </div>

                <div className="flex justify-end gap-3 pt-3">
                  <button
                    type="button"
                    onClick={() => setShowAddModal(false)}
                    className="rounded-xl px-4 py-2 text-xs text-slate-400 hover:text-white"
                  >
                    Cancel
                  </button>
                  <button
                    type="submit"
                    className="rounded-xl bg-gate-sky px-5 py-2 text-xs font-bold text-slate-950 shadow-glowSky hover:bg-sky-400"
                  >
                    Save Test Record
                  </button>
                </div>
              </form>
            </div>
          </div>
        )}
      </div>
    </AppShell>
  );
}
