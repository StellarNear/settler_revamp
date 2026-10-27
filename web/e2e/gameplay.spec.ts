import {
  expect,
  test,
  type APIRequestContext,
  type Page,
} from "@playwright/test";
import {
  attemptSchema,
  bootstrapSchema,
  jobSchema,
  resultSchema,
} from "../src/api";

async function openGame(
  page: Page,
  request: APIRequestContext,
  columns = 3,
  rows = 3,
  clickCycle = false,
) {
  const bootstrap = bootstrapSchema.parse(
    await (await request.get("/api/v1/bootstrap")).json(),
  );
  const headers = {
    Origin: "http://localhost:18093",
    "X-Tiwanaku-Token": bootstrap.requestToken,
  };
  const job = jobSchema.parse(
    await (
      await request.post("/api/v1/generation-jobs", {
        headers,
        data: {
          columns,
          rows,
          maxRegionSize: 5,
          difficulty: "EASY",
          seed: 3045,
        },
      })
    ).json(),
  );
  await expect
    .poll(
      async () =>
        jobSchema.parse(
          await (
            await request.get(`/api/v1/generation-jobs/${job.jobId}`)
          ).json(),
        ).status,
    )
    .toBe("GENERATED");
  const editorSessionId = crypto.randomUUID();
  const board = attemptSchema.parse(
    await (
      await request.post("/api/v1/attempts", {
        headers,
        data: {
          requestId: crypto.randomUUID(),
          generationJobId: job.jobId,
          editorSessionId,
        },
      })
    ).json(),
  );
  await page.goto("/setup");
  await page.evaluate(
    ({ id, editor }) => {
      sessionStorage.setItem("tiwanaku.attemptId", id);
      sessionStorage.setItem("tiwanaku.editorSessionId", editor);
    },
    { id: board.attemptId, editor: editorSessionId },
  );
  await page.goto("/play");
  await expect(page.locator(".cell")).toHaveCount(columns * rows);
  await expect(page.locator(".board-meta")).toContainText("READY");
  const clickCycleToggle = page.getByLabel("Click to change cycle", {
    exact: false,
  });
  if (!clickCycle) await clickCycleToggle.uncheck();
  return { board, headers, editorSessionId };
}

test("left/right board clicks cycle values and show legacy accuracy by default", async ({
  page,
  request,
}) => {
  const { board } = await openGame(page, request, 3, 3, true);
  const cell = board.givens.indexOf(0);
  const target = page.locator(".cell").nth(cell);
  const regionId = board.regionIds[cell];
  const regionSize = board.regionIds.filter((id) => id === regionId).length;

  await expect(
    page.getByText("Click accuracy: 100%", { exact: false }),
  ).toBeVisible();
  await target.click();
  await expect(target.locator(".cell-value")).toHaveText("1");
  await expect(page.locator(".selected-region-boundary")).toBeVisible();
  await expect(page.locator(".selected-region")).toContainText(
    `REGION ${regionId + 1}`,
  );
  await expect(page.locator(".selected-region")).toContainText(
    `${regionSize} cells`,
  );
  await expect(
    page.getByText("Click accuracy: 100%", { exact: false }),
  ).toBeVisible();

  await target.click({ button: "right" });
  await expect(target.locator(".cell-value")).toHaveCount(0);
  await expect(
    page.getByText("Click accuracy: 0%", { exact: false }),
  ).toBeVisible();
});

test("notes, assistance, cycling, history, pause/reload and replay are Java-persisted", async ({
  page,
  request,
}) => {
  const { board, editorSessionId } = await openGame(page, request);
  const cell = board.givens.indexOf(0);
  await page.locator(".cell").nth(cell).click();
  await page.getByLabel("Manual notes", { exact: false }).check();
  await page
    .getByRole("button", { name: "Toggle note 1", exact: true })
    .click();
  await expect(
    page.locator(".cell").nth(cell).locator(".manual-notes"),
  ).toHaveText("1");
  await expect(page.locator(".board-meta")).toContainText("READY");
  await page
    .getByRole("button", { name: "Show candidates", exact: true })
    .click();
  await expect(page.locator(".assistance")).toContainText(
    "1 candidate requests",
  );
  await page
    .getByRole("button", { name: "Save checkpoint", exact: true })
    .click();
  await page.getByLabel("Manual notes", { exact: false }).uncheck();
  await page.getByLabel("Click to change cycle", { exact: false }).check();
  await page.locator(".cell").nth(cell).click();
  await expect(page.locator(".board-meta")).toContainText("PLAYING");
  await expect(page.locator(".cell").nth(cell)).toHaveText("1");
  await page.locator(".cell").nth(cell).click({ button: "right" });
  await expect(page.locator(".cell").nth(cell)).toHaveText("");
  await page.getByRole("button", { name: "Undo", exact: true }).click();
  await expect(page.locator(".cell").nth(cell)).toHaveText("1");
  await page.getByRole("button", { name: "Redo", exact: true }).click();
  await expect(page.locator(".cell").nth(cell)).toHaveText("");
  page.once("dialog", (dialog) => dialog.accept());
  await page
    .getByRole("button", { name: "Restore checkpoint", exact: true })
    .click();
  await expect(
    page.locator(".cell").nth(cell).locator(".manual-notes"),
  ).toHaveText("1");
  await page.getByRole("button", { name: "Pause game", exact: true }).click();
  await expect(page.locator(".board-meta")).toContainText("SUSPENDED");
  const stopped = await page.getByLabel("Active elapsed time").textContent();
  await page.reload();
  await expect(page.locator(".board-meta")).toContainText("SUSPENDED");
  await expect(page.getByLabel("Active elapsed time")).toHaveText(stopped!);
  await page.getByRole("button", { name: "Resume game", exact: true }).click();
  await expect(page.locator(".board-meta")).toContainText("PLAYING");
  await page.getByRole("link", { name: "Settings", exact: true }).click();
  const saved = attemptSchema.parse(
    await (
      await request.get(
        `/api/v1/attempts/${board.attemptId}?editorSessionId=${editorSessionId}`,
      )
    ).json(),
  );
  expect(saved.status).toBe("SUSPENDED");
  expect(saved.progress.valueEntryCount).toBe(1);
  expect(saved.progress.candidateRequests).toBe(1);
  await page.getByRole("link", { name: "Play", exact: true }).click();
  page.once("dialog", (dialog) => dialog.accept());
  await page
    .getByRole("button", { name: "Restart this puzzle", exact: true })
    .click();
  await expect(page.locator(".board-meta")).toContainText("READY · REPLAY");
  const newId = await page.evaluate(() =>
    sessionStorage.getItem("tiwanaku.attemptId"),
  );
  expect(newId).not.toBe(board.attemptId);
  const replay = attemptSchema.parse(
    await (
      await request.get(
        `/api/v1/attempts/${newId}?editorSessionId=${editorSessionId}`,
      )
    ).json(),
  );
  expect(replay.values).toEqual(board.givens);
  expect(replay.progress.elapsedMillis).toBe(0);
  expect(replay.progress.notes.every((mask) => mask === 0)).toBe(true);
});

for (const committed of [false, true]) {
  test(`keyboard completion reconciles a ${committed ? "committed" : "not received"} lost response`, async ({
    page,
    request,
  }) => {
    const { board } = await openGame(page, request, 2, 1);
    const cell = board.givens.indexOf(0);
    const answer = board.givens[1 - cell] === 1 ? 2 : 1;
    let original = "";
    await page.route(
      "**/api/v1/attempts/*/commands",
      async (route) => {
        original = route.request().postData()!;
        if (committed) expect((await route.fetch()).ok()).toBe(true);
        await route.abort();
      },
      { times: 1 },
    );
    await page.locator(".cell").nth(cell).click();
    await page.keyboard.press(String(answer));
    await expect(
      page.getByText("SERVER DISCONNECTED", { exact: true }),
    ).toBeVisible();
    await page.getByRole("button", { name: "Reconnect", exact: true }).click();
    if (!committed) {
      await expect(page.locator(".board-meta")).toContainText("READY");
      let retried = "";
      await page.route(
        "**/api/v1/attempts/*/commands",
        async (route) => {
          retried = route.request().postData()!;
          await route.continue();
        },
        { times: 1 },
      );
      await page
        .getByRole("button", { name: "Resolve pending action", exact: false })
        .click();
      expect(retried).toBe(original);
    }
    await expect(page.locator(".completion")).toContainText("Result saved");
    const result = resultSchema.parse(
      await (
        await request.get(`/api/v1/attempts/${board.attemptId}/result`)
      ).json(),
    );
    expect(result.resultId).toBe(board.attemptId);
    expect(result.moveCount).toBe(1);
  });
}

test("touch keypad completes a real Java puzzle", async ({
  browser,
  request,
}) => {
  const context = await browser.newContext({
    baseURL: "http://localhost:18093",
    hasTouch: true,
    viewport: { width: 390, height: 844 },
  });
  const page = await context.newPage();
  try {
    const { board } = await openGame(page, request, 2, 1);
    const cell = board.givens.indexOf(0);
    const answer = board.givens[1 - cell] === 1 ? 2 : 1;
    await page.locator(".cell").nth(cell).tap();
    await page
      .getByRole("button", { name: `Enter ${answer}`, exact: true })
      .tap();
    await expect(page.locator(".completion")).toContainText("Result saved");
  } finally {
    await context.close();
  }
});
