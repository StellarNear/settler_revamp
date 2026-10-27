package com.apogames.settler.level.algorithmX;

import com.apogames.settler.level.helper.Helper;
import com.badlogic.gdx.math.GridPoint2;

import java.util.ArrayList;

public class AlgorithmX {

    private final int maxValue = 5;

    byte[][] background;
    byte[][] numbers;
    byte[][] regionSize;

    private int[][] matrix;

    public static void main(String[] args) {
        AlgorithmX algorithmX = new AlgorithmX();
        algorithmX.createMatrix(algorithmX.background, algorithmX.numbers, algorithmX.regionSize);
    }

    public AlgorithmX() {
//        byte[][] background = new byte[][] {
//                {4,1,1,1},
//                {4,3,3,1},
//                {4,3,3,3},
//                {4,2,2,2},
//        };
//
//        byte[][] numbers = new byte[][] {
//                {2,0,2,0},
//                {0,0,0,0},
//                {0,0,0,0},
//                {0,0,0,0},
//        };
//
//        byte[][] regionSize = new byte[][] {
//                {4,4,4,4},
//                {4,5,5,4},
//                {4,5,5,5},
//                {4,3,3,3},
//        };


        byte[][] background = new byte[][] {
                {4,4,3,3,3,4,4,4,1},
                {4,4,2,3,4,4,2,1,1},
                {4,2,2,1,1,1,2,1,4},
                {3,2,2,4,1,1,2,4,4},
                {3,3,3,4,4,2,2,4,4},
        };

        byte[][] numbers = new byte[][] {
                {0,0,0,0,0,0,0,0,0},
                {0,0,0,0,0,0,0,0,0},
                {0,0,0,0,0,0,0,0,0},
                {0,0,0,0,0,0,0,0,0},
                {0,0,0,0,0,0,0,0,0},
        };

        byte[][] regionSize = new byte[][] {
                {5,5,4,4,4,5,5,5,4},
                {5,5,5,4,5,5,5,4,4},
                {5,5,5,5,5,5,5,4,5},
                {4,5,5,3,5,5,5,5,5},
                {4,4,4,3,3,5,5,5,5},
        };

//        byte[][] background = new byte[][] {
//                {2,4,4,4,1,4,4},
//                {1,1,4,4,1,3,3},
//                {1,2,2,1,1,3,3},
//                {1,3,2,1,2,2,3},
//                {1,3,3,3,3,2,2},
//        };
//
//        byte[][] numbers = new byte[][] {
//                {0,0,0,0,0,0,0},
//                {0,0,0,0,0,0,0},
//                {0,0,0,0,0,0,0},
//                {0,0,0,0,0,0,0},
//                {0,0,0,0,0,0,0},
//        };
//
//        byte[][] regionSize = new byte[][] {
//                {1,5,5,5,5,2,2},
//                {5,5,5,5,5,5,5},
//                {5,3,3,5,5,5,5},
//                {5,5,3,5,4,4,5},
//                {5,5,5,5,5,4,4},
//        };

//        byte[][] background = new byte[][] {
//                {2,4,4},
//                {2,4,4},
//                {2,4,2},
//        };
//
//        byte[][] numbers = new byte[][] {
//                {0,1,0},
//                {0,0,5},
//                {0,2,0},
//        };
//
//        byte[][] regionSize = new byte[][] {
//                {3,5,5},
//                {3,5,5},
//                {3,5,1},
//        };

        this.background = background;
        this.numbers = numbers;
        this.regionSize = regionSize;
    }

    public AlgorithmX(byte[][] background, byte[][] numbers, byte[][] regionSize) {
        this.background = background;
        this.numbers = numbers;
        this.regionSize = regionSize;
    }

    public void createMatrix(byte[][] background, byte[][] numbers, byte[][] regionSize) {
        this.background = background;
        this.numbers = numbers;
        this.regionSize = regionSize;

        int size = background.length * background[0].length;
        int constraints = 2;
        int quadSize = (background.length - 1) * (background[0].length - 1);
        //this.matrix = new int[size*5][size*constraints+8*5*size];
        this.matrix = new int[size*5+quadSize*5][size*constraints+quadSize*size];

        // welche Zahlen sind möglich
        int x = 0;
        int y = 0;
        int realY = 0;
        int realX = 0;
        for (int i = 0; i < size; i++) {
            byte value = numbers[y][x];
            if (value != 0) {
                this.matrix[realY + x * maxValue + value - 1][realX] = 1;
            } else {
                int region = regionSize[y][x];
                for (int r = 1; r <= region; r++) {
                    this.matrix[realY + x * maxValue + r - 1][realX] = 1;
                }
            }
            x += 1;
            realX += 1;
            if (x >= background[0].length) {
                y += 1;
                realY += x * maxValue;
                x = 0;
            }
        }

        // welche sind möglich auf der Region, wo man drin ist
        x = 0;
        y = 0;
        realY = 0;
        realX = size;
        int addX = realX;
        boolean[][] visited = new boolean[background.length][background[0].length];
        for (int i = 0; i < size; i++) {
            if (!visited[y][x]) {
                fillRegionValues(addX, x, y, background[y][x], background, visited);
                addX += this.regionSize[y][x];
            }
            x += 1;
            realX += 1;
            if (x >= background[0].length) {
                y += 1;
                realY += x * maxValue;
                x = 0;
            }
        }

        // checke deine Nachbarn drum herum
//        x = 0;
//        y = 0;
//        addX = size * 2;
//        for (int i = 0; i < size; i++) {
//            visited = new boolean[background.length][background[0].length];
//            byte[][] level = getLevel(x, y);
//            fillNeighborValues(addX, x, y, numbers[y][x], level[y][x], level, visited);
//            addX += 5 * 8;
//            x += 1;
//            if (x >= background[0].length) {
//                y += 1;
//                x = 0;
//            }
//        }

        addX = size * 2;
        int start = size*5;
        for (int curY = 0; curY + 1 < background.length; curY += 1) {
            for (int curX = 0; curX + 1 < background[0].length; curX += 1) {
                byte[][] level = new byte[background.length][background[0].length];
                level[curY][curX] = 1;
                level[curY+1][curX] = 1;
                level[curY][curX+1] = 1;
                level[curY+1][curX+1] = 1;
                fillNeighborsValues(addX, start, curX, curY, level[curY][curX], level, new boolean[background.length][background[0].length]);
                addX += 5;
                start += 5;
            }
        }

        removeUselessColumnsAndRows();

        //setHelpWithConnection();

        //setConnectionForRowAndColumn();

        //for (int i = 0; i < 2; i++) {
        //this.solve();
        //}

//        Helper.printArray(this.matrix);
    }

    public int[][] getMatrix() {
        return matrix;
    }

    private void removeUselessColumnsAndRows() {
        ArrayList<Integer> neededColumns = new ArrayList<>();
        ArrayList<Integer> neededRows = new ArrayList<>();

        for (int y = 0; y < matrix.length; y++) {
            //for (int x = 0; x < matrix[0].length; x++) {
            neededRows.add(y);
//                if (matrix[y][x] == 1) {
//                    neededRows.add(y);
//                    break;
//                }
        }
        //}

        for (int x = 0; x < matrix[0].length; x++) {
            for (int y = 0; y < matrix.length; y++) {
                if (matrix[y][x] == 1) {
                    neededColumns.add(x);
                    break;
                }
            }
        }

        int[][] realMatrix = new int[neededRows.size()][neededColumns.size()];

        int removeColumn = 0;
        int removeRow = 0;
        for (int y = 0; y < matrix.length; y++) {
            if (!neededRows.contains((Integer)y)) {
                removeRow += 1;
                continue;
            }
            removeColumn = 0;
            for (int x = 0; x < matrix[0].length; x++) {
                if (!neededColumns.contains((Integer)x)) {
                    removeColumn += 1;
                    continue;
                }
                realMatrix[y - removeRow][x - removeColumn] = this.matrix[y][x];
            }
        }
        //Helper.printArray(this.matrix);
        //System.out.println();
        //Helper.printArray(realMatrix);
        //System.out.println();
        this.matrix = realMatrix;
        //System.out.println();
    }

    private byte[][] getLevel(int x, int y) {
        byte[][] level = new byte[this.background.length][this.background[0].length];
        level[y][x] = 1;

        if (isOk(x - 1, y - 1)) level[y-1][x-1] = 1;
        if (isOk(x, y - 1)) level[y-1][x] = 1;
        if (isOk(x + 1, y - 1)) level[y-1][x+1] = 1;

        if (isOk(x - 1, y)) level[y][x-1] = 1;
        if (isOk(x + 1, y)) level[y][x+1] = 1;

        if (isOk(x - 1, y + 1)) level[y+1][x-1] = 1;
        if (isOk(x, y + 1)) level[y+1][x] = 1;
        if (isOk(x + 1, y + 1)) level[y+1][x+1] = 1;

        return level;
    }

    private void fillNeighborValues(int addX, int x, int y, int value, int region, byte[][] background, boolean[][] visited) {
        if (isOk(x, y, region, background, visited)) {
            visited[y][x] = true;

            for (int i = 1; i < 6; i++) {
                for (int j = 0; j < 8; j++) {
                    int realY = (x / background[0].length) * maxValue * background[0].length + (y * maxValue * background[0].length);
                    int realX = addX;

                    GridPoint2 point = getPointForValue(j);
                    int addCaseX = point.x;
                    int addCaseY = point.y;

                    if (!isOk(x + addCaseX, y + addCaseY, region, background, visited)) {
                        continue;
                    }

//                    if (x == 1 && y == 0) {
//                        System.out.println();
//                    }

                    int otherY = ((x) / background[0].length) * maxValue * background[0].length + ((y + addCaseY) * maxValue * background[0].length);

                    int nextY = realY + x * maxValue + i - 1;
                    int nextOtherY = otherY + (x + addCaseX) * maxValue + i - 1;
                    //int nextX =  realX + j + i - 1;
                    int nextX =  realX + j;

                    //System.out.println();

                    if (value != 0) {
                        if (value == i) {
//                            this.matrix[realY + x * maxValue + value - 1][nextX + value - 1] = 1;
//                            if (this.numbers[y+addCaseY][x+addCaseX] == value || (this.numbers[y+addCaseY][x+addCaseX] == 0 && this.regionSize[y+addCaseY][x+addCaseX] >= value)) {
//                                this.matrix[otherY + (x + addCaseX) * maxValue + value - 1][nextX + value - 1] = 1;
//                            }
                            this.matrix[nextY][nextX] = 1;
                            if (this.numbers[y+addCaseY][x+addCaseX] == value || (this.numbers[y+addCaseY][x+addCaseX] == 0 && this.regionSize[y+addCaseY][x+addCaseX] >= value)) {
                                this.matrix[nextOtherY][nextX] = 1;
                            }
                        }
                    } else {
                        int regionSize = this.regionSize[y][x];
                        if (i <= regionSize) {
                            this.matrix[nextY][nextX] = 1;
                            if (this.numbers[y+addCaseY][x+addCaseX] == i || (this.numbers[y+addCaseY][x+addCaseX] == 0 && this.regionSize[y+addCaseY][x+addCaseX] >= i)) {
                                this.matrix[nextOtherY][nextX] = 1;
                            }
                        }
                    }
                }
                addX += 8;
            }
        }
    }

    private GridPoint2 getPointForValue(int j) {
        GridPoint2 point = new GridPoint2(0, 0);
        switch (j) {
            case 0: point.x = -1; point.y = -1; break;
            case 1: point.x = 0; point.y = -1; break;
            case 2: point.x = +1; point.y = -1; break;
            case 3: point.x = -1; point.y = 0; break;
            case 4: point.x = +1; point.y = 0; break;
            case 5: point.x = -1; point.y = 1; break;
            case 6: point.x = 0; point.y = 1; break;
            case 7: point.x = +1; point.y = 1; break;

        }
        return point;
    }

    private void fillRegion(int addX, int x, int y, int region, byte[][] background, boolean[][] visited) {
        fillRegionValues(addX, x-1, y-1, region, background, visited);
        fillRegionValues(addX, x-1, y, region, background, visited);
        fillRegionValues(addX, x+1, y, region, background, visited);
        fillRegionValues(addX, x, y+1, region, background, visited);
    }

    private void fillRegionValues(int addX, int x, int y, int region, byte[][] background, boolean[][] visited) {
        if (isOk(x, y, region, background, visited)) {
            byte value = numbers[y][x];
            visited[y][x] = true;

            int realY = (x / background[0].length) * maxValue * background[0].length + (y * maxValue * background[0].length);
            int realX = addX;

            if (value != 0) {
                this.matrix[realY + x * maxValue + value - 1][realX + value - 1] = 1;
            } else {
                int regionSize = this.regionSize[y][x];
                for (int r = 1; r <= regionSize; r++) {
                    this.matrix[realY + x * maxValue + r - 1][realX + r - 1] = 1;
                }
            }

            fillRegion(addX, x, y, region, background, visited);
        }
    }

    private void fillNeighborsValues(int addX, int start, int x, int y, int region, byte[][] background, boolean[][] visited) {
        if (isOk(x, y, region, background, visited)) {
            visited[y][x] = true;
            byte value = numbers[y][x];

            int addCaseX = 0;
            int addCaseY = 0;
            for (int i = 0; i < 8; i++) {

                if (i == 0) {
                    i += 3;
                } else {
                    GridPoint2 pointForValue = getPointForValue(i);
                    addCaseX = pointForValue.x;
                    addCaseY = pointForValue.y;

                    if (i == 4) {
                        i += 1;
                    }
                }

                int realY = ((x) / background[0].length) * maxValue * background[0].length + ((y + addCaseY) * maxValue * background[0].length);
                int realX = addX;

//                if (value > 0) {
//                    this.matrix[realY + (x + addCaseX) * maxValue + value - 1][realX + value - 1] = 1;
//                    if (i < 4) {
//                        this.matrix[start + value - 1][realX + value - 1] = 1;
//                    }
//                } else {
//                    for (int r = 1; r <= 5; r++) {
//                        if ((this.numbers[y+addCaseY][x+addCaseX] == 0 && r <= this.regionSize[y+addCaseY][x+addCaseX]) || this.numbers[y+addCaseY][x+addCaseX] == r) {
//                            this.matrix[realY + (x + addCaseX) * maxValue + r - 1][realX + r - 1] = 1;
//                        }
//                        if (i < 4) {
//                            this.matrix[start + r - 1][realX + r - 1] = 1;
//                        }
//                    }
//                }
                for (int r = 1; r <= 5; r++) {
                    if (r <= this.regionSize[y+addCaseY][x+addCaseX]) {
                        if (this.numbers[y+addCaseY][x+addCaseX] == 0 || this.numbers[y+addCaseY][x+addCaseX] == r) {
                            this.matrix[realY + (x + addCaseX) * maxValue + r - 1][realX + r - 1] = 1;
                        }
                    }
                    if (i < 4) {
                        this.matrix[start + r - 1][realX + r - 1] = 1;
                    }
                }
            }
        }
    }

    private boolean isOk(int x, int y, int region, byte[][] background, boolean[][] visited) {
        if (!isOk(x, y)) {
            return false;
        }
        if (region != background[y][x]) {
            return false;
        }
        return !visited[y][x];
    }

    private boolean isOk(int x, int y) {
        return x >= 0 && x < background[0].length && y >= 0 && y < background.length;
    }

}
