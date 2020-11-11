package fr.quentin.coevolutionMiner.v2.evolution;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedSet;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.refactoringminer.api.Refactoring;

import fr.quentin.coevolutionMiner.v2.sources.Sources;
import fr.quentin.coevolutionMiner.v2.utils.Utils;
import spoon.reflect.declaration.CtElement;
import fr.quentin.coevolutionMiner.v2.ast.Project;
import fr.quentin.coevolutionMiner.v2.ast.Project.AST.FileSnapshot.Range;
import fr.quentin.coevolutionMiner.v2.evolution.storages.Neo4jEvolutionsStorage;

public abstract class Evolutions implements Iterable<Evolutions.Evolution> {
    public static class Specifier {
        public final Sources.Specifier sources;
        public final Class<? extends EvolutionsMiner> miner;
        public final String commitIdBefore;
        public final String commitIdAfter;

        public Specifier(final Sources.Specifier sources, final String commitIdBefore, final String commitIdAfter,
                final Class<? extends EvolutionsMiner> miner) {
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
        public boolean equals(final Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            final Specifier other = (Specifier) obj;
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

    public abstract Set<Evolution> toSet();

    public abstract Map<Sources.Commit, Evolutions> perBeforeCommit();

    public abstract JsonElement toJson();

    protected final Sources sources;

    public final Sources getSources() {
        return sources;
    }

    public abstract Set<Evolution> getEvolution(String type, Project<?> source,
            List<ImmutablePair<Range, String>> before, Project<?> target, List<ImmutablePair<Range, String>> after);

    public Evolutions(final Specifier spec, final Sources sources) {
        this.spec = spec;
        this.sources = sources;
    }

    // @uniq // (also put relations as attributs)
    public class Evolution {

        private Evolution(final Object original, final String type) {
            this.original = original;
            this.type = type;
        }

        protected Evolution(final Object original, final String type, final Sources.Commit commitBefore,
                final Sources.Commit commitAfter) {
            this(original, type);
            this.commitBefore = commitBefore;
            this.commitAfter = commitAfter;
        }

        private final Object original;
        private final String type;
        private final List<DescRange> after = new ArrayList<>();
        private final List<DescRange> before = new ArrayList<>();

        private Sources.Commit commitBefore;
        private Sources.Commit commitAfter;

        /**
         * @return the commitBefore
         */
        public Sources.Commit getCommitBefore() {
            return commitBefore;
        }

        public Evolutions getContainer() {
            return Evolutions.this;
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

        void addBefore(final Range range, final String description) {
            before.add(new DescRange(range, description));
        }

        void addAfter(final Range range, final String description) {
            after.add(new DescRange(range, description));
        }

        // @relation
        public class DescRange {
            private final String description;
            private final Range range;

            DescRange(final Range range, final String description) {
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
            public boolean equals(final Object obj) {
                if (this == obj)
                    return true;
                if (obj == null)
                    return false;
                if (getClass() != obj.getClass())
                    return false;
                final DescRange other = (DescRange) obj;
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
        public boolean equals(final Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            final Evolution other = (Evolution) obj;
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

        protected void addUrl(Map<String, Object> map) {
            final StringBuilder url = new StringBuilder();
            Map<String, Object> content = (Map<String, Object>) map.get("content");
            url.append("http://localhost:50000/?repo=" + content.get("repository"));
            url.append("&before=" + content.get("commitIdBefore"));
            url.append("&after=" + content.get("commitIdAfter"));
            url.append("&type=" + content.get("type"));

            for (Map<String, Object> descR : (List<Map<String, Object>>) map.get("leftSideLocations")) {
                url.append("&before" + abrv((String) descR.get("description")) + "=" + rangeToString(descR));
            }
            for (Map<String, Object> descR : (List<Map<String, Object>>) map.get("rightSideLocations")) {
                url.append("&after" + abrv((String) descR.get("description")) + "=" + rangeToString(descR));
            }

            content.put("url", url.toString());
        }

        private String rangeToString(Map<String, Object> descR) {
            String string = descR.get("filePath") + ":" + descR.get("start") + "-" + descR.get("end");
            return string;
        }

        protected String abrv(String str) {
            StringBuilder strB = new StringBuilder();
            for (String word : str.split(" ")) {
                strB.append(word.substring(0, 1));
            }
            return strB.toString();
        }

        public Map<String, Object> asMap() {
            final Map<String, Object> res = new HashMap<>();
            final Map<String, Object> evofields = new HashMap<>();
            res.put("content", evofields);
            evofields.put("repository", getEnclosingInstance().spec.sources.repository);
            evofields.put("commitIdBefore", getCommitBefore().getId());
            evofields.put("commitIdAfter", getCommitAfter().getId());
            evofields.put("type", getType());

            final List<Object> leftSideLocations = new ArrayList<>();
            res.put("leftSideLocations", leftSideLocations);
            for (final DescRange descR : getBefore()) { // TODO sort list
                Range targetR = descR.getTarget();
                final Map<String, Object> rDescR = targetR.toMap();
                leftSideLocations.add(rDescR);
                final CtElement e_ori = (CtElement) targetR.getOriginal();
                if (e_ori != null) {
                    rDescR.put("type", Utils.formatedType(e_ori));
                } else {
                    rDescR.put("type", "evo null");
                }
                rDescR.put("description", descR.getDescription());
                evofields.putIfAbsent("before_" + abrv(descR.getDescription()), new ArrayList<>());
                ((List<String>) evofields.get("before_" + abrv(descR.getDescription()))).add(rangeToString(rDescR));
            }
            final List<Object> rightSideLocations = new ArrayList<>();
            res.put("rightSideLocations", rightSideLocations);
            for (final DescRange descR : getAfter()) { // TODO sort list
                Range targetR = descR.getTarget();
                final Map<String, Object> rDescR = targetR.toMap();
                rightSideLocations.add(rDescR);
                final CtElement e_ori = (CtElement) targetR.getOriginal();
                if (e_ori != null) {
                    rDescR.put("type", Utils.formatedType(e_ori));
                } else {
                    rDescR.put("type", "evo null");
                }
                rDescR.put("description", descR.getDescription());
                evofields.putIfAbsent("after_" + abrv(descR.getDescription()), new ArrayList<>());
                ((List<String>) evofields.get("after_" + abrv(descR.getDescription()))).add(rangeToString(rDescR));
            }
            addUrl(res);
            return res;
        }
    }

    public abstract Project<?>.AST.FileSnapshot.Range map(final Project<?>.AST.FileSnapshot.Range testBefore,
            final Project<?> target);

    public List<Map<String, Object>> asListofMaps() {
        List<Map<String, Object>> tmp = new ArrayList<>();
        for (Evolution evolution : this) {
            tmp.add(evolution.asMap());
        }
        return tmp;
    }
}