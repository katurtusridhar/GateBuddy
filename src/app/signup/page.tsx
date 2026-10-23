"use client";

import React, { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useApp } from "@/lib/store";
import { Sparkles, Lock, Mail, User, ArrowRight, AlertCircle } from "lucide-react";

export default function SignupPage() {
  const router = useRouter();
  const { register } = useApp();
  const [name, setName] = useState("Sridhar");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsLoading(true);
    setError(null);
    const res = await register(email, password, name);
    if (res.success) {
      router.push("/dashboard");
    } else {
      setError(res.error || "Registration failed.");
      setIsLoading(false);
    }
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
          <h2 className="mt-4 text-2xl font-extrabold text-white">Create GATE Account</h2>
          <p className="mt-1 text-xs text-slate-400">Sync all your Android progress automatically</p>
        </div>

        {error && (
          <div className="mt-4 flex items-center gap-2 rounded-xl border border-rose-500/50 bg-rose-500/10 p-3 text-xs text-rose-300">
            <AlertCircle className="h-4 w-4 shrink-0 text-rose-400" />
            <span>{error}</span>
          </div>
        )}

        <form onSubmit={handleSubmit} className="mt-6 space-y-4">
          <div>
            <label className="text-xs font-semibold uppercase tracking-wider text-slate-400">Full Name</label>
            <div className="mt-1.5 flex items-center rounded-xl border border-slate-700 bg-slate-900/80 px-3 py-2.5">
              <User className="h-4 w-4 text-slate-500" />
              <input
                type="text"
                required
                value={name}
                onChange={(e) => setName(e.target.value)}
                className="ml-2.5 w-full bg-transparent text-sm text-white placeholder-slate-500 focus:outline-none"
                placeholder="e.g. Sridhar"
              />
            </div>
          </div>

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

          <div>
            <label className="text-xs font-semibold uppercase tracking-wider text-slate-400">Password</label>
            <div className="mt-1.5 flex items-center rounded-xl border border-slate-700 bg-slate-900/80 px-3 py-2.5">
              <Lock className="h-4 w-4 text-slate-500" />
              <input
                type="password"
                required
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                className="ml-2.5 w-full bg-transparent text-sm text-white placeholder-slate-500 focus:outline-none"
                placeholder="Minimum 8 characters"
              />
            </div>
          </div>

          <button
            type="submit"
            disabled={isLoading}
            className="flex w-full items-center justify-center gap-2 rounded-xl bg-gate-sky py-3 text-sm font-bold text-slate-950 shadow-glowSky transition hover:bg-sky-400 disabled:opacity-50"
          >
            <span>{isLoading ? "Creating Account..." : "Create Account"}</span>
            <ArrowRight className="h-4 w-4" />
          </button>
        </form>

        <div className="mt-6 border-t border-slate-800/80 pt-4 text-center text-xs text-slate-400">
          Already have an account?{" "}
          <Link href="/login" className="font-semibold text-gate-sky hover:underline">
            Sign in
          </Link>
        </div>
      </div>
    </div>
  );
}
