"use client";

import React from "react";
import Link from "next/link";
import Image from "next/image";
import { Calendar, ShieldCheck, Zap, ArrowRight, LineChart } from "lucide-react";

export default function LandingPage() {
  return (
    <div className="min-h-screen bg-[#090D16] text-slate-100 font-sans">
      {/* Top Navigation */}
      <header className="mx-auto flex max-w-7xl items-center justify-between p-6">
        <div className="flex items-center gap-3">
          <div className="relative h-11 w-11 shrink-0 overflow-hidden rounded-xl border border-sky-400/40 bg-[#090D16] p-0.5 shadow-[0_0_15px_rgba(14,165,233,0.3)]">
            <Image
              src="/cropped_circle_image.png"
              alt="GateBuddy Logo"
              width={44}
              height={44}
              className="h-full w-full object-contain"
              priority
            />
          </div>
          <div>
            <span className="text-lg font-black tracking-tight text-white">GateBuddy</span>
            <span className="ml-2 rounded-md border border-sky-500/40 bg-sky-950/60 px-2 py-0.5 text-[10px] font-bold text-sky-400 uppercase tracking-wider">
              IIT BOMBAY CSE
            </span>
          </div>
        </div>

        <div className="flex items-center gap-3">
          <Link
            href="/login"
            className="rounded-xl px-4 py-2 text-xs font-semibold text-slate-300 hover:text-white transition"
          >
            Sign In
          </Link>
          <Link
            href="/dashboard"
            className="flex items-center gap-1.5 rounded-xl bg-sky-500 px-5 py-2.5 text-xs font-bold text-slate-950 shadow-[0_0_15px_rgba(14,165,233,0.3)] transition hover:bg-sky-400"
          >
            <span>Launch Command Center</span>
            <ArrowRight className="h-4 w-4" />
          </Link>
        </div>
      </header>

      {/* Hero Section */}
      <section className="mx-auto max-w-5xl px-6 py-16 text-center lg:py-24">
        <div className="inline-flex items-center gap-2 rounded-full border border-sky-500/30 bg-sky-950/40 px-4 py-1.5 text-xs font-bold text-sky-400 shadow-[0_0_15px_rgba(14,165,233,0.2)]">
          <Zap className="h-4 w-4 text-sky-400" />
          <span>Realtime Bidirectional Sync with Android App</span>
        </div>

        <h1 className="mt-6 text-4xl font-black tracking-tight text-white sm:text-6xl leading-tight">
          Master GATE CSE with <br className="hidden sm:inline" />
          <span className="bg-gradient-to-r from-sky-400 via-indigo-400 to-emerald-400 bg-clip-text text-transparent">
            Precision &amp; Ironclad Discipline
          </span>
        </h1>

        <p className="mx-auto mt-6 max-w-2xl text-sm sm:text-base text-slate-400 leading-relaxed">
          The ultimate companion platform for GATE CSE aspirants. 690 canonical curriculum tasks, intelligent Sunday catch-up scheduling, Rule 1 &amp; Rule 2 recovery enforcement, and real-time state synchronization with your Android device.
        </p>

        <div className="mt-10 flex flex-wrap justify-center gap-4">
          <Link
            href="/dashboard"
            className="flex items-center gap-2 rounded-xl bg-sky-500 px-7 py-3.5 text-sm font-bold text-slate-950 shadow-[0_0_20px_rgba(14,165,233,0.4)] transition hover:bg-sky-400"
          >
            <span>Open Today&apos;s Mission</span>
            <ArrowRight className="h-4 w-4" />
          </Link>
          <Link
            href="/calendar"
            className="flex items-center gap-2 rounded-xl border border-[#26354F] bg-[#151F32] px-7 py-3.5 text-sm font-semibold text-white transition hover:border-sky-400 hover:bg-[#1E2B45]"
          >
            <Calendar className="h-4 w-4 text-sky-400" />
            <span>Master Study Calendar</span>
          </Link>
        </div>
      </section>

      {/* Feature Cards Grid */}
      <section className="mx-auto max-w-6xl px-6 pb-24">
        <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3">
          <div className="rounded-2xl border border-[#26354F] bg-[#0F172A] p-6 shadow-xl">
            <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-sky-950/60 text-sky-400 border border-sky-800/40">
              <Calendar className="h-6 w-6" />
            </div>
            <h3 className="mt-4 text-base font-bold text-white">Full 345-Day Syllabus Calendar</h3>
            <p className="mt-2 text-xs text-slate-400 leading-relaxed">
              Canonical schedule from Oct 19, 2026 to Sep 28, 2027 with two daily lecture slots, Sunday mock tests, and designated catch-up buffers.
            </p>
          </div>

          <div className="rounded-2xl border border-[#26354F] bg-[#0F172A] p-6 shadow-xl">
            <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-emerald-950/60 text-emerald-400 border border-emerald-800/40">
              <ShieldCheck className="h-6 w-6" />
            </div>
            <h3 className="mt-4 text-base font-bold text-white">Recovery Engine Rules</h3>
            <p className="mt-2 text-xs text-slate-400 leading-relaxed">
              Enforces Rule 1 (max 2 lectures/day) and Rule 2 (no duplicate subjects per day). Prevents schedule loss and maintains strict lecture sequence.
            </p>
          </div>

          <div className="rounded-2xl border border-[#26354F] bg-[#0F172A] p-6 shadow-xl">
            <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-indigo-950/60 text-indigo-400 border border-indigo-800/40">
              <LineChart className="h-6 w-6" />
            </div>
            <h3 className="mt-4 text-base font-bold text-white">Daily Points &amp; Forecasting</h3>
            <p className="mt-2 text-xs text-slate-400 leading-relaxed">
              Real-time daily score accumulation (+10 per lecture, -2 past missed penalty, +5 boosters), 7-day/28-day adherence, and completion trajectory.
            </p>
          </div>
        </div>
      </section>

      {/* Footer */}
      <footer className="border-t border-[#26354F] py-8 text-center text-xs text-slate-500">
        GateBuddy • Engineered for IIT Bombay CSE Aspirants • AIR &lt; 100
      </footer>
    </div>
  );
}
