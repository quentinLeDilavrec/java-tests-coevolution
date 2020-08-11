package fr.quentin.coevolutionMiner.v2.evolution;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.refactoringminer.api.Refactoring;

import fr.quentin.coevolutionMiner.v2.sources.Sources;
import fr.quentin.coevolutionMiner.v2.sources.Sources.Commit;
import spoon.reflect.declaration.CtElement;
import fr.quentin.coevolutionMiner.v2.ast.Project;
import fr.quentin.coevolutionMiner.v2.ast.Project.AST.FileSnapshot.Range;

public class Evolutions implements Iterable<Evolutions.Evolution>{

    public static class Specifier {
        public final Sources.Specifier sources;
        public final Class<? extends EvolutionsMiner> miner;
        public final String commitIdBefore;
        public final String commitIdAfter;

        public Specifier(Sources.Specifier sources, String commitIdBefore, String commitIdAfter, Class<? extends EvolutionsMiner> miner) {
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

    public final Specifier spec;

    // public List<fr.quentin.impactMiner.Evolution<Refactoring>> toList() {
    // return new ArrayList<>();
    // }
    public Set<Evolution> toSet() {
        return new HashSet<>();
    }

    public List<Evolutions> perBeforeCommit() {
        return new ArrayList<>();
    }

    public JsonElement toJson() {
        return new JsonObject();
    }

    // TODO see if it is needed
    // public Evolution getEvolution(String type, List<ImmutablePair<Range,String>>
    // before, List<ImmutablePair<Range,String>> after) {
    // return null;
    // }

    protected final Set<Evolution> evolutions = new HashSet<>();
    private Map<ImmutablePair<String, List<ImmutablePair<Range, String>>>, Evolution> evoByBeforeList = new HashMap<>();
    protected final Sources sources;

    public Sources getSources() {
        return sources;
    }

    public Evolution getEvolution(String type, List<ImmutablePair<Range, String>> before, Project<?> target) {
        Evolution tmp = evoByBeforeList.get(new ImmutablePair<>(type, before));
        if (tmp == null) {
            throw new RuntimeException("evo of type " + type + " and " + before + " is not in list");
        }
        // TODO do some checks on target
        return tmp;
    }

    public Evolutions(Specifier spec, Sources sources) {
        this.spec = spec;
        this.sources = sources;
    }

    protected final Evolution addEvolution(String type, List<ImmutablePair<Range, String>> before,
            List<ImmutablePair<Range, String>> after, Commit commitBefore, Commit commitAfter, Object original) {
        Evolution evo = new Evolutions.Evolution(original, type, commitBefore, commitAfter);
        for (ImmutablePair<Range, String> immutablePair : before) {
            evo.addBefore(immutablePair.getLeft(), immutablePair.getRight());
        }
        for (ImmutablePair<Range, String> immutablePair : after) {
            evo.addAfter(immutablePair.getLeft(), immutablePair.getRight());
        }
        evolutions.add(evo);
        Evolution old = evoByBeforeList.put(new ImmutablePair<>(type, before), evo);
        if (old != null && evo.equals(old))
            Logger.getLogger("evo").info("evo sharing same type and before");
        return evo;
    }

    // @uniq // (also put relations as attributs)
    public class Evolution {

        private Evolution(Object original, String type) {
            this.original = original;
            this.type = type;
        }

        Evolution(Object original, String type, Sources.Commit commitBefore, Sources.Commit commitAfter) {
            this(original, type);
            this.commitBefore = commitBefore;
            this.commitAfter = commitAfter;
        }

        private Object original;
        private String type;
        private List<DescRange> after = new ArrayList<>();
        private List<DescRange> before = new ArrayList<>();

        private Sources.Commit commitBefore;
        private Sources.Commit commitAfter;

        /**
         * @return the commitBefore
         */
        public Sources.Commit getCommitBefore() {
            return commitBefore;
        }

        /**
         * @return the commitAfter
         */
        public Sources.Commit getCommitAfter() {
            return commitAfter;
        }

        /**
         * @return the type
         */
        public String getType() {
            return type;
        }

        /**
         * @return the original
         */
        public Object getOriginal() {
            return original;
        }

        // @relations
        public List<DescRange> getBefore() {
            return Collections.unmodifiableList(before);
        }

        // @relations
        public List<DescRange> getAfter() {
            return Collections.unmodifiableList(after);
        }

        void addBefore(Range range, String description) {
            before.add(new DescRange(range, description));
        }

        void addAfter(Range range, String description) {
            after.add(new DescRange(range, description));
        }

        // // @relation
        // public class Before extends DescRange {
        // Before(Range range, String description) {
        // super(range, description);
        // }
        // }

        // // @relation
        // public class After extends DescRange {
        // After(Range range, String description) {
        // super(range, description);
        // }
        // }

        // @relation
        public class DescRange {
            private final String description;
            private final Range range;

            DescRange(Range range, String description) {
                this.range = range;
                this.description = description;
            }

            public String getDescription() {
                return description;
            }

            // @source
            public Evolution getSource() {
                return Evolution.this;
            }

            // @target
            public Range getTarget() {
                return range;
            }

            @Override
            public int hashCode() {
                final int prime = 31;
                int result = 1;
                result = prime * result + ((description == null) ? 0 : description.hashCode());
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
                DescRange other = (DescRange) obj;
                if (description == null) {
                    if (other.description != null)
                        return false;
                } else if (!description.equals(other.description))
                    return false;
                if (range == null) {
                    if (other.range != null)
                        return false;
                } else if (!range.equals(other.range))
                    return false;
                return true;
            }
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((after == null) ? 0 : after.hashCode());
            result = prime * result + ((before == null) ? 0 : before.hashCode());
            result = prime * result + ((type == null) ? 0 : type.hashCode());
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
            Evolution other = (Evolution) obj;
            if (after == null) {
                if (other.after != null)
                    return false;
            } else if (!after.equals(other.after))
                return false;
            if (before == null) {
                if (other.before != null)
                    return false;
            } else if (!before.equals(other.before))
                return false;
            if (type == null) {
                if (other.type != null)
                    return false;
            } else if (!type.equals(other.type))
                return false;
            return true;
        }

        private Evolutions getEnclosingInstance() {
            return Evolutions.this;
        }
    }

	public Project<?>.AST.FileSnapshot.Range map(Project<?>.AST.FileSnapshot.Range testBefore, Project<?> target) {
		return null;
	}

    @Override
    public Iterator<Evolution> iterator() {
        return evolutions.iterator();
    }
}