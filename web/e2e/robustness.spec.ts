import AxeBuilder from "@axe-core/playwright";
import { expect, test, type Page } from "@playwright/test";
import { mkdtemp, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { attemptSchema, bootstrapSchema } from "../src/api";
import { launchServer } from "./server";

async function newGame(page: Page) {
  await page.goto("/setup");
  await expect(
    page.getByText("JAVA SERVER CONNECTED", { exact: true }),
  ).toBeVisible();
  await page
    .getByRole("combobox", { name: "BOARD", exact: true })
    .selectOption("3x3");
  await page
    .getByRole("button", { name: "GENERATE PUZZLE", exact: true })
    .click();
  await expect(page.locator(".cell")).toHaveCount(9);
}
async function snapshot(page: Page) {
  const ids = await page.evaluate(() => ({
    attempt: sessionStorage.getItem("tiwanaku.attemptId"),
    editor: sessionStorage.getItem("tiwanaku.editorSessionId"),
  }));
  return attemptSchema.parse(
    await (
      await page.request.get(
        `/api/v1/attempts/${ids.attempt}?editorSessionId=${ids.editor}`,
      )
    ).json(),
  );
}

test("lightweight accessibility checks cover routes, active/paused grid and native help without external requests", async ({
  page,
}, testInfo) => {
  test.setTimeout(60_000);
  const external: string[] = [];
  const policyErrors: string[] = [];
  page.on("request", (request) => {
    if (new URL(request.url()).origin !== "http://localhost:18093")
      external.push(request.url());
  });
  page.on("console", (message) => {
    if (/content security policy|violates.*directive/i.test(message.text()))
      policyErrors.push(message.text());
  });
  async function check(name: string) {
    const result = await new AxeBuilder({ page })
      .withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa"])
      .analyze();
    await testInfo.attach(`axe-${name}`, {
      body: JSON.stringify(result.violations, null, 2),
      contentType: "application/json",
    });
    expect(
      result.violations.filter((issue) =>
        ["serious", "critical"].includes(issue.impact ?? ""),
      ),
      JSON.stringify(
        result.violations.map((issue) => ({
          id: issue.id,
          impact: issue.impact,
          nodes: issue.nodes.map((node) => node.target),
        })),
        null,
        2,
      ),
    ).toEqual([]);
  }
  for (const theme of ["light", "dark", "high-contrast"]) {
    await page.goto("/settings");
    await page
      .getByRole("combobox", { name: "Theme", exact: true })
      .selectOption(theme);
    await page
      .getByRole("button", { name: "Save settings", exact: true })
      .click();
    await expect(page.locator("html")).toHaveAttribute("data-theme", theme);
    for (const route of ["setup", "settings", "history"]) {
      await page.goto(`/${route}`);
      await expect(
        page.getByText("JAVA SERVER CONNECTED", { exact: true }),
      ).toBeVisible();
      await expect(page.locator("html")).toHaveAttribute("data-theme", theme);
      if (route === "settings")
        await expect(
          page.getByRole("button", { name: "Save settings", exact: true }),
        ).toBeVisible();
      if (route === "history")
        await expect(page.locator(".history-summary")).toBeVisible();
      await check(`${theme}-${route}`);
    }
  }
  await newGame(page);
  await check("ready-grid");
  await page.getByRole("button", { name: "Rules and keyboard help" }).click();
  await check("help");
  await page.keyboard.press("Escape");
  const board = await snapshot(page);
  const cell = board.givens.indexOf(0);
  await page.locator(".cell").nth(cell).click();
  await page.keyboard.press("1");
  await expect(page.locator(".board-meta")).toContainText("PLAYING");
  await check("playing-grid");
  await page.getByRole("button", { name: "Pause game", exact: true }).click();
  await expect(page.locator(".board-meta")).toContainText("SUSPENDED");
  await check("paused-grid");
  await page.getByRole("button", { name: "Resume game", exact: true }).click();
  await expect(page.locator(".board-meta")).toContainText("PLAYING");
  expect(external).toEqual([]);
  expect(policyErrors).toEqual([]);
  const headers = await page.request.get("/play");
  expect(headers.headers()["content-security-policy"]).toContain(
    "connect-src 'self'",
  );
  expect(headers.headers()["x-content-type-options"]).toBe("nosniff");
  const timing = await page.evaluate(() => ({
    navigationMillis: performance.getEntriesByType("navigation")[0]?.duration,
    resourceCount: performance.getEntriesByType("resource").length,
  }));
  await testInfo.attach("local-browser-smoke-timing", {
    body: JSON.stringify(timing),
    contentType: "application/json",
  });
});

test("an open tab freezes with Java stopped and reconnects to the same saved game after real restart", async ({
  browser,
}, testInfo) => {
  test.setTimeout(60_000);
  const directory = await mkdtemp(join(tmpdir(), "tiwanaku-lifecycle-"));
  const context = await browser.newContext({
    baseURL: "http://localhost:18097",
  });
  let stop: (() => Promise<void>) | undefined;
  try {
    stop = await launchServer(18097, directory);
    const page = await context.newPage();
    await newGame(page);
    const bootstrap = bootstrapSchema.parse(
      await (await page.request.get("/api/v1/bootstrap")).json(),
    );
    const board = await snapshot(page);
    const cell = board.givens.indexOf(0);
    await page.locator(".cell").nth(cell).click();
    await page.keyboard.press("1");
    await expect(page.locator(".board-meta")).toContainText("PLAYING");
    const saved = await snapshot(page);
    await stop();
    stop = undefined;
    await expect(
      page.getByRole("button", { name: "Reconnect", exact: true }),
    ).toBeVisible({ timeout: 12_000 });
    const revision = saved.revision;
    let mutations = 0;
    page.on("request", (request) => {
      if (request.method() === "POST") mutations++;
    });
    await page.keyboard.press("2");
    await expect(page.locator(".cell").nth(cell)).toBeDisabled();
    expect(mutations).toBe(0);
    stop = await launchServer(18097, directory);
    const next = bootstrapSchema.parse(
      await (await page.request.get("/api/v1/bootstrap")).json(),
    );
    expect(next.processInstanceId).not.toBe(bootstrap.processInstanceId);
    expect(
      (
        await page.request.post("/api/v1/attempts", {
          headers: {
            Origin: "http://localhost:18097",
            "X-Tiwanaku-Token": bootstrap.requestToken,
          },
          data: {},
        })
      ).status(),
    ).toBe(403);
    await page.getByRole("button", { name: "Reconnect", exact: true }).click();
    await expect(
      page.getByText("JAVA SERVER CONNECTED", { exact: true }),
    ).toBeVisible();
    await expect(page.locator(".board-meta")).toContainText("SUSPENDED");
    const restored = await snapshot(page);
    expect(restored.attemptId).toBe(saved.attemptId);
    expect(restored.values).toEqual(saved.values);
    expect(restored.revision).toBe(revision + 1);
    // The failed in-flight heartbeat remains an unknown mutation, never silently
    // discarded just because startup suspended its attempt.
    await page
      .getByRole("button", { name: "Resolve pending action", exact: false })
      .click();
    await expect(
      page.getByRole("button", {
        name: "Resolve pending action",
        exact: false,
      }),
    ).toHaveCount(0);
    await page
      .getByRole("button", { name: "Resume game", exact: true })
      .click();
    await expect(page.locator(".board-meta")).toContainText("PLAYING");
    expect((await snapshot(page)).values).toEqual(saved.values);
    await testInfo.attach("restart-evidence", {
      body: JSON.stringify({
        attemptId: saved.attemptId,
        before: revision,
        restored: restored.revision,
        stoppedEdits: 0,
      }),
      contentType: "application/json",
    });
  } finally {
    await context.close();
    try {
      if (stop) await stop();
    } finally {
      await rm(directory, { recursive: true, force: true });
    }
  }
});

test("generation cancellation stays explicit and does not replace the saved game", async ({
  page,
}) => {
  await newGame(page);
  const original = await snapshot(page);
  const jobId = crypto.randomUUID();
  let cancelling = false;
  // Hold a deterministic queued response to exercise the UI race; Java cancellation
  // itself is independently exercised by LocalServerBootstrapIntegrationTest.
  await page.route("**/api/v1/generation-jobs", (route) =>
    route.fulfill({
      json: {
        jobId,
        status: "QUEUED",
        termination: null,
        error: null,
        elapsedMillis: 0,
        visitedNodes: 0,
      },
    }),
  );
  await page.route(`**/api/v1/generation-jobs/${jobId}`, async (route) => {
    if (route.request().method() === "DELETE") cancelling = true;
    await route.fulfill({
      json: {
        jobId,
        status: cancelling ? "CANCELLED" : "QUEUED",
        termination: cancelling ? "CANCELLED" : null,
        error: null,
        elapsedMillis: 0,
        visitedNodes: 0,
      },
    });
  });
  await page
    .getByRole("link", { name: "New puzzle", exact: true })
    .first()
    .click();
  await page
    .getByRole("button", { name: "GENERATE PUZZLE", exact: true })
    .click();
  const cancel = page.getByRole("button", {
    name: "Cancel generation",
    exact: true,
  });
  await expect(cancel).toBeEnabled();
  await cancel.click();
  await expect(
    page.getByRole("status").filter({ hasText: "Generation cancelled" }),
  ).toBeVisible();
  await expect(cancel).toHaveCount(0);
  await page.getByRole("link", { name: "Return to saved game" }).click();
  expect((await snapshot(page)).attemptId).toBe(original.attemptId);
  expect((await snapshot(page)).values).toEqual(original.values);
});
