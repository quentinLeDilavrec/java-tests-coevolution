package fr.quentin.coevolutionMiner.v2.coevolution;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import fr.quentin.coevolutionMiner.v2.evolution.Evolutions;
import fr.quentin.coevolutionMiner.v2.evolution.Evolutions.Evolution;
import fr.quentin.coevolutionMiner.v2.sources.Sources;
import fr.quentin.impactMiner.ImpactChain;
import fr.quentin.impactMiner.ImpactElement;
import fr.quentin.impactMiner.Impacts.Relations;
import fr.quentin.impactMiner.Position;
import fr.quentin.coevolutionMiner.v2.ast.Project;
import fr.quentin.coevolutionMiner.v2.ast.Project.AST.FileSnapshot.Range;

// TODO try to extends Evolutions or make an interface for inner class Evolution and implement it with CoEvolution
public class CoEvolutions {

    public final Specifier spec;

    public CoEvolutions(Specifier spec) {
        this.spec = spec;
    }

    public static class Specifier {
        public final Evolutions.Specifier evoSpec;
        public final String miner;
        public final Sources.Specifier srcSpec;

        public Specifier(Sources.Specifier srcSpec, Evolutions.Specifier evoSpec, String miner) {
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

    public static class CoEvolution {
        protected Set<Evolution> causes;
        protected Set<Evolution> resolutions;
        public Set<Evolution> getCauses() {
            return causes;
        }
        public Set<Evolution> getResolutions() {
            return resolutions;
        }
        // public AST.FileSnapshot.Range TestBefore;
        // public AST.FileSnapshot.Range TestAfter;
		public Set<Range> getTestsBefore() {
            // for (Evolution aaa : evosLong) {
            //     if (aaa.getType().equals("Move Method")||aaa.getType().equals("Change Variable Type")||aaa.getType().equals("Rename Variable")) {
            //         tmp.TestAfter = aaa.getAfter().get(0).getTarget();
            //     }
            // }
			return null;
        }
        
		public Set<Range> getTestsAfter() {
			return null;
        }

        @Override
        public int hashCode() {
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

        enum Validation {
            NONE,
            VALIDATED,
            NOT_CONCLUSIVE
        }

        private Validation validation = Validation.NONE;

        public Validation getValidation() {
            return validation;
        }

        public void validate() {
            this.validation = Validation.VALIDATED;
        }

        public void validationNotConclusive() {
            this.validation = Validation.NOT_CONCLUSIVE;
        }
        
    }

    public Set<CoEvolution> getValidated() {
        return null;
    }

    public Set<CoEvolution> getUnvalidated() {
        return null;
    }

    public JsonElement toJson() {
        return new JsonObject();
    }
}