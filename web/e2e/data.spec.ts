import { expect, test } from "@playwright/test";
import { readFile } from "node:fs/promises";
import { z } from "zod";
import {
  archiveSchema,
  historySchema,
  settingsSnapshotSchema,
} from "../src/dataApi";
import { bootstrapSchema } from "../src/api";

test.afterEach(async ({ request }) => {
  const bootstrap = bootstrapSchema.parse(
    await (await request.get("/api/v1/bootstrap")).json(),
  );
  const saved = settingsSnapshotSchema.parse(
    await (await request.get("/api/v1/settings")).json(),
  );
  expect(
    (
      await request.post("/api/v1/settings", {
        headers: {
          Origin: "http://localhost:18093",
          "X-Tiwanaku-Token": bootstrap.requestToken,
        },
        data: {
          revision: saved.revision,
          settings: {
            locale: "en",
            theme: "light",
            reducedDetail: false,
            showConflicts: false,
            preset: "7x5",
            maxRegionSize: 5,
            difficulty: "EASY",
            playerTag: "",
          },
        },
      })
    ).ok(),
  ).toBe(true);
});

test("settings and selected player persist after profile clearing without relabeling saved puzzles", async ({
  page,
  browser,
}) => {
  await page.goto("/settings");
  await page
    .getByRole("combobox", { name: "Language", exact: true })
    .selectOption("de");
  await page
    .getByRole("combobox", { name: "Theme", exact: true })
    .selectOption("light");
  await page
    .getByRole("combobox", { name: "Default board", exact: true })
    .selectOption("3x3");
  await page
    .getByRole("combobox", { name: "Default maximum region", exact: true })
    .selectOption("9");
  await page.getByLabel("Current player", { exact: false }).fill("ab");
  await page
    .getByRole("button", { name: "Save settings", exact: true })
    .click();
  await expect(
    page.getByRole("heading", { name: "Einstellungen", exact: true }),
  ).toBeVisible();
  await page.reload();
  await expect(
    page.getByRole("combobox", { name: "Sprache", exact: true }),
  ).toHaveValue("de");
  await expect(page.getByLabel("Current player", { exact: false })).toHaveValue(
    "AB",
  );
  const fresh = await browser.newContext({ baseURL: "http://localhost:18093" });
  try {
    const newPage = await fresh.newPage();
    await newPage.goto("/settings");
    await expect(
      newPage.getByRole("combobox", { name: "Sprache", exact: true }),
    ).toHaveValue("de");
    await expect(
      newPage.getByRole("combobox", { name: "Default board", exact: true }),
    ).toHaveValue("3x3");
    await newPage
      .getByRole("link", { name: "Neues Rätsel", exact: true })
      .click();
    await expect(
      newPage.getByRole("combobox", { name: "BOARD", exact: true }),
    ).toHaveValue("3x3");
  } finally {
    await fresh.close();
  }
});

test("legacy preview/import/filter/edit/export/delete retains raw data and reports corrupt records", async ({
  page,
  request,
}) => {
  await page.goto("/history");
  const raw =
    "P05-LEGACY|1000|80;P05-LEGACY|2000|60|1793110000000|<old>;broken";
  await page.getByLabel("Import source", { exact: true }).fill(raw);
  await page
    .getByRole("button", { name: "Preview import", exact: true })
    .click();
  await expect(page.locator(".import-preview")).toContainText(
    "2 valid records; 1 rejected",
  );
  page.once("dialog", (dialog) => dialog.accept());
  await page
    .getByRole("button", { name: "Confirm import", exact: true })
    .click();
  await expect(
    page.getByText("2 records imported.", { exact: false }),
  ).toBeVisible();
  await page.getByLabel("Board filter", { exact: true }).fill("P05-LEGACY");
  await page
    .getByRole("button", { name: "Apply filters", exact: true })
    .click();
  await expect(page.locator(".history-summary")).toContainText("2 results");
  await expect(page.locator(".history-summary")).toContainText(
    "Total time: 3 s across 2 timed records",
  );
  await expect(page.locator(".history-summary")).toContainText("70.00%");
  await expect(
    page.locator(".history-table .saved-player").filter({ hasText: "<old>" }),
  ).toBeVisible();
  await expect(
    page.locator(".history-table tbody tr").first().locator("td").nth(2),
  ).toHaveText("Legacy clicks");
  const originalDownload = page.waitForEvent("download");
  await page
    .getByRole("button", {
      name: "Download retained raw import source",
      exact: true,
    })
    .click();
  const original = await originalDownload;
  expect(await readFile((await original.path())!, "utf8")).toBe(raw);
  const exportDownload = page.waitForEvent("download");
  await page
    .getByRole("button", { name: "Export history and settings", exact: true })
    .click();
  const downloaded = await exportDownload;
  const archive = archiveSchema.parse(
    JSON.parse(await readFile((await downloaded.path())!, "utf8")),
  );
  expect(archive.checksum).toHaveLength(64);
  expect(archive.payload).toContain("P05-LEGACY");
  const row = page.locator(".history-table tbody tr").first();
  await row.getByLabel("New player tag", { exact: true }).fill("xy");
  await row
    .getByRole("button", { name: "Save player tag", exact: true })
    .click();
  await expect(
    page.locator(".saved-player").filter({ hasText: "XY" }),
  ).toHaveCount(1);
  page.once("dialog", (dialog) => dialog.accept());
  await page
    .locator(".history-table tbody tr")
    .first()
    .getByRole("button", { name: "Delete history entry", exact: true })
    .click();
  await expect(page.locator(".history-summary")).toContainText("1 results");
  page.once("dialog", (dialog) => dialog.accept());
  await page
    .getByRole("button", { name: "Confirm import", exact: true })
    .click();
  await expect(
    page.getByText("0 records imported.", { exact: false }),
  ).toBeVisible();
  await expect(page.locator(".history-summary")).toContainText("1 results");
  const backend = historySchema.parse(
    await (await request.get("/api/v1/history?mapSize=P05-LEGACY")).json(),
  );
  expect(backend.entries).toHaveLength(1);
  await page
    .getByRole("combobox", { name: "Import format", exact: true })
    .selectOption("logical");
  await page.getByLabel("Import file", { exact: true }).setInputFiles({
    name: "history.json",
    mimeType: "application/json",
    buffer: Buffer.from(JSON.stringify(archive)),
  });
  await page
    .getByRole("button", { name: "Preview import", exact: true })
    .click();
  await expect(page.locator(".import-preview")).toContainText(
    "2 valid records; 0 rejected",
  );
  page.once("dialog", (dialog) => dialog.accept());
  await page
    .getByRole("button", { name: "Confirm import", exact: true })
    .click();
  await expect(
    page.getByText("0 records imported.", { exact: false }),
  ).toBeVisible();
  await expect(page.locator(".history-summary")).toContainText("1 results");
  await page.reload();
  await page.getByLabel("Board filter", { exact: true }).fill("P05-LEGACY");
  await page
    .getByRole("button", { name: "Apply filters", exact: true })
    .click();
  await expect(page.locator(".history-summary")).toContainText("1 results");
});

test("real legacy TeaVM preference reader recognizes the exact same-origin key without modifying its source", async ({
  page,
}) => {
  await page.goto("/history");
  const raw = "P05-BROWSER|3000|90|1793110000000|ABC|10|1";
  const key = "pref:app:TiwanakuScoreboard:scoreboard_resultss";
  await page.evaluate(({ key, raw }) => localStorage.setItem(key, raw), {
    key,
    raw,
  });
  const legacy = await readFile("../docs/app.js", "utf8");
  const marker = "B.main=C;";
  expect(legacy).toContain(marker);
  // Execute the unchanged compiled legacy WebPreferences constructor; only expose a test reader.
  const fixture = legacy.replace(
    marker,
    `
    B.legacyPreferenceFixture=function(){
      const prefs=B0g(window.localStorage,Bh("app:TiwanakuScoreboard"),0);
      const strings=[], seen=new Set();
      function visit(value,depth){
        if(!value || typeof value!=="object" || depth<0 || seen.has(value)) return;
        seen.add(value);
        if(typeof value.PD==="string") strings.push(value.PD);
        for(const child of Object.values(value)) visit(child,depth-1);
      }
      visit(prefs.a4S,6);
      return {prefix:Bg(prefs.a48),strings};
    };${marker}`,
  );
  await page.route("**/__test__/legacy-reader.js", (route) =>
    route.fulfill({
      contentType: "application/javascript",
      body: fixture,
    }),
  );
  await page.addScriptTag({ url: "/__test__/legacy-reader.js" });
  const decoded = z
    .object({ prefix: z.string(), strings: z.array(z.string()) })
    .parse(await page.evaluate("legacyPreferenceFixture()"));
  expect(decoded.prefix).toBe("pref:app:TiwanakuScoreboard:");
  expect(decoded.strings).toContain(raw);
  expect(decoded.strings).toContain("scoreboard_results");
  page.once("dialog", (dialog) => dialog.accept());
  await page
    .getByRole("button", { name: "Read this origin's legacy key", exact: true })
    .click();
  await expect(page.getByLabel("Import source", { exact: true })).toHaveValue(
    raw,
  );
  await page
    .getByRole("button", { name: "Preview import", exact: true })
    .click();
  await expect(page.locator(".import-preview")).toContainText(
    "1 valid records; 0 rejected",
  );
  page.once("dialog", (dialog) => dialog.accept());
  await page
    .getByRole("button", { name: "Confirm import", exact: true })
    .click();
  await expect(
    page.getByText("1 records imported.", { exact: false }),
  ).toBeVisible();
  expect(await page.evaluate((key) => localStorage.getItem(key), key)).toBe(
    raw,
  );
});

test("history storage errors remain explicit instead of looking like empty success", async ({
  page,
}) => {
  await page.route("**/api/v1/history?*", (route) =>
    route.fulfill({
      status: 503,
      contentType: "application/problem+json",
      body: JSON.stringify({
        detail: "Local storage failed. Check free disk space.",
      }),
    }),
  );
  await page.goto("/history");
  await expect(page.getByRole("alert")).toContainText("Local storage failed");
  await expect(
    page.getByText("No history matches these filters.", { exact: true }),
  ).toHaveCount(0);
});
