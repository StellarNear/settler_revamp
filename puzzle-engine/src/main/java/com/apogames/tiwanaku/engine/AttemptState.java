package com.apogames.tiwanaku.engine;

public final class AttemptState {
    private final PuzzleDefinition puzzle;
    private final int[] values;
    private final int[] noteMasks;
    private final long revision;

    private AttemptState(PuzzleDefinition puzzle, int[] values, int[] noteMasks, long revision) {
        this.puzzle = puzzle;
        this.values = values;
        this.noteMasks = noteMasks;
        this.revision = revision;
    }

    public static AttemptState start(PuzzleDefinition puzzle) {
        return new AttemptState(puzzle, puzzle.givens(), new int[puzzle.cellCount()], 0);
    }

    public AttemptState withValue(int cell, int value) {
        checkCell(cell);
        if (value < 0 || value > puzzle.regionSizeAt(cell)) {
            throw new IllegalArgumentException("Value is outside the cell region range");
        }
        if (puzzle.givenAt(cell) != 0 && value != puzzle.givenAt(cell)) {
            throw new IllegalArgumentException("Given cells cannot be changed");
        }
        if (values[cell] == value) {
            return this;
        }
        int[] nextValues = values.clone();
        nextValues[cell] = value;
        return new AttemptState(puzzle, nextValues, noteMasks.clone(), nextRevision());
    }

    public AttemptState withNoteMask(int cell, int noteMask) {
        checkCell(cell);
        int allowedMask = (1 << puzzle.regionSizeAt(cell)) - 1;
        if ((noteMask & ~allowedMask) != 0) {
            throw new IllegalArgumentException("Note mask contains values outside the cell region range");
        }
        if (noteMasks[cell] == noteMask) {
            return this;
        }
        int[] nextMasks = noteMasks.clone();
        nextMasks[cell] = noteMask;
        return new AttemptState(puzzle, values.clone(), nextMasks, nextRevision());
    }

    private long nextRevision() {
        if (revision == Long.MAX_VALUE) {
            throw new IllegalStateException("Attempt revision is exhausted");
        }
        return revision + 1;
    }

    private void checkCell(int cell) {
        if (cell < 0 || cell >= puzzle.cellCount()) {
            throw new IndexOutOfBoundsException("Cell index is outside the board");
        }
    }

    public PuzzleDefinition puzzle() {
        return puzzle;
    }

    public int valueAt(int cell) {
        return values[cell];
    }

    public int noteMaskAt(int cell) {
        return noteMasks[cell];
    }

    public int[] values() {
        return values.clone();
    }

    public int[] noteMasks() {
        return noteMasks.clone();
    }

    public long revision() {
        return revision;
    }
}
