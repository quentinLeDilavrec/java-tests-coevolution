package fr.quentin.coevolutionMiner.v2.evolution.miners;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.MutablePair;

import fr.quentin.coevolutionMiner.utils.SourcesHelper;
import fr.quentin.coevolutionMiner.v2.ast.Project;
import fr.quentin.coevolutionMiner.v2.ast.Project.AST.FileSnapshot.Range;
import fr.quentin.coevolutionMiner.v2.ast.ProjectHandler;
import fr.quentin.coevolutionMiner.v2.ast.miners.SpoonMiner;
import fr.quentin.coevolutionMiner.v2.ast.miners.SpoonMiner.ProjectSpoon;
import fr.quentin.coevolutionMiner.v2.evolution.EvolutionHandler;
import fr.quentin.coevolutionMiner.v2.evolution.Evolutions;
import fr.quentin.coevolutionMiner.v2.evolution.EvolutionsMiner;
import fr.quentin.coevolutionMiner.v2.evolution.Evolutions.Evolution.DescRange;
import fr.quentin.coevolutionMiner.v2.sources.Sources;
import fr.quentin.coevolutionMiner.v2.sources.Sources.Commit;
import fr.quentin.coevolutionMiner.v2.sources.SourcesHandler;
import gumtree.spoon.AstComparator;
import gumtree.spoon.diff.Diff;
import gumtree.spoon.diff.operations.Operation;
import gumtree.spoon.diff.support.SpoonSupport;
import spoon.reflect.cu.SourcePosition;
import spoon.reflect.declaration.CtElement;

public class GumTreeSpoonMiner implements EvolutionsMiner {
    Logger LOGGER = Logger.getLogger("ImpactGT commitHandler");

    private ProjectHandler astHandler;
    private SourcesHandler srcHandler;
    private Evolutions.Specifier spec;

    // TODO instanciate filters correctly and make use of them
    private List<Object> filters;

    public GumTreeSpoonMiner(Evolutions.Specifier spec, SourcesHandler srcHandler, ProjectHandler astHandler) {
        this.spec = spec;
        this.astHandler = astHandler;
        this.srcHandler = srcHandler;
    }

    public GumTreeSpoonMiner(Evolutions.Specifier spec, SourcesHandler srcHandler, ProjectHandler astHandler,
            List<Object> filters) {
        this(spec, srcHandler, astHandler);
        this.filters = filters;
    }

    @Override
    public Evolutions compute() {
        Sources src = srcHandler.handle(spec.sources, "jgit");
        try {
            Set<Sources.Commit> commits = src.getCommitsBetween(spec.commitIdBefore, spec.commitIdAfter);
        } catch (Exception e1) {
            e1.printStackTrace();
        }
        AstComparator comp = new AstComparator();

        // List<Refactoring> detectedRefactorings = new ArrayList<Refactoring>();
        // List<Evolutions.Evolution> evolutions = new
        // ArrayList<Evolutions.Evolution>();

        EvolutionsExtension result;
        Map<ImmutablePair<Project<?>, Project<?>>, ImmutableTriple<Diff, String, List<Operation<?>>>> mapOpByCommit = new HashMap<>();
        try (SourcesHelper helper = src.open()) {
            result = new EvolutionsExtension(spec, src);
            Set<Commit> commits = src.getCommitsBetween(spec.commitIdBefore, spec.commitIdAfter);
            Commit beforeCom = null;
            for (Commit commit : commits) {
                if (beforeCom != null) {
                    Project<?> beforeProj = astHandler.handle(astHandler.buildSpec(spec.sources, beforeCom.getId()));
                    Project<?> afterProj = astHandler.handle(astHandler.buildSpec(spec.sources, commit.getId()));
                    // TODO handle changes at the module level (rename, move, ...)
                    computeProj(comp, mapOpByCommit, beforeProj, afterProj);
                }
                beforeCom = commit;
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        for (Entry<ImmutablePair<Project<?>, Project<?>>, ImmutableTriple<Diff, String, List<Operation<?>>>> entry : mapOpByCommit
                .entrySet()) {
            ImmutablePair<Project<?>, Project<?>> pair = entry.getKey();
            result.addDiff(entry.getValue().left, entry.getValue().middle, pair.left, pair.right);
            for (Operation<?> op : entry.getValue().right) {
                result.addEvolution(op, entry.getValue().middle, pair.left, pair.right);
            }
        }
        return result;
    }

    private void computeProj(AstComparator comp,
            Map<ImmutablePair<Project<?>, Project<?>>, ImmutableTriple<Diff, String, List<Operation<?>>>> mapOpByCommit,
            Project<?> beforeProj, Project<?> afterProj) {
        String relPath = beforeProj.spec.relPath.toString();
        Map<String, MutablePair<Project<?>, Project<?>>> modulesPairs = new HashMap<>();
        for (Project<?> p : beforeProj.getModules()) {
            modulesPairs.putIfAbsent(p.spec.relPath.toString(), new MutablePair<>());
            modulesPairs.get(p.spec.relPath.toString()).setLeft(p);
        }
        for (Project<?> p : afterProj.getModules()) {
            modulesPairs.putIfAbsent(p.spec.relPath.toString(), new MutablePair<>());
            modulesPairs.get(p.spec.relPath.toString()).setRight(p);
        }

        if (!beforeProj.getAst().isUsable() || !afterProj.getAst().isUsable()) {
            // continue;
        }
        Diff diff = comp.compare(((ProjectSpoon) beforeProj).getAst().launcher.getModel().getRootPackage(),
                ((ProjectSpoon) afterProj).getAst().launcher.getModel().getRootPackage());
        for (Operation<?> op : diff.getRootOperations()) {
            ImmutablePair<Project<?>, Project<?>> tmp1 = new ImmutablePair<>(beforeProj, afterProj);
            mapOpByCommit.putIfAbsent(tmp1, new ImmutableTriple<>(diff, relPath, new ArrayList<>()));
            mapOpByCommit.get(tmp1).right.add(op);
            LOGGER.info("O- " + op + "\n");
        }
    }

    public final class EvolutionsExtension extends Evolutions {

        private EvolutionsExtension(Specifier spec, Sources sources) {
            super(spec, sources);
        }

        private EvolutionsExtension(Specifier spec, Sources sources, Set<Evolution> subSet) {
            this(spec, sources);
            evolutions.addAll(subSet);
        }

        Map<ImmutablePair<Commit, Commit>, Map<String, Diff>> diffs = new HashMap<>();

        void addDiff(Diff diff, String relPath, Project<?> astBefore, Project<?> astAfter) {
            ImmutablePair<Commit, Commit> pair = new ImmutablePair<>(astBefore.commit, astAfter.commit);
            diffs.putIfAbsent(pair, new HashMap());
            diffs.get(new ImmutablePair<>(astBefore.commit, astAfter.commit)).put(relPath, diff);
        }

        public Diff getDiff(Commit before, Commit after, String relPath) {
            Map<String, Diff> map = diffs.get(new ImmutablePair<>(before, after));
            return map == null ? null : map.get(relPath);
        }

        @Override
        public <T> T map(Commit before, Commit after, Project<T> proj, T element, boolean fromSource) {
            // MAPPING !!!
            if (element instanceof CtElement) {
                CtElement aaa = new SpoonSupport().getMappedElement(getDiff(before,after,proj.spec.relPath.toString()),
                        (CtElement) element, true);
                return (T) aaa;
            }
            return null;
        }

        void addEvolution(Operation<?> op, String relPath, Project<?> astBefore, Project<?> astAfter) {
            List<ImmutablePair<Range, String>> before = new ArrayList<>();
            before.add(toRange(astBefore, op.getSrcNode(), "src"));
            List<ImmutablePair<Range, String>> after = new ArrayList<>();
            after.add(toRange(astAfter, op.getDstNode(), "dst"));
            Evolution evo = super.addEvolution(op.getAction().getName(), before, after, astBefore.commit,
                    astAfter.commit, (Object) op);
            for (DescRange dr : evo.getBefore()) {
                augment(dr);
            }
            for (DescRange dr : evo.getAfter()) {
                augment(dr);
            }
        }

        private void augment(DescRange dr) {
            CtElement ori = (CtElement) dr.getTarget().getOriginal();
            assert ori != null;
            HashSet<DescRange> md = (HashSet<DescRange>) ori.getMetadata(METADATA_KEY_EVO);
            if (md == null) {
                md = new HashSet<>();
                ori.putMetadata(METADATA_KEY_EVO, md);
            }
            md.add(dr);
        }

        private <T> ImmutablePair<Range, String> toRange(Project<T> ast, CtElement element, String desc) {
            SourcePosition position = element.getPosition();
            Project<T>.AST.FileSnapshot.Range range = ast.getRange(position.getFile().getPath(),
                    position.getSourceStart(), position.getSourceEnd(), element);
            return new ImmutablePair<>(range, desc);
        }

        @Override
        public Set<Evolution> toSet() {
            return evolutions;
        }

        @Override
        public JsonElement toJson() {
            // TODO optimize (serializing then deserializing is a dirty hack)
            Object diff = new Gson().fromJson(
                    JSON(spec.sources.repository, spec.commitIdAfter,
                            evolutions.stream().map(x -> (Operation<?>) x.getOriginal()).collect(Collectors.toList())),
                    new TypeToken<Object>() {
                    }.getType());
            return new Gson().toJsonTree(diff);
        }

        @Override
        public List<Evolutions> perBeforeCommit() {
            Map<String, Set<Evolution>> tmp = new HashMap<>();
            for (Evolution evolution : toSet()) {
                String cidb = evolution.getCommitBefore().getId();
                tmp.putIfAbsent(cidb, new HashSet<>());
                tmp.get(cidb).add(evolution);
            }
            List<Evolutions> r = new ArrayList<>();
            for (Set<Evolution> evolutionsSubSet : tmp.values()) {
                if (evolutionsSubSet.size() == 0) {
                    continue;
                }
                Evolutions newEvo = new EvolutionsExtension(
                        EvolutionHandler.buildSpec(spec.sources,
                                evolutionsSubSet.iterator().next().getCommitBefore().getId(),
                                evolutionsSubSet.iterator().next().getCommitAfter().getId()),
                        getSources(), evolutionsSubSet);
                r.add(newEvo);
            }
            return r;
        }
    }

    public static String JSON(String gitURL, String currentCommitId, List<Operation<?>> refactoringsAtRevision) {
        StringBuilder sb = new StringBuilder();
        // TODO
        return sb.toString();
    }
}