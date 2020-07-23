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
    public Integer compile;
    public Integer testCompile;
    public Integer testSuite;
    public Integer classes;
    public Integer executables;
    public Integer tests;

    @Override
    public String toString() {
        return "Stats [classes=" + classes + ", compile=" + compile + ", executables=" + executables + ", javaLoC="
                + javaLoC + ", loC=" + loC + ", testCompile=" + testCompile + ", testCoveredLoC=" + testCoveredLoC
                + ", testSuite=" + testSuite + ", tests=" + tests + "]";
    }

    public Map<String, Object> toMap() {
        Map<String, Object> r = new HashMap<>();
        r.put("loC", loC);
        r.put("javaLoC", javaLoC);
        r.put("testCoveredLoC", testCoveredLoC);
        r.put("compile", compile);
        r.put("testCompile", testCompile);
        r.put("testSuite", testSuite);
        r.put("classes", classes);
        r.put("executables", executables);
        r.put("tests", tests);
        return r;
    }
}