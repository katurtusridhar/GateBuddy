"use client";

import React, { useState } from "react";
import Image from "next/image";
import Link from "next/link";
import { useApp } from "@/lib/store";
import { Lock, User, ArrowRight, AlertCircle, Eye, EyeOff } from "lucide-react";

export default function LoginPage() {
  const { login } = useApp();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!username.trim() || !password.trim()) {
      setError("Please enter both username and password.");
      return;
    }
    setIsLoading(true);
    setError(null);
    const res = await login(username.trim(), password.trim());
    if (res.success) {
      window.location.href = "/dashboard";
    } else {
      setError(res.error || "Invalid username or password. Please try again.");
      setIsLoading(false);
    }
  };

  return (
    <div className="flex min-h-screen items-center justify-center bg-[#090D16] p-4 text-slate-100 font-sans">
      <div className="w-full max-w-md rounded-2xl border border-[#26354F] bg-[#0F172A] p-7 sm:p-8 shadow-2xl">
        {/* Brand Header with IIT Bombay Logo */}
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
          <h2 className="mt-4 text-2xl font-black text-white">Sign In to GateBuddy</h2>
          <p className="mt-1 text-xs text-slate-400">Personal GATE Preparation Command Center</p>
        </div>

        {error && (
          <div className="mt-5 flex items-center gap-2 rounded-xl border border-rose-500/50 bg-rose-950/40 p-3 text-xs text-rose-300">
            <AlertCircle className="h-4 w-4 shrink-0 text-rose-400" />
            <span>{error}</span>
          </div>
        )}

        <form onSubmit={handleSubmit} className="mt-6 space-y-4">
          <div>
            <label className="text-xs font-semibold uppercase tracking-wider text-slate-400">
              Username or Email
            </label>
            <div className="mt-1.5 flex items-center rounded-xl border border-[#26354F] bg-[#151F32] px-3.5 py-2.5 focus-within:border-sky-500 transition">
              <User className="h-4 w-4 text-slate-400" />
              <input
                type="text"
                required
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                autoComplete="username"
                className="ml-2.5 w-full bg-transparent text-sm text-white placeholder-slate-500 focus:outline-none"
                placeholder="Enter username or email"
              />
            </div>
          </div>

          <div>
            <div className="flex items-center justify-between">
              <label className="text-xs font-semibold uppercase tracking-wider text-slate-400">
                Password
              </label>
              <Link href="/forgot-password" className="text-xs font-medium text-sky-400 hover:underline">
                Forgot?
              </Link>
            </div>
            <div className="mt-1.5 flex items-center rounded-xl border border-[#26354F] bg-[#151F32] px-3.5 py-2.5 focus-within:border-sky-500 transition">
              <Lock className="h-4 w-4 text-slate-400" />
              <input
                type={showPassword ? "text" : "password"}
                required
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                autoComplete="current-password"
                className="ml-2.5 w-full bg-transparent text-sm text-white placeholder-slate-500 focus:outline-none"
                placeholder="Enter password"
              />
              <button
                type="button"
                onClick={() => setShowPassword(!showPassword)}
                className="text-slate-400 hover:text-white transition"
                aria-label={showPassword ? "Hide password" : "Show password"}
              >
                {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
              </button>
            </div>
          </div>

          <button
            type="submit"
            disabled={isLoading}
            className="flex w-full items-center justify-center gap-2 rounded-xl bg-sky-500 py-3 text-sm font-bold text-slate-950 shadow-[0_0_15px_rgba(14,165,233,0.4)] transition hover:bg-sky-400 disabled:opacity-50 active:scale-[0.99]"
          >
            <span>{isLoading ? "Signing In..." : "Sign In"}</span>
            <ArrowRight className="h-4 w-4" />
          </button>
        </form>

        <div className="mt-6 border-t border-[#26354F]/80 pt-4 text-center text-xs text-slate-400">
          Protected with Master Authorization Code • IIT Bombay 2028
        </div>
      </div>
    </div>
  );
}
