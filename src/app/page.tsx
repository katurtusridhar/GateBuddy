"use client";

import React from "react";
import Link from "next/link";
import Image from "next/image";
import { useApp } from "@/lib/store";
import { Calendar, ShieldCheck, Zap, ArrowRight, LineChart, LogIn } from "lucide-react";

export default function LandingPage() {
  const { user } = useApp();

  return (
    <div className="min-h-screen bg-[#090D16] text-slate-100 font-sans">
      {/* Top Navigation Bar - Responsive & Optimized for Mobile */}
      <header className="mx-auto flex max-w-7xl items-center justify-between px-4 py-3 sm:px-6 sm:py-5 border-b border-[#26354F]/40">
        <div className="flex items-center gap-2.5 sm:gap-3 min-w-0">
          <div className="relative h-9 w-9 sm:h-11 sm:w-11 shrink-0 overflow-hidden rounded-xl border border-sky-400/40 bg-[#090D16] p-0.5 shadow-[0_0_15px_rgba(14,165,233,0.3)]">
            <Image
              src="/cropped_circle_image.png"
              alt="GateBuddy Logo"
              width={44}
              height={44}
              className="h-full w-full object-contain"
              priority
            />
          </div>
          <div className="flex items-center min-w-0">
            <span className="text-base sm:text-lg font-black tracking-tight text-white whitespace-nowrap">
              GateBuddy
            </span>
            <span className="hidden sm:inline-block ml-2 rounded-md border border-sky-500/40 bg-sky-950/60 px-2 py-0.5 text-[10px] font-bold text-sky-400 uppercase tracking-wider whitespace-nowrap">
              IIT BOMBAY CSE
            </span>
          </div>
        </div>

        {/* Action Buttons */}
        <div className="flex items-center gap-2 sm:gap-3 shrink-0">
          {user ? (
            <Link
              href="/dashboard"
              className="flex items-center gap-1.5 rounded-xl bg-sky-500 px-3.5 py-2 sm:px-5 sm:py-2.5 text-xs font-bold text-slate-950 shadow-[0_0_15px_rgba(14,165,233,0.3)] transition hover:bg-sky-400 whitespace-nowrap"
            >
              <span>Command Center</span>
              <ArrowRight className="h-3.5 w-3.5" />
            </Link>
          ) : (
            <>
              <Link
                href="/login"
                className="hidden sm:inline-block rounded-xl px-4 py-2 text-xs font-semibold text-slate-300 hover:text-white transition whitespace-nowrap"
              >
                Sign In
              </Link>
              <Link
                href="/login"
                className="flex items-center gap-1.5 rounded-xl bg-sky-500 px-3.5 py-2 sm:px-5 sm:py-2.5 text-xs font-bold text-slate-950 shadow-[0_0_15px_rgba(14,165,233,0.3)] transition hover:bg-sky-400 whitespace-nowrap"
              >
                <LogIn className="h-3.5 w-3.5 sm:hidden" />
                <span className="sm:hidden">Sign In</span>
                <span className="hidden sm:inline">Launch Command Center</span>
                <ArrowRight className="hidden sm:inline h-3.5 w-3.5" />
              </Link>
            </>
          )}
        </div>
      </header>

      {/* Hero Section */}
      <section className="mx-auto max-w-5xl px-4 sm:px-6 py-12 sm:py-20 text-center">
        <div className="inline-flex items-center gap-2 rounded-full border border-sky-500/30 bg-sky-950/40 px-3.5 py-1.5 text-xs font-bold text-sky-400 shadow-[0_0_15px_rgba(14,165,233,0.2)]">
          <Zap className="h-3.5 w-3.5 text-sky-400" />
          <span>Realtime Bidirectional Sync with Android App</span>
        </div>

        <h1 className="mt-6 text-3xl sm:text-5xl lg:text-6xl font-black tracking-tight text-white leading-tight">
          Master GATE CSE with <br className="hidden sm:inline" />
          <span className="bg-gradient-to-r from-sky-400 via-indigo-400 to-emerald-400 bg-clip-text text-transparent">
            Precision &amp; Ironclad Discipline
          </span>
        </h1>

        <p className="mx-auto mt-5 max-w-2xl text-xs sm:text-base text-slate-400 leading-relaxed px-2">
          The ultimate companion platform for GATE CSE aspirants. 690 canonical curriculum tasks, intelligent Sunday catch-up scheduling, Rule 1 &amp; Rule 2 recovery enforcement, and real-time state synchronization with your Android device.
        </p>

        <div className="mt-8 sm:mt-10 flex flex-col sm:flex-row justify-center items-center gap-3 sm:gap-4 px-4">
          <Link
            href={user ? "/dashboard" : "/login"}
            className="w-full sm:w-auto flex items-center justify-center gap-2 rounded-xl bg-sky-500 px-7 py-3 text-sm font-bold text-slate-950 shadow-[0_0_20px_rgba(14,165,233,0.4)] transition hover:bg-sky-400"
          >
            <span>{user ? "Open Today's Mission" : "Sign In to Command Center"}</span>
            <ArrowRight className="h-4 w-4" />
          </Link>
          <Link
            href={user ? "/calendar" : "/login"}
            className="w-full sm:w-auto flex items-center justify-center gap-2 rounded-xl border border-[#26354F] bg-[#151F32] px-7 py-3 text-sm font-semibold text-white transition hover:border-sky-400 hover:bg-[#1E2B45]"
          >
            <Calendar className="h-4 w-4 text-sky-400" />
            <span>Master Study Calendar</span>
          </Link>
        </div>
      </section>

      {/* Feature Cards Grid */}
      <section className="mx-auto max-w-6xl px-4 sm:px-6 pb-20 sm:pb-24">
        <div className="grid grid-cols-1 gap-4 sm:gap-6 sm:grid-cols-2 lg:grid-cols-3">
          <div className="rounded-2xl border border-[#26354F] bg-[#0F172A] p-5 sm:p-6 shadow-xl">
            <div className="flex h-11 w-11 sm:h-12 sm:w-12 items-center justify-center rounded-xl bg-sky-950/60 text-sky-400 border border-sky-800/40">
              <Calendar className="h-5 w-5 sm:h-6 sm:w-6" />
            </div>
            <h3 className="mt-4 text-base font-bold text-white">Full 345-Day Syllabus Calendar</h3>
            <p className="mt-2 text-xs text-slate-400 leading-relaxed">
              Canonical schedule from Oct 19, 2026 to Sep 28, 2027 with two daily lecture slots, Sunday mock tests, and designated catch-up buffers.
            </p>
          </div>

          <div className="rounded-2xl border border-[#26354F] bg-[#0F172A] p-5 sm:p-6 shadow-xl">
            <div className="flex h-11 w-11 sm:h-12 sm:w-12 items-center justify-center rounded-xl bg-emerald-950/60 text-emerald-400 border border-emerald-800/40">
              <ShieldCheck className="h-5 w-5 sm:h-6 sm:w-6" />
            </div>
            <h3 className="mt-4 text-base font-bold text-white">Recovery Engine Rules</h3>
            <p className="mt-2 text-xs text-slate-400 leading-relaxed">
              Enforces Rule 1 (max 2 lectures/day) and Rule 2 (no duplicate subjects per day). Prevents schedule loss and maintains strict lecture sequence.
            </p>
          </div>

          <div className="rounded-2xl border border-[#26354F] bg-[#0F172A] p-5 sm:p-6 shadow-xl">
            <div className="flex h-11 w-11 sm:h-12 sm:w-12 items-center justify-center rounded-xl bg-indigo-950/60 text-indigo-400 border border-indigo-800/40">
              <LineChart className="h-5 w-5 sm:h-6 sm:w-6" />
            </div>
            <h3 className="mt-4 text-base font-bold text-white">Daily Points &amp; Forecasting</h3>
            <p className="mt-2 text-xs text-slate-400 leading-relaxed">
              Real-time daily score accumulation (+10 per lecture, -2 past missed penalty, +5 boosters), 7-day/28-day adherence, and completion trajectory.
            </p>
          </div>
        </div>
      </section>

      {/* Footer */}
      <footer className="border-t border-[#26354F] py-6 sm:py-8 text-center text-xs text-slate-500 px-4">
        GateBuddy • Engineered for IIT Bombay CSE Aspirants • AIR &lt; 100
      </footer>
    </div>
  );
}
