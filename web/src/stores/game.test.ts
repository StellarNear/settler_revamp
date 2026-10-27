import { beforeEach, describe, expect, it, vi } from "vitest";
import { createPinia, setActivePinia } from "pinia";
import { useGameStore } from "./game";

const fixture = {
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
};
const bootstrap = {
  apiVersion: "v1",
  processInstanceId: "e715780e-1ceb-455e-a05a-de4805b76d03",
  requestToken: "test-token",
  maxCells: 240,
  maxRegionSize: 9,
};
const fetchMock = vi.fn<typeof fetch>();
function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((done) => {
    resolve = done;
  });
  return { promise, resolve };
}
beforeEach(() => {
  setActivePinia(createPinia());
  const storage = new Map([
    ["tiwanaku.editorSessionId", fixture.editorSessionId],
    ["tiwanaku.attemptId", fixture.attemptId],
  ]);
  vi.stubGlobal("sessionStorage", {
    getItem: (key: string) => storage.get(key) ?? null,
    setItem: (key: string, value: string) => storage.set(key, value),
  });
  vi.stubGlobal("fetch", fetchMock);
  fetchMock.mockReset();
});
async function connectedStore() {
  fetchMock
    .mockResolvedValueOnce(Response.json(bootstrap))
    .mockResolvedValueOnce(Response.json(fixture));
  const game = useGameStore();
  await game.connect();
  game.selectedCell = 0;
  return game;
}

describe("Java-authoritative snapshot store", () => {
  it("reports a missing saved game without deleting its pointer or blocking new generation", async () => {
    fetchMock
      .mockResolvedValueOnce(Response.json(bootstrap))
      .mockResolvedValueOnce(
        Response.json({ detail: "Attempt not found" }, { status: 404 }),
      );
    const game = useGameStore();
    await game.connect();
    expect(game.connected).toBe(true);
    expect(game.attempt).toBeUndefined();
    expect(game.errorMessage).toContain("no data was deleted");
    expect(sessionStorage.getItem("tiwanaku.attemptId")).toBe(
      fixture.attemptId,
    );
  });
  it("reconciles a conflict without replaying or queueing the rejected move", async () => {
    const game = await connectedStore();
    fetchMock
      .mockResolvedValueOnce(
        Response.json(
          { detail: "The attempt revision changed" },
          { status: 409 },
        ),
      )
      .mockResolvedValueOnce(Response.json({ ...fixture, revision: 1 }));
    await game.enterValue(1);
    expect(game.attempt?.revision).toBe(1);
    expect(game.errorMessage).toBe("The attempt revision changed");
    expect(game.statusMessage).toContain("retry your move");
    expect(fetchMock).toHaveBeenCalledTimes(4);
    expect(game.attempt?.values).toEqual(fixture.values);
  });
  it("retains the saved board when generation fails", async () => {
    const game = await connectedStore();
    fetchMock.mockResolvedValueOnce(
      Response.json({
        jobId: crypto.randomUUID(),
        status: "EXHAUSTED",
        termination: "EXHAUSTED",
        error: null,
        elapsedMillis: 10,
        visitedNodes: 1,
      }),
    );
    expect(await game.startPuzzle()).toBe(false);
    expect(game.attempt?.attemptId).toBe(fixture.attemptId);
    expect(game.statusMessage).toContain("previous game is unchanged");
    expect(game.errorMessage).toContain("EXHAUSTED");
    expect(game.pending).toBe(false);
  });
  it("permits one mutation and drops additional input rather than queuing it", async () => {
    const game = await connectedStore();
    const response = deferred<Response>();
    fetchMock.mockReturnValueOnce(response.promise);
    const first = game.enterValue(1);
    await game.enterValue(1);
    expect(fetchMock).toHaveBeenCalledTimes(3);
    expect(game.attempt?.revision).toBe(0);
    response.resolve(
      Response.json({
        ...fixture,
        values: [1, 2],
        revision: 1,
        moveCount: 1,
        status: "COMPLETED",
      }),
    );
    await first;
    expect(game.attempt?.revision).toBe(1);
    expect(game.pending).toBe(false);
  });
  it("does not apply an old poll response over an accepted command", async () => {
    const game = await connectedStore();
    const stale = deferred<Response>();
    fetchMock
      .mockResolvedValueOnce(Response.json(bootstrap))
      .mockReturnValueOnce(stale.promise);
    const poll = game.refreshAttempt();
    await vi.waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(4));
    fetchMock.mockResolvedValueOnce(
      Response.json({
        ...fixture,
        values: [1, 2],
        revision: 1,
        moveCount: 1,
        status: "COMPLETED",
      }),
    );
    await game.enterValue(1);
    stale.resolve(Response.json(fixture));
    await poll;
    expect(game.attempt?.revision).toBe(1);
  });
  it("blocks offline edits and reconnects using a fresh token and server snapshot", async () => {
    const game = await connectedStore();
    fetchMock.mockRejectedValueOnce(new TypeError("Failed to fetch"));
    await game.enterValue(1);
    expect(game.connected).toBe(false);
    const count = fetchMock.mock.calls.length;
    await game.enterValue(1);
    expect(fetchMock).toHaveBeenCalledTimes(count);
    fetchMock
      .mockResolvedValueOnce(
        Response.json({ ...bootstrap, requestToken: "new-token" }),
      )
      .mockResolvedValueOnce(
        Response.json({
          ...fixture,
          values: [1, 2],
          revision: 1,
          moveCount: 1,
          status: "COMPLETED",
        }),
      );
    await game.connect();
    expect(game.connected).toBe(true);
    expect(game.attempt?.revision).toBe(1);
    const init = fetchMock.mock.calls.at(-1)?.[1];
    expect(new Headers(init?.headers).get("X-Tiwanaku-Token")).toBe(
      "new-token",
    );
  });
  it("blocks editing when the running process or API version changes", async () => {
    const game = await connectedStore();
    fetchMock.mockResolvedValueOnce(
      Response.json({ ...bootstrap, processInstanceId: crypto.randomUUID() }),
    );
    await game.refreshAttempt();
    expect(game.connected).toBe(false);
    fetchMock.mockResolvedValueOnce(
      Response.json({ ...bootstrap, apiVersion: "v2" }),
    );
    await game.connect();
    expect(game.connected).toBe(false);
    expect(game.errorMessage).toContain("incompatible");
  });
});
