"use client";

import React, { useState } from "react";
import Link from "next/link";
import Image from "next/image";
import { useApp } from "@/lib/store";
import { ArrowLeft, CheckCircle2, ShieldCheck, KeyRound, AlertCircle, User, Lock, Eye, EyeOff } from "lucide-react";

export default function ForgotPasswordPage() {
  const { changeCredentials, credentials } = useApp();

  // Step 1: Master Code verification
  const [masterCode, setMasterCode] = useState("");
  const [isMasterCodeVerified, setIsMasterCodeVerified] = useState(false);

  // Step 2: Reset Form
  const [usernameInput, setUsernameInput] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);

  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState(false);
  const [isLoading, setIsLoading] = useState(false);

  // Verify Master Code
  const handleVerifyMasterCode = (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    const validMasterCode = (credentials.masterCode || "oA-THL,UXyh6").trim();
    if (masterCode.trim() !== validMasterCode) {
      setError("Invalid Master Authorization Code. Access denied.");
      return;
    }

    // Success: reveal current username and unlock reset form
    setIsMasterCodeVerified(true);
    setUsernameInput(credentials.username || "katurtusridhar");
  };

  // Submit Password / Username Update
  const handleResetSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    if (!newPassword.trim()) {
      setError("Please enter a new password.");
      return;
    }

    if (newPassword !== confirmPassword) {
      setError("New password and confirm password do not match.");
      return;
    }

    setIsLoading(true);
    const res = await changeCredentials(
      usernameInput.trim() || credentials.username,
      newPassword.trim(),
      masterCode.trim()
    );

    if (res.success) {
      setSuccess(true);
    } else {
      setError(res.error || "Failed to update credentials.");
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
          <h2 className="mt-4 text-2xl font-black text-white">Account Recovery</h2>
          <p className="mt-1 text-xs text-slate-400">
            Protected with Master Security Code • IIT Bombay 2028
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
            <p className="mt-3 text-sm font-bold text-white">Credentials Updated Successfully</p>
            <p className="mt-1 text-xs text-slate-400">
              Your username and password have been securely updated. You can now sign in with your new credentials.
            </p>
            <Link
              href="/login"
              className="mt-6 inline-flex items-center gap-1.5 rounded-xl bg-sky-500 px-6 py-2.5 text-xs font-bold text-slate-950 shadow-lg hover:bg-sky-400 transition"
            >
              <ArrowLeft className="h-4 w-4" />
              <span>Go to Sign In</span>
            </Link>
          </div>
        ) : !isMasterCodeVerified ? (
          /* Step 1: Master Code Entry */
          <form onSubmit={handleVerifyMasterCode} className="mt-6 space-y-4">
            <div>
              <label className="text-xs font-semibold uppercase tracking-wider text-amber-300">
                Master Authorization Code
              </label>
              <div className="mt-1.5 flex items-center rounded-xl border border-amber-500/40 bg-[#151F32] px-3.5 py-2.5 focus-within:border-amber-400 transition">
                <ShieldCheck className="h-4 w-4 text-amber-400 shrink-0" />
                <input
                  type="password"
                  required
                  value={masterCode}
                  onChange={(e) => setMasterCode(e.target.value)}
                  className="ml-2.5 w-full bg-transparent text-sm text-white placeholder-slate-500 focus:outline-none"
                  placeholder="Enter Master Security Code"
                />
              </div>
              <p className="mt-1.5 text-[11px] text-slate-400">
                Enter your secret master code to reveal your username and reset your password.
              </p>
            </div>

            <button
              type="submit"
              className="flex w-full items-center justify-center gap-2 rounded-xl bg-amber-500 py-3 text-sm font-bold text-slate-950 shadow-[0_0_15px_rgba(245,158,11,0.3)] transition hover:bg-amber-400"
            >
              <KeyRound className="h-4 w-4" />
              <span>Verify Master Code</span>
            </button>

            <div className="pt-2 text-center">
              <Link href="/login" className="inline-flex items-center gap-1 text-xs text-slate-400 hover:text-white">
                <ArrowLeft className="h-3.5 w-3.5" />
                <span>Back to Sign In</span>
              </Link>
            </div>
          </form>
        ) : (
          /* Step 2: Master Code Verified -> Shows Username & Password Reset */
          <form onSubmit={handleResetSubmit} className="mt-6 space-y-4">
            <div className="rounded-xl border border-emerald-500/30 bg-emerald-950/30 p-3 text-xs text-emerald-300">
              <span className="font-semibold">Master Code Verified!</span>
              <p className="mt-1 text-slate-300">
                Your current username is: <strong className="text-white font-mono text-sm">{credentials.username || "katurtusridhar"}</strong>
              </p>
            </div>

            <div>
              <label className="text-xs font-semibold uppercase tracking-wider text-slate-400">
                Username (Edit or Keep)
              </label>
              <div className="mt-1.5 flex items-center rounded-xl border border-[#26354F] bg-[#151F32] px-3.5 py-2.5">
                <User className="h-4 w-4 text-slate-400 shrink-0" />
                <input
                  type="text"
                  required
                  value={usernameInput}
                  onChange={(e) => setUsernameInput(e.target.value)}
                  className="ml-2.5 w-full bg-transparent text-sm text-white placeholder-slate-500 focus:outline-none"
                  placeholder="Username"
                />
              </div>
            </div>

            <div>
              <label className="text-xs font-semibold uppercase tracking-wider text-slate-400">
                New Password
              </label>
              <div className="mt-1.5 flex items-center rounded-xl border border-[#26354F] bg-[#151F32] px-3.5 py-2.5">
                <Lock className="h-4 w-4 text-slate-400 shrink-0" />
                <input
                  type={showPassword ? "text" : "password"}
                  required
                  value={newPassword}
                  onChange={(e) => setNewPassword(e.target.value)}
                  className="ml-2.5 w-full bg-transparent text-sm text-white placeholder-slate-500 focus:outline-none"
                  placeholder="Enter new password"
                />
                <button
                  type="button"
                  onClick={() => setShowPassword(!showPassword)}
                  className="text-slate-400 hover:text-white"
                >
                  {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                </button>
              </div>
            </div>

            <div>
              <label className="text-xs font-semibold uppercase tracking-wider text-slate-400">
                Confirm New Password
              </label>
              <div className="mt-1.5 flex items-center rounded-xl border border-[#26354F] bg-[#151F32] px-3.5 py-2.5">
                <Lock className="h-4 w-4 text-slate-400 shrink-0" />
                <input
                  type={showPassword ? "text" : "password"}
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
              <span>{isLoading ? "Saving..." : "Save & Update Credentials"}</span>
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
