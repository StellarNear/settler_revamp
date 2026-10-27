package com.apogames.settler.characterization;

import com.apogames.settler.game.tiwanaku.LevelSize;
import com.apogames.settler.level.Level;
import com.apogames.settler.level.NewLevelCreate;
import com.apogames.settler.level.Solve;
import com.apogames.settler.level.famiru.App;
import com.apogames.settler.level.helper.Difficulty;
import org.junit.Test;

import java.util.ArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class LegacyEngineCharacterizationTest {
    @Test
    public void recordsAllSevenLegacyPresets() {
        int[][] expected = {
                {3, 3}, {4, 4}, {5, 5}, {7, 5}, {9, 5}, {15, 10}, {20, 12}
        };
        LevelSize[] presets = LevelSize.values();

        assertEquals(expected.length, presets.length);
        for (int index = 0; index < expected.length; index++) {
            assertEquals(expected[index][0], presets[index].getX());
            assertEquals(expected[index][1], presets[index].getY());
        }
    }

    @Test
    public void manualNotesToggleIndependentlyAndRestartClearsThem() {
        byte[][] regions = {{1, 1}};
        byte[][] solution = {{1, 2}};
        byte[][] clues = {{1, 0}};
        Level level = new Level(regions, solution, clues);

        level.toggleHint(1, 0, 1);
        level.toggleHint(1, 0, 2);
        assertTrue(level.hasHint(1, 0, 1));
        assertTrue(level.hasHint(1, 0, 2));

        level.clearHints(1, 0);
        assertFalse(level.hasHint(1, 0, 1));
        assertFalse(level.hasHint(1, 0, 2));

        level.toggleHint(1, 0, 1);
        level.getCurNumber()[0][1] = 2;
        level.registerValueClick(1, 0);
        assertEquals(1, level.getTotalClicks());

        level.restart();
        assertEquals(0, level.getCurNumber()[0][1]);
        assertFalse(level.hasHint(1, 0, 1));
        assertEquals(0, level.getTotalClicks());
    }

    @Test
    public void independentOracleDistinguishesValidInvalidAndAmbiguousBoards() {
        byte[][] oneRegionCell = {{1}};
        assertTrue(PuzzleRulesOracle.isValidCompleteBoard(oneRegionCell, new byte[][]{{1}}));
        assertEquals(1, PuzzleRulesOracle.countSolutions(oneRegionCell));

        byte[][] twoCellRegion = {{1, 1}};
        assertTrue(PuzzleRulesOracle.isValidCompleteBoard(twoCellRegion, new byte[][]{{1, 2}}));
        assertFalse(PuzzleRulesOracle.isValidCompleteBoard(twoCellRegion, new byte[][]{{1, 1}}));
        assertEquals(2, PuzzleRulesOracle.countSolutions(twoCellRegion));

        byte[][] fourSingletonRegions = {{1, 2}, {3, 4}};
        assertFalse(PuzzleRulesOracle.isValidCompleteBoard(
                fourSingletonRegions, new byte[][]{{1, 2}, {2, 1}}));
        assertEquals(0, PuzzleRulesOracle.countSolutions(fourSingletonRegions));
    }

    @Test
    public void currentErrorMarkerIncludesDiagonalDuplicates() {
        byte[][] regions = {{1, 2}, {3, 4}};
        byte[][] board = {{1, 2}, {2, 1}};
        Level level = new Level(regions, board, board);
        byte[][] errors = level.getError();

        assertEquals(1, errors[0][0]);
        assertEquals(1, errors[0][1]);
        assertEquals(1, errors[1][0]);
        assertEquals(1, errors[1][1]);
    }

    @Test(timeout = 60000)
    public void steppedGeneratorProducesAnIndependentlyValidTinySolution() {
        NewLevelCreate generator = new NewLevelCreate(Difficulty.EASY, 5);
        generator.beginGeneration(3, 3, 1, 0);

        int steps = 0;
        while (generator.isGenerating() && steps < 10000) {
            generator.stepGeneration(1);
            steps++;
        }

        assertTrue("Generator did not finish within the fixture step budget", steps < 10000);
        assertTrue("Tiny generation failed after " + generator.getAttemptCount() + " attempts",
                generator.isGenerationSuccessful());
        Level generated = generator.getLevel();
        assertNotNull(generated);
        assertTrue(PuzzleRulesOracle.isValidCompleteBoard(
                generated.getBackground(), generated.getNumbers()));
    }

    @Test
    public void currentSolveAcceptsAFullBoardThatTheIndependentOracleRejects() {
        byte[][] regions = {{1, 1}};
        byte[][] invalidFullBoard = {{1, 1}};

        ArrayList<byte[][]> solutions =
                new Solve(regions).tryToSolveLevel(copy(invalidFullBoard), 1, Difficulty.HARD);

        assertEquals("Characterizes the legacy early completion check", 1, solutions.size());
        assertFalse(PuzzleRulesOracle.isValidCompleteBoard(regions, solutions.get(0)));
    }

    @Test
    public void currentSparseSolverMissesOneRowAdjacencyConstraints() {
        ArrayList<byte[][]> solutions = new App(new byte[][]{{1, 2}}).run(1, false);

        assertNotNull(solutions);
        assertFalse("Characterizes the legacy one-row encoding defect",
                PuzzleRulesOracle.isValidCompleteBoard(new byte[][]{{1, 2}}, solutions.get(0)));
    }

    private static byte[][] copy(byte[][] source) {
        byte[][] copy = new byte[source.length][];
        for (int row = 0; row < source.length; row++) {
            copy[row] = source[row].clone();
        }
        return copy;
    }
}
