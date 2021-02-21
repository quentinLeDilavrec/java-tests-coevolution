package fr.quentin.coevolutionMiner.v2.evolution.miners;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.github.gumtreediff.actions.model.Action;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import fr.quentin.coevolutionMiner.v2.ast.Project;
import fr.quentin.coevolutionMiner.v2.ast.Project.AST.FileSnapshot.Range;
import fr.quentin.coevolutionMiner.v2.ast.ProjectHandler;
import fr.quentin.coevolutionMiner.v2.evolution.EvolutionHandler;
import fr.quentin.coevolutionMiner.v2.evolution.Evolutions;
import fr.quentin.coevolutionMiner.v2.evolution.EvolutionsImpl;
import fr.quentin.coevolutionMiner.v2.evolution.EvolutionsMiner;
import fr.quentin.coevolutionMiner.v2.evolution.miners.GumTreeSpoonMiner.EvolutionsAtCommit;
import fr.quentin.coevolutionMiner.v2.sources.Sources;
import fr.quentin.coevolutionMiner.v2.sources.Sources.Commit;
import fr.quentin.coevolutionMiner.v2.sources.SourcesHandler;
import fr.quentin.coevolutionMiner.v2.utils.Iterators2;
import fr.quentin.coevolutionMiner.v2.utils.Utils;
import gumtree.spoon.diff.Diff;

public class MultiGTSMiner implements EvolutionsMiner {
    public static Logger logger = LogManager.getLogger();

    private final ProjectHandler astHandler;
    private final SourcesHandler srcHandler;
    private final Evolutions.Specifier spec;

    // TODO instanciate filters correctly and make use of them
    private List<Object> filters;

    private final EvolutionHandler handler;

    public static Utils.Spanning spanning = Utils.Spanning.PER_COMMIT;

    public MultiGTSMiner(Evolutions.Specifier spec, SourcesHandler srcHandler, ProjectHandler astHandler,
            EvolutionHandler handler) {
        this.spec = spec;
        this.astHandler = astHandler;
        this.srcHandler = srcHandler;
        this.handler = handler;
    }

    public MultiGTSMiner(Evolutions.Specifier spec, SourcesHandler srcHandler, ProjectHandler astHandler,
            EvolutionHandler handler, List<Object> filters) {
        this(spec, srcHandler, astHandler, handler);
        this.filters = filters;
    }

    @Override
    public Evolutions compute() {
        Sources src = srcHandler.handle(spec.sources);

        switch (spanning) {
            case ONCE: {
                return handler.handle(handler.buildSpec(spec.sources, spec.commitIdBefore, spec.commitIdAfter,
                        GumTreeSpoonMiner.class));
            }
            case PER_COMMIT:
            default: {
                EvolutionsMany result = new EvolutionsMany(spec, src);
                return result.compute();
            }
        }
    }

    public final class EvolutionsMany extends EvolutionsImpl {

        private EvolutionsMany(Specifier spec, Sources sources) {
            super(spec, sources);
        }

        private EvolutionsMany(Specifier spec, Sources sources, Set<Evolution> subSet) {
            this(spec, sources);
            evolutions.addAll(subSet);
        }

        public Diff getDiff(Project.Specifier<?> before, Project.Specifier<?> after, String relPath) {
            GumTreeSpoonMiner.EvolutionsAtCommit tmp = getPerCommit(before.commitId, after.commitId);
            return tmp.getDiff(before, after);
        }

        public GumTreeSpoonMiner.EvolutionsAtCommit getPerCommit(String before, String after) {
            return (EvolutionsAtCommit) handler
                    .handle(handler.buildSpec(spec.sources, before, after, GumTreeSpoonMiner.class));
        }

        @Override
        public Project<?>.AST.FileSnapshot.Range map(Project<?>.AST.FileSnapshot.Range range, Project<?> target) {
            return getPerCommit(range.getFile().getCommit().getId(), target.spec.commitId).map(range, target);
        }

        Evolutions compute() {
            List<Commit> commits = Utils.getCommitList(this.sources, spec.commitIdBefore, spec.commitIdAfter);
            Commit beforeCom = null;
            if (commits.size() <= 2) {
                handler.handle(handler.buildSpec(spec.sources, spec.commitIdBefore, spec.commitIdAfter,
                        GumTreeSpoonMiner.class));
            }
            for (Commit commit : commits) {
                if (beforeCom != null) {
                    // per commit evolutions
                    GumTreeSpoonMiner.EvolutionsAtCommit perCommit = (GumTreeSpoonMiner.EvolutionsAtCommit) handler
                            .handle(handler.buildSpec(spec.sources, beforeCom.getId(), commit.getId(),
                                    GumTreeSpoonMiner.class));
                    // putPerCommit(beforeCom, commit, perCommit); // TODO necessary for spreaded coevolution detection
                }
                beforeCom = commit;
            }
            return this;
        }

        @Override
        public Set<Evolution> getEvolution(String type, Project<?> source, List<ImmutablePair<Range, String>> before,
                Project<?> target, List<ImmutablePair<Range, String>> after) {
            // Project source = before.get(0).left.getFile().getAST().getProject();
            return getPerCommit(source.spec.commitId, target.spec.commitId).getEvolution(type, source, before, target,
                    after);
        }

        private boolean evoSetWasBuilt = false;

        @Override
        public Set<Evolution> toSet() {
            if (evoSetWasBuilt) {
                return evolutions;
            }
            for (Evolution e : this) {
                evolutions.add(e);
            }
            evoSetWasBuilt = true;
            return evolutions;
        }

        @Override
        public Iterator<Evolution> iterator() {
            if (evoSetWasBuilt) {
                return evolutions.iterator();
            }
            return new Iterators2.IteratorPairCustom<Commit, Evolutions.Evolution>(
                    Utils.getCommitList(sources, spec.commitIdBefore, spec.commitIdAfter).iterator()) {

                @Override
                public Iterator<Evolution> makeIt(Commit prev, Commit next) {
                    return getPerCommit(prev.getId(), next.getId()).iterator();
                }

            };
        }

        @Override
        public JsonElement toJson() {
            // TODO optimize (serializing then deserializing is a dirty hack)
            Object diff = new Gson().fromJson(
                    JSON(spec.sources.repository, spec.commitIdAfter,
                            evolutions.stream().map(x -> (Action) x.getOriginal()).collect(Collectors.toList())),
                    new TypeToken<Object>() {
                    }.getType());
            return new Gson().toJsonTree(diff);
        }

        @Override
        public Map<Commit, Evolutions> perBeforeCommit() {
            Map<String, Set<Evolution>> tmp = new LinkedHashMap<>();
            for (Evolution evolution : this) {
                String cidb = evolution.getCommitBefore().getId();
                tmp.putIfAbsent(cidb, new LinkedHashSet<>());
                tmp.get(cidb).add(evolution);
            }
            Map<Commit, Evolutions> r = new LinkedHashMap<>();
            for (Set<Evolution> evolutionsSubSet : tmp.values()) {
                if (evolutionsSubSet.size() == 0) {
                    continue;
                }
                Evolutions newEvo = new EvolutionsMany(
                        new Evolutions.Specifier(spec.sources,
                                evolutionsSubSet.iterator().next().getCommitBefore().getId(),
                                evolutionsSubSet.iterator().next().getCommitAfter().getId(), spec.miner),
                        getSources(), evolutionsSubSet);
                r.put(evolutionsSubSet.iterator().next().getCommitBefore(), newEvo);
            }
            return r;
        }
    }

    public static String JSON(String gitURL, String currentCommitId, List<?> refactoringsAtRevision) {
        StringBuilder sb = new StringBuilder();
        // TODO
        return sb.toString();
    }
}