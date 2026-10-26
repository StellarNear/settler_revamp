package com.apogames.tiwanaku.engine;

import java.util.ArrayList;
import java.util.List;

public final class SearchResult {
    private final UniquenessStatus status;
    private final SearchTermination termination;
    private final long visitedNodes;
    private final List<int[]> solutions;

    SearchResult(
            UniquenessStatus status,
            SearchTermination termination,
            long visitedNodes,
            List<int[]> solutions) {
        this.status = status;
        this.termination = termination;
        this.visitedNodes = visitedNodes;
        this.solutions = solutions.stream().map(int[]::clone).toList();
    }

    public UniquenessStatus status() {
        return status;
    }

    public SearchTermination termination() {
        return termination;
    }

    public long visitedNodes() {
        return visitedNodes;
    }

    public List<int[]> solutions() {
        List<int[]> copies = new ArrayList<>(solutions.size());
        for (int[] solution : solutions) {
            copies.add(solution.clone());
        }
        return List.copyOf(copies);
    }
}
