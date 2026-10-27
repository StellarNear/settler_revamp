import { expect, test } from "@playwright/test";
import {
  attemptSchema,
  bootstrapSchema,
  jobSchema,
  resultSchema,
} from "../src/api";

test("frozen v1 schemas accept real Java generation, completion and result responses", async ({
  request,
}) => {
  const bootstrap = bootstrapSchema.parse(
    await (await request.get("/api/v1/bootstrap")).json(),
  );
  const headers = {
    Origin: "http://localhost:18093",
    "X-Tiwanaku-Token": bootstrap.requestToken,
  };
  const submitted = await request.post("/api/v1/generation-jobs", {
    headers,
    data: {
      columns: 2,
      rows: 1,
      maxRegionSize: 5,
      difficulty: "EASY",
      seed: 3045,
    },
  });
  expect(submitted.ok()).toBe(true);
  const job = jobSchema.parse(await submitted.json());
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
  const creation = await request.post("/api/v1/attempts", {
    headers,
    data: {
      requestId: crypto.randomUUID(),
      generationJobId: job.jobId,
      editorSessionId,
    },
  });
  expect(creation.ok()).toBe(true);
  const created = attemptSchema.parse(await creation.json());
  const cellIndex = created.givens.indexOf(0);
  const answer = created.givens[1 - cellIndex] === 1 ? 2 : 1;
  const completion = await request.post(
    `/api/v1/attempts/${created.attemptId}/commands`,
    {
      headers,
      data: {
        commandId: crypto.randomUUID(),
        expectedRevision: 0,
        editorSessionId,
        type: "SET_VALUE",
        cellIndex,
        value: answer,
      },
    },
  );
  expect(completion.ok()).toBe(true);
  const completed = attemptSchema.parse(await completion.json());
  expect(completed.status).toBe("COMPLETED");
  const result = resultSchema.parse(
    await (
      await request.get(`/api/v1/attempts/${created.attemptId}/result`)
    ).json(),
  );
  expect(result.attemptId).toBe(created.attemptId);
  expect(result.values).toEqual(completed.values);
  expect(result.moveCount).toBe(1);
});

test("Titan max-five generation includes five-cell regions in the accepted board", async ({
  request,
}) => {
  const bootstrap = bootstrapSchema.parse(
    await (await request.get("/api/v1/bootstrap")).json(),
  );
  const headers = {
    Origin: "http://localhost:18093",
    "X-Tiwanaku-Token": bootstrap.requestToken,
  };
  const submitted = await request.post("/api/v1/generation-jobs", {
    headers,
    data: {
      columns: 20,
      rows: 12,
      maxRegionSize: 5,
      difficulty: "EASY",
      seed: 2026,
    },
  });
  expect(submitted.ok()).toBe(true);
  const job = jobSchema.parse(await submitted.json());
  await expect
    .poll(
      async () =>
        jobSchema.parse(
          await (
            await request.get(`/api/v1/generation-jobs/${job.jobId}`)
          ).json(),
        ).status,
      { timeout: 30_000 },
    )
    .toBe("GENERATED");
  const createdResponse = await request.post("/api/v1/attempts", {
    headers,
    data: {
      requestId: crypto.randomUUID(),
      generationJobId: job.jobId,
      editorSessionId: crypto.randomUUID(),
    },
  });
  expect(createdResponse.ok()).toBe(true);
  const board = attemptSchema.parse(await createdResponse.json());
  const sizes = new Map<number, number>();
  for (const regionId of board.regionIds) {
    sizes.set(regionId, (sizes.get(regionId) ?? 0) + 1);
  }
  expect([...sizes.values()].every((size) => size <= 5)).toBe(true);
  const regionSizes = [...sizes.values()];
  expect(regionSizes).toContain(5);
});

test("real Java generation, routed play, saved entry and shell navigation", async ({
  page,
}) => {
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
  await expect(page).toHaveURL(/\/play$/);
  await expect(page.locator(".cell")).toHaveCount(9);
  await page.getByLabel("Click to change cycle", { exact: false }).uncheck();
  const move = await page.evaluate(async () => {
    const id = sessionStorage.getItem("tiwanaku.attemptId");
    const response = await fetch(
      `/api/v1/attempts/${id}?editorSessionId=${sessionStorage.getItem("tiwanaku.editorSessionId")}`,
    );
    const board: {
      columns: number;
      values: number[];
      givens: number[];
      regionIds: number[];
    } = await response.json();
    for (let cell = 0; cell < board.values.length; cell++) {
      if (board.givens[cell]) continue;
      const size = board.regionIds.filter(
        (id) => id === board.regionIds[cell],
      ).length;
      for (let value = 1; value <= size; value++) {
        const conflict = board.values.some(
          (other, index) =>
            other === value &&
            (board.regionIds[index] === board.regionIds[cell] ||
              (Math.abs((index % board.columns) - (cell % board.columns)) <=
                1 &&
                Math.abs(
                  Math.floor(index / board.columns) -
                    Math.floor(cell / board.columns),
                ) <= 1)),
        );
        if (!conflict) return { cell, value };
      }
    }
    throw new Error("No legal test entry");
  });
  await page.locator(".cell").nth(move.cell).click();
  await page.keyboard.press(String(move.value));
  await expect(page.locator(".board-meta")).toContainText("REV 1");
  await page.reload();
  await expect(page.locator(".cell").nth(move.cell)).toHaveText(
    String(move.value),
  );
  await page.route("**/api/v1/**", (route) => route.abort());
  await expect(
    page.getByText("SERVER DISCONNECTED", { exact: true }),
  ).toBeVisible({ timeout: 15_000 });
  await expect(page.locator(".cell").nth(move.cell)).toBeDisabled();
  await page.unroute("**/api/v1/**");
  await page.getByRole("button", { name: "Reconnect", exact: true }).click();
  await expect(
    page.getByText("JAVA SERVER CONNECTED", { exact: true }),
  ).toBeVisible();
  await expect(page.locator(".cell").nth(move.cell)).toHaveText(
    String(move.value),
  );
  await page.getByRole("link", { name: "History", exact: true }).focus();
  await page.keyboard.press("Enter");
  await expect(
    page.getByRole("heading", { name: "History", exact: true }),
  ).toBeVisible();
  await page.getByRole("link", { name: "Settings", exact: true }).focus();
  await page.keyboard.press("Enter");
  await page
    .getByRole("combobox", { name: "Language", exact: true })
    .selectOption("de");
  await page
    .getByRole("button", { name: "Save settings", exact: true })
    .click();
  await expect(
    page.getByRole("heading", { name: "Einstellungen", exact: true }),
  ).toBeVisible();
  await page.getByRole("link", { name: "Spielen", exact: true }).focus();
  await page.keyboard.press("Enter");
  await expect(page).toHaveURL(/\/play$/);
  await expect(page.locator("#content")).toBeFocused();
  await expect(page.locator(".cell").nth(move.cell)).toHaveText(
    String(move.value),
  );
});

test("incompatible API versions explicitly block generation", async ({
  page,
}) => {
  await page.route("**/api/v1/bootstrap", async (route) => {
    const response = await route.fetch();
    const body: unknown = await response.json();
    if (typeof body !== "object" || body === null)
      throw new Error("Invalid bootstrap fixture");
    await route.fulfill({ json: { ...body, apiVersion: "v2" } });
  });
  await page.goto("/setup");
  await expect(page.getByRole("alert")).toContainText("incompatible");
  await expect(
    page.getByRole("button", { name: "GENERATE PUZZLE", exact: true }),
  ).toBeDisabled();
});

test("shell routes resolve without masking missing API or asset responses", async ({
  request,
}) => {
  for (const route of ["/setup", "/play", "/history", "/settings"]) {
    const response = await request.get(route);
    expect(response.status()).toBe(200);
    expect(response.headers()["cache-control"]).toBe("no-store");
    expect(await response.text()).toContain('id="app"');
  }
  for (const route of [
    "/api/v1/missing",
    "/assets/missing.js",
    "/unknown-page",
  ]) {
    expect((await request.get(route)).status()).toBe(404);
  }
});
