import { beforeEach, describe, expect, it, vi } from "vitest";
import { createPinia, setActivePinia } from "pinia";
import { useDataStore } from "./data";
import { bootstrapFixture } from "../testFixtures";

const settings = {
  locale: "en",
  theme: "light",
  reducedDetail: false,
  showConflicts: false,
  preset: "7x5",
  maxRegionSize: 5,
  difficulty: "EASY",
  playerTag: "",
  shape: "SQUARE",
};
const fetchMock = vi.fn<typeof fetch>();
beforeEach(() => {
  setActivePinia(createPinia());
  vi.stubGlobal("fetch", fetchMock);
  fetchMock.mockReset();
});
async function loaded() {
  fetchMock
    .mockResolvedValueOnce(Response.json(bootstrapFixture))
    .mockResolvedValueOnce(Response.json({ revision: 0, settings }));
  const data = useDataStore();
  await data.load();
  return data;
}
describe("durable settings reconciliation", () => {
  it("uses acknowledged normalized settings and an expected revision", async () => {
    const data = await loaded();
    fetchMock
      .mockResolvedValueOnce(Response.json(bootstrapFixture))
      .mockResolvedValueOnce(
        Response.json({
          revision: 1,
          settings: { ...settings, playerTag: "ABC" },
        }),
      );
    await data.save({ ...data.settings!, playerTag: "abc" });
    expect(data.revision).toBe(1);
    expect(data.settings?.playerTag).toBe("ABC");
    expect(
      JSON.parse(String(fetchMock.mock.calls.at(-1)?.[1]?.body)).revision,
    ).toBe(0);
  });
  it("does not optimistically replace settings after a lost response and reloads committed state", async () => {
    const data = await loaded();
    fetchMock
      .mockResolvedValueOnce(Response.json(bootstrapFixture))
      .mockRejectedValueOnce(new TypeError("response lost"));
    await data.save({ ...data.settings!, playerTag: "ABC" });
    expect(data.settings?.playerTag).toBe("");
    expect(data.error).toContain("outcome may be unknown");
    fetchMock
      .mockResolvedValueOnce(Response.json(bootstrapFixture))
      .mockResolvedValueOnce(
        Response.json({
          revision: 1,
          settings: { ...settings, playerTag: "ABC" },
        }),
      );
    await data.load();
    expect(data.settings?.playerTag).toBe("ABC");
  });
  it("keeps a stale-revision failure explicit until the user reloads", async () => {
    const data = await loaded();
    fetchMock
      .mockResolvedValueOnce(Response.json(bootstrapFixture))
      .mockResolvedValueOnce(
        Response.json(
          { detail: "Settings changed in another tab" },
          { status: 409 },
        ),
      );
    await data.save({ ...data.settings!, playerTag: "XYZ" });
    expect(data.error).toBe("Settings changed in another tab");
    expect(data.revision).toBe(0);
    expect(data.settings?.playerTag).toBe("");
  });
});
