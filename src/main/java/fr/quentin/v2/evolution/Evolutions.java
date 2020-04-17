package fr.quentin.v2.evolution;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.refactoringminer.api.Refactoring;

import fr.quentin.Evolution;
import fr.quentin.v2.sources.Sources;

public class Evolutions {

    public static class Specifier {
        public final Sources.Specifier sources;
        public final String miner;
        public final String commitIdBefore;
        public final String commitIdAfter;

        public Specifier(Sources.Specifier sources, String commitIdBefore, String commitIdAfter, String miner) {
            this.sources = sources;
            this.commitIdBefore = commitIdBefore;
            this.commitIdAfter = commitIdAfter;
            this.miner = miner;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((miner == null) ? 0 : miner.hashCode());
            result = prime * result + ((commitIdBefore == null) ? 0 : commitIdBefore.hashCode());
            result = prime * result + ((commitIdAfter == null) ? 0 : commitIdAfter.hashCode());
            result = prime * result + ((sources == null) ? 0 : sources.hashCode());
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
            if (commitIdBefore == null) {
                if (other.commitIdBefore != null)
                    return false;
            } else if (!commitIdBefore.equals(other.commitIdBefore))
                return false;
            if (commitIdAfter == null) {
                if (other.commitIdAfter != null)
                    return false;
            } else if (!commitIdAfter.equals(other.commitIdAfter))
                return false;
            if (sources == null) {
                if (other.sources != null)
                    return false;
            } else if (!sources.equals(other.sources))
                return false;
            return true;
        }
    }

    public List<Evolution<Refactoring>> toList() {
        return new ArrayList<>();
    }

    public JsonElement toJson() {
        return new JsonObject();
    }

}