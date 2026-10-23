"use client";

import React, { useState } from "react";
import Link from "next/link";
import { Sparkles, Mail, ArrowLeft, CheckCircle2 } from "lucide-react";

export default function ForgotPasswordPage() {
  const [email, setEmail] = useState("");
  const [sent, setSent] = useState(false);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (email) setSent(true);
  };

  return (
    <div className="flex min-h-screen items-center justify-center bg-gate-dark p-4">
      <div className="w-full max-w-md rounded-2xl border border-slate-800 bg-gate-card p-8 shadow-2xl">
        <div className="text-center">
          <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-2xl bg-gradient-to-tr from-gate-sky to-gate-indigo p-0.5 shadow-glowSky">
            <div className="flex h-full w-full items-center justify-center rounded-[14px] bg-gate-dark">
              <Sparkles className="h-6 w-6 text-gate-sky" />
            </div>
          </div>
          <h2 className="mt-4 text-2xl font-extrabold text-white">Reset Password</h2>
          <p className="mt-1 text-xs text-slate-400">Enter your email to receive recovery instructions</p>
        </div>

        {sent ? (
          <div className="mt-6 text-center">
            <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-emerald-500/10 text-emerald-400">
              <CheckCircle2 className="h-6 w-6" />
            </div>
            <p className="mt-3 text-sm font-semibold text-white">Recovery link dispatched</p>
            <p className="mt-1 text-xs text-slate-400">
              We have sent instructions to <strong className="text-slate-200">{email}</strong>.
            </p>
            <Link
              href="/login"
              className="mt-6 inline-flex items-center gap-1 text-xs font-semibold text-gate-sky hover:underline"
            >
              <ArrowLeft className="h-4 w-4" />
              <span>Back to Sign In</span>
            </Link>
          </div>
        ) : (
          <form onSubmit={handleSubmit} className="mt-6 space-y-4">
            <div>
              <label className="text-xs font-semibold uppercase tracking-wider text-slate-400">Email Address</label>
              <div className="mt-1.5 flex items-center rounded-xl border border-slate-700 bg-slate-900/80 px-3 py-2.5">
                <Mail className="h-4 w-4 text-slate-500" />
                <input
                  type="email"
                  required
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  className="ml-2.5 w-full bg-transparent text-sm text-white placeholder-slate-500 focus:outline-none"
                  placeholder="you@domain.com"
                />
              </div>
            </div>

            <button
              type="submit"
              className="w-full rounded-xl bg-gate-sky py-3 text-sm font-bold text-slate-950 shadow-glowSky transition hover:bg-sky-400"
            >
              Send Recovery Link
            </button>

            <div className="text-center">
              <Link href="/login" className="inline-flex items-center gap-1 text-xs text-slate-400 hover:text-white">
                <ArrowLeft className="h-3.5 w-3.5" />
                <span>Return to Sign In</span>
              </Link>
            </div>
          </form>
        )}
      </div>
    </div>
  );
}
