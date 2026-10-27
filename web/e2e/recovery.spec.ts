import { expect, test } from "@playwright/test";
import { execFileSync } from "node:child_process";
import { mkdir, mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { launchServer } from "./server";
import { attemptSchema, bootstrapSchema, jobSchema } from "../src/api";
import {
  historySchema,
  outcomeSchema,
  previewSchema,
  settingsSnapshotSchema,
  sourceSchema,
} from "../src/dataApi";

test("Java restart, port change and physical restore retain history, settings and exactly-once completion", async ({
  playwright,
  browser,
}) => {
  test.setTimeout(120_000);
  const directory = await mkdtemp(join(tmpdir(), "tiwanaku-recovery-"));
  let stop: (() => Promise<void>) | undefined;
  try {
    const home = join(directory, "isolated-home");
    await mkdir(join(home, ".prefs"), { recursive: true });
    const desktopFile = join(home, ".prefs", "TiwanakuScoreboard");
    const desktopRaw = await readFile(
      "../server/src/test/resources/fixtures/TiwanakuScoreboard.xml",
      "utf8",
    );
    await writeFile(desktopFile, desktopRaw);
    stop = await launchServer(18094, directory, home);
    const first = await playwright.request.newContext({
      baseURL: "http://localhost:18094",
    });
    const bootstrap = bootstrapSchema.parse(
      await (await first.get("/api/v1/bootstrap")).json(),
    );
    const headers = {
      Origin: "http://localhost:18094",
      "X-Tiwanaku-Token": bootstrap.requestToken,
    };
    expect(
      (
        await first.post("/api/v1/data/desktop-preview?confirmed=false", {
          headers,
        })
      ).status(),
    ).toBe(400);
    expect(
      sourceSchema.parse(
        await (
          await first.post("/api/v1/data/desktop-preview?confirmed=true", {
            headers,
          })
        ).json(),
      ).source,
    ).toBe(desktopRaw);
    const desktopPreview = previewSchema.parse(
      await (
        await first.post("/api/v1/data/preview", {
          headers,
          data: { source: desktopRaw, format: "legacy" },
        })
      ).json(),
    );
    expect(
      outcomeSchema.parse(
        await (
          await first.post("/api/v1/data/import", {
            headers,
            data: {
              source: desktopRaw,
              format: "legacy",
              fingerprint: desktopPreview.fingerprint,
              confirmed: true,
            },
          })
        ).json(),
      ).imported,
    ).toBe(3);
    expect(await readFile(desktopFile, "utf8")).toBe(desktopRaw);
    const settings = settingsSnapshotSchema.parse(
      await (await first.get("/api/v1/settings")).json(),
    );
    expect(
      (
        await first.post("/api/v1/settings", {
          headers,
          data: {
            revision: settings.revision,
            settings: {
              ...settings.settings,
              playerTag: "XYZ",
              theme: "light",
            },
          },
        })
      ).ok(),
    ).toBe(true);
    const raw = "RECOVERY|1234|87";
    const preview = previewSchema.parse(
      await (
        await first.post("/api/v1/data/preview", {
          headers,
          data: { source: raw, format: "legacy" },
        })
      ).json(),
    );
    expect(
      outcomeSchema.parse(
        await (
          await first.post("/api/v1/data/import", {
            headers,
            data: {
              source: raw,
              format: "legacy",
              fingerprint: preview.fingerprint,
              confirmed: true,
            },
          })
        ).json(),
      ).imported,
    ).toBe(1);
    const job = jobSchema.parse(
      await (
        await first.post("/api/v1/generation-jobs", {
          headers,
          data: {
            columns: 2,
            rows: 1,
            maxRegionSize: 7,
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
              await first.get(`/api/v1/generation-jobs/${job.jobId}`)
            ).json(),
          ).status,
      )
      .toBe("GENERATED");
    const editorSessionId = crypto.randomUUID();
    const created = attemptSchema.parse(
      await (
        await first.post("/api/v1/attempts", {
          headers,
          data: {
            requestId: crypto.randomUUID(),
            generationJobId: job.jobId,
            editorSessionId,
          },
        })
      ).json(),
    );
    const next = settingsSnapshotSchema.parse(
      await (await first.get("/api/v1/settings")).json(),
    );
    expect(
      (
        await first.post("/api/v1/settings", {
          headers,
          data: {
            revision: next.revision,
            settings: {
              ...next.settings,
              maxRegionSize: 9,
              difficulty: "HARD",
            },
          },
        })
      ).ok(),
    ).toBe(true);
    const cell = created.givens.indexOf(0);
    const finalMove = {
      commandId: crypto.randomUUID(),
      expectedRevision: 0,
      editorSessionId,
      type: "SET_VALUE",
      cellIndex: cell,
      value: created.givens[1 - cell] === 1 ? 2 : 1,
    };
    expect(
      (
        await first.post(`/api/v1/attempts/${created.attemptId}/commands`, {
          headers,
          data: finalMove,
        })
      ).ok(),
    ).toBe(true);
    const before = historySchema.parse(
      await (await first.get("/api/v1/history")).json(),
    );
    expect(before.entries).toHaveLength(5);
    const result = before.entries.find(
      (entry) => entry.attemptId === created.attemptId,
    )!;
    expect(result.playerTag).toBe("XYZ");
    expect(result.puzzleSettings).toMatchObject({
      maxRegionSize: 7,
      difficulty: "EASY",
      seed: "3045",
    });
    const backup = await first.post("/api/v1/data/backup?confirmed=true", {
      headers,
    });
    expect(backup.ok()).toBe(true);
    const bytes = await backup.body();
    expect(bytes.subarray(0, 2).toString()).toBe("PK");
    const zip = join(directory, "downloaded-backup.zip");
    await writeFile(zip, bytes);
    await first.dispose();
    await stop();
    stop = undefined;

    stop = await launchServer(18095, directory);
    const second = await playwright.request.newContext({
      baseURL: "http://localhost:18095",
    });
    expect(
      historySchema.parse(await (await second.get("/api/v1/history")).json()),
    ).toEqual(before);
    const boot2 = bootstrapSchema.parse(
      await (await second.get("/api/v1/bootstrap")).json(),
    );
    expect(boot2.processInstanceId).not.toBe(bootstrap.processInstanceId);
    const headers2 = {
      Origin: "http://localhost:18095",
      "X-Tiwanaku-Token": boot2.requestToken,
    };
    expect(
      sourceSchema.parse(
        await (
          await second.get(`/api/v1/data/imports/${preview.fingerprint}`)
        ).json(),
      ).source,
    ).toBe(raw);
    expect(
      outcomeSchema.parse(
        await (
          await second.post("/api/v1/data/import", {
            headers: headers2,
            data: {
              source: raw,
              format: "legacy",
              fingerprint: preview.fingerprint,
              confirmed: true,
            },
          })
        ).json(),
      ).alreadyImported,
    ).toBe(true);
    expect(
      (
        await second.post(`/api/v1/attempts/${created.attemptId}/commands`, {
          headers: headers2,
          data: finalMove,
        })
      ).ok(),
    ).toBe(true);
    expect(
      historySchema.parse(await (await second.get("/api/v1/history")).json()),
    ).toEqual(before);
    const clean = await browser.newContext();
    try {
      const page = await clean.newPage();
      await page.goto("http://localhost:18095/history");
      await expect(page.locator(".history-summary")).toContainText("5 results");
      await expect(
        page.locator(".saved-player").filter({ hasText: "XYZ" }),
      ).toHaveCount(1);
    } finally {
      await clean.close();
    }
    await second.dispose();
    await stop();
    stop = undefined;

    const restored = join(directory, "restored");
    await mkdir(restored);
    execFileSync("jar", ["xf", zip, "tiwanaku.mv.db"], { cwd: restored });
    stop = await launchServer(18096, restored);
    const third = await playwright.request.newContext({
      baseURL: "http://localhost:18096",
    });
    expect(
      historySchema.parse(await (await third.get("/api/v1/history")).json()),
    ).toEqual(before);
    expect(
      settingsSnapshotSchema.parse(
        await (await third.get("/api/v1/settings")).json(),
      ).settings.playerTag,
    ).toBe("XYZ");
    expect(
      sourceSchema.parse(
        await (
          await third.get(`/api/v1/data/imports/${preview.fingerprint}`)
        ).json(),
      ).source,
    ).toBe(raw);
    await third.dispose();
  } finally {
    try {
      if (stop) await stop();
    } finally {
      await rm(directory, { recursive: true, force: true });
    }
  }
});
