import { describe, expect, it } from "vitest";
import { regionTerrain, terrainGeometry } from "./terrain";

describe("board-coordinate terrain projection", () => {
  it("uses one silhouette per region with shared board-space phase and unambiguous borders", () => {
    const geometry = terrainGeometry(3, 3, [0, 0, 1, 0, 2, 1, 3, 3, 1]);
    expect(geometry.regions).toHaveLength(4);
    expect(geometry.regions[0].path).toBe(
      "M0 0h1v1h-1ZM1 0h1v1h-1ZM0 1h1v1h-1Z",
    );
    expect(geometry.boundaries).toContain("M1 1v1");
    expect(geometry.boundaries).not.toContain("M1 0v1");
    expect(geometry.boundaries).toContain("M3 2v1");
    expect(geometry.boundaries).toContain("M2 3h1");
  });
  it("preserves a hole and distinguishes adjacent regions sharing a biome", () => {
    const ring = terrainGeometry(3, 3, [0, 0, 0, 0, 4, 0, 0, 0, 0]);
    expect(ring.regions[0].path.match(/M/g)).toHaveLength(8);
    expect(ring.regions[0].path).not.toContain("M1 1");
    expect(ring.regions[0].biome).toBe(ring.regions[1].biome);
    expect(ring.boundaries).toContain("M1 1v1");
    expect(ring.boundaries).toContain("M2 1v1");
    expect(ring.boundaries).toContain("M1 1h1");
    expect(ring.boundaries).toContain("M1 2h1");
    expect(regionTerrain(0)).toEqual(regionTerrain(4));
  });
  it.each([
    [3, 3],
    [4, 4],
    [5, 5],
    [7, 5],
    [9, 5],
    [15, 10],
    [20, 12],
  ])("projects %ix%i exactly once per cell", (columns, rows) => {
    const geometry = terrainGeometry(
      columns,
      rows,
      Array.from({ length: columns * rows }, (_, i) => Math.floor(i / 5)),
    );
    expect(
      geometry.regions.flatMap((region) => region.path.match(/M/g)),
    ).toHaveLength(columns * rows);
  });
  it("refuses malformed projection inputs rather than drawing a fallback board", () => {
    expect(() => terrainGeometry(2, 1, [0])).toThrow();
    expect(() => terrainGeometry(1, 1, [-1])).toThrow();
  });
});
