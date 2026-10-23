"use client";

import React from "react";
import { AppShell } from "@/components/layout/AppShell";
import { useApp } from "@/lib/store";
import { CyberCard } from "@/components/ui/CyberCard";
import confetti from "canvas-confetti";
import { Award, Lock, CheckCircle2 } from "lucide-react";
import clsx from "clsx";

export default function AchievementsPage() {
  const { achievements } = useApp();

  const unlockedCount = achievements.filter((a) => a.isUnlocked).length;
  const totalCount = achievements.length;
  const percentage = Math.round((unlockedCount / totalCount) * 100);

  const triggerCelebration = (isUnlocked: boolean) => {
    if (isUnlocked) {
      confetti({
        particleCount: 50,
        spread: 60,
        origin: { y: 0.7 },
      });
    }
  };

  return (
    <AppShell>
      <div className="mx-auto max-w-7xl space-y-6">
        {/* Banner */}
        <CyberCard glow="purple">
          <div className="flex flex-col justify-between gap-4 sm:flex-row sm:items-center">
            <div>
              <span className="text-[10px] font-bold tracking-widest text-gate-purple uppercase">
                GAMIFICATION &amp; REWARDS
              </span>
              <h1 className="mt-1 text-2xl font-black text-white sm:text-3xl">Milestones &amp; Badges</h1>
              <p className="mt-1 text-xs text-slate-400">
                Authoritative badges unlocked automatically upon satisfying genuine syllabus requirements.
              </p>
            </div>

            <div className="text-right">
              <span className="text-3xl font-black text-gate-purple">{unlockedCount} / {totalCount}</span>
              <p className="text-xs text-slate-400">{percentage}% Badges Unlocked</p>
            </div>
          </div>

          <div className="mt-4 h-2 w-full overflow-hidden rounded-full bg-slate-800">
            <div
              className="h-full rounded-full bg-gradient-to-r from-gate-purple to-gate-sky transition-all duration-500"
              style={{ width: `${percentage}%` }}
            />
          </div>
        </CyberCard>

        {/* Badges Grid */}
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {achievements.map((ach) => (
            <div
              key={ach.code}
              onClick={() => triggerCelebration(ach.isUnlocked)}
              className={clsx(
                "group relative cursor-pointer rounded-2xl border p-5 shadow-xl transition-all duration-200",
                ach.isUnlocked
                  ? "border-purple-500/40 bg-gradient-to-b from-gate-card to-purple-950/20 shadow-glowPurple hover:scale-[1.01]"
                  : "border-slate-800 bg-gate-card/50 opacity-60"
              )}
            >
              <div className="flex items-start justify-between">
                <div
                  className={clsx(
                    "flex h-12 w-12 items-center justify-center rounded-xl",
                    ach.isUnlocked ? "bg-purple-500/20 text-gate-purple" : "bg-slate-800 text-slate-600"
                  )}
                >
                  <Award className="h-6 w-6" />
                </div>

                {ach.isUnlocked ? (
                  <span className="flex items-center gap-1 rounded-full bg-emerald-500/10 px-2.5 py-0.5 text-[10px] font-bold text-emerald-400">
                    <CheckCircle2 className="h-3 w-3" />
                    <span>Unlocked</span>
                  </span>
                ) : (
                  <span className="flex items-center gap-1 rounded-full bg-slate-800 px-2.5 py-0.5 text-[10px] font-medium text-slate-500">
                    <Lock className="h-3 w-3" />
                    <span>Locked</span>
                  </span>
                )}
              </div>

              <h3 className="mt-4 text-base font-bold text-white">{ach.title}</h3>
              <p className="mt-1 text-xs text-slate-400">{ach.description}</p>

              <div className="mt-4 flex items-center justify-between border-t border-slate-800/80 pt-3 text-[11px] text-slate-500">
                <span className="uppercase tracking-wider">{ach.category}</span>
                {ach.unlockedAtTimestamp && (
                  <span className="text-slate-400">Earned</span>
                )}
              </div>
            </div>
          ))}
        </div>
      </div>
    </AppShell>
  );
}
