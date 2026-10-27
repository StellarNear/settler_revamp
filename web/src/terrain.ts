export const biomes = [
  { name: "Forest", color: "#bed4ad", ink: "#456746" },
  { name: "Sand", color: "#e7cf9c", ink: "#8b6237" },
  { name: "Mountain", color: "#cbc9bf", ink: "#66665e" },
  { name: "Water", color: "#aed4de", ink: "#39778a" },
] as const;

export function assignRegionBiomes(
  columns: number,
  rows: number,
  regionIds: readonly number[],
) {
  if (
    !Number.isInteger(columns) ||
    !Number.isInteger(rows) ||
    columns < 1 ||
    rows < 1 ||
    columns * rows !== regionIds.length
  )
    throw new Error("Terrain requires validated board dimensions.");

  const adjacency = new Map<number, Set<number>>();
  const connect = (first: number, second: number) => {
    if (first === second) return;
    adjacency.get(first)!.add(second);
    adjacency.get(second)!.add(first);
  };
  for (let cell = 0; cell < regionIds.length; cell++) {
    const id = regionIds[cell];
    if (!Number.isInteger(id) || id < 0)
      throw new Error("Terrain requires valid region IDs.");
    if (!adjacency.has(id)) adjacency.set(id, new Set());
    const x = cell % columns,
      y = Math.floor(cell / columns);
    if (x + 1 < columns) {
      const right = regionIds[cell + 1];
      if (!Number.isInteger(right) || right < 0)
        throw new Error("Terrain requires valid region IDs.");
      if (!adjacency.has(right)) adjacency.set(right, new Set());
      connect(id, right);
    }
    if (y + 1 < rows) {
      const below = regionIds[cell + columns];
      if (!Number.isInteger(below) || below < 0)
        throw new Error("Terrain requires valid region IDs.");
      if (!adjacency.has(below)) adjacency.set(below, new Set());
      connect(id, below);
    }
  }

  const assigned = new Map<number, number>();
  function colorNextRegion(): boolean {
    if (assigned.size === adjacency.size) return true;

    let selectedRegion: number | undefined;
    let highestSaturation = -1;
    let highestDegree = -1;
    for (const [region, neighbors] of adjacency) {
      if (assigned.has(region)) continue;
      const saturation = new Set(
        [...neighbors]
          .map((neighbor) => assigned.get(neighbor))
          .filter((color) => color !== undefined),
      ).size;
      if (
        saturation > highestSaturation ||
        (saturation === highestSaturation && neighbors.size > highestDegree)
      ) {
        selectedRegion = region;
        highestSaturation = saturation;
        highestDegree = neighbors.size;
      }
    }
    if (selectedRegion === undefined) return false;

    const preferred = selectedRegion % biomes.length;
    for (let offset = 0; offset < biomes.length; offset++) {
      const color = (preferred + offset) % biomes.length;
      if (
        [...adjacency.get(selectedRegion)!].some(
          (neighbor) => assigned.get(neighbor) === color,
        )
      )
        continue;
      assigned.set(selectedRegion, color);
      if (colorNextRegion()) return true;
      assigned.delete(selectedRegion);
    }
    return false;
  }

  if (!colorNextRegion())
    throw new Error("Region adjacency cannot be assigned distinct biomes.");
  return assigned;
}

// Coordinates stay in board-cell units so neighboring cells share pattern phase.
export function terrainGeometry(
  columns: number,
  rows: number,
  regionIds: readonly number[],
) {
  const assignedBiomes = assignRegionBiomes(columns, rows, regionIds);
  const regions = new Map<number, string[]>();
  const regionBoundaries = new Map<number, string[]>();
  const boundaries: string[] = [];
  for (let cell = 0; cell < regionIds.length; cell++) {
    const id = regionIds[cell];
    const x = cell % columns,
      y = Math.floor(cell / columns);
    const paths = regions.get(id) ?? [];
    const edges = regionBoundaries.get(id) ?? [];
    paths.push(`M${x} ${y}h1v1h-1Z`);
    regions.set(id, paths);
    regionBoundaries.set(id, edges);
    if (x === 0 || regionIds[cell - 1] !== id) {
      boundaries.push(`M${x} ${y}v1`);
      edges.push(`M${x} ${y}v1`);
    }
    if (y === 0 || regionIds[cell - columns] !== id) {
      boundaries.push(`M${x} ${y}h1`);
      edges.push(`M${x} ${y}h1`);
    }
    if (x === columns - 1) boundaries.push(`M${x + 1} ${y}v1`);
    if (y === rows - 1) boundaries.push(`M${x} ${y + 1}h1`);
    if (x === columns - 1 || regionIds[cell + 1] !== id)
      edges.push(`M${x + 1} ${y}v1`);
    if (y === rows - 1 || regionIds[cell + columns] !== id)
      edges.push(`M${x} ${y + 1}h1`);
  }
  const separators = [
    ...Array.from(
      { length: columns - 1 },
      (_, index) => `M${index + 1} 0v${rows}`,
    ),
    ...Array.from(
      { length: rows - 1 },
      (_, index) => `M0 ${index + 1}h${columns}`,
    ),
  ].join("");
  return {
    regions: [...regions].map(([id, paths]) => ({
      id,
      path: paths.join(""),
      boundary: regionBoundaries.get(id)!.join(""),
      biome: assignedBiomes.get(id)!,
    })),
    boundaries: boundaries.join(""),
    separators,
  };
}
