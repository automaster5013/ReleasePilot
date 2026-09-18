import { expect, test } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";

test("showcase explains and completes the approval-to-canary flow", async ({ page }) => {
  await page.goto("/showcase");
  await expect(page.getByRole("heading", { name: /안전한 결정을 운영하는 플랫폼/ })).toBeVisible();
  await expect(page.getByText("승인 검토", { exact: true })).toBeVisible();
  await expect(page.getByText("트래픽 제어", { exact: true })).toBeVisible();
  await expect(page.getByText("자동 롤백", { exact: true })).toBeVisible();
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

  const structuredData = JSON.parse(await page.locator('script[type="application/ld+json"]').textContent() ?? "{}");
  expect(structuredData).toMatchObject({
    "@type": "SoftwareApplication",
    name: "ReleasePilot",
    applicationSubCategory: "Progressive Delivery Control Plane",
    url: "https://releasepilot.kr/showcase",
  });
  expect(structuredData.featureList).toContain("메트릭 기반 자동 롤백");
});

test("public discovery endpoints advertise the showcase", async ({ request }) => {
  const robots = await request.get("/robots.txt");
  expect(robots.ok()).toBe(true);
  expect(await robots.text()).toContain("Disallow: /control-api/");
  expect(await robots.text()).toContain("Sitemap: https://releasepilot.kr/sitemap.xml");

  const sitemap = await request.get("/sitemap.xml");
  expect(sitemap.ok()).toBe(true);
  expect(await sitemap.text()).toContain("https://releasepilot.kr/showcase");
});

test("showcase automatically rolls back when the error policy is breached", async ({ page }) => {
  await page.goto("/showcase");
  await page.getByRole("button", { name: "배포 승인", exact: true }).click();
  await page.getByLabel("테스트 오류율").fill("12");
  await page.getByRole("button", { name: "점진적 배포 시작", exact: true }).click();
  await expect(page.getByRole("alert").filter({ hasText: "정책 임계치 5% 초과" })).toBeVisible();
  await expect(page.getByText("복구 완료", { exact: true })).toBeVisible({ timeout: 4_000 });
  await expect(page.getByText("0% traffic", { exact: true })).toBeVisible();
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
