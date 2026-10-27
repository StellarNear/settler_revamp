export const biomes = [
  { name: "Forest", color: "#d7e2ce", ink: "#536c4b" },
  { name: "Sand", color: "#eadbb9", ink: "#8a6943" },
  { name: "Mountain", color: "#d9dee3", ink: "#626f7b" },
  { name: "Water", color: "#d2e3e9", ink: "#527a8c" },
] as const;

export function regionTerrain(regionId: number) {
  return biomes[regionId % biomes.length];
}

// Coordinates stay in board-cell units so neighboring cells share pattern phase.
export function terrainGeometry(
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
  const regions = new Map<number, string[]>();
  const boundaries: string[] = [];
  for (let cell = 0; cell < regionIds.length; cell++) {
    const id = regionIds[cell];
    if (!Number.isInteger(id) || id < 0)
      throw new Error("Terrain requires valid region IDs.");
    const x = cell % columns,
      y = Math.floor(cell / columns);
    const paths = regions.get(id) ?? [];
    paths.push(`M${x} ${y}h1v1h-1Z`);
    regions.set(id, paths);
    if (x === 0 || regionIds[cell - 1] !== id) boundaries.push(`M${x} ${y}v1`);
    if (y === 0 || regionIds[cell - columns] !== id)
      boundaries.push(`M${x} ${y}h1`);
    if (x === columns - 1) boundaries.push(`M${x + 1} ${y}v1`);
    if (y === rows - 1) boundaries.push(`M${x} ${y + 1}h1`);
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
      biome: id % biomes.length,
    })),
    boundaries: boundaries.join(""),
    separators,
  };
}
