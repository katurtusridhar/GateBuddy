"use client";

import React, { useState } from "react";
import Link from "next/link";
import Image from "next/image";
import { useApp } from "@/lib/store";
import { ArrowLeft, CheckCircle2, ShieldCheck, KeyRound, AlertCircle } from "lucide-react";

export default function ForgotPasswordPage() {
  const { changeCredentials, credentials } = useApp();
  const [masterCode, setMasterCode] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState(false);
  const [isLoading, setIsLoading] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    if (newPassword !== confirmPassword) {
      setError("New password and confirm password do not match.");
      return;
    }

    setIsLoading(true);
    const res = await changeCredentials(
      credentials.username || "katurtusridhar",
      newPassword,
      masterCode.trim()
    );

    if (res.success) {
      setSuccess(true);
    } else {
      setError(res.error || "Invalid master authorization code.");
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
          <h2 className="mt-4 text-2xl font-black text-white">Reset Password</h2>
          <p className="mt-1 text-xs text-slate-400">
            Authorized with Master Security Code • IIT Bombay 2028
          </p>
        </div>

        {error && (
          <div className="mt-5 flex items-center gap-2 rounded-xl border border-rose-500/50 bg-rose-950/40 p-3 text-xs text-rose-300">
            <AlertCircle className="h-4 w-4 shrink-0 text-rose-400" />
            <span>{error}</span>
          </div>
        )}

        {success ? (
          <div className="mt-6 text-center">
            <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-emerald-500/10 text-emerald-400">
              <CheckCircle2 className="h-6 w-6" />
            </div>
            <p className="mt-3 text-sm font-bold text-white">Password Updated Successfully</p>
            <p className="mt-1 text-xs text-slate-400">
              Your password has been securely updated. You can now sign in with your new credentials.
            </p>
            <Link
              href="/login"
              className="mt-6 inline-flex items-center gap-1.5 rounded-xl bg-sky-500 px-6 py-2.5 text-xs font-bold text-slate-950 shadow-lg hover:bg-sky-400 transition"
            >
              <ArrowLeft className="h-4 w-4" />
              <span>Go to Sign In</span>
            </Link>
          </div>
        ) : (
          <form onSubmit={handleSubmit} className="mt-6 space-y-4">
            <div>
              <label className="text-xs font-semibold uppercase tracking-wider text-slate-400">
                Master Security Code
              </label>
              <div className="mt-1.5 flex items-center rounded-xl border border-amber-500/40 bg-[#151F32] px-3.5 py-2.5">
                <ShieldCheck className="h-4 w-4 text-amber-400" />
                <input
                  type="password"
                  required
                  value={masterCode}
                  onChange={(e) => setMasterCode(e.target.value)}
                  className="ml-2.5 w-full bg-transparent text-sm text-white placeholder-slate-500 focus:outline-none"
                  placeholder="Enter Master Code"
                />
              </div>
            </div>

            <div>
              <label className="text-xs font-semibold uppercase tracking-wider text-slate-400">
                New Password
              </label>
              <div className="mt-1.5 flex items-center rounded-xl border border-[#26354F] bg-[#151F32] px-3.5 py-2.5">
                <KeyRound className="h-4 w-4 text-slate-400" />
                <input
                  type="password"
                  required
                  value={newPassword}
                  onChange={(e) => setNewPassword(e.target.value)}
                  className="ml-2.5 w-full bg-transparent text-sm text-white placeholder-slate-500 focus:outline-none"
                  placeholder="Enter new password"
                />
              </div>
            </div>

            <div>
              <label className="text-xs font-semibold uppercase tracking-wider text-slate-400">
                Confirm New Password
              </label>
              <div className="mt-1.5 flex items-center rounded-xl border border-[#26354F] bg-[#151F32] px-3.5 py-2.5">
                <KeyRound className="h-4 w-4 text-slate-400" />
                <input
                  type="password"
                  required
                  value={confirmPassword}
                  onChange={(e) => setConfirmPassword(e.target.value)}
                  className="ml-2.5 w-full bg-transparent text-sm text-white placeholder-slate-500 focus:outline-none"
                  placeholder="Confirm new password"
                />
              </div>
            </div>

            <button
              type="submit"
              disabled={isLoading}
              className="flex w-full items-center justify-center gap-2 rounded-xl bg-sky-500 py-3 text-sm font-bold text-slate-950 shadow-[0_0_15px_rgba(14,165,233,0.4)] transition hover:bg-sky-400 disabled:opacity-50"
            >
              <span>{isLoading ? "Validating Code..." : "Authorize & Reset Password"}</span>
            </button>

            <div className="pt-2 text-center">
              <Link href="/login" className="inline-flex items-center gap-1 text-xs text-slate-400 hover:text-white">
                <ArrowLeft className="h-3.5 w-3.5" />
                <span>Back to Sign In</span>
              </Link>
            </div>
          </form>
        )}
      </div>
    </div>
  );
}
