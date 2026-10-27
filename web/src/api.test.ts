import { describe, expect, it } from "vitest";
import { initialProgress, bootstrapFixture } from "./testFixtures";
import {
  attemptSchema,
  bootstrapSchema,
  ContractError,
  HttpError,
  readResponse,
} from "./api";
import {
  commandSchema,
  creationSchema,
  generationSchema,
  resultSchema,
} from "./api";

export const fixture = {
  attemptId: "5cdf1090-a740-4911-860f-d574709a6373",
  puzzleId: "7b823485-2e96-4849-bfb2-c890e4d8cce8",
  columns: 2,
  rows: 1,
  regionIds: [0, 0],
  givens: [0, 2],
  values: [0, 2],
  status: "READY",
  revision: 0,
  editorSessionId: "test-editor-session",
  editorLeaseExpiresAt: "2026-10-27T15:00:00Z",
  moveCount: 0,
  progress: initialProgress,
};

describe("v1 HTTP contracts", () => {
  it("round trips command, generation, creation and completed-result records", () => {
    const command = {
      commandId: "command-1",
      expectedRevision: 0,
      editorSessionId: fixture.editorSessionId,
      type: "SET_VALUE",
      cellIndex: 0,
      value: 1,
    };
    expect(commandSchema.parse(command)).toEqual(command);
    expect(commandSchema.safeParse({ ...command, value: -1 }).success).toBe(
      false,
    );
    expect(
      commandSchema.safeParse({ ...command, unexpected: true }).success,
    ).toBe(false);
    expect(
      generationSchema.safeParse({
        columns: 20,
        rows: 13,
        maxRegionSize: 5,
        difficulty: "EASY",
        seed: 1,
      }).success,
    ).toBe(false);
    const creation = {
      requestId: "creation-1",
      generationJobId: bootstrapFixture.processInstanceId,
      editorSessionId: fixture.editorSessionId,
    };
    expect(creationSchema.parse(creation)).toEqual(creation);
    const result = {
      resultId: fixture.puzzleId,
      attemptId: fixture.attemptId,
      values: [1, 2],
      moveCount: 1,
      completedAt: fixture.editorLeaseExpiresAt,
      playerTag: null,
      progress: null,
    };
    expect(resultSchema.parse(JSON.parse(JSON.stringify(result)))).toEqual(
      result,
    );
    expect(resultSchema.safeParse({ ...result, values: [0, 2] }).success).toBe(
      false,
    );
  });
  it("round trips valid snapshots without dropping fields", () => {
    expect(attemptSchema.parse(JSON.parse(JSON.stringify(fixture)))).toEqual(
      fixture,
    );
    expect(bootstrapSchema.parse(bootstrapFixture)).toEqual(bootstrapFixture);
  });
  it.each([
    { columns: 241 },
    { values: [0] },
    { givens: [0, 1] },
    { values: [3, 2] },
    { regionIds: [0, 1] },
    { revision: -1 },
    { extraField: true },
    { status: "UNKNOWN" },
  ])("rejects malformed or unknown snapshot data: %o", (patch) => {
    expect(attemptSchema.safeParse({ ...fixture, ...patch }).success).toBe(
      false,
    );
  });
  it("reports incompatible API versions", async () => {
    await expect(
      readResponse(
        Response.json({ ...bootstrapFixture, apiVersion: "v2" }),
        bootstrapSchema,
      ),
    ).rejects.toBeInstanceOf(ContractError);
  });
  it("treats malformed successful JSON as a contract failure", async () => {
    await expect(
      readResponse(new Response("{", { status: 200 }), bootstrapSchema),
    ).rejects.toBeInstanceOf(ContractError);
  });
  it("preserves explicit conflict details", async () => {
    await expect(
      readResponse(
        Response.json({ detail: "The editor lease expired" }, { status: 409 }),
        attemptSchema,
      ),
    ).rejects.toEqual(new HttpError(409, "The editor lease expired"));
  });
});
