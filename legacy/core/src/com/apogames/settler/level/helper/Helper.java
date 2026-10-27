package com.apogames.settler.level.helper;

import java.util.ArrayList;
import java.util.List;

public class Helper {

    public static int[][] cloneArray(int[][] numbers) {
        int[][] cloneArray = new int[numbers.length][numbers[0].length];
        for (int y = 0; y < numbers.length; y++) {
            System.arraycopy(numbers[y], 0, cloneArray[y], 0, numbers[0].length);
        }
        return cloneArray;
    }

    public static byte[][] cloneArray(byte[][] numbers) {
        byte[][] cloneArray = new byte[numbers.length][numbers[0].length];
        for (int y = 0; y < numbers.length; y++) {
            System.arraycopy(numbers[y], 0, cloneArray[y], 0, numbers[0].length);
        }
        return cloneArray;
    }

    public static boolean areEqual(int[][] numbers, int[][] check) {
        if (numbers.length != check.length || numbers[0].length != check[0].length) {
            return false;
        }
        for (int y = 0; y < numbers.length; y++) {
            for (int x = 0; x < numbers[0].length; x++) {
                if (check[y][x] != numbers[y][x]) {
                    return false;
                }
            }
        }
        return true;
    }

    public static boolean areEqual(byte[][] numbers, byte[][] check) {
        if (numbers.length != check.length || numbers[0].length != check[0].length) {
            return false;
        }
        for (int y = 0; y < numbers.length; y++) {
            for (int x = 0; x < numbers[0].length; x++) {
                if (check[y][x] != numbers[y][x]) {
                    return false;
                }
            }
        }
        return true;
    }


    public static void printArray(int[][] background) {
        for (int y = 0; y < background.length; y++) {
            for (int x = 0; x < background[0].length; x++) {
                System.out.print(background[y][x]);
            }
            System.out.println();
        }
    }

    public static void printArray(byte[][] background) {
        for (byte y = 0; y < background.length; y++) {
            for (byte x = 0; x < background[0].length; x++) {
                System.out.print(background[y][x]);
            }
            System.out.println();
        }
    }

    public static List<String> getStringList(byte[][] level) {
        List<String> needed = new ArrayList<>();
        for (int i = 0; i < level.length; i++) {
            StringBuilder s = new StringBuilder();
            for (int j = 0; j < level[0].length; j++) {
                s.append((char) (level[i][j] & 0xFF));
            }
            needed.add(s.toString());
        }
        return needed;
    }

    public static boolean canPlaceValue(int x, int y, int value, boolean[][] visited, byte[][] background) {
        boolean result = isOK(x - 1, y - 1, value, visited, background);
        result &= isOK(x, y - 1, value, visited, background);
        result &= isOK(x + 1, y - 1, value, visited, background);
        result &= isOK(x - 1, y, value, visited, background);
        result &= isOK(x + 1, y, value, visited, background);
        result &= isOK(x - 1, y + 1, value, visited, background);
        result &= isOK(x, y + 1, value, visited, background);
        result &= isOK(x + 1, y + 1, value, visited, background);
        return result;
    }

    public static boolean isOK(int x, int y, int value, boolean[][] visited, byte[][] background) {
        if (x < 0 || x >= background[0].length || y < 0 || y >= background.length) {
            return true;
        }
        return background[y][x] != value || !visited[y][x];
    }

    public static boolean canPlaceValue(int x, int y, int value, byte[][] background) {
        boolean result = isOK(x - 1, y - 1, value, background);
        result &= isOK(x, y - 1, value, background);
        result &= isOK(x + 1, y - 1, value, background);
        result &= isOK(x - 1, y, value, background);
        result &= isOK(x + 1, y, value, background);
        result &= isOK(x - 1, y + 1, value, background);
        result &= isOK(x, y + 1, value, background);
        result &= isOK(x + 1, y + 1, value, background);
        return result;
    }

    public static boolean isOK(int x, int y, int value, byte[][] background) {
        if (x < 0 || x >= background[0].length || y < 0 || y >= background.length) {
            return true;
        }
        return background[y][x] != value;
    }
}
