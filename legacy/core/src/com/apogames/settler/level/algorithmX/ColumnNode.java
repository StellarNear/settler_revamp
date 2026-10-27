package com.apogames.settler.level.algorithmX;

public class ColumnNode extends Node {

    int column;

    public ColumnNode() {
        super(-1, 0);

        this.column = -1;
    }

    public ColumnNode(int x, int y) {
        super(x, y);

        this.column = x;
    }
}
