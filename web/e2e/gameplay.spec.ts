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
  shape = "SQUARE",
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
          shape,
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
  await expect(
    page.locator(shape === "SQUARE" ? ".cell" : ".poly-cell"),
  ).toHaveCount(columns * rows);
  await expect(page.locator(".board-meta")).toContainText("READY");
  const clickCycleToggle = page.getByLabel("Click to change cycle", {
    exact: false,
  });
  if (!clickCycle) await clickCycleToggle.uncheck();
  return { board, headers, editorSessionId };
}

test("left/right board clicks cycle values and count clicks in the HUD", async ({
  page,
  request,
}) => {
  const { board } = await openGame(page, request, 3, 3, true);
  const cell = board.givens.indexOf(0);
  const target = page.locator(".cell").nth(cell);
  const regionId = board.regionIds[cell];
  const regionSize = board.regionIds.filter((id) => id === regionId).length;

  await expect(page.getByLabel("Total clicks")).toHaveText("0");
  await expect(page.locator(".hud")).toContainText("3 × 3");
  await expect(page.locator(".hud")).toContainText("EASY · max 5");
  await expect(page.getByText("Click accuracy", { exact: false })).toHaveCount(
    0,
  );
  await target.click();
  await expect(target.locator(".cell-value")).toHaveText("1");
  await expect(page.locator(".selected-region-boundary")).toBeVisible();
  await expect(page.locator(".selected-region")).toContainText(
    `REGION ${regionId + 1}`,
  );
  await expect(page.locator(".selected-region")).toContainText(
    `${regionSize} cells`,
  );
  await expect(page.getByLabel("Total clicks")).toHaveText("1");

  await target.click({ button: "right" });
  await expect(target.locator(".cell-value")).toHaveCount(0);
  await expect(page.getByLabel("Total clicks")).toHaveText("2");
});

test("wheel notes, cycling, history, pause/reload and replay are persisted", async ({
  page,
  request,
}) => {
  const { board, editorSessionId } = await openGame(page, request);
  const cell = board.givens.indexOf(0);
  const regionSize = board.regionIds.filter(
    (id) => id === board.regionIds[cell],
  ).length;
  const note = page.locator(".cell").nth(cell).locator(".wheel-note");
  await expect(page.getByLabel("Manual notes", { exact: false })).toHaveCount(
    0,
  );
  await page.locator(".cell").nth(cell).hover();
  await page.mouse.wheel(0, -100);
  await expect(note).toHaveText("1");
  await page.mouse.wheel(0, -100);
  await expect(note).toHaveText(regionSize > 1 ? "2" : "");
  await page.mouse.wheel(0, 100);
  await expect(note).toHaveText("1");
  await page.mouse.wheel(0, 100);
  await expect(note).toHaveCount(0);
  await page.mouse.wheel(0, 100);
  await expect(note).toHaveText(String(regionSize));
  await expect(page.getByLabel("Total clicks")).toHaveText("0");
  await page.reload();
  await expect(note).toHaveText(String(regionSize));
  await expect(page.locator(".board-meta")).toContainText("SUSPENDED");
  await page.getByRole("button", { name: "Resume game", exact: true }).click();
  await expect(page.locator(".board-meta")).toContainText("READY");
  await expect(note).toHaveText(String(regionSize));
  await expect(page.getByLabel("Total clicks")).toHaveText("0");
  await expect(
    page.getByRole("button", { name: "Show candidates", exact: true }),
  ).toHaveCount(0);
  await page
    .getByRole("button", { name: "Save checkpoint", exact: true })
    .click();
  await page.getByLabel("Click to change cycle", { exact: false }).check();
  await page.locator(".cell").nth(cell).click();
  await expect(page.locator(".board-meta")).toContainText("PLAYING");
  await expect(page.locator(".cell").nth(cell)).toHaveText("1");
  await expect(note).toHaveCount(0);
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
  await expect(page.locator(".cell").nth(cell)).toHaveText("");
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
  expect(saved.progress.candidateRequests).toBe(0);
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

test("touch taps cycle values to complete a real Java puzzle", async ({
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
    const { board } = await openGame(page, request, 2, 1, true);
    const cell = board.givens.indexOf(0);
    const answer = board.givens[1 - cell] === 1 ? 2 : 1;
    const target = page.locator(".cell").nth(cell);
    for (let tap = 0; tap < answer; tap++) {
      await target.tap();
      await expect(target.locator(".cell-value")).toHaveText(String(tap + 1));
    }
    await expect(page.locator(".completion")).toContainText("Result saved");
    const win = page.getByRole("dialog", { name: "Well played!" });
    await expect(win).toBeVisible();
    await expect(win).toContainText("100%");
    await expect(win).toContainText(String(answer));
    await win.getByLabel("Player for this result").selectOption("__new__");
    await win.getByLabel("New player tag").fill("tap");
    await win.getByRole("button", { name: "Add", exact: true }).click();
    await expect(win.getByRole("status")).toHaveText("Result saved for TAP.");
    const history = await (await request.get("/api/v1/history")).json();
    expect(
      history.entries.find(
        (entry: { recordId: string }) => entry.recordId === board.attemptId,
      ).playerTag,
    ).toBe("TAP");
  } finally {
    await context.close();
  }
});

test("hexagon boards are generated from setup and played with left/right clicks", async ({
  page,
}) => {
  await page.goto("/setup");
  await page
    .getByRole("combobox", { name: "BOARD", exact: true })
    .selectOption("4x4");
  await page
    .getByRole("combobox", { name: "TILES", exact: true })
    .selectOption("HEXAGON");
  await page.getByRole("button", { name: /generate/i }).click();
  await expect(page.locator(".poly-cell")).toHaveCount(16, { timeout: 30000 });
  await expect(page.locator(".cell")).toHaveCount(0);
  await expect(page.locator(".hud")).toContainText("4 × 4 · Hexagons");
  const open = page.locator(".poly-cell:not(.given)").first();
  const labels = page.locator(".poly-label .cell-value");
  const before = await labels.count();
  await open.click();
  await expect(open).toHaveClass(/selected/);
  await expect(page.locator(".selected-region-boundary")).toBeVisible();
  await expect(labels).toHaveCount(before + 1);
  await expect(page.getByLabel("Total clicks")).toHaveText("1");
  await open.click({ button: "right" });
  await expect(labels).toHaveCount(before);
  await expect(page.getByLabel("Total clicks")).toHaveText("2");
});

test("a won hexagon game is recorded with its tile shape on the leaderboard", async ({
  page,
  request,
}) => {
  const { board } = await openGame(page, request, 2, 1, true, "HEXAGON");
  expect(board.cellPolygons).toHaveLength(2);
  expect(board.puzzleSettings?.shape).toBe("HEXAGON");
  const cell = board.givens.indexOf(0);
  expect(cell).toBeGreaterThanOrEqual(0);
  const answer = board.givens[1 - cell] === 1 ? 2 : 1;
  const target = page.locator(".poly-cell").nth(cell);
  for (let click = 0; click < answer; click++) await target.click();
  await expect(
    page.getByRole("dialog", { name: "Well played!" }),
  ).toContainText("Hexagons");
  await page.goto("/history");
  const row = page.locator(`tr[data-record-id="${board.attemptId}"]`);
  await expect(row).toContainText("Hexagons");
  await page.getByLabel("Tiles filter", { exact: true }).selectOption("SQUARE");
  await expect(row).toHaveCount(0);
  await page
    .getByLabel("Tiles filter", { exact: true })
    .selectOption("HEXAGON");
  await expect(row).toBeVisible();
});
