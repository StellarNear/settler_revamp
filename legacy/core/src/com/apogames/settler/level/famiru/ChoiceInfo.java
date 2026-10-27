package com.apogames.settler.level.famiru;

public class ChoiceInfo {

    public int x;
    public int y;
    public int number;

    public ChoiceInfo(int x, int y, int number) {
        this.x = x;
        this.y = y;
        this.number = number;
    }

    public int getX() { return x; }

    public int getY() { return y; }

    public int getNumber() { return number; }

    @Override
    public String toString() {
        return "(" + x + "|" + y + ") = " + number;
    }
}
