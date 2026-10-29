package com.apogames.tiwanaku.server;

import com.apogames.tiwanaku.engine.PuzzleDefinition;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class GameProgress {
    public int[] notes;
    public long elapsedMillis;
    public boolean started;
    public boolean replay;
    public int valueEntryCount;
    public int wrongEntryCount;
    public long[] clickCounts;
    public long[] clickMissCounts;
    public long totalClicks;
    public long totalMissClicks;
    public boolean directValueEntryUsed;
    public int candidateRequests;
    public int explanationRequests;
    public int reveals;
    public boolean continuousConflictsUsed;
    public boolean showConflicts;
    public List<Frame> undo = new ArrayList<>();
    public List<Frame> redo = new ArrayList<>();
    public Frame checkpoint;
    public int[] conflicts = new int[0];
    public int[] candidates = new int[0];
    public String helpMessage = "";
    public String timerProcessId;
    public long timerBaselineNanos;
    public long timerBaselineElapsed;
    public long timingSequence;

    public record Frame(int[] values, int[] notes) {
    }

    public static GameProgress initial(int cells, boolean replay) {
        GameProgress progress = new GameProgress();
        progress.notes = new int[cells];
        progress.clickCounts = new long[cells];
        progress.clickMissCounts = new long[cells];
        progress.replay = replay;
        return progress;
    }

    public void ensureClickStats(int cells) {
        if (clickCounts == null || clickMissCounts == null) directValueEntryUsed = true;
        if (clickCounts == null) clickCounts = new long[cells];
        if (clickMissCounts == null) clickMissCounts = new long[cells];
        if (clickCounts.length != cells || clickMissCounts.length != cells)
            throw new IllegalStateException("Saved click statistics do not match the puzzle dimensions");
    }

    public Integer legacyAccuracy() {
        if (directValueEntryUsed) return null;
        if (totalClicks == 0) return 100;
        return (int) Math.round((totalClicks - totalMissClicks) * 100.0 / totalClicks);
    }

    public void startTimer(GameplayClock clock) {
        started = true;
        timerProcessId = clock.processId();
        timerBaselineNanos = clock.nanos();
        timerBaselineElapsed = elapsedMillis;
        timingSequence = 0;
    }

    public boolean timerBelongsTo(GameplayClock clock) {
        return clock.processId().equals(timerProcessId);
    }

    public void acceptTime(Long sequence, Long proposal, GameplayClock clock) {
        if (sequence == null && proposal == null) return;
        if (sequence == null || proposal == null || sequence < 0 || proposal < 0) {
            throw ApiException.badRequest("Timing sequence and cumulative elapsed milliseconds are required together");
        }
        if (!timerBelongsTo(clock)) {
            throw ApiException.conflict("The timer lease changed; resume the saved game");
        }
        if (sequence <= timingSequence) return;
        if (proposal < elapsedMillis) {
            throw ApiException.conflict("The elapsed proposal decreased; reload the saved timer");
        }
        long observed = Math.max(0, (clock.nanos() - timerBaselineNanos) / 1_000_000);
        if (proposal - timerBaselineElapsed > observed + 1_000) {
            throw ApiException.badRequest("Elapsed proposal exceeds active server-observed time plus 1000 ms tolerance");
        }
        elapsedMillis = proposal;
        timingSequence = sequence;
    }

    public void remember(int[] values) {
        undo.add(new Frame(values.clone(), notes.clone()));
        if (undo.size() > 100) undo.removeFirst();
        redo.clear();
    }

    public void restore(Frame frame, int[] values) {
        System.arraycopy(frame.values(), 0, values, 0, values.length);
        notes = frame.notes().clone();
    }

    public void edit(PuzzleDefinition puzzle, int[] solution, int[] values, AttemptCommand command) {
        int cell = command.cellIndex() == null ? -1 : command.cellIndex();
        switch (command.type()) {
            case SET_VALUE, CYCLE_VALUE, REVEAL -> {
                if (command.type() == AttemptCommand.CommandType.CYCLE_VALUE
                        && (command.value() == null || Math.abs(command.value()) != 1)) {
                    throw ApiException.badRequest("Cycle direction must be 1 or -1");
                }
                if (command.type() == AttemptCommand.CommandType.CYCLE_VALUE
                        && cell >= 0 && cell < puzzle.cellCount() && puzzle.givenAt(cell) != 0) {
                    registerWastedClick();
                    break;
                }
                requireEditableCell(puzzle, cell);
                int value;
                if (command.type() == AttemptCommand.CommandType.CYCLE_VALUE) {
                    value = Math.floorMod(values[cell] + command.value(), puzzle.regionSizeAt(cell) + 1);
                } else if (command.type() == AttemptCommand.CommandType.REVEAL) {
                    value = solution[cell];
                    reveals++;
                    directValueEntryUsed = true;
                } else {
                    value = command.value() == null ? -1 : command.value();
                }
                if (value < 0 || value > puzzle.regionSizeAt(cell)) {
                    throw ApiException.badRequest("The value exceeds this region's size");
                }
                if (value != values[cell]) {
                    remember(values);
                    values[cell] = value;
                    notes[cell] = 0;
                    if (command.type() == AttemptCommand.CommandType.CYCLE_VALUE) {
                        registerValueClick(cell, puzzle.regionSizeAt(cell), value);
                    } else {
                        directValueEntryUsed = true;
                    }
                    if (value != 0) {
                        valueEntryCount++;
                        if (value != solution[cell]) wrongEntryCount++;
                    }
                }
            }
            case TOGGLE_NOTE -> {
                requireEditableCell(puzzle, cell);
                if (values[cell] != 0) throw ApiException.conflict("Clear the cell before editing manual notes");
                if (command.value() == null || command.value() < 1
                        || command.value() > puzzle.regionSizeAt(cell)) {
                    throw ApiException.badRequest("The note exceeds this region's size");
                }
                remember(values);
                notes[cell] ^= 1 << (command.value() - 1);
            }
            case UNDO -> {
                if (undo.isEmpty()) throw ApiException.conflict("There is no move to undo");
                directValueEntryUsed = true;
                redo.add(new Frame(values.clone(), notes.clone()));
                restore(undo.removeLast(), values);
            }
            case REDO -> {
                if (redo.isEmpty()) throw ApiException.conflict("There is no move to redo");
                directValueEntryUsed = true;
                undo.add(new Frame(values.clone(), notes.clone()));
                restore(redo.removeLast(), values);
            }
            case CHECKPOINT -> checkpoint = new Frame(values.clone(), notes.clone());
            case RESTORE_CHECKPOINT -> {
                if (checkpoint == null) throw ApiException.conflict("No checkpoint has been saved");
                directValueEntryUsed = true;
                remember(values);
                restore(checkpoint, values);
            }
            case CHECK, SHOW_CONFLICTS -> {
                if (command.type() == AttemptCommand.CommandType.SHOW_CONFLICTS) {
                    if (command.value() == null || command.value() < 0 || command.value() > 1) {
                        throw ApiException.badRequest("Conflict display must be 0 or 1");
                    }

                    showConflicts = command.value() == 1;
                    continuousConflictsUsed |= showConflicts;
                }
                helpMessage = "Highlighted cells contain duplicate values, not necessarily every wrong answer.";
            }
            case CANDIDATES -> {
                requireEditableCell(puzzle, cell);
                candidateRequests++;
                candidates = candidates(puzzle, values, cell);
                helpMessage = "Candidates are legal under visible entries, not a global solvability guarantee.";
            }
            case EXPLAIN -> {
                explanationRequests++;
                helpMessage = "No single-candidate deduction is available. This is not a full logical solver.";
                for (int index = 0; index < values.length; index++) {
                    if (values[index] == 0) {
                        int[] legal = candidates(puzzle, values, index);
                        if (legal.length == 1) {
                            helpMessage = "Cell " + (index + 1) + " has only candidate " + legal[0]
                                    + " after excluding its region and eight neighbors.";
                            break;
                        }
                    }
                }
            }
            default -> throw ApiException.badRequest("Not a board command");
        }
        if (command.type() != AttemptCommand.CommandType.CANDIDATES) candidates = new int[0];
        if (showConflicts || command.type() == AttemptCommand.CommandType.CHECK
                || Arrays.stream(values).noneMatch(value -> value == 0)) {
            conflicts = conflicts(puzzle, values);
        } else {
            conflicts = new int[0];
        }
    }

    private void registerValueClick(int cell, int regionSize, int value) {
        totalClicks++;
        clickCounts[cell]++;
        int optimalClicks = value == 0 ? 0 : Math.min(value, regionSize - value + 1);
        long missClicks = Math.max(0, clickCounts[cell] - optimalClicks);
        totalMissClicks += missClicks - clickMissCounts[cell];
        clickMissCounts[cell] = missClicks;
    }

    private void registerWastedClick() {
        totalClicks++;
        totalMissClicks++;
    }

    private void requireEditableCell(PuzzleDefinition puzzle, int cell) {
        if (cell < 0 || cell >= puzzle.cellCount()) throw ApiException.badRequest("A valid cell is required");
        if (puzzle.givenAt(cell) != 0) throw ApiException.conflict("Given cells cannot be changed");
    }

    private static boolean related(PuzzleDefinition puzzle, int first, int second) {
        if (puzzle.regionIdAt(first) == puzzle.regionIdAt(second)) return true;
        for (int neighbor : puzzle.topology().conflictNeighbors(first)) {
            if (neighbor == second) return true;
        }
        return false;
    }

    public static int[] candidates(PuzzleDefinition puzzle, int[] values, int cell) {
        List<Integer> legal = new ArrayList<>();
        for (int value = 1; value <= puzzle.regionSizeAt(cell); value++) {
            boolean present = false;
            for (int other = 0; other < values.length; other++) {
                if (other != cell && values[other] == value && related(puzzle, cell, other)) {
                    present = true;
                    break;
                }
            }
            if (!present) legal.add(value);
        }
        return legal.stream().mapToInt(Integer::intValue).toArray();
    }

    public static int[] conflicts(PuzzleDefinition puzzle, int[] values) {
        boolean[] conflict = new boolean[values.length];
        for (int first = 0; first < values.length; first++) {
            if (values[first] == 0) continue;
            for (int second = first + 1; second < values.length; second++) {
                if (values[first] == values[second] && related(puzzle, first, second)) {
                    conflict[first] = true;
                    conflict[second] = true;
                }
            }
        }
        List<Integer> cells = new ArrayList<>();
        for (int cell = 0; cell < values.length; cell++) if (conflict[cell]) cells.add(cell);
        return cells.stream().mapToInt(Integer::intValue).toArray();
    }
}
