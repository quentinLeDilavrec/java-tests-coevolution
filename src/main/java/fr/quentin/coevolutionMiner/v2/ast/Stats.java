package fr.quentin.coevolutionMiner.v2.ast;

import java.util.HashMap;
import java.util.Map;

/**
 * Stats
 */
public class Stats {
    public Integer loC;
    public Integer javaLoC;
    public Integer testCoveredLoC;
    public Integer codeCompile;
    public Integer testsCompile;
    public Integer testSuite;
    public Integer classes;
    public Integer executables;
    public Integer tests;
	public int codeAST;
	public int testsAST;

    @Override
    public String toString() {
        return "Stats [classes=" + classes + ", codeAST=" + codeAST + ", codeCompile=" + codeCompile + ", executables="
                + executables + ", javaLoC=" + javaLoC + ", loC=" + loC + ", testAST=" + testsAST + ", testCompile="
                + testsCompile + ", testCoveredLoC=" + testCoveredLoC + ", testSuite=" + testSuite + ", tests=" + tests
                + "]";
    }

    public Map<String, Object> toMap() {
        Map<String, Object> r = new HashMap<>();
        r.put("loC", loC);
        r.put("javaLoC", javaLoC);
        r.put("testCoveredLoC", testCoveredLoC);
        r.put("codeCompile", codeCompile);
        r.put("testsCompile", testsCompile);
        r.put("testSuite", testSuite);
        r.put("classes", classes);
        r.put("executables", executables);
        r.put("tests", tests);
        r.put("codeAST", codeAST);
        r.put("testsAST", testsAST);
        return r;
    }
}