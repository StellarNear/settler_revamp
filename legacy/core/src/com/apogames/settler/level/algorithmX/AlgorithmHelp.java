package com.apogames.settler.level.algorithmX;

public class AlgorithmHelp {

    private final int x;
    private final int y;

    private int left = -1;

    private int right = -1;
    private int up = -1;
    private int down = -1;

    private int size;

    private boolean saved;

    public AlgorithmHelp(final int x, final int y) {
        this.x = x;
        this.y = y;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public boolean isSaved() {
        return saved;
    }

    public void setSaved(boolean saved) {
        this.saved = saved;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getLeft() {
        return left;
    }

    public void setLeft(int left) {
        this.left = left;
    }

    public int getRight() {
        return right;
    }

    public void setRight(int right) {
        this.right = right;
    }

    public int getUp() {
        return up;
    }

    public void setUp(int up) {
        this.up = up;
    }

    public int getDown() {
        return down;
    }

    public void setDown(int down) {
        this.down = down;
    }
}
