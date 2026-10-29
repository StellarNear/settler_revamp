package com.apogames.tiwanaku.engine;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cell adjacency of a board.
 *
 * <p>Region neighbors share a side and define region connectivity. Conflict neighbors may never hold
 * equal values: shared sides, plus shared corners in the SQUARE and PENTAGON_CORNERS variants.
 * SQUARE preserves the original 8-neighbor rule; PENTAGON preserves its side-only rule.
 * Region connectivity always requires shared sides, regardless of the conflict rule.
 *
 * <p>Non-square boards contain exactly {@code columns × rows} tiles cut from a large tiling as the
 * most compact rectangle-like patch with a {@code columns : rows} aspect ratio.
 */
public final class Topology {
    private static final Map<String, Topology> CACHE = new ConcurrentHashMap<>();
    private static final double SQRT3 = Math.sqrt(3);

    private final TileShape shape;
    private final int columns;
    private final int rows;
    private final int[][] regionNeighbors;
    private final int[][] conflictNeighbors;
    private final double[][] polygons;

    private Topology(TileShape shape, int columns, int rows, int[][] regionNeighbors,
                     int[][] conflictNeighbors, double[][] polygons) {
        this.shape = shape;
        this.columns = columns;
        this.rows = rows;
        this.regionNeighbors = regionNeighbors;
        this.conflictNeighbors = conflictNeighbors;
        this.polygons = polygons;
    }

    public static Topology of(TileShape shape, int columns, int rows) {
        Objects.requireNonNull(shape, "shape");
        if (columns <= 0 || rows <= 0 || (long) columns * rows > PuzzleDefinition.MAX_CELLS) {
            throw new IllegalArgumentException("Board dimensions must contain between 1 and 240 cells");
        }
        return CACHE.computeIfAbsent(shape + ":" + columns + "x" + rows,
                key -> shape.isSquare() ? square(shape, columns, rows) : polygonal(shape, columns, rows));
    }

    public TileShape shape() {
        return shape;
    }

    public int columns() {
        return columns;
    }

    public int rows() {
        return rows;
    }

    public int cellCount() {
        return regionNeighbors.length;
    }

    /** Cells sharing a side, in a stable order. */
    public int[] regionNeighbors(int cell) {
        return regionNeighbors[cell];
    }

    /** Cells that may not hold the same value, ascending. */
    public int[] conflictNeighbors(int cell) {
        return conflictNeighbors[cell];
    }

    /** Polygon of a cell as x0,y0,x1,y1,... in board units (top-left near 0,0, y downwards). */
    public double[] polygon(int cell) {
        return polygons[cell].clone();
    }

    private static Topology square(TileShape shape, int columns, int rows) {
        int count = columns * rows;
        int[][] region = new int[count][];
        int[][] conflict = new int[count][];
        double[][] polygons = new double[count][];
        for (int cell = 0; cell < count; cell++) {
            int x = cell % columns;
            int y = cell / columns;
            List<Integer> sides = new ArrayList<>(4);
            if (x > 0) sides.add(cell - 1);
            if (x + 1 < columns) sides.add(cell + 1);
            if (y > 0) sides.add(cell - columns);
            if (y + 1 < rows) sides.add(cell + columns);
            region[cell] = sides.stream().mapToInt(Integer::intValue).toArray();
            List<Integer> around = new ArrayList<>(8);
            for (int ny = Math.max(0, y - 1); ny <= Math.min(rows - 1, y + 1); ny++) {
                for (int nx = Math.max(0, x - 1); nx <= Math.min(columns - 1, x + 1); nx++) {
                    int neighbor = ny * columns + nx;
                    if (neighbor != cell) around.add(neighbor);
                }
            }
            conflict[cell] = shape.includesCornerConflicts()
                    ? around.stream().mapToInt(Integer::intValue).toArray()
                    : Arrays.stream(region[cell]).sorted().toArray();
            polygons[cell] = new double[]{x, y, x + 1, y, x + 1, y + 1, x, y + 1};
        }
        return new Topology(shape, columns, rows, region, conflict, polygons);
    }

    private static Topology polygonal(TileShape shape, int columns, int rows) {
        int count = columns * rows;
        int reach = (int) Math.ceil(Math.sqrt(count)) * 2 + 6;
        List<double[]> patch = switch (shape) {
            case HEXAGON -> hexagons(reach);
            case TRIANGLE -> triangles(reach);
            case PENTAGON, PENTAGON_CORNERS -> pentagons(reach);
            case SQUARE, SQUARE_SIDES_ONLY -> throw new IllegalStateException();
        };
        double aspect = (double) columns / rows;
        Comparator<double[]> compact = Comparator
                .comparingDouble((double[] polygon) -> {
                    double[] center = centroid(polygon);
                    return Math.max(Math.abs(center[0]) / aspect, Math.abs(center[1]));
                })
                .thenComparingDouble(polygon -> {
                    double[] center = centroid(polygon);
                    return Math.hypot(center[0] / aspect, center[1]);
                })
                .thenComparingDouble(polygon -> centroid(polygon)[1])
                .thenComparingDouble(polygon -> centroid(polygon)[0]);
        List<double[]> chosen = growCompactPatch(patch.stream().sorted(compact).toList(), count);
        chosen.sort(Comparator
                .comparingDouble((double[] polygon) -> Math.round(centroid(polygon)[1] * 1000) / 1000.0)
                .thenComparingDouble(polygon -> centroid(polygon)[0]));
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        for (double[] polygon : chosen) {
            for (int i = 0; i < polygon.length; i += 2) {
                minX = Math.min(minX, polygon[i]);
                minY = Math.min(minY, polygon[i + 1]);
            }
        }
        double[][] polygons = new double[count][];
        for (int cell = 0; cell < count; cell++) {
            double[] polygon = chosen.get(cell).clone();
            for (int i = 0; i < polygon.length; i += 2) {
                polygon[i] = round(polygon[i] - minX);
                polygon[i + 1] = round(polygon[i + 1] - minY);
            }
            polygons[cell] = polygon;
        }

        Map<Long, List<Integer>> cellsByVertex = new HashMap<>();
        for (int cell = 0; cell < count; cell++) {
            for (int i = 0; i < polygons[cell].length; i += 2) {
                cellsByVertex.computeIfAbsent(vertexKey(polygons[cell][i], polygons[cell][i + 1]),
                        key -> new ArrayList<>()).add(cell);
            }
        }
        int[][] sharedVertices = new int[count][count];
        for (List<Integer> cells : cellsByVertex.values()) {
            for (int a : cells) {
                for (int b : cells) {
                    if (a != b) sharedVertices[a][b]++;
                }
            }
        }
        int[][] region = new int[count][];
        int[][] conflict = new int[count][];
        for (int cell = 0; cell < count; cell++) {
            List<Integer> sides = new ArrayList<>();
            List<Integer> touching = new ArrayList<>();
            for (int other = 0; other < count; other++) {
                int shared = sharedVertices[cell][other];
                if (shared >= 2) sides.add(other);
                if (shared >= 1) touching.add(other);
            }
            region[cell] = sides.stream().mapToInt(Integer::intValue).toArray();
            conflict[cell] = shape.includesCornerConflicts()
                    ? touching.stream().mapToInt(Integer::intValue).toArray()
                    : region[cell];
        }
        requireConnected(region);
        return new Topology(shape, columns, rows, region, conflict, polygons);
    }

    private static void requireConnected(int[][] region) {
        boolean[] seen = new boolean[region.length];
        ArrayDeque<Integer> pending = new ArrayDeque<>();
        pending.add(0);
        seen[0] = true;
        int reached = 0;
        while (!pending.isEmpty()) {
            int cell = pending.remove();
            reached++;
            for (int neighbor : region[cell]) {
                if (!seen[neighbor]) {
                    seen[neighbor] = true;
                    pending.add(neighbor);
                }
            }
        }
        if (reached != region.length) {
            throw new IllegalStateException("Generated tile patch is not connected");
        }
    }

    /** Pointy-top hexagons with side 1. */
    private static List<double[]> hexagons(int reach) {
        List<double[]> result = new ArrayList<>();
        for (int row = -reach; row <= reach; row++) {
            for (int column = -reach; column <= reach; column++) {
                double cx = SQRT3 * (column + (Math.floorMod(row, 2) == 1 ? 0.5 : 0));
                double cy = 1.5 * row;
                double[] polygon = new double[12];
                for (int corner = 0; corner < 6; corner++) {
                    double angle = Math.toRadians(-90 + 60 * corner);
                    polygon[2 * corner] = cx + Math.cos(angle);
                    polygon[2 * corner + 1] = cy + Math.sin(angle);
                }
                result.add(polygon);
            }
        }
        return result;
    }

    /** Equilateral triangles with side 1 in alternating up/down rows. */
    private static List<double[]> triangles(int reach) {
        double height = SQRT3 / 2;
        List<double[]> result = new ArrayList<>();
        for (int row = -reach; row <= reach; row++) {
            double top = row * height;
            double bottom = top + height;
            for (int index = -2 * reach; index <= 2 * reach; index++) {
                double left = index * 0.5;
                boolean up = Math.floorMod(index + row, 2) == 0;
                result.add(up
                        ? new double[]{left + 0.5, top, left + 1, bottom, left, bottom}
                        : new double[]{left, top, left + 1, top, left + 0.5, bottom});
            }
        }
        return result;
    }

    /**
     * Cairo pentagonal tiling: a checkerboard of 2×2 squares split by a short segment through the
     * center (horizontal on even squares, vertical on odd ones); each pentagon joins a square's
     * trapezoid with the neighboring square's triangle.
     */
    private static List<double[]> pentagons(int reach) {
        List<double[]> result = new ArrayList<>();
        int squares = reach / 2 + 2;
        for (int b = -squares; b <= squares; b++) {
            for (int a = -squares; a <= squares; a++) {
                double x = 2.0 * a;
                double y = 2.0 * b;
                if (Math.floorMod(a + b, 2) == 0) {
                    result.add(new double[]{x, y, x + 1, y - 0.5, x + 2, y, x + 1.5, y + 1, x + 0.5, y + 1});
                    result.add(new double[]{x, y + 2, x + 0.5, y + 1, x + 1.5, y + 1, x + 2, y + 2, x + 1, y + 2.5});
                } else {
                    result.add(new double[]{x, y, x + 1, y + 0.5, x + 1, y + 1.5, x, y + 2, x - 0.5, y + 1});
                    result.add(new double[]{x + 2, y, x + 2.5, y + 1, x + 2, y + 2, x + 1, y + 1.5, x + 1, y + 0.5});
                }
            }
        }
        return result;
    }

    static double[] centroid(double[] polygon) {
        double x = 0;
        double y = 0;
        int corners = polygon.length / 2;
        for (int i = 0; i < polygon.length; i += 2) {
            x += polygon[i];
            y += polygon[i + 1];
        }
        return new double[]{x / corners, y / corners};
    }

    /**
     * Takes {@code count} tiles from a patch ordered by distance to the centre, always picking the
     * closest tile that shares a side with the tiles already taken so the board stays connected.
     */
    private static List<double[]> growCompactPatch(List<double[]> ordered, int count) {
        Map<Long, List<Integer>> tilesByVertex = new HashMap<>();
        for (int tile = 0; tile < ordered.size(); tile++) {
            double[] polygon = ordered.get(tile);
            for (int i = 0; i < polygon.length; i += 2) {
                tilesByVertex.computeIfAbsent(vertexKey(polygon[i], polygon[i + 1]), key -> new ArrayList<>())
                        .add(tile);
            }
        }
        boolean[] queued = new boolean[ordered.size()];
        java.util.PriorityQueue<Integer> frontier = new java.util.PriorityQueue<>();
        frontier.add(0);
        queued[0] = true;
        List<double[]> chosen = new ArrayList<>(count);
        while (chosen.size() < count) {
            int tile = frontier.remove();
            chosen.add(ordered.get(tile));
            Map<Integer, Integer> shared = new HashMap<>();
            double[] polygon = ordered.get(tile);
            for (int i = 0; i < polygon.length; i += 2) {
                for (int other : tilesByVertex.get(vertexKey(polygon[i], polygon[i + 1]))) {
                    shared.merge(other, 1, Integer::sum);
                }
            }
            shared.forEach((other, vertices) -> {
                if (vertices >= 2 && !queued[other]) {
                    queued[other] = true;
                    frontier.add(other);
                }
            });
        }
        return chosen;
    }

    private static double round(double value) {
        return Math.round(value * 1_000_000d) / 1_000_000d;
    }

    private static long vertexKey(double x, double y) {
        return Math.round(x * 1_000) * 4_000_003L + Math.round(y * 1_000);
    }

    @Override
    public String toString() {
        return "Topology[" + shape + " " + columns + "x" + rows + ", cells=" + cellCount()
                + ", sides=" + Arrays.stream(regionNeighbors).mapToInt(n -> n.length).sum() / 2 + "]";
    }
}
