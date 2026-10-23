import React from "react";
import clsx from "clsx";

interface CyberCardProps extends React.HTMLAttributes<HTMLDivElement> {
  children: React.ReactNode;
  glow?: "sky" | "emerald" | "amber" | "rose" | "purple" | "none";
  className?: string;
}

export function CyberCard({ children, glow = "none", className, ...props }: CyberCardProps) {
  const glowStyles = {
    sky: "hover:border-gate-sky/50 hover:shadow-glowSky",
    emerald: "hover:border-gate-emerald/50 hover:shadow-glowEmerald",
    amber: "hover:border-gate-amber/50 hover:shadow-glowAmber",
    rose: "hover:border-gate-rose/50 hover:shadow-glowRose",
    purple: "hover:border-gate-purple/50 hover:shadow-glowPurple",
    none: "hover:border-slate-600",
  }[glow];

  return (
    <div
      className={clsx(
        "relative rounded-2xl border border-slate-800 bg-gradient-to-b from-gate-card to-gate-cardElevated/80 p-5 shadow-xl transition-all duration-300 backdrop-blur-md",
        glowStyles,
        className
      )}
      {...props}
    >
      {children}
    </div>
  );
}
