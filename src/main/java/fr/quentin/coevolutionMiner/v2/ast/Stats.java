package fr.quentin.coevolutionMiner.v2.ast;

/**
 * Stats
 */
public class Stats {
    public Integer loC;
	public Integer javaLoC;
    public Integer testCoveredLoC;
    public Integer compile;
    public Integer testCompile;
    public Integer testSuite;
    public Integer classes;
    public Integer executables;
    public Integer tests;

    @Override
    public String toString() {
        return "Stats [classes=" + classes + ", compile=" + compile + ", executables=" + executables
                + ", javaLoC=" + javaLoC + ", loC=" + loC + ", testCompile=" + testCompile + ", testCoveredLoC="
                + testCoveredLoC + ", testSuite=" + testSuite + ", tests=" + tests + "]";
    }
}