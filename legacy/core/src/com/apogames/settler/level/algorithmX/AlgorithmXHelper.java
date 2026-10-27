package com.apogames.settler.level.algorithmX;

public class AlgorithmXHelper {

    private int step;
    private ColumnNode columnNode;
    private Node node;

    public AlgorithmXHelper(int step, ColumnNode columnNode, Node node) {
        this.step = step;
        this.columnNode = columnNode;
        this.node = node;
    }

    public int getStep() {
        return step;
    }

    public Node getNode() {
        return node;
    }

    public ColumnNode getColumnNode() {
        return columnNode;
    }
}
