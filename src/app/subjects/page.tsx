"use client";

import React, { useMemo } from "react";
import Link from "next/link";
import { AppShell } from "@/components/layout/AppShell";
import { useApp } from "@/lib/store";
import { CyberCard } from "@/components/ui/CyberCard";
import { SUBJECT_REGISTRY } from "@/lib/subjectRegistry";
import { ArrowRight } from "lucide-react";

export default function SubjectsPage() {
  const { tasks, progress } = useApp();

  const subjectsProgress = useMemo(() => {
    return SUBJECT_REGISTRY.map((s) => {
      const subjectTasks = tasks.filter((t) => t.subject === s.name);
      const completedScheduled = subjectTasks.filter((t) => progress.get(t.id)?.isCompleted).length;
      const totalDone = s.priorCompletedLectures + completedScheduled;
      const percent = s.totalLectures > 0 ? Math.min(100, Math.round((totalDone / s.totalLectures) * 100)) : 0;

      return {
        ...s,
        completedScheduled,
        totalDone,
        percent,
        scheduledCount: subjectTasks.length,
      };
    });
  }, [tasks, progress]);

  return (
    <AppShell>
      <div className="mx-auto max-w-7xl space-y-6">
        <div>
          <h1 className="text-2xl font-black text-white">Syllabus &amp; Subject Mastery</h1>
          <p className="text-xs text-slate-400">
            Real-time syllabus completion across all 22 GATE CSE core disciplines.
          </p>
        </div>

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {subjectsProgress.map((subj) => (
            <Link key={subj.code} href={`/subjects/${subj.code}`}>
              <CyberCard glow={subj.percent === 100 ? "emerald" : "sky"} className="cursor-pointer transition-all hover:scale-[1.01]">
                <div className="flex items-start justify-between">
                  <div>
                    <span className="rounded-md border border-slate-700 bg-slate-800 px-2 py-0.5 text-[10px] font-black text-gate-sky">
                      {subj.code}
                    </span>
                    <h3 className="mt-2 text-base font-bold text-white">{subj.name}</h3>
                  </div>
                  <span className="text-lg font-black text-white">{subj.percent}%</span>
                </div>

                {/* Progress Bar */}
                <div className="mt-4 h-2 w-full overflow-hidden rounded-full bg-slate-800">
                  <div
                    className="h-full rounded-full bg-gradient-to-r from-gate-sky to-gate-emerald transition-all duration-500"
                    style={{ width: `${subj.percent}%` }}
                  />
                </div>

                <div className="mt-3 flex items-center justify-between text-xs text-slate-400">
                  <span>
                    {subj.totalDone} / {subj.totalLectures} Done
                  </span>
                  <div className="flex items-center gap-1 font-semibold text-gate-sky">
                    <span>Details</span>
                    <ArrowRight className="h-3 w-3" />
                  </div>
                </div>
              </CyberCard>
            </Link>
          ))}
        </div>
      </div>
    </AppShell>
  );
}
