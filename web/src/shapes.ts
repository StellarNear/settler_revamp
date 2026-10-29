import type { TileShape } from "./api";

export const shapeLabels: Record<TileShape, string> = {
  SQUARE: "Squares · Sides + corners",
  SQUARE_SIDES_ONLY: "Squares · Sides only",
  HEXAGON: "Hexagons",
  TRIANGLE: "Triangles",
  PENTAGON: "Pentagons · Sides only",
  PENTAGON_CORNERS: "Pentagons · Sides + corners",
};

export function contactRuleDescription(shape: TileShape): string {
  return shape === "SQUARE" || shape === "PENTAGON_CORNERS"
    ? "Equal numbers cannot share an edge or touch at a corner."
    : "Equal numbers cannot share an edge. Touching only at a corner is allowed.";
}
