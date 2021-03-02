package fr.quentin.coevolutionMiner.v2.dependency;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.apache.commons.lang3.tuple.Pair;

import fr.quentin.coevolutionMiner.v2.evolution.Evolutions;
import fr.quentin.coevolutionMiner.v2.evolution.Evolutions.Evolution;
import fr.quentin.coevolutionMiner.v2.sources.Sources.Commit;
import fr.quentin.coevolutionMiner.v2.utils.DbUtils;
import fr.quentin.impactMiner.Position;
import fr.quentin.coevolutionMiner.v2.ast.Project;
import fr.quentin.coevolutionMiner.v2.ast.Project.AST;
import fr.quentin.coevolutionMiner.v2.ast.Project.AST.FileSnapshot;
import fr.quentin.coevolutionMiner.v2.ast.Project.AST.FileSnapshot.Range;

public abstract class Dependencies implements Iterable<Dependencies.Dependency> {

    public final Specifier spec;
    protected final Project ast;

    public Dependencies(Specifier spec, Project ast) {
        this.spec = spec;
        this.ast = ast;
    }

    /**
     * @return the ast
     */
    public Project getAst() {
        return ast;
    }

    public Map<Object, Set<Dependency>> getPerRootCause() {
        return perRoot;
    }

    public Path getCauseRootDir() {
        return null;
    }

    public JsonElement toJson() {
        Gson gson = new Gson();
        return gson.toJsonTree(getValueCompressed());
    }

    public final List<Map<String, Object>> asListofMaps() { // TODO SoftReference? memoize result?
        List<Map<String, Object>> res = new ArrayList<>();
        for (Dependency impact : impacts.values()) {
            Map<String, Object> map = impact.toMap(this);
            res.add(map);
        }
        return res;
    }

    public final Map<String, Object> getValueCompressed() { // TODO SoftReference? memoize result?
        Map<String, Object> res = new HashMap<>();
        List<Map<String, Object>> serializedDependencies = new ArrayList<>();
        List<Map<String, Object>> serializedRanges = new ArrayList<>();
        Map<Project.AST.FileSnapshot.Range, Integer> serializedEvolutionsMap = new HashMap<>();
        res.put("ranges", serializedRanges);
        res.put("impacts", serializedDependencies);
        res.put("tool", spec.miner);
        for (Dependency impact : impacts.values()) {
            Map<String, Object> content = makeDependencyContent(ast.getAst().rootDir, impact);
            content.put("type", impact.getType());
            List<Object> causes = new ArrayList<>();
            for (Dependency.DescRange aaa : impact.getCauses()) {
                causes.add(compressRefToRange(aaa, serializedEvolutionsMap, serializedRanges));
            }
            List<Object> effects = new ArrayList<>();
            for (Dependency.DescRange aaa : impact.getEffects()) {
                effects.add(compressRefToRange(aaa, serializedEvolutionsMap, serializedRanges));
            }
            serializedDependencies.add(makeDependency(content, causes, effects));
        }
        return res;
    }

    private Integer compressRefToRange(Dependency.DescRange aaa, Map<Range, Integer> map, List<Map<String, Object>> list) {
        Integer r = map.get(aaa.getTarget());
        if (r == null) {
            r = list.size();
            map.put(aaa.getTarget(), r);
            Map<String, Object> o = makeRange(aaa);
            list.add(o);
        }
        return r;
    }

    protected Map<String, Object> makeRange(Dependency.DescRange descRange) {
        return descRange.toMap();
    }

    private Map<String, Object> makeDependencyContent(Path rootDir, Dependency impact) {
        Map<String, Object> content = new HashMap<>();
        int i_cause = 0;
        for (Dependency.DescRange cause : impact.idCauses) {
            List<String> builder = new ArrayList<>();
            Range range = cause.getTarget();
            FileSnapshot file = range.getFile();
            Commit commit = file.getCommit();
            builder.add(commit.getRepository().getUrl());
            builder.add(commit.getId());
            builder.add(file.getPath());
            builder.add(range.getStart().toString());
            builder.add(range.getEnd().toString());
            content.put("cause" + i_cause++, builder.toString());
        }
        int i_effect = 0;
        for (Dependency.DescRange cause : impact.idEffects) {
            List<String> builder = new ArrayList<>();
            Range range = cause.getTarget();
            FileSnapshot file = range.getFile();
            Commit commit = file.getCommit();
            builder.add(commit.getRepository().getUrl());
            builder.add(commit.getId());
            builder.add(file.getPath());
            builder.add(range.getStart().toString());
            builder.add(range.getEnd().toString());
            content.put("effect" + i_effect++, builder.toString());
        }
        return content;
    }

    private final Map<String, Object> makeDependency(Map<String, Object> content, List<Object> causes,
            List<Object> effects) {
        Map<String, Object> res = new HashMap<>();
        // Content
        res.put("content", content);
        // effects
        res.put("effects", effects);
        // causes
        res.put("causes", causes);
        return res;
    }

    public static class Specifier {
        public final Project.Specifier projSpec;
        public final Evolutions.Specifier evoSpec;
        public final String miner;

        public Specifier(Project.Specifier projSpec, Evolutions.Specifier evoSpec, String miner) {
            this.projSpec = projSpec;
            this.evoSpec = evoSpec;
            this.miner = miner;
            this.hashCode = hashCodeCompute();
        }
        // TODO allow to specify Impacts more precisely with filters

        @Override
        public int hashCode() {
            return hashCode;
        }

        private final int hashCode;

        private int hashCodeCompute() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((miner == null) ? 0 : miner.hashCode());
            result = prime * result + ((projSpec == null) ? 0 : projSpec.hashCode());
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
            if (projSpec == null) {
                if (other.projSpec != null)
                    return false;
            } else if (!projSpec.equals(other.projSpec))
                return false;
            if (evoSpec == null) {
                if (other.evoSpec != null)
                    return false;
            } else if (!evoSpec.equals(other.evoSpec))
                return false;
            return true;
        }
    }

    public Map<Range, Set<Evolution.DescRange>> getDependentTests() {
        return Collections.unmodifiableMap(impactedTests);
    }

    protected Map<Dependency, Dependency> impacts = new LinkedHashMap<>();
    protected Map<Range, Set<Evolution.DescRange>> impactedTests = new HashMap<>();
    protected Map<Object, Set<Dependency>> perRoot = new HashMap<>();

    protected Dependency addDependency(String type, Set<Pair<Range, String>> idCauses, Set<Pair<Range, String>> idEffects) {
        Dependency x = new Dependency(type, idCauses, idEffects);
        x = impacts.getOrDefault(x, x);
        impacts.putIfAbsent(x, x);
        return x;
    }

    protected void addCause(Dependency imp, Range range, String description) {
        imp.addCause(range, description);
    }

    protected void addEffect(Dependency imp, Range range, String description) {
        imp.addEffect(range, description);
    }

    // @uniq // (also put some relations as attributs (ease later fusions))
    public class Dependency {

        Dependency(String type, Set<Pair<Range, String>> idCauses, Set<Pair<Range, String>> idEffects) {
            this.type = type;
            this.idCauses = idCauses.stream().map(x -> new DescRange(x.getLeft(), x.getRight()))
                    .collect(Collectors.toSet());
            this.idEffects = idEffects.stream().map(x -> new DescRange(x.getLeft(), x.getRight()))
                    .collect(Collectors.toSet());
            ;
            causes.addAll(this.idCauses);
            effects.addAll(this.idEffects);
            this.hashCode = hashCodeCompute();
        }

        private final String type;
        private final Set<DescRange> effects = new HashSet<>();
        private final Set<DescRange> causes = new HashSet<>();
        private final Set<DescRange> idEffects;
        private final Set<DescRange> idCauses;

        /**
         * @return the type
         */
        public String getType() {
            return type;
        }

        // @relations
        public Set<Dependency.DescRange> getCauses() {
            return Collections.unmodifiableSet(causes);
        }

        // @relations
        public Set<Dependency.DescRange> getEffects() {
            return Collections.unmodifiableSet(effects);
        }

        public void addCause(Range range, String description) {
            causes.add(new DescRange(range, description));
        }

        public void addEffect(Range range, String description) {
            effects.add(new DescRange(range, description));
        }

        // // @relation
        // public class Cause extends DescRange {
        // Cause(Range range, String description) {
        // super(range, description);
        // }
        // }

        // // @relation
        // public class Effect extends DescRange {
        // Effect(Range range, String description) {
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
                this.hashCode = hashCodeCompute();
            }

            public String getDescription() {
                return description;
            }

            // @source
            public Dependency getSource() {
                return Dependency.this;
            }

            // @target
            public Range getTarget() {
                return range;
            }

            @Override
            public int hashCode() {
                return hashCode;
            }

            private final int hashCode;

            private int hashCodeCompute() {
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

            private Map<String, Object> toMap() {
                Map<String, Object> o = getTarget().toMap();
                o.put("description", getDescription());
                return o;
            }
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

        private final int hashCode;

        private int hashCodeCompute() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((idCauses == null) ? 0 : idCauses.hashCode());
            result = prime * result + ((idEffects == null) ? 0 : idEffects.hashCode());
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
            Dependency other = (Dependency) obj;
            if (!getEnclosingInstance().equals(other.getEnclosingInstance()))
                return false;
            if (idCauses == null) {
                if (other.idCauses != null)
                    return false;
            } else if (!idCauses.equals(other.idCauses))
                return false;
            if (idEffects == null) {
                if (other.idEffects != null)
                    return false;
            } else if (!idEffects.equals(other.idEffects))
                return false;
            if (type == null) {
                if (other.type != null)
                    return false;
            } else if (!type.equals(other.type))
                return false;
            return true;
        }

        private Dependencies getEnclosingInstance() {
            return Dependencies.this;
        }

        public Map<String, Object> toMap(Dependencies impacts) {
            Map<String, Object> content = impacts.makeDependencyContent(impacts.ast.getAst().rootDir, this);
            content.put("type", getType());
            List<Object> causes = new ArrayList<>();
            for (DescRange aaa : getCauses()) {
                Map<String, Object> o = impacts.makeRange(aaa);
                causes.add(o);
            }
            causes.sort((a, b) -> a.hashCode() - b.hashCode());
            List<Object> effects = new ArrayList<>();
            for (DescRange aaa : getEffects()) {
                Map<String, Object> o = impacts.makeRange(aaa);
                effects.add(o);
            }
            effects.sort((a, b) -> a.hashCode() - b.hashCode());
            Map<String, Object> map = impacts.makeDependency(content, causes, effects);
            return map;
        }
    }

    @Override
    public Iterator<Dependency> iterator() {
        return impacts.keySet().iterator();
    }

}