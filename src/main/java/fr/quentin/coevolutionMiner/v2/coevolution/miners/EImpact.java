package fr.quentin.coevolutionMiner.v2.coevolution.miners;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.math.Fraction;
import org.apache.commons.lang3.tuple.ImmutablePair;

import fr.quentin.coevolutionMiner.v2.ast.Project.AST.FileSnapshot.Range;
import fr.quentin.coevolutionMiner.v2.evolution.Evolutions.Evolution;

public class EImpact {

    public static class FailureReport {
        public final String what;
        public final String where;
        public final String when;

        public FailureReport(String what, String where, String when) {
            this.what = what;
            this.where = where;
            this.when = when;
            this.hashCode = hashCodeCompute();
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

        private final int hashCode;

        private int hashCodeCompute() {
            int result = 1;
            result = 31 * result + ((what == null) ? 0 : what.hashCode());
            result = 31 * result + ((when == null) ? 0 : when.hashCode());
            result = 31 * result + ((where == null) ? 0 : where.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            FailureReport other = (FailureReport) obj;
            if (what == null) {
                if (other.what != null)
                    return false;
            } else if (!what.equals(other.what))
                return false;
            if (when == null) {
                if (other.when != null)
                    return false;
            } else if (!when.equals(other.when))
                return false;
            if (where == null) {
                if (other.where != null)
                    return false;
            } else if (!where.equals(other.where))
                return false;
            return true;
        }

    }

    public static class ImpactedRange {
        public final Range range;
        public final EImpact.FailureReport report;

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((range == null) ? 0 : range.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            ImpactedRange other = (ImpactedRange) obj;
            if (range == null) {
                if (other.range != null)
                    return false;
            } else if (!range.equals(other.range))
                return false;
            return true;
        }

        public ImpactedRange(Range range, FailureReport report) {
            this.range = range;
            this.report = report;
        }

        @Override
        public String toString() {
            return "ImpactedRange [range=" + range + ", report=" + report + "]";
        }

    }

    // TODO a nested map would be more precise (such as when tests are duplicated)
    private final Map<Range, ImpactedRange> tests;
    private final Set<Evolution> evolutions;
    private final int hashCode;

    public Set<Evolution> getEvolutions() {
        return evolutions;
    }

    public Set<Range> getSharedTests() {
        return tests.keySet();
    }

    public ImpactedRange getSharingTest(Range test) {
        return tests.get(test);
    }

    public EImpact(Map<Range, ImpactedRange> tests, Set<Evolution> evolutions) {
        this.tests = new HashMap<>(tests);
        this.evolutions = Collections.unmodifiableSet(new LinkedHashSet<>(evolutions));

        this.hashCode = hashCodeCompute();
    }

    public EImpact(Range executed, EImpact.FailureReport report) {
        this.tests = Collections.singletonMap(executed, new ImpactedRange(executed, report));
        this.evolutions = Collections.emptySet();

        this.hashCode = hashCodeCompute();
    }

    public EImpact(Range testBefore, Range executed, FailureReport report, Set<Evolution> evolutions) {
        this.tests = Collections.singletonMap(testBefore, new ImpactedRange(executed, report));
        this.evolutions = Collections.unmodifiableSet(new LinkedHashSet<>(evolutions));

        this.hashCode = hashCodeCompute();
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    private int hashCodeCompute() {
        int result = 1;
        result = 31 * result + ((evolutions == null) ? 0 : evolutions.hashCode());
        result = 31 * result + ((tests == null) ? 0 : tests.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        EImpact other = (EImpact) obj;
        if (evolutions == null) {
            if (other.evolutions != null)
                return false;
        } else if (!evolutions.equals(other.evolutions))
            return false;
        if (tests == null) {
            if (other.tests != null)
                return false;
        } else if (!tests.equals(other.tests))
            return false;
        return true;
    }

}