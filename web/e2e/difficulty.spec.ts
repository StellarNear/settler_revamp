import { expect, test } from "@playwright/test";
import { attemptSchema, bootstrapSchema } from "../src/api";
import { historySchema, settingsSnapshotSchema } from "../src/dataApi";

function solveSmallSquare(regionIds: number[], givens: number[]) {
  const values = [...givens];
  const solutions: number[][] = [];
  function search(): void {
    if (solutions.length >= 2) return;
    const cell = values.indexOf(0);
    if (cell < 0) {
      solutions.push([...values]);
      return;
    }
    const size = regionIds.filter((id) => id === regionIds[cell]).length;
    for (let value = 1; value <= size; value++) {
      const forbidden = values.some(
        (other, peer) =>
          other === value &&
          (regionIds[peer] === regionIds[cell] ||
            (Math.abs((peer % 3) - (cell % 3)) <= 1 &&
              Math.abs(Math.floor(peer / 3) - Math.floor(cell / 3)) <= 1)),
      );
      if (!forbidden) {
        values[cell] = value;
        search();
        values[cell] = 0;
      }
    }
  }
  search();
  expect(solutions).toHaveLength(1);
  return solutions[0]!;
}

for (const difficulty of ["EXTREME", "GODLIKE"] as const) {
  test(`${difficulty} persists through settings, generation, completion and leaderboard`, async ({
    page,
    request,
  }) => {
    const bootstrap = bootstrapSchema.parse(
      await (await request.get("/api/v1/bootstrap")).json(),
    );
    const headers = {
      Origin: "http://localhost:18093",
      "X-Tiwanaku-Token": bootstrap.requestToken,
    };
    const original = settingsSnapshotSchema.parse(
      await (await request.get("/api/v1/settings")).json(),
    );
    try {
      await page.goto("/settings");
      await page
        .getByRole("combobox", { name: "Default board", exact: true })
        .selectOption("3x3");
      await page
        .getByRole("combobox", { name: "Default tiles", exact: true })
        .selectOption("SQUARE");
      await page
        .getByRole("combobox", { name: "Default maximum region", exact: true })
        .selectOption("5");
      await page
        .getByRole("combobox", { name: "Default difficulty", exact: true })
        .selectOption(difficulty);
      await page
        .getByRole("button", { name: "Save settings", exact: true })
        .click();
      await expect
        .poll(
          async () =>
            settingsSnapshotSchema.parse(
              await (await request.get("/api/v1/settings")).json(),
            ).settings.difficulty,
        )
        .toBe(difficulty);
      await page.reload();
      await expect(
        page.getByRole("combobox", { name: "Default difficulty", exact: true }),
      ).toHaveValue(difficulty);
      await page.goto("/setup");
      await expect(
        page.getByRole("combobox", { name: "DIFFICULTY", exact: true }),
      ).toHaveValue(difficulty);
      await expect(
        page.getByText("No clue is individually removable", { exact: false }),
      ).toBeVisible();
      await page
        .getByRole("button", { name: "GENERATE PUZZLE", exact: true })
        .click();
      await expect(page.locator(".cell")).toHaveCount(9, { timeout: 35000 });
      await expect(page.locator(".hud")).toContainText(difficulty);
      const attemptId = await page.evaluate(() =>
        sessionStorage.getItem("tiwanaku.attemptId"),
      );
      expect(attemptId).not.toBeNull();
      const editorSessionId = await page.evaluate(() =>
        sessionStorage.getItem("tiwanaku.editorSessionId"),
      );
      expect(editorSessionId).not.toBeNull();
      const board = attemptSchema.parse(
        await (
          await request.get(
            `/api/v1/attempts/${attemptId}?editorSessionId=${editorSessionId}`,
          )
        ).json(),
      );
      expect(board.puzzleSettings?.difficulty).toBe(difficulty);
      const solution = solveSmallSquare(board.regionIds, board.givens);
      for (let cell = 0; cell < solution.length; cell++) {
        if (board.givens[cell] !== 0) continue;
        const target = page.locator(".cell").nth(cell);
        for (let value = 1; value <= solution[cell]!; value++) {
          await target.click();
          await expect(target.locator(".cell-value")).toHaveText(String(value));
        }
      }
      await expect(
        page.getByRole("dialog", { name: "Well played!" }),
      ).toBeVisible();
      const history = historySchema.parse(
        await (await request.get("/api/v1/history")).json(),
      );
      expect(
        history.entries.find((entry) => entry.recordId === attemptId)
          ?.puzzleSettings?.difficulty,
      ).toBe(difficulty);
      await page.goto("/history");
      await expect(
        page.locator(`tr[data-record-id="${attemptId}"]`),
      ).toContainText(difficulty);
    } finally {
      const latest = settingsSnapshotSchema.parse(
        await (await request.get("/api/v1/settings")).json(),
      );
      expect(
        (
          await request.post("/api/v1/settings", {
            headers,
            data: { revision: latest.revision, settings: original.settings },
          })
        ).ok(),
      ).toBe(true);
    }
  });
}
