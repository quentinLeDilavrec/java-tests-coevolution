package fr.quentin.coevolutionMiner.v2.evolution.miners;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.hamcrest.Matcher;
import org.refactoringminer.api.GitHistoryRefactoringMiner;
import org.refactoringminer.api.Refactoring;
import org.refactoringminer.api.RefactoringHandler;
import org.refactoringminer.rm1.GitHistoryRefactoringMinerImpl;

import fr.quentin.coevolutionMiner.utils.SourcesHelper;
import fr.quentin.coevolutionMiner.v2.ast.Project;
import fr.quentin.coevolutionMiner.v2.ast.Project.AST.FileSnapshot.Range;
import fr.quentin.coevolutionMiner.v2.ast.ProjectHandler;
import fr.quentin.coevolutionMiner.v2.ast.miners.SpoonMiner;
import fr.quentin.coevolutionMiner.v2.evolution.EvolutionHandler;
import fr.quentin.coevolutionMiner.v2.evolution.Evolutions;
import fr.quentin.coevolutionMiner.v2.evolution.EvolutionsImpl;
import fr.quentin.coevolutionMiner.v2.evolution.EvolutionsMiner;
import fr.quentin.coevolutionMiner.v2.evolution.Evolutions.Evolution.DescRange;
import fr.quentin.coevolutionMiner.v2.sources.Sources;
import fr.quentin.coevolutionMiner.v2.sources.SourcesHandler;
import fr.quentin.coevolutionMiner.v2.sources.Sources.Commit;
import fr.quentin.coevolutionMiner.v2.utils.Utils;
import fr.quentin.coevolutionMiner.v2.utils.Utils.Spanning;
import gr.uom.java.xmi.diff.CodeRange;
import spoon.reflect.declaration.CtElement;

public class RefactoringMiner implements EvolutionsMiner {

    public static Spanning spanning = Spanning.PER_COMMIT;

    Logger logger = LogManager.getLogger();
    private static final String systemFileSeparator = java.util.regex.Matcher.quoteReplacement(File.separator);

    private ProjectHandler astHandler;
    private SourcesHandler srcHandler;
    private Evolutions.Specifier spec;

    // TODO instanciate filters correctly and make use of them
    private List<Object> filters;

    public RefactoringMiner(Evolutions.Specifier spec, SourcesHandler srcHandler, ProjectHandler astHandler) {
        this.spec = spec;
        this.astHandler = astHandler;
        this.srcHandler = srcHandler;
    }

    public RefactoringMiner(Evolutions.Specifier spec, SourcesHandler srcHandler, ProjectHandler astHandler,
            List<Object> filters) {
        this(spec, srcHandler, astHandler);
        this.filters = filters;
    }

    @Override
    public Evolutions compute() {
        Sources src = srcHandler.handle(spec.sources);
        try {
            // TODO remove that, after fix the need to init things it inits
            List<Sources.Commit> commits = src.getCommitsBetween(spec.commitIdBefore, spec.commitIdAfter);
        } catch (Exception e1) {
            e1.printStackTrace();
        }
        GitHistoryRefactoringMinerImpl miner = new GitHistoryRefactoringMinerImpl();

        // List<Refactoring> detectedRefactorings = new ArrayList<Refactoring>();
        // List<Evolutions.Evolution> evolutions = new
        // ArrayList<Evolutions.Evolution>();

        EvolutionsExtension result = new EvolutionsExtension(spec, src);
        Map<ImmutablePair<String, String>, List<Refactoring>> mapOpByCommit = new HashMap<>();
        List<Refactoring> opOnce = new ArrayList<>();
        try (SourcesHelper helper = src.open()) {
            RefactoringHandler rh = new RefactoringHandler() {
                @Override
                public void handle(String commitId, List<Refactoring> refactorings) {
                    for (Refactoring op : refactorings) {
                        String before;
                        try {
                            before = helper.getBeforeCommit(commitId);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                        ImmutablePair<String, String> tmp1 = new ImmutablePair<String, String>(before, commitId);
                        mapOpByCommit.putIfAbsent(tmp1, new ArrayList<>());
                        mapOpByCommit.get(tmp1).add(op);
                        opOnce.add(op);
                        logger.info("O- " + op + "\n");
                    }
                }

            };
            switch (spanning) {
                case ONCE:
                    // TODO would need to mat expli evolution spanning over multiple commits for this to be clean 
                    miner.detectBetweenCommitsOnce(helper.getRepo(), spec.commitIdBefore, spec.commitIdAfter, rh);
                    break;
                case PER_COMMIT:
                default:
                    miner.detectBetweenCommits(helper.getRepo(), spec.commitIdBefore, spec.commitIdAfter, rh);
                    break;
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        switch (spanning) {
            case ONCE:
                // TODO would need to mat expli evolution spanning over multiple commits for this to be clean 
                oncePostProcess(result, opOnce);
                break;
            case PER_COMMIT:
            default:
                perCommitPostProcess(result, mapOpByCommit);
                break;
        }
        return result;
    }

    private void oncePostProcess(EvolutionsExtension result, List<Refactoring> opRelaxed) {
        Project beforeAST = astHandler.handle(astHandler.buildSpec(spec.sources, spec.commitIdBefore));
        assert beforeAST != null;
        Project afterAST = astHandler.handle(astHandler.buildSpec(spec.sources, spec.commitIdAfter));
        assert afterAST != null;
        for (Refactoring op : opRelaxed) {
            result.addEvolution(op, beforeAST, afterAST);
        }
    }

    private void perCommitPostProcess(EvolutionsExtension result,
            Map<ImmutablePair<String, String>, List<Refactoring>> mapOpByCommit) {
        for (Entry<ImmutablePair<String, String>, List<Refactoring>> entry : mapOpByCommit.entrySet()) {
            Project beforeAST = astHandler.handle(astHandler.buildSpec(spec.sources, entry.getKey().left));
            assert beforeAST != null;
            Project afterAST = astHandler.handle(astHandler.buildSpec(spec.sources, entry.getKey().right));
            assert afterAST != null;
            for (Refactoring op : entry.getValue()) {
                result.addEvolution(op, beforeAST, afterAST);
            }
        }
    }

    public final class EvolutionsExtension extends EvolutionsImpl {

        private EvolutionsExtension(Specifier spec, Sources sources) {
            super(spec, sources);
        }

        private EvolutionsExtension(Specifier spec, Sources sources, Set<Evolution> subSet) {
            this(spec, sources);
            evolutions.addAll(subSet);
        }

        void addEvolution(Refactoring refact, Project<?> astBefore, Project<?> astAfter) {
            List<ImmutablePair<Range, String>> before = aux(refact.leftSide(), astBefore);
            List<ImmutablePair<Range, String>> after = aux(refact.rightSide(), astAfter);
            if (before.size()==0 && after.size()==0) {
                logger.error("an evolution should point on at least one range");
                return;
            }
            addEvolution(refact.getName(), before, after, astBefore.commit, astAfter.commit, refact);
        }

        private List<ImmutablePair<Range, String>> aux(List<CodeRange> list, Project<?> ast) {
            List<ImmutablePair<Range, String>> result = new ArrayList<>();
            for (CodeRange range : list) {
                ImmutablePair<Range, String> rg = toRange(ast, range);
                if (rg != null) {
                    result.add(rg);
                }
            }
            return result;
        }

        private ImmutablePair<Range, String> toRange(Project proj, CodeRange range) {
            Range tmp = proj.getRange(range.getFilePath().replaceAll("/", systemFileSeparator), range.getStartOffset(),
                    range.getEndOffset() - 1);
            if (tmp == null) {
                return null;
            }
            return new ImmutablePair<>(tmp, range.getDescription());
        }

        @Override
        public JsonElement toJson() {
            // TODO optimize (serializing then deserializing is a dirty hack)
            Object diff = new Gson().fromJson(
                    JSON(spec.sources.repository, spec.commitIdAfter,
                            toSet().stream().map(x -> (Refactoring) x.getOriginal()).collect(Collectors.toList())),
                    new TypeToken<Object>() {
                    }.getType());
            return new Gson().toJsonTree(diff);
        }

        @Override
        public Map<Commit, Evolutions> perBeforeCommit() {
            Map<String, Set<Evolution>> tmp = new LinkedHashMap<>();
            for (Evolution evolution : toSet()) {
                String cidb = evolution.getCommitBefore().getId();
                tmp.putIfAbsent(cidb, new LinkedHashSet<>());
                tmp.get(cidb).add(evolution);
            }
            Map<Commit, Evolutions> r = new LinkedHashMap<>();
            for (Set<Evolution> evolutionsSubSet : tmp.values()) {
                if (evolutionsSubSet.size() == 0) {
                    continue;
                }
                Evolutions newEvo = new EvolutionsExtension(
                        new Evolutions.Specifier(spec.sources,
                                evolutionsSubSet.iterator().next().getCommitBefore().getId(),
                                evolutionsSubSet.iterator().next().getCommitAfter().getId(), spec.miner),
                        getSources(), evolutionsSubSet);
                r.put(evolutionsSubSet.iterator().next().getCommitBefore(), newEvo);
            }
            return r;
        }
    }

    public static String JSON(String gitURL, String currentCommitId, List<Refactoring> refactoringsAtRevision) {
        StringBuilder sb = new StringBuilder();
        sb.append("{").append("\n");
        sb.append("\"").append("commits").append("\"").append(": ");
        sb.append("[");
        sb.append("{");
        sb.append("\t").append("\"").append("repository").append("\"").append(": ").append("\"").append(gitURL)
                .append("\"").append(",").append("\n");
        sb.append("\t").append("\"").append("sha1").append("\"").append(": ").append("\"").append(currentCommitId)
                .append("\"").append(",").append("\n");
        String url;
        if (gitURL.indexOf(".git") > -1) {
            url = gitURL.substring(0, gitURL.length() - ".git".length()) + "/commit/" + currentCommitId;
        } else {
            url = gitURL + "/commit/" + currentCommitId;
        }
        sb.append("\t").append("\"").append("url").append("\"").append(": ").append("\"").append(url).append("\"")
                .append(",").append("\n");
        sb.append("\t").append("\"").append("refactorings").append("\"").append(": ");
        sb.append("[");
        int counter = 0;
        for (Refactoring refactoring : refactoringsAtRevision) {
            sb.append(refactoring.toJSON());
            if (counter < refactoringsAtRevision.size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
            counter++;
        }
        sb.append("]");
        sb.append("}");
        sb.append("]").append("\n");
        sb.append("}");
        return sb.toString();
    }

    private static Map<String, EvoType> RefactoringTypes = null;

    public static Map<String, EvoType> getCRefactoringTypes() {
        if (RefactoringTypes != null) {
            return RefactoringTypes;
        }
        Map<String, EvoType> res = new Gson().fromJson(Utils.memoizedReadResource("RefactoringTypes_named.json"),
                new TypeToken<Map<String, EvoType>>() {
                }.getType());
        Map<String, EvoType> resByDN = new HashMap<>();
        for (Entry<String, EvoType> e : res.entrySet()) {
            e.getValue().name = e.getKey();
            resByDN.put(e.getValue().displayName, e.getValue());
        }
        return resByDN;
    }

    public static class Side {
        public Boolean many;
        public String description;
    }

    public static class EvoType {
        public String name;
        public String displayName;
        public List<Side> left;
        public List<Side> right;
    }
}