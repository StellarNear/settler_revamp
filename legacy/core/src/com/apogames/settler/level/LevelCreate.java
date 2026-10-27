package com.apogames.settler.level;

import com.apogames.settler.level.helper.Difficulty;
import com.apogames.settler.level.helper.Helper;
import com.badlogic.gdx.math.GridPoint2;

import java.util.ArrayList;
import java.util.HashSet;

public class LevelCreate {

    private byte[][] myBackground;

    private boolean found = false;
    private boolean finished = false;

    private Level level;

    private Difficulty difficulty;

    public static void main(String[] args) {
        LevelCreate levelCreate = new LevelCreate(Difficulty.HARD);
        //levelCreate.createLevel(9, 9, 15, 20);
        //long t = System.nanoTime();
        for (int i = 0; i < 10; i++) {
            //levelCreate.createLevel(9, 5, 9, 9);
            levelCreate.createLevel(9, 9, 15, 20);
        }
        //System.out.println(System.nanoTime()-t);
    }

    public LevelCreate(Difficulty difficulty) {
        this.difficulty = difficulty;
    }

    private void init() {
        this.finished = false;
        this.found = false;
    }

    public Level getLevel() {
        return level;
    }

    public void createLevel(int xSize, int ySize, int fiveCount, int fourCount) {
        this.init();

        //long t = System.nanoTime();
        this.setBackground(xSize, ySize, fiveCount, fourCount);

        fillMissingNumbers();
        //System.out.println("Nur Erstellung des Levels: "+(System.nanoTime()-t)+" ns");
        //Helper.printArray(myBackground);
        //System.out.println();

        if (!finished) {
            createLevel(xSize, ySize, fiveCount, fourCount);
        } else {
//            System.out.println();
//            Helper.printArray(this.myBackground);
            FillAndUniqueCheck fill = new FillAndUniqueCheck(this.difficulty);
            if (!fill.fillLevel(myBackground)) {
                createLevel(xSize, ySize, fiveCount, fourCount);
            } else {
                this.level = fill.getLevel();
                this.finished = true;
            }
        }
    }


    private void fillMissingNumbers() {
        this.finished = false;
        HashSet<Byte> allValues = new HashSet<>();
        allValues.add((byte) 1);
        allValues.add((byte) 2);
        allValues.add((byte) 3);
        allValues.add((byte) 4);
        for (byte[] ints : myBackground) {
            for (int x = 0; x < myBackground[0].length; x++) {
                if (ints[x] != 0) {
                    allValues.remove(ints[x]);
                }
            }
        }

        byte myNumber = -1;
        if (allValues.size() == 1) {
            myNumber = (byte)(allValues.toArray()[0]);
        }
        boolean[][] visited = new boolean[myBackground.length][myBackground[0].length];
        for (int y = 0; y < myBackground.length; y++) {
            for (int x = 0; x < myBackground[0].length; x++) {
                if (this.myBackground[y][x] != 0) {
                    visited[y][x] = true;
                }
            }
        }
        for (int y = 0; y < myBackground.length; y++) {
            for (int x = 0; x < myBackground[0].length; x++) {
                if (myBackground[y][x] == 0) {
                    boolean[][] visitedNow = new boolean[myBackground.length][myBackground[0].length];
                    ArrayList<Byte> possibleValues = getPossibleValues(x, y, visitedNow);

                    if (possibleValues.isEmpty()) {
                        return;
                    }

                    byte num = possibleValues.get((byte)(Math.random() * possibleValues.size()));
                    if (possibleValues.contains(myNumber)) {
                        num = myNumber;
                    }

                    if (!fillWithNumber(x, y, num, visited, visitedNow)) {
                        fillWithNumber(x, y, (byte) 0, visited, visitedNow);
                    }
                }
            }
        }
        this.finished = true;
    }

    private boolean fillWithNumber(int x, int y, byte num, boolean[][] visited, boolean[][] visitedNow) {
        if (x < 0 || y < 0 || x >= this.myBackground[0].length || y >= this.myBackground.length) {
            return true;
        }
        if (this.myBackground[y][x] != 0) {
            return true;
        }

        if (!getPossibleValues(x, y, visitedNow).contains(num)) {
            return false;
        }

        this.myBackground[y][x] = num;
        visitedNow[y][x] = true;

        this.fillWithNumber(x - 1, y, num, visited, visitedNow);
        this.fillWithNumber(x + 1, y, num, visited, visitedNow);
        this.fillWithNumber(x, y - 1, num, visited, visitedNow);
        this.fillWithNumber(x, y + 1, num, visited, visitedNow);

        return true;
    }

    private ArrayList<Byte> getPossibleValues(int x, int y, boolean[][] visitedNow) {
        HashSet<Byte> allValues = new HashSet<>();
        allValues.add((byte) 1);
        allValues.add((byte) 2);
        allValues.add((byte) 3);
        allValues.add((byte) 4);

        allValues.remove(getValue(x - 1, y - 1, myBackground, visitedNow));
        allValues.remove(getValue(x, y - 1, myBackground, visitedNow));
        allValues.remove(getValue(x + 1, y - 1, myBackground, visitedNow));

        allValues.remove(getValue(x - 1, y, myBackground, visitedNow));
        allValues.remove(getValue(x + 1, y, myBackground, visitedNow));

        allValues.remove(getValue(x - 1, y + 1, myBackground, visitedNow));
        allValues.remove(getValue(x, y + 1, myBackground, visitedNow));
        allValues.remove(getValue(x + 1, y + 1, myBackground, visitedNow));

        return new ArrayList<>(allValues);
    }

    private byte getValue(int x, int y, byte[][] background, boolean[][] visitedNow) {
        if (x < 0 || x >= background[0].length || y < 0 || y >= background.length || visitedNow[y][x]) {
            return -1;
        }
        return background[y][x];
    }


    private void setBackground(int xSize, int ySize, int fiveCount, int fourCount) {
        this.myBackground = null;
        this.found = false;
        byte[][] background = new byte[ySize][xSize];

        boolean[][] visited = new boolean[ySize][xSize];
        for (int i = 0; i < fiveCount; i++) {
            int value = (int) (Math.random() * 4) + 1;
            int curValue = value;
            while (!setInFive(curValue, 5, visited, background)) {
                curValue += 1;
                if (curValue > 4) {
                    curValue = 1;
                }
                if (curValue == value) {
                    break;
                }
            }
        }

        for (int i = 0; i < fourCount; i++) {
            int value = (int) (Math.random() * 4) + 1;
            int curValue = value;
            while (!setInFive(curValue, 4, visited, background)) {
                curValue += 1;
                if (curValue > 4) {
                    curValue = 1;
                }
                if (curValue == value) {
                    break;
                }
            }
        }

        if (found && (!minimumThreeNumbers(background) && background.length > 4 && background[0].length > 4)) {
            setBackground(xSize, ySize, fiveCount, fourCount);
        }

        if (found) {
            if (this.myBackground == null) {
                this.myBackground = new byte[background.length][background[0].length];
                for (int y = 0; y < background.length; y++) {
                    for (int x = 0; x < background[0].length; x++) {
                        myBackground[y][x] = background[y][x];
                    }
                }
//                System.out.println();
//                printBackground();
            }
        } else {
            setBackground(xSize, ySize, fiveCount, fourCount);
        }
    }

    private boolean minimumThreeNumbers(byte[][] background) {
        HashSet<Byte> value = new HashSet<>();
        for (byte[] ints : background) {
            for (int x = 0; x < background[0].length; x++) {
                if (ints[x] != 0) {
                    value.add(ints[x]);
                }
            }
        }
        return value.size() >= 3;
    }

    private boolean setInFive(int value, int size, boolean[][] visited, byte[][] background) {
        int startX = (int)(Math.random() * background[0].length);
        int startY = (int)(Math.random() * background.length);

        int count = 0;
        while ((count < 100) && (background[startY][startX] != 0 || visited[startY][startX] || !Helper.canPlaceValue(startX, startY, value, visited, background))) {
            startX = (int)(Math.random() * background[0].length);
            startY = (int)(Math.random() * background.length);
            count += 1;
        }
        if (count >= 100) {
            //System.out.println("Nichts gefunden");
            found = false;
            return false;
        }

        found = false;
        tryPlaceValue(startX, startY, (byte) value, (int)(Math.random() * 4), size - 1, visited, background);
        if (found) {
            count = 0;
            for (int y = 0; y < background.length; y++) {
                for (int x = 0; x < background[0].length; x++) {
                    if (background[y][x] == value && !visited[y][x]) {
                        visited[y][x] = true;
                        count += 1;
                    }
                }
            }
            if (count > size) {
                return false;
            }
        } else {
            for (int y = 0; y < background.length; y++) {
                for (int x = 0; x < background[0].length; x++) {
                    if (background[y][x] != 0 && !visited[y][x]) {
                        background[y][x] = 0;
                    }
                }
            }
        }
        return this.found;
    }

    private void tryPlaceValue(int x, int y, byte value, int direction, int size, boolean[][] visited, byte[][] background) {
        if (this.found) {
            return;
        }
        if (size < 0) {
            this.found = true;
            return;
        }
        if (x < 0 || x >= background[0].length || y < 0 || y >= background.length) {
            return;
        }

        if (background[y][x] != 0 || visited[y][x] || !Helper.canPlaceValue(x, y, value, visited, background)) {
            return;
        }
        background[y][x] = value;

        int curDirection = direction;
        for (int i = 0; i < 4; i++) {
            GridPoint2 forDirection = getForDirection(curDirection);
            int addX = forDirection.x;
            int addY = forDirection.y;

            tryPlaceValue(x + addX, y + addY, value, (int)(Math.random() * 4), size - 1, visited, background);

            if (this.found) {
                return;
            }

            curDirection += 1;
            if (curDirection > 3) {
                curDirection = 0;
            }
        }

        background[y][x] = 0;
    }

    private GridPoint2 getForDirection(int direction) {
        if (direction == 1) {
            return new GridPoint2(1, 0);
        } else if (direction == 2) {
            return new GridPoint2(0, 1);
        } else if (direction == 3) {
            return new GridPoint2(-1, 0);
        }
        return new GridPoint2(0, -1);
    }

}
