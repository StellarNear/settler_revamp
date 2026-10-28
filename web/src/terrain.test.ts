import { describe, expect, it } from "vitest";
import {
  assignRegionBiomes,
  biomes,
  polygonTerrainGeometry,
  terrainGeometry,
} from "./terrain";

describe("board-coordinate terrain projection", () => {
  it("keeps the approved sea and sand while giving mountains a neutral gray palette", () => {
    expect(biomes[1]).toEqual({
      name: "Sand",
      color: "#e7cf9c",
      ink: "#8b6237",
    });
    expect(biomes[2]).toEqual({
      name: "Mountain",
      color: "#cbc9bf",
      ink: "#66665e",
    });
    expect(biomes[3]).toEqual({
      name: "Water",
      color: "#aed4de",
      ink: "#39778a",
    });
  });

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
    expect(ring.regions[0].biome).not.toBe(ring.regions[1].biome);
    expect(ring.boundaries).toContain("M1 1v1");
    expect(ring.boundaries).toContain("M2 1v1");
    expect(ring.boundaries).toContain("M1 1h1");
    expect(ring.boundaries).toContain("M1 2h1");
    expect(ring.regions[1].boundary).toBe("M1 1v1M1 1h1M2 1v1M1 2h1");
  });
  it("assigns different biomes to all regions that share a tile edge", () => {
    const regionIds = [0, 4, 0, 4, 1, 2, 1, 2, 3, 4, 3, 4];
    const assigned = assignRegionBiomes(4, 3, regionIds);
    for (let cell = 0; cell < regionIds.length; cell++) {
      const x = cell % 4;
      const y = Math.floor(cell / 4);
      if (x + 1 < 4 && regionIds[cell] !== regionIds[cell + 1])
        expect(assigned.get(regionIds[cell])).not.toBe(
          assigned.get(regionIds[cell + 1]),
        );
      if (y + 1 < 3 && regionIds[cell] !== regionIds[cell + 4])
        expect(assigned.get(regionIds[cell])).not.toBe(
          assigned.get(regionIds[cell + 4]),
        );
    }
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
    const assigned = new Map(
      geometry.regions.map(({ id, biome }) => [id, biome]),
    );
    const regionIds = Array.from({ length: columns * rows }, (_, i) =>
      Math.floor(i / 5),
    );
    for (let cell = 0; cell < regionIds.length; cell++) {
      const x = cell % columns;
      const y = Math.floor(cell / columns);
      if (x + 1 < columns && regionIds[cell] !== regionIds[cell + 1])
        expect(assigned.get(regionIds[cell])).not.toBe(
          assigned.get(regionIds[cell + 1]),
        );
      if (y + 1 < rows && regionIds[cell] !== regionIds[cell + columns])
        expect(assigned.get(regionIds[cell])).not.toBe(
          assigned.get(regionIds[cell + columns]),
        );
    }
  });
  it("refuses malformed projection inputs rather than drawing a fallback board", () => {
    expect(() => terrainGeometry(2, 1, [0])).toThrow();
    expect(() => terrainGeometry(1, 1, [-1])).toThrow();
  });
  it("projects polygon tiles with shared-edge boundaries and distinct neighbouring biomes", () => {
    const triangles = [
      [0, 1, 0.5, 0, 1, 1],
      [0.5, 0, 1.5, 0, 1, 1],
      [1, 1, 1.5, 0, 2, 1],
    ];
    const tiles = polygonTerrainGeometry(triangles, [4, 4, 7]);
    expect(tiles.width).toBe(2);
    expect(tiles.height).toBe(1);
    expect(tiles.cellPaths).toHaveLength(3);
    expect(tiles.centroids[1]).toEqual([1, 1 / 3]);
    expect(tiles.inscribed).toBeGreaterThan(0.5);
    expect(tiles.regions.map((region) => region.id)).toEqual([4, 7]);
    expect(tiles.regions[0].biome).not.toBe(tiles.regions[1].biome);
    expect(tiles.boundaries).toContain("M1.5 0L1 1");
    expect(tiles.boundaries).not.toContain("M0.5 0L1 1");
    expect(() => polygonTerrainGeometry(triangles, [4, 4])).toThrow();
  });
});
