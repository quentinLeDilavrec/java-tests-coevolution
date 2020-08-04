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
        Map<ImmutablePair<Project<?>, Project<?>>, ImmutablePair<Diff, List<Operation<?>>>> mapOpByCommit = new HashMap<>();
        try (SourcesHelper helper = src.open()) {
            result = new EvolutionsExtension(spec, src);
            Set<Commit> commits = src.getCommitsBetween(spec.commitIdBefore, spec.commitIdAfter);
            Commit beforeCom = null;
            for (Commit commit : commits) {
                if (beforeCom != null) {
                    Project<?> beforeAST = astHandler.handle(astHandler.buildSpec(spec.sources, beforeCom.getId()));
                    Project<?> afterAST = astHandler.handle(astHandler.buildSpec(spec.sources, commit.getId()));
                    if (!beforeAST.getAst().isUsable() || !afterAST.getAst().isUsable()) {
                        continue;
                    }
                    Diff diff = comp.compare(((ProjectSpoon) beforeAST).getAst().launcher.getModel().getRootPackage(),
                            ((ProjectSpoon) afterAST).getAst().launcher.getModel().getRootPackage());
                    for (Operation<?> op : diff.getRootOperations()) {
                        ImmutablePair<Project<?>, Project<?>> tmp1 = new ImmutablePair<>(beforeAST, afterAST);
                        mapOpByCommit.putIfAbsent(tmp1, new ImmutablePair<>(diff, new ArrayList<>()));
                        mapOpByCommit.get(tmp1).right.add(op);
                        LOGGER.info("O- " + op + "\n");
                    }
                }
                beforeCom = commit;
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        for (Entry<ImmutablePair<Project<?>, Project<?>>, ImmutablePair<Diff, List<Operation<?>>>> entry : mapOpByCommit
                .entrySet()) {
            ImmutablePair<Project<?>, Project<?>> pair = entry.getKey();
            result.addDiff(entry.getValue().left, pair.left, pair.right);
            for (Operation<?> op : entry.getValue().right) {
                result.addEvolution(op, pair.left, pair.right);
            }
        }
        return result;
    }

    public final class EvolutionsExtension extends Evolutions {

        private EvolutionsExtension(Specifier spec, Sources sources) {
            super(spec, sources);
        }

        private EvolutionsExtension(Specifier spec, Sources sources, Set<Evolution> subSet) {
            this(spec, sources);
            evolutions.addAll(subSet);
        }

        Map<ImmutablePair<Commit, Commit>, Diff> diffs = new HashMap<>();

        void addDiff(Diff diff, Project<?> astBefore, Project<?> astAfter) {
            diffs.put(new ImmutablePair<>(astBefore.commit, astAfter.commit), diff);
        }

        public Diff getDiff(Commit before, Commit after) {
            return diffs.get(new ImmutablePair<>(before, after));
        }

        @Override
        public <T> T map(Commit before, Commit after, T element, boolean fromSource) {
            // MAPPING !!!
            if (element instanceof CtElement) {
                CtElement aaa = new SpoonSupport().getMappedElement(diffs.get(new ImmutablePair<>(before, after)),
                        (CtElement) element, true);
                return (T) aaa;
            }
            return null;
        }

        void addEvolution(Operation<?> op, Project<?> astBefore, Project<?> astAfter) {
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