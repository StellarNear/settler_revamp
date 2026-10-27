import { expect, test, type Browser, type Page } from "@playwright/test";
import { attemptSchema } from "../src/api";
import { initialProgress } from "../src/testFixtures";
import type { LocalSettings } from "../src/dataApi";

const settings: LocalSettings = {
  locale: "en",
  theme: "light",
  reducedDetail: false,
  showConflicts: false,
  preset: "9x5",
  maxRegionSize: 9,
  difficulty: "EASY",
  playerTag: "",
};
// Presentation-only snapshots: never used to assert generation, solving or persistence.
function fixture(columns: number, rows: number) {
  const regionIds = Array.from(
    { length: columns * rows },
    (_, index) =>
      8 +
      Math.floor(index / columns) * columns +
      Math.floor((index % columns) / 3),
  );
  if (columns >= 3 && rows >= 3) {
    for (let y = 0; y < 3; y++)
      for (let x = 0; x < 3; x++)
        regionIds[y * columns + x] = x === 1 && y === 1 ? 4 : 0;
  }
  if (columns >= 9 && rows >= 5) {
    for (let y = 2; y < 5; y++)
      for (let x = 6; x < 9; x++) regionIds[y * columns + x] = 10002;
  }
  const givens = new Array<number>(columns * rows).fill(0);
  givens[0] = 1;
  const notes = new Array<number>(columns * rows).fill(0);
  notes[columns + 2] = 1;
  if (columns >= 9 && rows >= 5) notes[3 * columns + 7] = 511;
  return attemptSchema.parse({
    attemptId: "5cdf1090-a740-4911-860f-d574709a6373",
    puzzleId: "7b823485-2e96-4849-bfb2-c890e4d8cce8",
    columns,
    rows,
    regionIds,
    givens,
    values: givens,
    status: "READY",
    revision: 0,
    editorSessionId: "terrain-test-editor",
    editorLeaseExpiresAt: null,
    moveCount: 0,
    progress: { ...initialProgress, notes, conflicts: [columns + 2] },
  });
}
async function show(page: Page, columns = 9, rows = 5, preferences = settings) {
  const snapshot = fixture(columns, rows);
  await page.route("**/api/v1/settings", (route) =>
    route.fulfill({ json: { revision: 0, settings: preferences } }),
  );
  await page.route("**/api/v1/attempts/**", (route) =>
    route.fulfill({ json: snapshot }),
  );
  await page.addInitScript(
    ({ id, editor }) => {
      sessionStorage.setItem("tiwanaku.attemptId", id);
      sessionStorage.setItem("tiwanaku.editorSessionId", editor);
    },
    { id: snapshot.attemptId, editor: snapshot.editorSessionId },
  );
  await page.goto("/play");
  await expect(page.getByRole("grid")).toBeVisible();
  await expect(page.locator(".cell")).toHaveCount(columns * rows);
  await expect(page.locator("html")).toHaveAttribute(
    "data-theme",
    preferences.theme,
  );
}
async function alignment(page: Page) {
  const result = await page.evaluate(() => {
    const board = document.querySelector(".board")!.getBoundingClientRect();
    const svg = document
      .querySelector("svg.terrain-layer")!
      .getBoundingClientRect();
    const cells = Array.from(document.querySelectorAll(".cell")).map((cell) =>
      cell.getBoundingClientRect(),
    );
    const columns = Number(
      document.querySelector('[role="grid"]')!.getAttribute("aria-colcount"),
    );
    return {
      svgError: Math.max(
        Math.abs(board.x - svg.x),
        Math.abs(board.y - svg.y),
        Math.abs(board.width - svg.width),
        Math.abs(board.height - svg.height),
      ),
      cellError: Math.max(
        ...cells.map((cell, index) =>
          Math.max(
            Math.abs(
              cell.x - (board.x + ((index % columns) * board.width) / columns),
            ),
            Math.abs(
              cell.y -
                (board.y +
                  (Math.floor(index / columns) * board.width) / columns),
            ),
          ),
        ),
      ),
      pageOverflow: document.documentElement.scrollWidth - innerWidth,
      size: cells[0].width,
      scrolls:
        document.querySelector(".board-viewport")!.scrollWidth >
        document.querySelector(".board-viewport")!.clientWidth,
    };
  });
  expect(result.svgError).toBeLessThan(0.1);
  expect(result.cellError).toBeLessThan(0.1);
  expect(result.pageOverflow).toBeLessThanOrEqual(1);
  return result;
}

test("all seven presets retain readable cells at desktop and 390px, with aligned overview and precise selection", async ({
  page,
}, testInfo) => {
  for (const width of [1440, 390]) {
    await page.setViewportSize({ width, height: 900 });
    for (const [columns, rows] of [
      [3, 3],
      [4, 4],
      [5, 5],
      [7, 5],
      [9, 5],
      [15, 10],
      [20, 12],
    ]) {
      await show(page, columns, rows);
      const readable = await alignment(page);
      expect(readable.size).toBe(44);
      if (width === 390 && columns >= 9) expect(readable.scrolls).toBe(true);
      expect(await page.locator('.cell[tabindex="0"]').count()).toBe(1);
      await page
        .getByRole("button", { name: "Fit overview", exact: true })
        .click();
      const overview = await alignment(page);
      expect(overview.scrolls).toBe(false);
      if (columns === 20) await page.locator(".cell").last().focus();
      await page
        .getByRole("button", { name: "Zoom to selection", exact: true })
        .click();
      expect((await alignment(page)).size).toBe(44);
      await expect(
        columns === 20
          ? page.locator(".cell").last()
          : page.locator(".cell").first(),
      ).toBeFocused();
      if (width === 390 && columns === 20) {
        const visible = await page
          .locator(".cell")
          .last()
          .evaluate((cell) => {
            const viewport = document
              .querySelector(".board-viewport")!
              .getBoundingClientRect();
            const bounds = cell.getBoundingClientRect();
            return (
              bounds.left >= viewport.left &&
              bounds.right <= viewport.right &&
              bounds.top >= viewport.top &&
              bounds.bottom <= viewport.bottom
            );
          });
        expect(visible).toBe(true);
      }
      const targets = await page
        .locator(".viewport-controls button, .number-pad button")
        .evaluateAll((buttons) =>
          buttons
            .map((button) => button.getBoundingClientRect())
            .every((rect) => rect.width >= 44 && rect.height >= 44),
        );
      expect(targets).toBe(true);
      if (width === 390) {
        const keypad = await page.locator(".number-pad").evaluate((element) => {
          const box = element.getBoundingClientRect();
          return {
            position: getComputedStyle(element).position,
            visible: box.top >= 0 && box.bottom <= innerHeight + 1,
          };
        });
        expect(keypad.position).toBe("sticky");
        expect(keypad.visible).toBe(true);
      }
      await page.screenshot({
        path: testInfo.outputPath(`${width}-${columns}x${rows}.png`),
        fullPage: true,
      });
    }
  }
});

test("keyboard has one roving stop, row endpoints, exit, fixed-clue discovery, and guarded form shortcuts", async ({
  page,
}) => {
  await show(page);
  const cells = page.locator(".cell");
  await cells.first().focus();
  await expect(cells.first()).toHaveAccessibleName(/region 1, given, value 1/);
  await page.keyboard.press("End");
  await expect(cells.nth(8)).toBeFocused();
  await page.keyboard.press("ArrowDown");
  await expect(cells.nth(17)).toBeFocused();
  await page.keyboard.press("Home");
  await expect(cells.nth(9)).toBeFocused();
  expect(await page.locator('.cell[tabindex="0"]').count()).toBe(1);
  await page.keyboard.press("Tab");
  await expect(cells.nth(9)).not.toBeFocused();
  await cells.nth(9).focus();
  await page.keyboard.press("n");
  await expect(page.getByLabel("Manual notes", { exact: false })).toBeChecked();
  await page.keyboard.press("Escape");
  await expect(
    page.getByLabel("Manual notes", { exact: false }),
  ).not.toBeChecked();
  await page.getByLabel("Manual notes", { exact: false }).focus();
  await page.keyboard.press("n");
  await expect(
    page.getByLabel("Manual notes", { exact: false }),
  ).not.toBeChecked();
  await page.getByLabel("Manual notes", { exact: false }).check();
  await page.keyboard.press("Escape");
  await expect(
    page.getByLabel("Manual notes", { exact: false }),
  ).not.toBeChecked();
  let commands = 0;
  page.on("request", (request) => {
    if (request.url().endsWith("/commands")) commands++;
  });
  await page.evaluate(() => {
    const input = document.createElement("input");
    input.id = "shortcut-test";
    document.querySelector("main")!.append(input);
    input.focus();
  });
  await page.keyboard.type("19n0");
  await expect(page.locator("#shortcut-test")).toHaveValue("19n0");
  expect(commands).toBe(0);
  await page.getByRole("button", { name: "Rules and keyboard help" }).click();
  await expect(page.getByRole("dialog")).toBeVisible();
  await page.keyboard.press("Escape");
  await expect(page.getByRole("dialog")).not.toBeVisible();
  await expect(
    page.getByRole("button", { name: "Rules and keyboard help" }),
  ).toBeFocused();
});

async function scaled(browser: Browser, dpr: number, zoom: number) {
  const context = await browser.newContext({
    viewport: { width: 1100, height: 900 },
    deviceScaleFactor: dpr,
  });
  const page = await context.newPage();
  await show(page);
  // CSS zoom exercises actual layout at fractional browser scale, not a screenshot resize.
  await page.evaluate((scale) => {
    document.documentElement.style.zoom = String(scale);
  }, zoom);
  await alignment(page);
  await page.getByRole("button", { name: "Fit overview", exact: true }).click();
  await alignment(page);
  await context.close();
}
test("fractional DPR/zoom preserve shared SVG and HTML geometry; 200% text does not hide controls", async ({
  browser,
  page,
}) => {
  await scaled(browser, 1.25, 1.1);
  await scaled(browser, 1.5, 1.25);
  await page.setViewportSize({ width: 390, height: 900 });
  await show(page, 20, 12);
  await page.evaluate(() => {
    for (const element of Array.from(
      document.querySelectorAll<HTMLElement>("button,p,label,h2"),
    )) {
      if (!element.closest(".board"))
        element.style.fontSize = `${parseFloat(getComputedStyle(element).fontSize) * 2}px`;
    }
  });
  await alignment(page);
  await expect(
    page.getByRole("button", { name: "Zoom to selection", exact: true }),
  ).toBeVisible();
});

test("deterministic concave/hole terrain, notes 1–9, all themes and reduced detail stay legible", async ({
  page,
}, testInfo) => {
  for (const theme of ["light", "dark", "high-contrast"] as const) {
    await show(page, 9, 5, { ...settings, theme });
    await expect(
      page.locator(".terrain-region[data-region='0'] path").first(),
    ).not.toHaveAttribute("d", /M1 1/);
    await expect(
      page.locator(".terrain-region[data-region='4']"),
    ).toHaveAttribute("data-biome", "Forest");
    const notes = page.locator(".cell").nth(34).locator(".manual-notes");
    expect(await notes.locator("span").allTextContents()).toEqual([
      "1",
      "2",
      "3",
      "4",
      "5",
      "6",
      "7",
      "8",
      "9",
    ]);
    await expect(page.locator(".cell").nth(11)).toHaveAccessibleName(
      /notes 1, conflict/,
    );
    await page.locator(".cell").nth(34).focus();
    expect(
      await notes.evaluate((element) =>
        parseFloat(getComputedStyle(element).fontSize),
      ),
    ).toBeGreaterThanOrEqual(10);
    const contrast = await page
      .locator(".cell-value,.manual-notes")
      .evaluateAll((elements) => {
        function luminance(color: string) {
          const values = color
            .match(/[\d.]+/g)!
            .slice(0, 3)
            .map(Number)
            .map((value) => value / 255)
            .map((value) =>
              value <= 0.04045
                ? value / 12.92
                : ((value + 0.055) / 1.055) ** 2.4,
            );
          return values[0] * 0.2126 + values[1] * 0.7152 + values[2] * 0.0722;
        }
        return Math.min(
          ...elements
            .filter((element) => element.textContent?.trim())
            .map((element) => {
              const style = getComputedStyle(element);
              return (
                (luminance(style.backgroundColor) + 0.05) /
                (luminance(style.color) + 0.05)
              );
            }),
        );
      });
    expect(contrast).toBeGreaterThanOrEqual(4.5);
    if (theme === "high-contrast")
      await expect(page.locator(".biome-texture").first()).toBeHidden();
    await page.screenshot({
      path: testInfo.outputPath(`${theme}-terrain.png`),
      fullPage: true,
    });
  }
  await show(page, 9, 5, { ...settings, reducedDetail: true });
  await expect(page.locator(".biome-texture").first()).toBeHidden();
  await page.screenshot({
    path: testInfo.outputPath("reduced-detail.png"),
    fullPage: true,
  });
});
