import type { Metadata, Viewport } from "next";
import { Plus_Jakarta_Sans } from "next/font/google";
import { AppProvider } from "@/lib/store";
import "./globals.css";

const jakarta = Plus_Jakarta_Sans({
  subsets: ["latin"],
  weight: ["300", "400", "500", "600", "700", "800"],
  variable: "--font-jakarta",
  display: "swap",
});

export const metadata: Metadata = {
  title: "GateBuddy — GATE CSE Preparation Command Center",
  description: "Mission-critical preparation command center for GATE CSE aspirants with real-time Android synchronization.",
  icons: {
    icon: "/cropped_circle_image.png",
    shortcut: "/cropped_circle_image.png",
    apple: "/cropped_circle_image.png",
  },
};

export const viewport: Viewport = {
  themeColor: "#090D16",
  width: "device-width",
  initialScale: 1,
  maximumScale: 1,
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en" className={`dark ${jakarta.variable}`}>
      <body className="min-h-screen bg-gate-dark font-sans text-slate-100 antialiased selection:bg-indigo-500/30 selection:text-white">
        <AppProvider>{children}</AppProvider>
      </body>
    </html>
  );
}
