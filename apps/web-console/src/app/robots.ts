import type { MetadataRoute } from "next";

export default function robots(): MetadataRoute.Robots {
  return {
    rules: { userAgent: "*", allow: ["/showcase", "/robots.txt", "/sitemap.xml"], disallow: ["/control-api/"] },
    sitemap: "https://releasepilot.kr/sitemap.xml",
  };
}
