"use client";

import React, { useState } from "react";
import { AppShell } from "@/components/layout/AppShell";
import { useApp } from "@/lib/store";
import { CyberCard } from "@/components/ui/CyberCard";
import { Save, RefreshCw, CheckCircle2, Shield, KeyRound, AlertTriangle, X, LogOut } from "lucide-react";

export default function SettingsPage() {
  const { settings, updateSettings, credentials, changeCredentials, syncStatus, syncWithCloud, logout } = useApp();

  const [examName, setExamName] = useState(settings.targetExamName);
  const [examDate, setExamDate] = useState(settings.targetExamDate || "2028-02-12");
  const [dailyLimit, setDailyLimit] = useState(settings.dailyRecoveryLimit.toString());
  const [themeMode] = useState(settings.themeMode);
  const [savedSuccess, setSavedSuccess] = useState(false);

  // Security Credentials Modal State
  const [showSecurityModal, setShowSecurityModal] = useState(false);
  const [masterCodeInput, setMasterCodeInput] = useState("");
  const [newUsernameInput, setNewUsernameInput] = useState(credentials.username);
  const [newPasswordInput, setNewPasswordInput] = useState("");
  const [confirmPasswordInput, setConfirmPasswordInput] = useState("");
  const [securityError, setSecurityError] = useState<string | null>(null);
  const [securitySuccess, setSecuritySuccess] = useState(false);
  const [isUpdatingCreds, setIsUpdatingCreds] = useState(false);

  const handleSave = async (e: React.FormEvent) => {
    e.preventDefault();
    await updateSettings({
      targetExamName: examName,
      targetExamDate: examDate,
      dailyRecoveryLimit: parseInt(dailyLimit, 10) || 2,
      themeMode: themeMode as any,
    });
    setSavedSuccess(true);
    setTimeout(() => setSavedSuccess(false), 2500);
  };

  const handleModifyCredentials = async (e: React.FormEvent) => {
    e.preventDefault();
    setSecurityError(null);

    if (newPasswordInput && newPasswordInput !== confirmPasswordInput) {
      setSecurityError("New password and confirm password do not match.");
      return;
    }

    setIsUpdatingCreds(true);
    const res = await changeCredentials(
      newUsernameInput,
      newPasswordInput || credentials.password || "",
      masterCodeInput
    );

    if (res.success) {
      setSecuritySuccess(true);
      setTimeout(() => {
        setSecuritySuccess(false);
        setShowSecurityModal(false);
        setMasterCodeInput("");
        setNewPasswordInput("");
        setConfirmPasswordInput("");
      }, 1500);
    } else {
      setSecurityError(res.error || "Failed to update credentials.");
    }
    setIsUpdatingCreds(false);
  };

  return (
    <AppShell>
      <div className="mx-auto max-w-4xl space-y-6">
        <div>
          <h1 className="text-2xl font-black text-white">System Settings &amp; Security</h1>
          <p className="text-xs text-slate-400">Configure exam countdown, study rules, and master-protected account credentials</p>
        </div>

        {savedSuccess && (
          <div className="flex items-center gap-2 rounded-xl border border-emerald-500/40 bg-emerald-500/10 p-3 text-xs text-emerald-300">
            <CheckCircle2 className="h-4 w-4 text-emerald-400 shrink-0" />
            <span>Settings saved successfully and synced with cloud!</span>
          </div>
        )}

        {/* Master Code Security & Credentials Section */}
        <CyberCard glow="amber">
          <div className="flex items-start justify-between gap-4">
            <div>
              <div className="flex items-center gap-2">
                <Shield className="h-4 w-4 text-amber-400" />
                <h3 className="text-sm font-bold text-white">Security &amp; Account Credentials</h3>
                <span className="rounded-md border border-amber-500/40 bg-amber-500/15 px-2 py-0.5 text-[10px] font-bold text-amber-400">
                  MASTER CODE PROTECTED
                </span>
              </div>
              <p className="mt-1 text-xs text-slate-400">
                Modification of username or password strictly requires the authorized Master Security Code.
              </p>
            </div>

            <button
              type="button"
              onClick={() => {
                setSecurityError(null);
                setNewUsernameInput(credentials.username);
                setShowSecurityModal(true);
              }}
              className="flex items-center gap-1.5 rounded-xl border border-amber-500/50 bg-amber-500/20 px-3.5 py-2 text-xs font-bold text-amber-300 transition hover:bg-amber-500/30 hover:text-white shrink-0"
            >
              <KeyRound className="h-4 w-4" />
              <span>Modify Credentials</span>
            </button>
          </div>

          <div className="mt-4 grid grid-cols-1 sm:grid-cols-2 gap-3 text-xs">
            <div className="rounded-xl border border-[#26354F] bg-[#151F32] p-3">
              <span className="text-slate-400 font-medium">Current Username</span>
              <p className="mt-0.5 font-bold text-white">{credentials.username}</p>
            </div>
            <div className="rounded-xl border border-[#26354F] bg-[#151F32] p-3">
              <span className="text-slate-400 font-medium">Current Password</span>
              <p className="mt-0.5 font-bold text-slate-400 tracking-widest">••••••••••••</p>
            </div>
          </div>
        </CyberCard>

        <form onSubmit={handleSave} className="space-y-6">
          {/* Target Exam */}
          <CyberCard glow="sky">
            <h3 className="text-sm font-bold text-white">Target Exam Configuration</h3>
            <p className="text-xs text-slate-400">Controls the countdown widget and syllabus pacing</p>

            <div className="mt-4 grid grid-cols-1 gap-4 sm:grid-cols-2">
              <div>
                <label className="text-xs font-semibold text-slate-400">Exam Title</label>
                <input
                  type="text"
                  value={examName}
                  onChange={(e) => setExamName(e.target.value)}
                  className="mt-1 w-full rounded-xl border border-[#26354F] bg-[#151F32] px-3.5 py-2 text-xs text-white focus:border-sky-400 focus:outline-none"
                />
              </div>

              <div>
                <label className="text-xs font-semibold text-slate-400">Exam Date (YYYY-MM-DD)</label>
                <input
                  type="text"
                  value={examDate}
                  onChange={(e) => setExamDate(e.target.value)}
                  className="mt-1 w-full rounded-xl border border-[#26354F] bg-[#151F32] px-3.5 py-2 text-xs text-white focus:border-sky-400 focus:outline-none"
                />
              </div>
            </div>
          </CyberCard>

          {/* Recovery Limit */}
          <CyberCard glow="none">
            <h3 className="text-sm font-bold text-white">Scheduling &amp; Recovery Rules</h3>
            <p className="text-xs text-slate-400">Controls maximum workload per recovery day</p>

            <div className="mt-4 max-w-xs">
              <label className="text-xs font-semibold text-slate-400">Max Recovery Lectures Per Day (Rule 1)</label>
              <input
                type="number"
                min="1"
                max="2"
                value={dailyLimit}
                onChange={(e) => setDailyLimit(e.target.value)}
                className="mt-1 w-full rounded-xl border border-[#26354F] bg-[#151F32] px-3.5 py-2 text-xs text-white focus:border-sky-400 focus:outline-none"
              />
              <p className="mt-1 text-[11px] text-slate-500">
                Rule 1 limits recovery to a maximum of 2 lectures per day to prevent burnout.
              </p>
            </div>
          </CyberCard>

          {/* Cross-Platform Synchronization */}
          <CyberCard glow="emerald">
            <div className="flex items-center justify-between">
              <div>
                <h3 className="text-sm font-bold text-white">Realtime Synchronization</h3>
                <p className="text-xs text-slate-400">Bidirectional realtime link between Android and Web</p>
              </div>

              <div className="flex items-center gap-2">
                <span
                  className={
                    syncStatus === "synced"
                      ? "rounded-md border border-emerald-500/50 bg-emerald-500/15 px-2.5 py-1 text-xs font-bold text-emerald-400"
                      : "rounded-md border border-amber-500/50 bg-amber-500/15 px-2.5 py-1 text-xs font-bold text-amber-400"
                  }
                >
                  {syncStatus === "synced" ? "CONNECTED & REALTIME" : "SYNCING"}
                </span>
                <button
                  type="button"
                  onClick={syncWithCloud}
                  className="flex items-center gap-1.5 rounded-xl border border-[#26354F] bg-[#151F32] px-3 py-1.5 text-xs font-semibold text-white hover:border-sky-400 transition"
                >
                  <RefreshCw className="h-3.5 w-3.5 text-sky-400" />
                  <span>Sync Now</span>
                </button>
              </div>
            </div>
          </CyberCard>

          {/* Account Session & Sign Out */}
          <CyberCard glow="rose">
            <div className="flex items-center justify-between">
              <div>
                <h3 className="text-sm font-bold text-white">Active Session</h3>
                <p className="text-xs text-slate-400">Signed in as {credentials.username}</p>
              </div>

              <button
                type="button"
                onClick={logout}
                className="flex items-center gap-1.5 rounded-xl border border-rose-500/40 bg-rose-950/30 px-4 py-2 text-xs font-bold text-rose-300 hover:bg-rose-900/40 transition"
              >
                <LogOut className="h-3.5 w-3.5 text-rose-400" />
                <span>Sign Out</span>
              </button>
            </div>
          </CyberCard>

          <div className="flex justify-end">
            <button
              type="submit"
              className="flex items-center gap-2 rounded-xl bg-sky-500 px-6 py-2.5 text-xs font-bold text-slate-950 shadow-[0_0_15px_rgba(14,165,233,0.3)] hover:bg-sky-400 transition"
            >
              <Save className="h-4 w-4" />
              <span>Save Settings</span>
            </button>
          </div>
        </form>

        {/* Master Code Security Modal */}
        {showSecurityModal && (
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 backdrop-blur-sm p-4">
            <div className="w-full max-w-md rounded-2xl border border-amber-500/40 bg-[#0F172A] p-6 shadow-2xl">
              <div className="flex items-center justify-between border-b border-[#26354F] pb-3">
                <div className="flex items-center gap-2">
                  <Shield className="h-5 w-5 text-amber-400" />
                  <h3 className="text-base font-bold text-white">Master Code Verification</h3>
                </div>
                <button
                  onClick={() => setShowSecurityModal(false)}
                  className="rounded-lg p-1 text-slate-400 hover:bg-[#1E2B45] hover:text-white"
                >
                  <X className="h-5 w-5" />
                </button>
              </div>

              {securityError && (
                <div className="mt-4 flex items-center gap-2 rounded-xl border border-rose-500/50 bg-rose-950/40 p-3 text-xs text-rose-300">
                  <AlertTriangle className="h-4 w-4 shrink-0 text-rose-400" />
                  <span>{securityError}</span>
                </div>
              )}

              {securitySuccess && (
                <div className="mt-4 flex items-center gap-2 rounded-xl border border-emerald-500/50 bg-emerald-950/40 p-3 text-xs text-emerald-300">
                  <CheckCircle2 className="h-4 w-4 shrink-0 text-emerald-400" />
                  <span>Credentials updated successfully!</span>
                </div>
              )}

              <form onSubmit={handleModifyCredentials} className="mt-4 space-y-4">
                {/* Master Security Code Input */}
                <div>
                  <label className="text-xs font-bold uppercase tracking-wider text-amber-300">
                    Master Security Code (Required)
                  </label>
                  <input
                    type="password"
                    required
                    value={masterCodeInput}
                    onChange={(e) => setMasterCodeInput(e.target.value)}
                    placeholder="Enter Master Security Code"
                    className="mt-1.5 w-full rounded-xl border border-amber-500/50 bg-[#151F32] px-3.5 py-2.5 text-xs text-white placeholder-slate-500 focus:border-amber-400 focus:outline-none"
                  />
                  <p className="mt-1 text-[11px] text-slate-400">
                    Enter the Master Security Code configured for this ecosystem to authorize credential modifications.
                  </p>
                </div>

                {/* New Username Input */}
                <div>
                  <label className="text-xs font-semibold text-slate-300">New Username</label>
                  <input
                    type="text"
                    required
                    value={newUsernameInput}
                    onChange={(e) => setNewUsernameInput(e.target.value)}
                    className="mt-1 w-full rounded-xl border border-[#26354F] bg-[#151F32] px-3.5 py-2 text-xs text-white focus:border-sky-400 focus:outline-none"
                  />
                </div>

                {/* New Password Input */}
                <div>
                  <label className="text-xs font-semibold text-slate-300">New Password</label>
                  <input
                    type="password"
                    value={newPasswordInput}
                    onChange={(e) => setNewPasswordInput(e.target.value)}
                    placeholder="Leave blank to keep current"
                    className="mt-1 w-full rounded-xl border border-[#26354F] bg-[#151F32] px-3.5 py-2 text-xs text-white placeholder-slate-500 focus:border-sky-400 focus:outline-none"
                  />
                </div>

                {/* Confirm New Password Input */}
                {newPasswordInput && (
                  <div>
                    <label className="text-xs font-semibold text-slate-300">Confirm New Password</label>
                    <input
                      type="password"
                      value={confirmPasswordInput}
                      onChange={(e) => setConfirmPasswordInput(e.target.value)}
                      placeholder="Repeat new password"
                      className="mt-1 w-full rounded-xl border border-[#26354F] bg-[#151F32] px-3.5 py-2 text-xs text-white placeholder-slate-500 focus:border-sky-400 focus:outline-none"
                    />
                  </div>
                )}

                <div className="flex justify-end gap-2 pt-2">
                  <button
                    type="button"
                    onClick={() => setShowSecurityModal(false)}
                    className="rounded-xl px-4 py-2 text-xs font-semibold text-slate-400 hover:text-white"
                  >
                    Cancel
                  </button>
                  <button
                    type="submit"
                    disabled={isUpdatingCreds}
                    className="rounded-xl bg-amber-500 px-5 py-2 text-xs font-bold text-slate-950 shadow-[0_0_12px_rgba(245,158,11,0.3)] hover:bg-amber-400 disabled:opacity-50 transition"
                  >
                    {isUpdatingCreds ? "Authorizing..." : "Authorize & Update"}
                  </button>
                </div>
              </form>
            </div>
          </div>
        )}
      </div>
    </AppShell>
  );
}
