package fr.quentin.coevolutionMiner.v2.coevolution;

import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.apache.commons.lang3.tuple.ImmutablePair;

import fr.quentin.coevolutionMiner.v2.evolution.Evolutions;
import fr.quentin.coevolutionMiner.v2.evolution.Evolutions.Evolution;
import fr.quentin.coevolutionMiner.v2.sources.Sources;
import fr.quentin.impactMiner.ImpactChain;
import fr.quentin.impactMiner.ImpactElement;
import fr.quentin.impactMiner.Impacts.Relations;
import fr.quentin.impactMiner.Position;
import fr.quentin.coevolutionMiner.v2.ast.Project;
import fr.quentin.coevolutionMiner.v2.ast.Project.AST.FileSnapshot.Range;
import fr.quentin.coevolutionMiner.v2.coevolution.miners.EImpact;
import fr.quentin.coevolutionMiner.v2.coevolution.miners.EImpact.FailureReport;
import fr.quentin.coevolutionMiner.v2.coevolution.miners.EImpact.ImpactedRange;

// TODO try to extends Evolutions or make an interface for inner class Evolution and implement it with CoEvolution
public abstract class CoEvolutions {

    public final Specifier spec;

    protected CoEvolutions(Specifier spec) {
        this.spec = spec;
    }

    public static class FailedCoEvolutions extends CoEvolutions {

        public Exception exception;

        public FailedCoEvolutions(Specifier spec, Exception exc) {
            super(spec);
            this.exception = exc;
        }

        @Override
        public Set<CoEvolution> getCoEvolutions() {
            return Collections.emptySet();
        }

        @Override
        public Set<EImpact> getEImpacts() {
            return Collections.emptySet();
        }

        @Override
        public Set<ImpactedRange> getInitialTests() {
            return Collections.emptySet();
        }
        
    }

    public static class Specifier {
        public final Evolutions.Specifier evoSpec;
        public final Class<? extends CoEvolutionsMiner> miner;
        public final Sources.Specifier srcSpec;

        public Specifier(Sources.Specifier srcSpec, Evolutions.Specifier evoSpec, 
                Class<? extends CoEvolutionsMiner> miner) {
            this.srcSpec = srcSpec;
            this.evoSpec = evoSpec;
            this.miner = miner;
        }
        // TODO allow to specify Impacts more precisely with filters

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((miner == null) ? 0 : miner.hashCode());
            result = prime * result + ((evoSpec == null) ? 0 : evoSpec.hashCode());
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
            Specifier other = (Specifier) obj;
            if (miner == null) {
                if (other.miner != null)
                    return false;
            } else if (!miner.equals(other.miner))
                return false;
            if (evoSpec == null) {
                if (other.evoSpec != null)
                    return false;
            } else if (!evoSpec.equals(other.evoSpec))
                return false;
            return true;
        }
    }

    public abstract class CoEvolution {
        protected final Set<Evolution> causes;
        protected final Set<Evolution> resolutions;

        protected final Set<Range> testsBefore;
        protected final Set<Range> testsAfter;

        public CoEvolution(Set<Evolution> causes, Set<Evolution> resolutions, Set<Range> testsBefore,
                Set<Range> testsAfter) {
            this.causes = causes;
            this.resolutions = resolutions;
            this.testsBefore = testsBefore;
            this.testsAfter = testsAfter;
            this.hashCode = hashCodeCompute();
        }

        public final Set<Evolution> getCauses() {
            return causes;
        }

        public final Set<Evolution> getResolutions() {
            return resolutions;
        }

        public final Set<Range> getTestsBefore() {
            return Collections.unmodifiableSet(testsBefore);
        }

        public final Set<Range> getTestsAfter() {
            return Collections.unmodifiableSet(testsAfter);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

        private final int hashCode;

        private int hashCodeCompute() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((causes == null) ? 0 : causes.hashCode());
            result = prime * result + ((resolutions == null) ? 0 : resolutions.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (!(obj instanceof CoEvolution))
                return false;
            CoEvolution other = (CoEvolution) obj;
            if (causes == null) {
                if (other.causes != null)
                    return false;
            } else if (!causes.equals(other.causes))
                return false;
            if (resolutions == null) {
                if (other.resolutions != null)
                    return false;
            } else if (!resolutions.equals(other.resolutions))
                return false;
            return true;
        }
    }

    public abstract Set<CoEvolution> getCoEvolutions();

    public abstract Set<EImpact> getEImpacts();

    public abstract Set<EImpact.ImpactedRange> getInitialTests();

    public JsonElement toJson() {
        return new JsonObject();
    }
}