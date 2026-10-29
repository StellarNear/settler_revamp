import { z } from "zod";

const integer = z.number().int().safe().nonnegative();
export const difficulties = [
  "EASY",
  "MEDIUM",
  "HARD",
  "EXTREME",
  "GODLIKE",
] as const;
export const difficultySchema = z.enum(difficulties);
export const tileShapes = [
  "SQUARE",
  "SQUARE_SIDES_ONLY",
  "HEXAGON",
  "TRIANGLE",
  "PENTAGON",
  "PENTAGON_CORNERS",
] as const;
export type TileShape = (typeof tileShapes)[number];
/** Old squares include corners; old pentagons use sides only. Missing shapes were square. */
export const tileShapeSchema = z
  .enum(tileShapes)
  .nullish()
  .transform((shape): TileShape => shape ?? "SQUARE");
export const bootstrapSchema = z.strictObject({
  apiVersion: z.literal("v1"),
  processInstanceId: z.uuid(),
  requestToken: z.string().min(1),
  maxCells: z.literal(240),
  maxRegionSize: z.literal(9),
});
export const jobSchema = z.strictObject({
  jobId: z.uuid(),
  status: z.enum([
    "QUEUED",
    "CANCELLING",
    "CANCELLED",
    "GENERATED",
    "EXHAUSTED",
    "UNKNOWN",
    "FAILED",
  ]),
  termination: z
    .enum([
      "EXHAUSTED",
      "TWO_SOLUTIONS_FOUND",
      "CANCELLED",
      "THREAD_INTERRUPTED",
      "NODE_BUDGET_EXCEEDED",
      "DEADLINE_EXCEEDED",
    ])
    .nullable(),
  error: z.string().nullable(),
  elapsedMillis: integer,
  visitedNodes: integer,
  progressPercent: integer.max(100).default(0),
  tryNumber: integer.min(1).default(1),
});
export const progressSchema = z.strictObject({
  notes: z.array(integer.max(511)).min(1).max(240),
  elapsedMillis: integer,
  started: z.boolean(),
  replay: z.boolean(),
  valueEntryCount: integer,
  wrongEntryCount: integer.nullable(),
  candidateRequests: integer,
  explanationRequests: integer,
  totalClicks: integer,
  totalMissClicks: integer,
  legacyAccuracy: integer.min(0).max(100).nullable(),
  directValueEntryUsed: z.boolean(),
  reveals: integer,
  continuousConflictsUsed: z.boolean(),
  showConflicts: z.boolean(),
  canUndo: z.boolean(),
  canRedo: z.boolean(),
  hasCheckpoint: z.boolean(),
  conflicts: z.array(integer.max(239)).max(240),
  candidates: z.array(integer.min(1).max(9)).max(9),
  helpMessage: z.string(),
  timingSequence: integer,
});
export const attemptSchema = z
  .strictObject({
    attemptId: z.uuid(),
    puzzleId: z.uuid(),
    columns: integer.min(1).max(240),
    rows: integer.min(1).max(240),
    regionIds: z.array(integer).min(1).max(240),
    givens: z.array(integer.max(9)).min(1).max(240),
    values: z.array(integer.max(9)).min(1).max(240),
    status: z.enum(["READY", "PLAYING", "SUSPENDED", "COMPLETED"]),
    revision: integer,
    editorSessionId: z.string().min(16).max(128),
    editorLeaseExpiresAt: z.iso.datetime({ offset: true }).nullable(),
    moveCount: integer,
    progress: progressSchema,
    puzzleSettings: z
      .object({
        maxRegionSize: integer.min(1).max(9),
        difficulty: difficultySchema,
        shape: tileShapeSchema,
      })
      .nullable()
      .default(null),
    cellPolygons: z
      .array(z.array(z.number().finite()).min(6).max(12))
      .max(240)
      .nullable()
      .default(null),
  })
  .superRefine((attempt, context) => {
    const cells = attempt.columns * attempt.rows;
    if (
      cells > 240 ||
      [
        attempt.regionIds,
        attempt.givens,
        attempt.values,
        attempt.progress.notes,
      ].some((a) => a.length !== cells)
    ) {
      context.addIssue({
        code: "custom",
        message: "Board dimensions and arrays must agree",
      });
      return;
    }
    if (attempt.cellPolygons && attempt.cellPolygons.length !== cells) {
      context.addIssue({
        code: "custom",
        message: "Tile geometry must cover every cell",
      });
      return;
    }
    const sizes = new Map<number, number>();
    for (const id of attempt.regionIds) sizes.set(id, (sizes.get(id) ?? 0) + 1);
    if (
      attempt.progress.notes.some(
        (mask, index) =>
          mask >= 2 ** (sizes.get(attempt.regionIds[index]) ?? 0) ||
          (mask !== 0 && attempt.values[index] !== 0),
      ) ||
      attempt.progress.conflicts.some((index) => index >= cells)
    ) {
      context.addIssue({
        code: "custom",
        message: "Invalid manual notes or conflict indices",
      });
    }
    if (
      [...sizes.values()].some((size) => size > 9) ||
      attempt.values.some(
        (value, i) =>
          value > (sizes.get(attempt.regionIds[i]) ?? 0) ||
          (attempt.givens[i] !== 0 && value !== attempt.givens[i]),
      )
    ) {
      context.addIssue({
        code: "custom",
        message: "Invalid region size, value or changed given",
      });
    }
  });
export type Bootstrap = z.infer<typeof bootstrapSchema>;
export type AttemptSnapshot = z.infer<typeof attemptSchema>;
export type GenerationJob = z.infer<typeof jobSchema>;

const identifier = z.string().min(1).max(128);
const editorSession = z.string().min(16).max(128);
const commandFields = {
  commandId: identifier,
  expectedRevision: integer,
  editorSessionId: editorSession,
  timingSequence: integer.optional(),
  elapsedMillis: integer.optional(),
};
export const commandSchema = z.discriminatedUnion("type", [
  z.strictObject({
    ...commandFields,
    type: z.literal("SET_VALUE"),
    cellIndex: integer.max(239),
    value: integer.max(9),
  }),
  z.strictObject({
    ...commandFields,
    type: z.enum([
      "PAUSE",
      "RESUME",
      "TAKEOVER",
      "UNDO",
      "REDO",
      "CHECKPOINT",
      "RESTORE_CHECKPOINT",
      "CHECK",
      "SHOW_CONFLICTS",
      "CANDIDATES",
      "EXPLAIN",
      "REVEAL",
      "HEARTBEAT",
    ]),
    cellIndex: integer.max(239).nullable().optional(),
    value: integer.max(9).nullable().optional(),
  }),
  z.strictObject({
    ...commandFields,
    type: z.literal("CYCLE_VALUE"),
    cellIndex: integer.max(239),
    value: z.union([z.literal(-1), z.literal(1)]),
  }),
  z.strictObject({
    ...commandFields,
    type: z.literal("TOGGLE_NOTE"),
    cellIndex: integer.max(239),
    value: integer.min(1).max(9),
  }),
]);
export type CommandType = z.infer<typeof commandSchema>["type"];
export const creationSchema = z
  .strictObject({
    requestId: identifier,
    generationJobId: z.uuid().optional(),
    restartAttemptId: z.uuid().optional(),
    editorSessionId: editorSession,
  })
  .refine(
    (value) =>
      Boolean(value.generationJobId) !== Boolean(value.restartAttemptId),
    "Choose generation or restart",
  );
export const generationSchema = z
  .strictObject({
    columns: integer.min(1).max(240),
    rows: integer.min(1).max(240),
    maxRegionSize: integer.min(5).max(9),
    difficulty: difficultySchema,
    seed: z.number().int().safe(),
    shape: z.enum(tileShapes),
  })
  .refine(
    (value) => value.columns * value.rows <= 240,
    "The board exceeds 240 cells",
  );
export const resultSchema = z.strictObject({
  resultId: z.uuid(),
  attemptId: z.uuid(),
  values: z.array(integer.min(1).max(9)).min(1).max(240),
  moveCount: integer,
  completedAt: z.iso.datetime({ offset: true }),
  playerTag: z.string().max(32).nullable(),
  progress: progressSchema.nullable(),
});

export function jsonBody<T>(schema: z.ZodType<T>, value: unknown): string {
  return JSON.stringify(schema.parse(value));
}

export class HttpError extends Error {
  constructor(
    public readonly status: number,
    detail: string,
  ) {
    super(detail);
  }
}

export class ContractError extends Error {}

export async function readResponse<T>(
  response: Response,
  schema: z.ZodType<T>,
): Promise<T> {
  if (!response.ok) {
    const body = await response.text();
    let detail = `Local server returned HTTP ${response.status}`;
    if (response.headers.get("Content-Type")?.includes("json")) {
      const problem: unknown = JSON.parse(body);
      if (
        typeof problem === "object" &&
        problem !== null &&
        "detail" in problem &&
        typeof problem.detail === "string"
      ) {
        detail = problem.detail;
      }
    } else if (body) {
      detail = body;
    }
    throw new HttpError(response.status, detail);
  }
  let payload: unknown;
  try {
    payload = await response.json();
  } catch (error) {
    if (!(error instanceof SyntaxError)) throw error;
    throw new ContractError(
      "The local server returned invalid JSON. Edits are paused.",
    );
  }
  const parsed = schema.safeParse(payload);
  if (!parsed.success) {
    throw new ContractError(
      "The local server response is incompatible with UI v1. Restart with matching UI/server versions.",
    );
  }
  return parsed.data;
}

export async function loadBootstrap() {
  return readResponse(
    await fetch("/api/v1/bootstrap", { signal: AbortSignal.timeout(10_000) }),
    bootstrapSchema,
  );
}

export async function request<T>(
  bootstrap: Bootstrap,
  path: string,
  schema: z.ZodType<T>,
  init: RequestInit = {},
): Promise<T> {
  const headers = new Headers(init.headers);
  headers.set("Content-Type", "application/json");
  headers.set("X-Tiwanaku-Token", bootstrap.requestToken);
  return readResponse(
    await fetch(path, {
      ...init,
      headers,
      signal: AbortSignal.timeout(5_000),
    }),
    schema,
  );
}
