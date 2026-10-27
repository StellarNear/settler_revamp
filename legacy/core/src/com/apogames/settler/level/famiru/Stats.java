package com.apogames.settler.level.famiru;

import java.util.List;

public class Stats {

    public int numberOfChoices;
    public int numberOfPrimaryConstraints;
    public int numberOfSecondaryConstraints;
    public int numberOfElements;
    public int numberOfSolutions;
    public List<Long> numberOfUpdates;
    public List<Long> numberOfVisitedNodes;

    public Stats(int numberOfChoices, int numberOfPrimaryConstraints, int numberOfSecondaryConstraints,
                 int numberOfElements, int numberOfSolutions,
                 List<Long> numberOfUpdates, List<Long> numberOfVisitedNodes) {
        this.numberOfChoices = numberOfChoices;
        this.numberOfPrimaryConstraints = numberOfPrimaryConstraints;
        this.numberOfSecondaryConstraints = numberOfSecondaryConstraints;
        this.numberOfElements = numberOfElements;
        this.numberOfSolutions = numberOfSolutions;
        this.numberOfUpdates = numberOfUpdates;
        this.numberOfVisitedNodes = numberOfVisitedNodes;
    }

    public int numberOfConstraints() {
        return numberOfPrimaryConstraints + numberOfSecondaryConstraints;
    }
}
