package com.apogames.settler.level.helper;

import java.util.ArrayList;

public class FillHelp {

    private final int x;
    private final int y;
    private final ArrayList<Byte> possibleValues = new ArrayList<>();

    public FillHelp(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public ArrayList<Byte> getPossibleValues() {
        return possibleValues;
    }

    public void addAllValuesTo(int max) {
        this.possibleValues.clear();
        for (byte i = 1; i <= max; i++) {
            this.possibleValues.add(i);
        }
    }

    public void setAllValuesTo(byte value) {
        this.possibleValues.clear();
        this.possibleValues.add(value);
    }
}

