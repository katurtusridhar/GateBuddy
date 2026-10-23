import type { Config } from "tailwindcss";

const config: Config = {
  darkMode: "class",
  content: [
    "./src/pages/**/*.{js,ts,jsx,tsx,mdx}",
    "./src/components/**/*.{js,ts,jsx,tsx,mdx}",
    "./src/app/**/*.{js,ts,jsx,tsx,mdx}",
  ],
  theme: {
    extend: {
      colors: {
        background: "var(--background)",
        foreground: "var(--foreground)",
        gate: {
          dark: "#090D16",
          surface: "#0F172A",
          card: "#151F32",
          cardElevated: "#1E2B45",
          border: "#26354F",
          sky: "#0EA5E9",
          skyContainer: "#082F49",
          emerald: "#10B981",
          emeraldContainer: "#064E3B",
          amber: "#F59E0B",
          amberContainer: "#451A03",
          rose: "#F43F5E",
          roseContainer: "#4C0519",
          indigo: "#6366F1",
          indigoContainer: "#1E1B4B",
          purple: "#8B5CF6",
          purpleContainer: "#2E1065",
        },
      },
      fontFamily: {
        sans: ["var(--font-jakarta)", "-apple-system", "BlinkMacSystemFont", "Segoe UI", "Roboto", "sans-serif"],
      },
      boxShadow: {
        glowSky: "0 0 25px -5px rgba(14, 165, 233, 0.35)",
        glowEmerald: "0 0 25px -5px rgba(16, 185, 129, 0.35)",
        glowAmber: "0 0 25px -5px rgba(245, 158, 11, 0.35)",
        glowRose: "0 0 25px -5px rgba(244, 63, 94, 0.35)",
        glowIndigo: "0 0 25px -5px rgba(99, 102, 241, 0.35)",
      },
    },
  },
  plugins: [],
};
export default config;
