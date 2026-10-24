"use client";

import React, { useState } from "react";
import Link from "next/link";
import Image from "next/image";
import { useApp } from "@/lib/store";
import { Lock, Mail, User, ArrowRight, AlertCircle } from "lucide-react";

export default function SignupPage() {
  const { register } = useApp();
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsLoading(true);
    setError(null);
    const res = await register(email.trim(), password.trim(), name.trim());
    if (res.success) {
      window.location.href = "/dashboard";
    } else {
      setError(res.error || "Registration failed.");
      setIsLoading(false);
    }
  };

  return (
    <div className="flex min-h-screen items-center justify-center bg-[#090D16] p-4 text-slate-100 font-sans">
      <div className="w-full max-w-md rounded-2xl border border-[#26354F] bg-[#0F172A] p-7 sm:p-8 shadow-2xl">
        <div className="text-center">
          <Link href="/" title="Go to Home" className="inline-block transition hover:scale-105 cursor-pointer">
            <div className="mx-auto flex h-16 w-16 items-center justify-center overflow-hidden rounded-2xl border border-sky-400/40 bg-[#090D16] p-1 shadow-[0_0_20px_rgba(14,165,233,0.3)]">
              <Image
                src="/cropped_circle_image.png"
                alt="GateBuddy Logo"
                width={60}
                height={60}
                className="h-full w-full object-contain"
                priority
              />
            </div>
          </Link>
          <h2 className="mt-4 text-2xl font-black text-white">Create GATE Account</h2>
          <p className="mt-1 text-xs text-slate-400">Sync all your Android progress automatically</p>
        </div>

        {error && (
          <div className="mt-5 flex items-center gap-2 rounded-xl border border-rose-500/50 bg-rose-950/40 p-3 text-xs text-rose-300">
            <AlertCircle className="h-4 w-4 shrink-0 text-rose-400" />
            <span>{error}</span>
          </div>
        )}

        <form onSubmit={handleSubmit} className="mt-6 space-y-4">
          <div>
            <label className="text-xs font-semibold uppercase tracking-wider text-slate-400">Full Name</label>
            <div className="mt-1.5 flex items-center rounded-xl border border-[#26354F] bg-[#151F32] px-3.5 py-2.5">
              <User className="h-4 w-4 text-slate-400" />
              <input
                type="text"
                required
                value={name}
                onChange={(e) => setName(e.target.value)}
                className="ml-2.5 w-full bg-transparent text-sm text-white placeholder-slate-500 focus:outline-none"
                placeholder="Enter your name"
              />
            </div>
          </div>

          <div>
            <label className="text-xs font-semibold uppercase tracking-wider text-slate-400">Email Address</label>
            <div className="mt-1.5 flex items-center rounded-xl border border-[#26354F] bg-[#151F32] px-3.5 py-2.5">
              <Mail className="h-4 w-4 text-slate-400" />
              <input
                type="email"
                required
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                className="ml-2.5 w-full bg-transparent text-sm text-white placeholder-slate-500 focus:outline-none"
                placeholder="you@gatebuddy.com"
              />
            </div>
          </div>

          <div>
            <label className="text-xs font-semibold uppercase tracking-wider text-slate-400">Password</label>
            <div className="mt-1.5 flex items-center rounded-xl border border-[#26354F] bg-[#151F32] px-3.5 py-2.5">
              <Lock className="h-4 w-4 text-slate-400" />
              <input
                type="password"
                required
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                className="ml-2.5 w-full bg-transparent text-sm text-white placeholder-slate-500 focus:outline-none"
                placeholder="••••••••"
              />
            </div>
          </div>

          <button
            type="submit"
            disabled={isLoading}
            className="flex w-full items-center justify-center gap-2 rounded-xl bg-sky-500 py-3 text-sm font-bold text-slate-950 shadow-[0_0_15px_rgba(14,165,233,0.4)] transition hover:bg-sky-400 disabled:opacity-50"
          >
            <span>{isLoading ? "Creating Account..." : "Create Account"}</span>
            <ArrowRight className="h-4 w-4" />
          </button>
        </form>

        <p className="mt-6 text-center text-xs text-slate-400">
          Already have an account?{" "}
          <Link href="/login" className="font-semibold text-sky-400 hover:underline">
            Sign In
          </Link>
        </p>
      </div>
    </div>
  );
}
