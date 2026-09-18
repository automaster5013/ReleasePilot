import { expect, test } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";

test("showcase explains and completes the approval-to-canary flow", async ({ page }) => {
  await page.goto("/showcase");
  await expect(page.getByRole("heading", { name: /안전한 결정을 운영하는 플랫폼/ })).toBeVisible();
  await expect(page.getByText("승인 검토", { exact: true })).toBeVisible();
  await expect(page.getByText("트래픽 제어", { exact: true })).toBeVisible();
  await expect(page.getByText("자동 롤백", { exact: true })).toBeVisible();
  await expect(page.getByRole("table", { name: "릴리스 일정 관리 도구와 ReleasePilot 비교" })).toBeVisible();
  await expect(page.getByText("지금 더 많은 트래픽을 보내도 안전한가?", { exact: true })).toBeVisible();
  await expect(page.getByRole("heading", { name: "자주 묻는 질문", exact: true })).toBeVisible();
  await expect(page.getByText("승인 검토 중", { exact: true })).toBeVisible();

  await page.getByRole("button", { name: "배포 승인", exact: true }).click();
  await expect(page.getByText("배포 승인됨", { exact: true })).toBeVisible();
  await page.getByRole("button", { name: "점진적 배포 시작", exact: true }).click();
  await expect(page.getByText("점진적 배포 진행 중", { exact: true })).toBeVisible();
  await expect(page.getByText("10% traffic", { exact: true })).toBeVisible({ timeout: 3_000 });
});

test("control room exposes the public showcase and showcase metadata is canonical", async ({ page }) => {
  await page.goto("/");
  await expect(page.locator('meta[name="robots"]')).toHaveAttribute("content", /noindex/);
  const showcaseLink = page.getByRole("link", { name: "제품 둘러보기", exact: true });
  await expect(showcaseLink).toHaveAttribute("href", "/showcase");
  await showcaseLink.click();
  await expect(page).toHaveURL(/\/showcase$/);
  await expect(page).toHaveTitle("Progressive Delivery 시뮬레이터 | ReleasePilot");
  await expect(page.locator('link[rel="canonical"]')).toHaveAttribute("href", "https://releasepilot.kr/showcase");
  await expect(page.locator('meta[name="robots"]')).toHaveAttribute("content", /index/);
  await expect(page.locator('meta[name="robots"]')).not.toHaveAttribute("content", /noindex/);
  await expect(page.locator('meta[property="og:title"]')).toHaveAttribute("content", "ReleasePilot Progressive Delivery 시뮬레이터");
  await expect(page.locator('meta[property="og:image"]')).toHaveAttribute("content", /^https:\/\/releasepilot\.kr\/showcase\/opengraph-image(?:\?.+)?$/);
  await expect(page.locator('meta[property="og:image:width"]')).toHaveAttribute("content", "1200");
  await expect(page.locator('meta[property="og:image:height"]')).toHaveAttribute("content", "630");
  await expect(page.locator('meta[name="twitter:card"]')).toHaveAttribute("content", "summary_large_image");
  await expect(page.locator('meta[name="twitter:image"]')).toHaveAttribute("content", /^https:\/\/releasepilot\.kr\/showcase\/opengraph-image(?:\?.+)?$/);

  const structuredData = JSON.parse(await page.locator('script[type="application/ld+json"]').first().textContent() ?? "{}");
  expect(structuredData).toMatchObject({
    "@type": "SoftwareApplication",
    name: "ReleasePilot",
    applicationSubCategory: "Progressive Delivery Control Plane",
    url: "https://releasepilot.kr/showcase",
  });
  expect(structuredData.featureList).toContain("메트릭 기반 자동 롤백");
  const faqStructuredData = JSON.parse(await page.locator('script[type="application/ld+json"]').nth(1).textContent() ?? "{}");
  expect(faqStructuredData["@type"]).toBe("FAQPage");
  expect(faqStructuredData.mainEntity).toHaveLength(3);
  expect(faqStructuredData.mainEntity[0].name).toBe("ReleasePilot은 릴리스 일정 관리 도구인가요?");
});

test("public domain root leads with the showcase while the control room remains available", async ({ request, page }) => {
  const root = await request.get("/", { headers: { "X-Forwarded-Host": "releasepilot.kr" }, maxRedirects: 0 });
  expect(root.status()).toBe(307);
  expect(root.headers().location).toBe("https://releasepilot.kr/showcase");

  await page.goto("/console");
  await expect(page.getByRole("heading", { name: /Progressive delivery/ })).toBeVisible();
  await page.goto("/showcase");
  await expect(page.getByRole("link", { name: /Control room 열기/ })).toHaveAttribute("href", "/console");
});

test("desktop showcase communicates the whole product without page scrolling", async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 });
  await page.goto("/showcase");
  await expect(page.getByRole("heading", { name: /안전한 결정을 운영하는 플랫폼/ })).toBeVisible();
  await expect(page.getByRole("heading", { name: "Canary deployment simulator" })).toBeVisible();
  await expect(page.getByRole("table", { name: "릴리스 일정 관리 도구와 ReleasePilot 비교" })).toBeVisible();
  await expect(page.getByRole("heading", { name: "자주 묻는 질문" })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollHeight <= document.documentElement.clientHeight)).toBe(true);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth)).toBe(true);
  expect(await page.locator("[class*='controls']").evaluate((element) => element.getBoundingClientRect().bottom <= window.innerHeight)).toBe(true);
  expect(await page.locator("[class*='faq']").evaluate((element) => element.getBoundingClientRect().bottom <= window.innerHeight)).toBe(true);
});

test("delivery routes terminate at the center edge of their release nodes", async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 });
  await page.goto("/showcase");

  for (const target of ["stable", "canary"]) {
    const alignment = await page.evaluate((release) => {
      const route = document.querySelector<SVGPathElement>(`[data-route="${release}"]`)!;
      const node = document.querySelector<SVGGElement>(`[data-release="${release}"]`)!;
      const point = route.getPointAtLength(route.getTotalLength());
      const matrix = route.getScreenCTM()!;
      const endpoint = new DOMPoint(point.x, point.y).matrixTransform(matrix);
      const bounds = node.getBoundingClientRect();
      return { x: Math.abs(endpoint.x - bounds.left), y: Math.abs(endpoint.y - (bounds.top + bounds.height / 2)) };
    }, target);
    expect(alignment.x).toBeLessThan(1);
    expect(alignment.y).toBeLessThan(1);
  }
});

test("public discovery endpoints advertise the showcase", async ({ request }) => {
  const robots = await request.get("/robots.txt");
  expect(robots.ok()).toBe(true);
  expect(await robots.text()).toContain("Disallow: /control-api/");
  expect(await robots.text()).toContain("Sitemap: https://releasepilot.kr/sitemap.xml");

  const sitemap = await request.get("/sitemap.xml");
  expect(sitemap.ok()).toBe(true);
  expect(await sitemap.text()).toContain("https://releasepilot.kr/showcase");

  const socialImage = await request.get("/showcase/opengraph-image");
  expect(socialImage.ok()).toBe(true);
  expect(socialImage.headers()["content-type"]).toContain("image/png");
  expect((await socialImage.body()).byteLength).toBeGreaterThan(10_000);
});

test("showcase automatically rolls back when the error policy is breached", async ({ page }) => {
  await page.goto("/showcase");
  await page.getByRole("button", { name: "배포 승인", exact: true }).click();
  await page.getByLabel("테스트 오류율").fill("12");
  await page.getByRole("button", { name: "점진적 배포 시작", exact: true }).click();
  await expect(page.getByRole("alert").filter({ hasText: "정책 임계치 5% 초과" })).toBeVisible();
  await expect(page.getByText("복구 완료", { exact: true })).toBeVisible({ timeout: 4_000 });
  await expect(page.getByText("0% traffic", { exact: true })).toBeVisible();
  await expect(page.getByText("QUARANTINED", { exact: true })).toBeVisible();
  await expect(page.getByText("RECOVERED", { exact: true })).toBeVisible();
  await expect(page.getByText("TRAFFIC RESTORED · CANARY ISOLATED", { exact: true })).toBeVisible();
});

test("@a11y showcase has no automated WCAG A or AA violations", async ({ page }) => {
  await page.goto("/showcase");
  const results = await new AxeBuilder({ page }).withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa"]).analyze();
  expect(results.violations).toEqual([]);
});

for (const width of [320, 390]) {
  test(`@a11y showcase remains actionable without horizontal clipping at ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 });
    await page.goto("/showcase");
    await expect(page.getByRole("button", { name: "배포 승인", exact: true })).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth)).toBe(true);
  });
}
