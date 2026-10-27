package com.apogames.settler.level.algorithmX;

public class Node {
    int x;
    int y;
    Node left;
    Node right;
    Node up;
    Node down;

    ColumnNode head;
    int size;

    private boolean solution;
    private boolean visited;

    public Node() {
        this(0, 0);
    }

    public Node(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public Node getLeft() {
        return left;
    }

    public void setLeft(Node left) {
        this.left = left;
    }

    public Node getRight() {
        return right;
    }

    public void setRight(Node right) {
        this.right = right;
    }

    public Node getUp() {
        return up;
    }

    public void setUp(Node up) {
        this.up = up;
    }

    public Node getDown() {
        return down;
    }

    public void setDown(Node down) {
        this.down = down;
    }

    public ColumnNode getHead() {
        return head;
    }

    public void setHead(ColumnNode head) {
        this.head = head;
    }

    public boolean isSolution() {
        return solution;
    }

    public void setSolution(boolean solution) {
        this.solution = solution;
    }

    public boolean isVisited() {
        return visited;
    }

    public void setVisited(boolean visited) {
        this.visited = visited;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }
}
