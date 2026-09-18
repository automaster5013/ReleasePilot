import type { MetadataRoute } from "next";

export default function sitemap(): MetadataRoute.Sitemap {
  return [
    {
      url: "https://releasepilot.kr/showcase",
      changeFrequency: "monthly",
      priority: 1,
    },
  ];
}
