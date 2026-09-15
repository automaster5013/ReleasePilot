import type { Metadata } from "next";
import { Geist, Geist_Mono } from "next/font/google";
import type { ReactNode } from "react";
import { connection } from "next/server";
import "./globals.css";

const geistSans = Geist({
  variable: "--font-geist-sans",
  subsets: ["latin"],
});

const geistMono = Geist_Mono({
  variable: "--font-geist-mono",
  subsets: ["latin"],
});

export const metadata: Metadata = {
  title: "ReleasePilot — Safe delivery control plane",
  description: "승인, Canary 운영 지표 검증, 자동 롤백과 감사 기록을 연결하는 DevOps 운영 플랫폼",
};

export default async function RootLayout({ children }: Readonly<{ children: ReactNode }>) {
  // Request-specific CSP nonces must never be baked into a cached static shell.
  await connection();
  return (
    <html lang="ko" className={`${geistSans.variable} ${geistMono.variable}`}>
      <body>{children}</body>
    </html>
  );
}
