package fr.quentin.v2.impact;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import fr.quentin.v2.evolution.Evolutions;
import fr.quentin.ImpactElement;
import fr.quentin.Impacts.Relations;
import fr.quentin.v2.ast.AST;

public class Impacts {

    public JsonElement toJson() {
        return new JsonObject();
    }

    public Map<ImpactElement, Map<ImpactElement, Relations>> getPerRootCause() {
        return null;
    }
    
    public Path getCauseRootDir() {
        return null;
    }

    public static class Specifier {
        public final AST.Specifier astSpec;
        public final Evolutions.Specifier evoSpec;
        public final String miner;

        public Specifier(AST.Specifier astSpec, Evolutions.Specifier evoSpec, String miner) {
            this.astSpec = astSpec;
            this.evoSpec = evoSpec;
            this.miner = miner;
        }
        // TODO allow to specify Impacts more precisely with filters

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((miner == null) ? 0 : miner.hashCode());
            result = prime * result + ((astSpec == null) ? 0 : astSpec.hashCode());
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
            if (astSpec == null) {
                if (other.astSpec != null)
                    return false;
            } else if (!astSpec.equals(other.astSpec))
                return false;
            if (evoSpec == null) {
                if (other.evoSpec != null)
                    return false;
            } else if (!evoSpec.equals(other.evoSpec))
                return false;
            return true;
        }
    }

}