package fr.quentin.coevolutionMiner.v2.evolution.miners;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.eclipse.jgit.revwalk.RevCommit;

import fr.quentin.impactMiner.Evolution;
import fr.quentin.impactMiner.Position;
import gumtree.spoon.AstComparator;
import gumtree.spoon.diff.Diff;
import gumtree.spoon.diff.operations.Operation;
import gumtree.spoon.diff.support.SpoonSupport;
import spoon.reflect.cu.SourcePosition;
import spoon.reflect.declaration.CtElement;
import fr.quentin.coevolutionMiner.utils.SourcesHelper;
import fr.quentin.coevolutionMiner.v2.ast.Project;
import fr.quentin.coevolutionMiner.v2.ast.ProjectHandler;
import fr.quentin.coevolutionMiner.v2.ast.Project.AST.FileSnapshot.Range;
import fr.quentin.coevolutionMiner.v2.ast.miners.SpoonMiner;
import fr.quentin.coevolutionMiner.v2.ast.miners.SpoonMiner.ProjectSpoon;
import fr.quentin.coevolutionMiner.v2.evolution.EvolutionHandler;
import fr.quentin.coevolutionMiner.v2.evolution.Evolutions;
import fr.quentin.coevolutionMiner.v2.evolution.EvolutionsMiner;
import fr.quentin.coevolutionMiner.v2.evolution.Evolutions.Specifier;
import fr.quentin.coevolutionMiner.v2.sources.Sources;
import fr.quentin.coevolutionMiner.v2.sources.SourcesHandler;
import fr.quentin.coevolutionMiner.v2.sources.Sources.Commit;

public class GumTreeSpoonMiner implements EvolutionsMiner {
    Logger LOGGER = Logger.getLogger("ImpactRM commitHandler");

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

        EvolutionsExtension result = new EvolutionsExtension(spec, src);
        Map<ImmutablePair<Project<?>, Project<?>>, List<Operation<?>>> mapOpByCommit = new HashMap<>();
        try (SourcesHelper helper = src.open()) {
            Set<Commit> commits = src.getCommitsBetween(spec.commitIdBefore, spec.commitIdAfter);
            Commit beforeCom = null;
            for (Commit commit : commits) {
                if (beforeCom != null) {
                    Project<?> beforeAST = astHandler.handle(astHandler.buildSpec(spec.sources, beforeCom.getId()),
                            SpoonMiner.class);

                    Project<?> afterAST = astHandler.handle(astHandler.buildSpec(spec.sources, commit.getId()),
                            SpoonMiner.class);
                    Diff diff = comp.compare(((ProjectSpoon) beforeAST).getAst().launcher.getModel().getRootPackage(),
                            ((ProjectSpoon) afterAST).getAst().launcher.getModel().getRootPackage());

                    for (Operation<?> op : diff.getAllOperations()) {
                        ImmutablePair<Project<?>, Project<?>> tmp1 = new ImmutablePair<Project<?>, Project<?>>(
                                beforeAST, afterAST);
                        mapOpByCommit.putIfAbsent(tmp1, new ArrayList<>());
                        mapOpByCommit.get(tmp1).add(op);
                        LOGGER.info("O- " + op + "\n");
                    }

                    // MAPPING !!!
                    CtElement aaa = new SpoonSupport().getMappedElement(diff,
                            ((ProjectSpoon) beforeAST).getAst().launcher.getModel().getRootPackage(), true);
                }
                beforeCom = commit;
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        for (Entry<ImmutablePair<Project<?>, Project<?>>, List<Operation<?>>> entry : mapOpByCommit.entrySet()) {
            ImmutablePair<Project<?>, Project<?>> pair = entry.getKey();
            for (Operation<?> op : entry.getValue()) {
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

        void addEvolution(Operation<?> op, Project<?> astBefore, Project<?> astAfter) {
            List<ImmutablePair<Range, String>> before = new ArrayList<>();
            before.add(toRange(astBefore, op.getSrcNode(), "src"));
            List<ImmutablePair<Range, String>> after = new ArrayList<>();
            after.add(toRange(astAfter, op.getDstNode(), "dst"));
            super.addEvolution(op.getAction().getName(), before, after, astBefore.commit, astAfter.commit, (Object) op);
        }

        private <T> ImmutablePair<Range, String> toRange(Project<T> ast, CtElement range, String desc) {
            SourcePosition position = range.getPosition();
            return new ImmutablePair<>(
                    ast.getRange(position.getFile().getPath(), position.getSourceStart(), position.getSourceEnd()),
                    desc);
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

    // public final class EvolutionsExtensionOld extends Evolutions {
    // private final List<Evolution<Refactoring>> evolutions;

    // private EvolutionsExtensionOld(Specifier spec, List<Evolution<Refactoring>>
    // evolutions) {
    // super(spec);
    // this.evolutions = evolutions;
    // }

    // @Override
    // public List<Evolution<Refactoring>> toList() {
    // return evolutions;
    // }

    // @Override
    // public JsonElement toJson() {
    // // TODO optimize (serializing then deserializing is a dirty hack)
    // Object diff = new Gson().fromJson(
    // JSON(spec.sources.repository, spec.commitIdAfter,
    // evolutions.stream().map(x -> x.getOriginal()).collect(Collectors.toList())),
    // new TypeToken<Object>() {
    // }.getType());
    // return new Gson().toJsonTree(diff);
    // }

    // @Override
    // public List<Evolutions> perBeforeCommit() {
    // Map<String, List<Evolution<Refactoring>>> tmp = new HashMap<>();
    // for (Evolution<Refactoring> evolution : toList()) {
    // tmp.putIfAbsent(evolution.getCommitIdBefore(), new ArrayList<>());
    // tmp.get(evolution.getCommitIdBefore()).add(evolution);
    // }
    // List<Evolutions> r = new ArrayList<>();
    // for (List<Evolution<Refactoring>> evolutionsSubSet : tmp.values()) {
    // if (evolutionsSubSet.size() == 0) {
    // continue;
    // }
    // Evolutions newEvo = new
    // EvolutionsExtensionOld(EvolutionHandler.buildSpec(spec.sources,
    // evolutionsSubSet.get(0).getCommitIdBefore(),
    // evolutionsSubSet.get(0).getCommitIdAfter()),
    // evolutionsSubSet);
    // r.add(newEvo);
    // }
    // return r;
    // }
    // }

    // public static class OtherEvolution implements Evolution<Refactoring> {
    // Set<Position> pre = new HashSet<>();
    // Set<Position> post = new HashSet<>();
    // Map<String, Set<Position>> preDesc = new HashMap<>();
    // Map<String, Set<Position>> postDesc = new HashMap<>();
    // private Refactoring op;
    // private String commitIdBefore;
    // private String commitIdAfter;

    // OtherEvolution(Refactoring op, String commitIdBefore, String commitIdAfter) {
    // this.op = op;
    // this.commitIdBefore = commitIdBefore;
    // this.commitIdAfter = commitIdAfter;
    // for (CodeRange range : op.leftSide()) {
    // Position pos = new Position(range.getFilePath(), range.getStartOffset(),
    // range.getEndOffset());
    // this.preDesc.putIfAbsent(range.getDescription(), new HashSet<>());
    // this.preDesc.get(range.getDescription()).add(pos);
    // this.pre.add(pos);
    // }
    // for (CodeRange range : op.rightSide()) {
    // Position pos = new Position(range.getFilePath(), range.getStartOffset(),
    // range.getEndOffset());
    // this.postDesc.putIfAbsent(range.getDescription(), new HashSet<>());
    // this.postDesc.get(range.getDescription()).add(pos);
    // this.post.add(pos);
    // }
    // }

    // public Map<String, Set<Position>> getPreEvolutionPositionsDesc() {
    // return preDesc;
    // }

    // public Map<String, Set<Position>> getPostEvolutionPositionsDesc() {
    // return postDesc;
    // }

    // @Override
    // public Set<Position> getPreEvolutionPositions() {
    // return pre;
    // }

    // @Override
    // public Set<Position> getPostEvolutionPositions() {
    // return post;
    // }

    // @Override
    // public Refactoring getOriginal() {
    // return op;
    // }

    // @Override
    // public String getCommitIdBefore() {
    // return commitIdBefore;
    // }

    // @Override
    // public String getCommitIdAfter() {
    // return commitIdAfter;
    // }

    // @Override
    // public JsonObject toJson() {
    // JsonObject r = new JsonObject();
    // r.addProperty("type", op.getName());
    // r.addProperty("commitIdBefore", getCommitIdBefore());
    // r.addProperty("commitIdAfter", getCommitIdAfter());
    // JsonArray before = new JsonArray();
    // for (CodeRange p : op.leftSide()) {
    // JsonObject o = new JsonObject();
    // before.add(o);
    // o.addProperty("file", p.getFilePath());
    // o.addProperty("start", p.getStartOffset());
    // o.addProperty("end", p.getEndOffset());
    // o.addProperty("type", p.getCodeElementType().getName());
    // o.addProperty("desc", p.getDescription());
    // }
    // r.add("before", before);
    // JsonArray after = new JsonArray();
    // for (CodeRange p : op.rightSide()) {
    // JsonObject o = new JsonObject();
    // after.add(o);
    // o.addProperty("file", p.getFilePath());
    // o.addProperty("start", p.getStartOffset());
    // o.addProperty("end", p.getEndOffset());
    // o.addProperty("type", p.getCodeElementType().getName());
    // o.addProperty("desc", p.getDescription());
    // }
    // r.add("after", after);
    // return r;
    // }

    // }

    // public Evolutions computeOld() {
    // Sources src = srcHandler.handle(spec.sources, "jgit");
    // GitHistoryRefactoringMiner miner = new GitHistoryRefactoringMinerImpl();

    // List<Refactoring> detectedRefactorings = new ArrayList<Refactoring>();
    // List<Evolution<Refactoring>> evolutions = new
    // ArrayList<Evolution<Refactoring>>();

    // try (SourcesHelper helper = src.open()) {
    // miner.detectBetweenCommits(helper.getRepo(), spec.commitIdBefore,
    // spec.commitIdAfter,
    // new RefactoringHandler() {
    // @Override
    // public void handle(String commitId, List<Refactoring> refactorings) {
    // detectedRefactorings.addAll(refactorings);
    // for (Refactoring op : refactorings) {
    // String before;
    // try {
    // before = helper.getBeforeCommit(commitId);
    // } catch (IOException e) {
    // throw new RuntimeException(e);
    // }
    // OtherEvolution tmp = new OtherEvolution(op, before, commitId);
    // evolutions.add(tmp);
    // logger.info("O- " + tmp.op + "\n" + tmp.pre.size());
    // }
    // }

    // });
    // } catch (Exception e) {
    // throw new RuntimeException(e);
    // }

    // return null;
    // // return new EvolutionsExtension(spec, evolutions);
    // }
}