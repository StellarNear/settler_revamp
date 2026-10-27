package com.apogames.settler.level.algorithmX;

import com.apogames.settler.level.helper.Helper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;

public class AlgorithmXSolve {

    public static void main(String[] args) {
        //long t = System.nanoTime();
        AlgorithmXSolve algorithmXSolve = new AlgorithmXSolve();
        algorithmXSolve.run(9, 5);
        //System.out.println((System.nanoTime() - t)+" ns");
    }

    private ColumnNode root = null;
    public ArrayList<Node> solution = new ArrayList<>();
    public ArrayList<byte[][]> allSolutions = new ArrayList<>();

    private int maxSolutions = 0;

    //public ArrayList<AlgorithmXHelper> steps = new ArrayList<>();

    private int[][] matrix;

    private int xSize;
    private int ySize;

    public void run(int xSize, int ySize) {
        this.matrix = createMatrix(xSize, ySize);

        this.run(xSize, ySize, this.matrix);
    }

    public int[][] getMatrix() {
        return matrix;
    }

    public ColumnNode getRoot() {
        return root;
    }

    public ArrayList<Node> getSolution() {
        return solution;
    }

    public int getxSize() {
        return xSize;
    }

    public int getySize() {
        return ySize;
    }

    public ArrayList<byte[][]> run(int xSize, int ySize, int[][] matrix) {
        return run(xSize, ySize, matrix, -1);
    }

    public ArrayList<byte[][]> run(int xSize, int ySize, int[][] matrix, int maxSolutions) {
        this.xSize = xSize;
        this.ySize = ySize;

        this.matrix = matrix;

        this.maxSolutions = maxSolutions;

        this.allSolutions.clear();
        createDoubleLinkedLists(matrix);
        search(0);
        System.out.println(this.allSolutions.size());
        return this.allSolutions;
    }

    public void setUpEverything(int xSize, int ySize, int[][] matrix) {
        this.xSize = xSize;
        this.ySize = ySize;

        this.matrix = matrix;

        this.allSolutions.clear();
        createDoubleLinkedLists(matrix);
    }

    public int searchIterativ(int k) {
        return searchIterativ(k, null, null);
    }
    public int searchIterativ(int k, ColumnNode columnNode, Node node) {
        if (k < 0) {
            return 0;
        }
        ColumnNode c = columnNode;
        Node r = node;
        if ((root.right == root)) {// || (root.right.x >= 2 * (xSize * ySize))) {// && isSolved())) {// || this.solution.size() == 9) {
            showSolution();
            return k;
        }
        if (columnNode == null) {
            c = choose(); // we choose a column to cover
            cover(c);
            r = c.down;
            //showSolution();
            //System.out.println();
        } else {
            cover(c);
            //System.out.println("r = "+r);
        }
        while (r != c) {
            if (k < solution.size()) {
                Node remove = solution.remove(k);
                //System.out.println("Remove solution "+remove);
            }
            solution.add(k, r);
            //System.out.println("Add solution "+r);

            Node j = r.right;
            while (j != r) {
                cover(j.head);
                j = j.right;
            }
            //this.steps.add(new AlgorithmXHelper(k, c, r));

            return k + 1;
        }
        uncover(c);
        return k;
    }

    public int uncoverAndSearch(int k) {
        Node r = this.solution.get(k);
        ColumnNode c = r.head;

        Node r2 = solution.get(k);
        Node j2 = r2.left;
        while(j2 != r2) {
            uncover(j2.head);
            j2 = j2.left;
        }
        r = r.down;

        if (k < solution.size()) {
            Node remove = solution.remove(k);
            //System.out.println("Remove solution "+remove);
        }

        //System.out.println("uncover new r : "+r);

        return this.searchIterativ(k, c, r);
    }
    
    private int[][] createMatrix(int xSize, int ySize) {
        AlgorithmX algorithmX = new AlgorithmX();
        algorithmX.createMatrix(algorithmX.background, algorithmX.numbers, algorithmX.regionSize);
        return algorithmX.getMatrix();
    }

    private ColumnNode createDoubleLinkedLists(int[][] matrix)
    {
        root = new ColumnNode(-1, 0); // the root is used as an entry-way to the linked list i.e. we access the list through the root
        // create the column heads
        ColumnNode curColumn = root;
        for(int col = 0; col < matrix[0].length; col++) // getting the column heads from the sparse matrix and filling in the information about the
        // constraints. We iterate for all the column heads, thus going through all the items in the first row of the sparse matrix
        {
            // We create the ColumnID that will store the information. We will later map this ID to the current curColumn
            curColumn.right = new ColumnNode(col, 0);
            curColumn.right.left = curColumn;
            curColumn = (ColumnNode)curColumn.right;
            curColumn.head = curColumn;
        }
        curColumn.right = root; // making the list circular i.e. the right-most ColumnHead is linked to the root
        root.left = curColumn;

        // Once all the ColumnHeads are set, we iterate over the entire matrix
        // Iterate over all the rows
        for(int row = 0; row < matrix.length; row++) {
            // iterator over all the columns
            curColumn = (ColumnNode)root.right;
            Node lastCreatedElement = null;
            Node firstElement = null;
            for(int col = 0; col < matrix[row].length; col++) {
                if(matrix[row][col] == 1)  {
                    Node colElement = curColumn;
                    while(colElement.down != null)
                    {
                        colElement = colElement.down;
                    }
                    colElement.down = new Node(col, row);
                    if(firstElement == null) {
                        firstElement = colElement.down;
                    }
                    colElement.down.up = colElement;
                    colElement.down.left = lastCreatedElement;
                    colElement.down.head = curColumn;
                    if(lastCreatedElement != null)
                    {
                        colElement.down.left.right = colElement.down;
                    }
                    lastCreatedElement = colElement.down;
                    curColumn.size++;
                }
                curColumn = (ColumnNode)curColumn.right;
            }
            if(lastCreatedElement != null)
            {
                lastCreatedElement.right = firstElement;
                firstElement.left = lastCreatedElement;
            }
        }
        curColumn = (ColumnNode)root.right;
        for(int i = 0; i < matrix[0].length; i++)
        {
            Node colElement = curColumn;
            while(colElement.down != null)
            {
                colElement = colElement.down;
            }
            colElement.down = curColumn;
            curColumn.up = colElement;
            curColumn = (ColumnNode)curColumn.right;
        }
        return root;
    }

    private void search(int k)
    {
        if (this.allSolutions.size() >= this.maxSolutions && this.maxSolutions > 0) {
            return;
        }
        if (root.right == root) {// || (root.right.x >= 2 * (xSize * ySize))) {
            showSolution();
            return;
        }
        ColumnNode c = choose(); // we choose a column to cover
        cover(c);
        Node r = c.down;
        //showSolution();
        //System.out.println();

        while(r != c)
        {
            if(k < solution.size())
            {
                solution.remove(k); // if we had to enter this loop again
            }
            solution.add(k,r); // the solution is added

            Node j = r.right;
            while(j != r) {
                cover(j.head);
                j = j.right;
            }
            search(k+1); //recursively search

            Node r2 = (Node)solution.get(k);
            Node j2 = r2.left;
            while(j2 != r2) {
                //System.out.println("Falsch "+k+" "+r.x+" "+r.y);
                uncover(j2.head);
                j2 = j2.left;
            }
            r = r.down;
        }
        uncover(c);
    }

    public byte[][] getCurrentSolution() {
        byte[][] solutionArray = new byte[ySize][xSize];
        for (Node node : this.solution) {
            if (node.getY() < 5 * ySize * xSize) {
                int cover = node.getY() / 5;
                int x = cover % xSize;
                int y = cover / xSize;
                int value = (node.y - cover * 5) + 1;
                if (solutionArray[y][x] == 0) {
                    solutionArray[y][x] = (byte) value;
                }
            }
        }
        return solutionArray;
    }

    private void showSolution() {
        byte[][] solutionArray = getCurrentSolution();
        this.allSolutions.add(solutionArray);
    }

    private ColumnNode choose() {
        ColumnNode rightOfRoot = (ColumnNode)root.right;
        ColumnNode smallest = rightOfRoot;
        while(rightOfRoot.right != root) {
            rightOfRoot = (ColumnNode)rightOfRoot.right;
            int x = rightOfRoot.x;
            if ((x < 2 * xSize * ySize && rightOfRoot.size < smallest.size)) {// || (rightOfRoot.size > 0 && rightOfRoot.size < smallest.size)){
                smallest = rightOfRoot;
            }
        }
        return smallest;
    }

    // covers the column; used as a helper method for the search method. Pseudo code by Jonathan Chu (credited above)
    private void cover(Node column) {
        column.right.left = column.left;
        column.left.right = column.right;

        Node curRow = column.down;
        while(curRow != column) {
            Node curNode = curRow.right;
            while(curNode != curRow) {
                curNode.down.up = curNode.up;
                curNode.up.down = curNode.down;
                curNode.head.size--;
                curNode = curNode.right;
            }
            curRow = curRow.down;
        }
    }

    private void uncover(Node column) {
        Node curRow = column.up;
        while(curRow != column) {
            Node curNode = curRow.left;
            while(curNode != curRow) {
                curNode.head.size++;
                curNode.down.up = curNode;
                curNode.up.down = curNode;
                curNode = curNode.left;
            }
            curRow = curRow.up;
        }
        column.right.left = column;
        column.left.right = column;
    }
    
}
