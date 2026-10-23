"use client";

import React, { useMemo } from "react";
import { useParams } from "next/navigation";
import Link from "next/link";
import { AppShell } from "@/components/layout/AppShell";
import { useApp } from "@/lib/store";
import { CyberCard } from "@/components/ui/CyberCard";
import { getSubjectInfoByCode } from "@/lib/subjectRegistry";
import { ArrowLeft, CheckCircle2, Clock } from "lucide-react";

export default function SubjectDetailPage() {
  const params = useParams();
  const code = (params?.code as string)?.toUpperCase() || "";
  const { tasks, progress } = useApp();

  const subjectInfo = useMemo(() => getSubjectInfoByCode(code), [code]);

  const subjectTasks = useMemo(() => {
    if (!subjectInfo) return [];
    return tasks.filter((t) => t.subject.toLowerCase().trim() === subjectInfo.name.toLowerCase().trim());
  }, [tasks, subjectInfo]);

  const completedCount = useMemo(() => {
    return subjectTasks.filter((t) => progress.get(t.id)?.isCompleted).length;
  }, [subjectTasks, progress]);

  const totalDone = (subjectInfo?.priorCompletedLectures || 0) + completedCount;
  const totalLectures = subjectInfo?.totalLectures || 1;
  const progressPercent = Math.min(100, Math.round((totalDone / totalLectures) * 100));

  if (!subjectInfo) {
    return (
      <AppShell>
        <div className="py-12 text-center text-slate-400">
          <p>Subject not found.</p>
          <Link href="/subjects" className="mt-4 inline-flex items-center gap-1 text-xs text-gate-sky hover:underline">
            <ArrowLeft className="h-4 w-4" />
            <span>Back to Subjects</span>
          </Link>
        </div>
      </AppShell>
    );
  }

  return (
    <AppShell>
      <div className="mx-auto max-w-7xl space-y-6">
        {/* Back navigation */}
        <Link href="/subjects" className="inline-flex items-center gap-1 text-xs font-semibold text-gate-sky hover:underline">
          <ArrowLeft className="h-4 w-4" />
          <span>Back to Subjects</span>
        </Link>

        {/* Subject Banner */}
        <CyberCard glow="sky">
          <div className="flex flex-col justify-between gap-4 sm:flex-row sm:items-center">
            <div>
              <span className="rounded-md border border-slate-700 bg-slate-800 px-2 py-0.5 text-xs font-black text-gate-sky">
                {subjectInfo.code}
              </span>
              <h1 className="mt-2 text-2xl font-black text-white">{subjectInfo.name}</h1>
              <p className="mt-1 text-xs text-slate-400">
                Course Type: {subjectInfo.type} • Prior Completed: {subjectInfo.priorCompletedLectures} Lectures
              </p>
            </div>
            <div className="text-right">
              <span className="text-3xl font-black text-white">{progressPercent}%</span>
              <p className="text-xs text-slate-400">
                {totalDone} of {totalLectures} Lectures Mastered
              </p>
            </div>
          </div>

          <div className="mt-4 h-2.5 w-full overflow-hidden rounded-full bg-slate-800">
            <div
              className="h-full rounded-full bg-gradient-to-r from-gate-sky via-gate-emerald to-gate-indigo transition-all duration-500"
              style={{ width: `${progressPercent}%` }}
            />
          </div>
        </CyberCard>

        {/* Lecture Breakdown */}
        <div>
          <h2 className="text-lg font-bold text-white">Scheduled Lectures ({subjectTasks.length})</h2>
          <p className="text-xs text-slate-400">Track each lecture in chronological sequence</p>

          <div className="mt-4 space-y-3">
            {subjectTasks.map((task) => {
              const isDone = progress.get(task.id)?.isCompleted === true;
              return (
                <div
                  key={task.id}
                  className="flex items-center justify-between rounded-xl border border-slate-800 bg-gate-card p-4 transition hover:border-slate-700"
                >
                  <div>
                    <div className="flex items-center gap-2">
                      <span className="rounded bg-slate-800 px-2 py-0.5 text-[10px] font-bold text-slate-400">
                        {task.date}
                      </span>
                      <span className="text-xs font-bold text-white">{task.title}</span>
                    </div>
                    {task.details && <p className="mt-1 text-xs text-slate-400">{task.details}</p>}
                  </div>
                  <div>
                    {isDone ? (
                      <span className="flex items-center gap-1 rounded-full bg-emerald-500/10 px-3 py-1 text-xs font-bold text-emerald-400">
                        <CheckCircle2 className="h-3.5 w-3.5" />
                        <span>Completed</span>
                      </span>
                    ) : (
                      <span className="flex items-center gap-1 rounded-full bg-slate-800 px-3 py-1 text-xs font-medium text-slate-400">
                        <Clock className="h-3.5 w-3.5" />
                        <span>Scheduled</span>
                      </span>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      </div>
    </AppShell>
  );
}
