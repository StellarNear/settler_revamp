package com.apogames.settler.level;

import java.util.ArrayList;

public class SolveCheck {

    public static void main(String[] args) {
        new SolveCheck();
    }

    public SolveCheck() {
//        byte[][] background = new byte[][] {
//                {3, 3, 1, 1, 1},
//                {3, 4, 4, 3, 1},
//                {3, 4, 2, 3, 3},
//                {4, 4, 2, 1, 3},
//                {1, 1, 1, 1, 3},
//        };
//
//        byte[][] save = new byte[][] {
//                {1, 3, 1, 0, 2},
//                {0, 0, 0, 0, 0},
//                {0, 0, 0, 4, 0},
//                {1, 0, 0, 0, 0},
//                {0, 0, 0, 0, 2},
//        };

        byte[][] background = new byte[][] {
                {1, 1, 3, 4, 4},
                {1, 1, 1, 4, 4},
                {2, 2, 2, 2, 4},
                {4, 4, 4, 1, 1},
                {3, 4, 4, 1, 1},
        };

        byte[][] save = new byte[][] {
                {0, 5, 0, 0, 5},
                {0, 0, 0, 0, 0},
                {0, 0, 1, 0, 4},
                {0, 5, 0, 0, 0},
                {0, 2, 1, 0, 0},
        };

        Solve solve = new Solve(background);
        solve.setSpotValues(save);
        ArrayList<byte[][]> solutions = solve.tryToSolveLevel(save);
        System.out.println(solutions.size());
    }

}
