import { describe, expect, it } from "vitest";
import { archiveSchema, historyEntrySchema, settingsSchema } from "./dataApi";

describe("durable data contracts", () => {
  it("preserves unknown legacy fields rather than synthesizing entry accuracy or settings", () => {
    const legacy = {
      recordId: crypto.randomUUID(),
      attemptId: null,
      mapSize: "SMALL",
      completedMillis: null,
      elapsedMillis: 1000,
      playerTag: "<old>",
      scoringVersion: "legacy-clicks-v1",
      valueEntries: null,
      legacyAccuracy: 75,
      totalClicks: null,
      totalMissClicks: null,
      assistance: null,
      puzzleSettings: null,
    };
    expect(historyEntrySchema.parse(legacy)).toEqual(legacy);
    expect(
      historyEntrySchema.safeParse({ ...legacy, elapsedMillis: -1 }).success,
    ).toBe(false);
    expect(
      historyEntrySchema.safeParse({ ...legacy, scoringVersion: "invented" })
        .success,
    ).toBe(false);
    expect(
      historyEntrySchema.safeParse({ ...legacy, extra: true }).success,
    ).toBe(false);
  });
  it("validates durable settings and version/checksum envelope", () => {
    const settings = {
      locale: "en",
      theme: "light",
      reducedDetail: false,
      showConflicts: false,
      preset: "7x5",
      maxRegionSize: 5,
      difficulty: "EASY",
      playerTag: "",
    };
    expect(settingsSchema.parse(settings)).toEqual(settings);
    expect(
      settingsSchema.safeParse({ ...settings, playerTag: "123" }).success,
    ).toBe(false);
    expect(
      settingsSchema.safeParse({ ...settings, preset: "2x1" }).success,
    ).toBe(false);
    expect(
      settingsSchema.safeParse({ ...settings, showConflicts: undefined })
        .success,
    ).toBe(false);
    expect(
      archiveSchema.safeParse({
        schemaVersion: 2,
        checksum: "a".repeat(64),
        payload: "{}",
      }).success,
    ).toBe(false);
  });
});
