import type { Metadata } from "next";
import type { ReactNode } from "react";

const showcaseUrl = "https://releasepilot.kr/showcase";

export const metadata: Metadata = {
  title: "Progressive Delivery 시뮬레이터 | ReleasePilot",
  description: "승인 검토, Canary 트래픽 제어, 메트릭 기반 자동 롤백을 직접 체험하는 ReleasePilot Progressive Delivery 시뮬레이터",
  alternates: { canonical: showcaseUrl },
  openGraph: {
    type: "website",
    locale: "ko_KR",
    url: showcaseUrl,
    siteName: "ReleasePilot",
    title: "ReleasePilot Progressive Delivery 시뮬레이터",
    description: "배포 승인부터 점진적 트래픽 전환과 자동 롤백까지 하나의 안전한 흐름으로 확인하세요.",
  },
  twitter: {
    card: "summary",
    title: "ReleasePilot Progressive Delivery 시뮬레이터",
    description: "승인 검토, Canary 트래픽 제어, 자동 롤백을 직접 체험하세요.",
  },
};

export default function ShowcaseLayout({ children }: Readonly<{ children: ReactNode }>) {
  return children;
}
