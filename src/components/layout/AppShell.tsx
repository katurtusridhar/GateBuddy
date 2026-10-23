"use client";

import React, { useState } from "react";
import Link from "next/link";
import Image from "next/image";
import { usePathname } from "next/navigation";
import { useApp } from "@/lib/store";
import { formatDisplayDate, daysBetween } from "@/lib/dateUtils";
import {
  CalendarDays,
  Calendar,
  BookOpen,
  RotateCcw,
  BarChart3,
  ClipboardList,
  Award,
  Settings,
  RefreshCw,
  Clock,
} from "lucide-react";
import clsx from "clsx";

interface AppShellProps {
  children: React.ReactNode;
}

export function AppShell({ children }: AppShellProps) {
  const pathname = usePathname();
  const { user, syncStatus, syncWithCloud, todayDateStr, settings, activeBacklogs } = useApp();
  const [isSyncingManual, setIsSyncingManual] = useState(false);

  const daysToGate = settings.targetExamDate ? daysBetween(todayDateStr, settings.targetExamDate) : 0;

  const desktopNavItems = [
    { label: "Today", href: "/dashboard", icon: CalendarDays },
    { label: "Calendar", href: "/calendar", icon: Calendar },
    { label: "Subjects", href: "/subjects", icon: BookOpen },
    {
      label: "Recovery Center",
      href: "/recovery",
      icon: RotateCcw,
      badge: activeBacklogs.length > 0 ? `${activeBacklogs.length}` : null,
    },
    { label: "Analytics", href: "/analytics", icon: BarChart3 },
    { label: "Tests & PYQ", href: "/tests", icon: ClipboardList },
    { label: "Achievements", href: "/achievements", icon: Award },
    { label: "Settings", href: "/settings", icon: Settings },
  ];

  // Mobile Bottom Navigation (Matching Android app's 6 bottom tabs)
  const mobileBottomNav = [
    { label: "Today", href: "/dashboard", icon: CalendarDays },
    { label: "Calendar", href: "/calendar", icon: Calendar },
    { label: "Subjects", href: "/subjects", icon: BookOpen },
    { label: "Analytics", href: "/analytics", icon: BarChart3 },
    { label: "Tests", href: "/tests", icon: ClipboardList },
    { label: "Settings", href: "/settings", icon: Settings },
  ];

  const handleManualSync = async () => {
    setIsSyncingManual(true);
    await syncWithCloud();
    setTimeout(() => setIsSyncingManual(false), 500);
  };

  return (
    <div className="flex min-h-screen bg-[#090D16] text-slate-100 font-sans pb-20 lg:pb-0">
      {/* Desktop Sidebar */}
      <aside className="hidden w-64 shrink-0 flex-col border-r border-[#26354F] bg-[#0F172A] p-4 lg:flex">
        {/* Brand Header */}
        <div className="flex items-center gap-3 border-b border-[#26354F]/80 pb-4">
          <div className="relative h-11 w-11 shrink-0 overflow-hidden rounded-xl border border-sky-400/40 bg-[#090D16] p-0.5 shadow-[0_0_15px_rgba(14,165,233,0.3)]">
            <Image
              src="/cropped_circle_image.png"
              alt="GateBuddy IIT Bombay Logo"
              width={44}
              height={44}
              className="h-full w-full object-contain"
              priority
            />
          </div>
          <div>
            <h1 className="text-base font-extrabold tracking-tight text-white flex items-center gap-1.5">
              <span>GateBuddy</span>
            </h1>
            <p className="text-[10px] font-bold tracking-widest text-sky-400 uppercase">
              Mission IIT Bombay
            </p>
          </div>
        </div>

        {/* User Card */}
        <div className="mt-4 rounded-xl border border-[#26354F] bg-[#151F32] p-3">
          <div className="flex items-center justify-between">
            <span className="text-xs font-bold text-white">{user?.name || "Sridhar"}</span>
            <div className="flex items-center gap-1.5">
              <span
                className={clsx(
                  "h-2 w-2 rounded-full",
                  syncStatus === "synced"
                    ? "bg-emerald-400 shadow-[0_0_8px_#34d399]"
                    : syncStatus === "syncing"
                    ? "animate-pulse bg-amber-400"
                    : "bg-rose-400"
                )}
              />
              <span className="text-[10px] font-medium text-slate-400 capitalize">
                {syncStatus === "synced" ? "Realtime" : syncStatus}
              </span>
            </div>
          </div>
          <p className="mt-0.5 text-[11px] text-slate-400">Target: AIR &lt; 100 • CSE</p>
        </div>

        {/* Navigation */}
        <nav className="mt-5 flex-1 space-y-1">
          {desktopNavItems.map((item) => {
            const Icon = item.icon;
            const isActive =
              pathname === item.href || (item.href !== "/dashboard" && pathname.startsWith(item.href));
            return (
              <Link
                key={item.href}
                href={item.href}
                className={clsx(
                  "flex items-center justify-between rounded-xl px-3.5 py-2.5 text-xs font-semibold transition-all duration-150",
                  isActive
                    ? "border border-sky-500/50 bg-[#1E2B45] text-sky-300 shadow-[0_0_15px_rgba(14,165,233,0.2)]"
                    : "text-slate-400 hover:border-[#26354F] hover:bg-[#151F32] hover:text-white"
                )}
              >
                <div className="flex items-center gap-3">
                  <Icon className={clsx("h-4 w-4", isActive ? "text-sky-400" : "text-slate-400")} />
                  <span>{item.label}</span>
                </div>
                {item.badge && (
                  <span className="rounded-full bg-rose-500/20 px-2 py-0.5 text-[10px] font-bold text-rose-400">
                    {item.badge}
                  </span>
                )}
              </Link>
            );
          })}
        </nav>

        {/* Countdown Pill */}
        <div className="mt-auto border-t border-[#26354F] pt-4">
          <div className="rounded-xl border border-sky-500/30 bg-sky-950/30 p-3 text-center">
            <div className="flex items-center justify-center gap-1.5 text-xs font-bold text-sky-400">
              <Clock className="h-4 w-4" />
              <span>{settings.targetExamName}</span>
            </div>
            <p className="mt-1 text-2xl font-black text-white">{daysToGate}</p>
            <p className="text-[10px] font-semibold text-slate-400">Days Remaining</p>
          </div>
        </div>
      </aside>

      {/* Main Content Area */}
      <div className="flex flex-1 flex-col overflow-hidden">
        {/* Top Navbar */}
        <header className="flex h-16 shrink-0 items-center justify-between border-b border-[#26354F] bg-[#0F172A]/90 px-4 backdrop-blur-md lg:px-8">
          <div className="flex items-center gap-3">
            {/* Mobile Brand Logo */}
            <div className="relative h-9 w-9 shrink-0 overflow-hidden rounded-xl border border-sky-400/40 bg-[#090D16] p-0.5 lg:hidden">
              <Image
                src="/cropped_circle_image.png"
                alt="GateBuddy"
                width={36}
                height={36}
                className="h-full w-full object-contain"
              />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <span className="text-[11px] font-bold tracking-wider text-sky-400 uppercase">
                  IST
                </span>
                <span className="text-[11px] font-medium text-slate-400">• Asia/Kolkata</span>
              </div>
              <p className="text-sm font-bold text-white">{formatDisplayDate(todayDateStr)}</p>
            </div>
          </div>

          <div className="flex items-center gap-3">
            {/* Realtime Status Indicator */}
            <div className="hidden sm:flex items-center gap-2 rounded-xl border border-[#26354F] bg-[#151F32] px-3 py-1.5 text-xs">
              <span
                className={clsx(
                  "h-2 w-2 rounded-full",
                  syncStatus === "synced"
                    ? "bg-emerald-400 shadow-[0_0_8px_#34d399]"
                    : "animate-pulse bg-amber-400"
                )}
              />
              <span className="font-semibold text-slate-300">
                {syncStatus === "synced" ? "Realtime Synced" : "Syncing..."}
              </span>
            </div>

            {/* Quick Sync Button */}
            <button
              onClick={handleManualSync}
              disabled={isSyncingManual}
              className="flex items-center gap-1.5 rounded-xl border border-[#26354F] bg-[#151F32] px-3 py-1.5 text-xs font-semibold text-slate-300 transition hover:border-sky-400 hover:text-white"
            >
              <RefreshCw className={clsx("h-3.5 w-3.5", isSyncingManual && "animate-spin text-sky-400")} />
              <span className="hidden md:inline">Sync</span>
            </button>

            {/* User Profile Avatar */}
            <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-gradient-to-tr from-sky-400 to-indigo-500 text-xs font-black text-slate-950 shadow-[0_0_12px_rgba(14,165,233,0.3)]">
              {(user?.name || "S")[0].toUpperCase()}
            </div>
          </div>
        </header>

        {/* Page Content */}
        <main className="flex-1 overflow-y-auto p-4 md:p-6 lg:p-8">{children}</main>
      </div>

      {/* Mobile Bottom Navigation Bar (Matching Android app's 6 icons) */}
      <nav className="fixed bottom-0 left-0 right-0 z-40 flex items-center justify-around border-t border-[#26354F] bg-[#0F172A]/95 backdrop-blur-lg py-2 px-1 lg:hidden">
        {mobileBottomNav.map((item) => {
          const Icon = item.icon;
          const isActive =
            pathname === item.href || (item.href !== "/dashboard" && pathname.startsWith(item.href));
          return (
            <Link
              key={item.href}
              href={item.href}
              className={clsx(
                "flex flex-col items-center justify-center rounded-xl px-2.5 py-1 text-[11px] font-semibold transition-all duration-150",
                isActive ? "text-sky-400" : "text-slate-400 hover:text-white"
              )}
            >
              <div
                className={clsx(
                  "flex h-8 w-12 items-center justify-center rounded-xl transition-all",
                  isActive ? "bg-sky-500/20 text-sky-300 border border-sky-500/30" : ""
                )}
              >
                <Icon className="h-5 w-5" />
              </div>
              <span className="mt-0.5 tracking-tight">{item.label}</span>
            </Link>
          );
        })}
      </nav>
    </div>
  );
}
