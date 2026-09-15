export type SessionUser = {
  id: string;
  displayName: string;
  roles: string[];
  demo: boolean;
};

export type ActiveSession = {
  reference: string;
  current: boolean;
  createdAt: string;
  lastAccessedAt: string;
  expiresAt: string;
};

export function canManageSessions(user: SessionUser | null) {
  return user !== null && !user.demo;
}

export function sessionConnectionLabel(user: SessionUser | null, streamStatus: string, hasRelease = false): string {
  if (!user) return "DEMO SNAPSHOT";
  if (hasRelease && ["LIVE", "RECONNECTING"].includes(streamStatus)) return streamStatus;
  return user.demo ? "DEMO · VIEW ONLY" : "SIGNED IN";
}

export function formatSessionTime(value: string, locale = "ko-KR") {
  const time = new Date(value);
  if (Number.isNaN(time.getTime())) return "알 수 없음";
  return new Intl.DateTimeFormat(locale, {
    year: "numeric", month: "2-digit", day: "2-digit",
    hour: "2-digit", minute: "2-digit",
  }).format(time);
}
