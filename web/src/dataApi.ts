import { z } from "zod";
import {
  difficultySchema,
  loadBootstrap,
  request,
  tileShapeSchema,
} from "./api";

const integer = z.number().int().safe().nonnegative();
export const settingsSchema = z.strictObject({
  locale: z.enum(["en", "de"]),
  theme: z.enum(["dark", "light", "high-contrast"]),
  reducedDetail: z.boolean(),
  showConflicts: z.boolean(),
  preset: z.enum(["3x3", "4x4", "5x5", "7x5", "9x5", "15x10", "20x12"]),
  maxRegionSize: integer.min(5).max(9),
  difficulty: difficultySchema,
  playerTag: z.string().regex(/^[A-Za-z]{0,3}$/),
  shape: tileShapeSchema,
});
export const settingsSnapshotSchema = z.strictObject({
  revision: integer,
  settings: settingsSchema,
});
export type LocalSettings = z.infer<typeof settingsSchema>;
export const historyEntrySchema = z.strictObject({
  recordId: z.uuid(),
  attemptId: z.uuid().nullable(),
  mapSize: z.string().min(1).max(64),
  completedMillis: integer.nullable(),
  elapsedMillis: integer.nullable(),
  playerTag: z.string().max(128).nullable(),
  scoringVersion: z.enum([
    "direct-entry-v1",
    "legacy-clicks-v1",
    "unrecorded-v0",
  ]),
  valueEntries: integer.nullable(),
  legacyAccuracy: integer.max(100).nullable(),
  totalClicks: integer.nullable(),
  totalMissClicks: integer.nullable(),
  assistance: z
    .strictObject({
      wrongEntries: integer.nullable(),
      candidates: integer,
      explanations: integer,
      reveals: integer,
      continuousConflicts: z.boolean(),
      replay: z.boolean(),
    })
    .nullable(),
  puzzleSettings: z
    .strictObject({
      columns: integer.min(1).max(240),
      rows: integer.min(1).max(240),
      maxRegionSize: integer.min(5).max(9),
      difficulty: difficultySchema,
      seed: z.string().regex(/^-?[0-9]{1,19}$/),
      engineVersion: z.string().max(64),
      generatorVersion: z.string().max(64),
      shape: tileShapeSchema,
    })
    .nullable(),
});
export type HistoryEntry = z.infer<typeof historyEntrySchema>;
export const historySchema = z.strictObject({
  entries: z.array(historyEntrySchema).max(10_000),
  summary: z.strictObject({
    count: integer,
    knownElapsedCount: integer,
    elapsedSum: integer,
    legacyAccuracyCount: integer,
    legacyAccuracyMean: z.number().min(0).max(100).nullable(),
  }),
});
export const archiveSchema = z.strictObject({
  schemaVersion: z.literal(1),
  checksum: z.string().regex(/^[a-f0-9]{64}$/),
  payload: z.string(),
});
export const previewSchema = z.strictObject({
  fingerprint: z.string().regex(/^[a-f0-9]{64}$/),
  entries: z.array(historyEntrySchema),
  rejected: z.array(z.string()),
  alreadyImported: z.boolean(),
});
export const outcomeSchema = z.strictObject({
  fingerprint: z.string(),
  imported: integer,
  rejected: z.array(z.string()),
  alreadyImported: z.boolean(),
});
export const sourceSchema = z.strictObject({ source: z.string() });

export const playerTagPattern = /^[A-Za-z]{1,3}$/;
export function playersFrom(entries: HistoryEntry[], extra: string[] = []) {
  return [
    ...new Set(
      [...entries.map((entry) => entry.playerTag?.trim() ?? ""), ...extra]
        .map((tag) => tag.toUpperCase())
        .filter((tag) => playerTagPattern.test(tag)),
    ),
  ].sort((first, second) => first.localeCompare(second));
}
export async function loadPlayers(extra: string[] = []) {
  const all = await dataRequest(
    "/api/v1/history?player=&mapSize=&scoring=&assistance=&sort=date-desc",
    historySchema,
  );
  return playersFrom(all.entries, extra);
}
export function assignPlayer(recordId: string, playerTag: string) {
  return dataRequest(`/api/v1/history/${recordId}/player`, historySchema, {
    method: "POST",
    body: JSON.stringify({ playerTag }),
  });
}

export async function dataRequest<T>(
  path: string,
  schema: z.ZodType<T>,
  init: RequestInit = {},
) {
  return request(await loadBootstrap(), path, schema, init);
}
export function download(
  content: BlobPart,
  filename: string,
  type = "application/json",
) {
  const url = URL.createObjectURL(new Blob([content], { type }));
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = filename;
  anchor.click();
  window.setTimeout(() => URL.revokeObjectURL(url), 1_000);
}
