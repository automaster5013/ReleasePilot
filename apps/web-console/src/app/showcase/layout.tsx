import type { Metadata } from "next";
import type { ReactNode } from "react";

const showcaseUrl = "https://releasepilot.kr/showcase";

export const metadata: Metadata = {
  title: "Progressive Delivery 시뮬레이터 | ReleasePilot",
  description: "승인 검토, Canary 트래픽 제어, 메트릭 기반 자동 롤백을 직접 체험하는 ReleasePilot Progressive Delivery 시뮬레이터",
  alternates: { canonical: showcaseUrl },
  robots: {
    index: true,
    follow: true,
    googleBot: { index: true, follow: true },
  },
  openGraph: {
    type: "website",
    locale: "ko_KR",
    url: showcaseUrl,
    siteName: "ReleasePilot",
    title: "ReleasePilot Progressive Delivery 시뮬레이터",
    description: "배포 승인부터 점진적 트래픽 전환과 자동 롤백까지 하나의 안전한 흐름으로 확인하세요.",
    images: [{ url: `${showcaseUrl}/opengraph-image`, width: 1200, height: 630, alt: "ReleasePilot — Progressive Delivery Control Plane" }],
  },
  twitter: {
    card: "summary_large_image",
    title: "ReleasePilot Progressive Delivery 시뮬레이터",
    description: "승인 검토, Canary 트래픽 제어, 자동 롤백을 직접 체험하세요.",
    images: [`${showcaseUrl}/opengraph-image`],
  },
};

const structuredData = {
  "@context": "https://schema.org",
  "@type": "SoftwareApplication",
  name: "ReleasePilot",
  applicationCategory: "DeveloperApplication",
  applicationSubCategory: "Progressive Delivery Control Plane",
  operatingSystem: "Web",
  url: showcaseUrl,
  description: "배포 승인, Canary 트래픽 제어, 메트릭 기반 자동 롤백과 감사 기록을 통합하는 Progressive Delivery 플랫폼",
  featureList: ["배포 승인 검토", "점진적 Canary 트래픽 제어", "메트릭 기반 자동 롤백", "감사 가능한 릴리스 기록"],
};

export default function ShowcaseLayout({ children }: Readonly<{ children: ReactNode }>) {
  return (
    <>
      <script
        type="application/ld+json"
        dangerouslySetInnerHTML={{ __html: JSON.stringify(structuredData).replace(/</g, "\\u003c") }}
      />
      {children}
    </>
  );
}
