package fr.quentin.coevolutionMiner.v2.coevolution.miners;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.math.Fraction;
import org.apache.commons.lang3.tuple.ImmutablePair;

import fr.quentin.coevolutionMiner.v2.ast.Project.AST.FileSnapshot.Range;
import fr.quentin.coevolutionMiner.v2.evolution.Evolutions.Evolution;

class EImpact {

    static class FailureReport {
	    String what;
	    String where;
	    String when;
	}

	public final Map<Range, ImmutablePair<Range, EImpact.FailureReport>> tests = new HashMap<>();
    public final Map<Evolution, Fraction> evolutions = new HashMap<>();

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((evolutions == null) ? 0 : evolutions.hashCode());
        result = prime * result + ((tests == null) ? 0 : tests.hashCode());
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