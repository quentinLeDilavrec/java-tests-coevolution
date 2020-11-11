package fr.quentin.coevolutionMiner.v2.evolution.miners;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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
import gr.uom.java.xmi.diff.CodeRange;
import spoon.reflect.declaration.CtElement;

public class RefactoringMiner implements EvolutionsMiner {
    Logger LOGGER = Logger.getLogger("ImpactRM commitHandler");
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
        Sources src = srcHandler.handle(spec.sources, "jgit");
        try {
            // TODO remove that, after fix the need to init things it inits
            List<Sources.Commit> commits = src.getCommitsBetween(spec.commitIdBefore, spec.commitIdAfter);
        } catch (Exception e1) {
            e1.printStackTrace();
        }
        GitHistoryRefactoringMiner miner = new GitHistoryRefactoringMinerImpl();

        // List<Refactoring> detectedRefactorings = new ArrayList<Refactoring>();
        // List<Evolutions.Evolution> evolutions = new
        // ArrayList<Evolutions.Evolution>();

        EvolutionsExtension result = new EvolutionsExtension(spec, src);
        Map<ImmutablePair<String, String>, List<Refactoring>> mapOpByCommit = new HashMap<>();
        try (SourcesHelper helper = src.open()) {
            miner.detectBetweenCommits(helper.getRepo(), spec.commitIdBefore, spec.commitIdAfter,
                    new RefactoringHandler() {
                        @Override
                        public void handle(String commitId, List<Refactoring> refactorings) {
                            for (Refactoring op : refactorings) {
                                String before;
                                try {
                                    before = helper.getBeforeCommit(commitId);
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                                ImmutablePair<String, String> tmp1 = new ImmutablePair<String, String>(before,
                                        commitId);
                                mapOpByCommit.putIfAbsent(tmp1, new ArrayList<>());
                                mapOpByCommit.get(tmp1).add(op);
                                LOGGER.info("O- " + op + "\n");
                            }
                        }

                    });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        for (Entry<ImmutablePair<String, String>, List<Refactoring>> entry : mapOpByCommit.entrySet()) {
            Project beforeAST = astHandler.handle(astHandler.buildSpec(spec.sources, entry.getKey().left));
            assert beforeAST != null;
            Project afterAST = astHandler.handle(astHandler.buildSpec(spec.sources, entry.getKey().right));
            assert afterAST != null;
            for (Refactoring op : entry.getValue()) {
                result.addEvolution(op, beforeAST, afterAST);
            }
        }

        return result;
    }

    public final class EvolutionsExtension extends EvolutionsImpl {

        private EvolutionsExtension(Specifier spec, Sources sources) {
            super(spec, sources);
        }

        private EvolutionsExtension(Specifier spec, Sources sources, Set<Evolution> subSet) {
            this(spec, sources);
            evolutions.addAll(subSet);
        }

        class EvolutionExtension extends Evolutions.Evolution {

            EvolutionExtension(Object original, String type, Commit commitBefore, Commit commitAfter) {
                super(original, type, commitBefore, commitAfter);
            }
            @Override
            public Map<String, Object> asMap() {
                final Map<String, EvoType> evoTypesByName = getCRefactoringTypes();
                final Map<String, Object> res = new HashMap<>();
                final Map<String, Object> evofields = new HashMap<>();
                res.put("content", evofields);
                evofields.put("repository", EvolutionsExtension.this.spec.sources.repository);
                evofields.put("commitIdBefore", this.getCommitBefore().getId());
                evofields.put("commitIdAfter", this.getCommitAfter().getId());
                // Refactoring ori = (Refactoring) this.getOriginal();
                evofields.put("type", this.getType());

                final StringBuilder url = new StringBuilder();
                url.append("http://176.180.199.146:50000/?repo=" + EvolutionsExtension.this.spec.sources.repository);
                url.append("&before=" + this.getCommitBefore().getId());
                url.append("&after=" + this.getCommitAfter().getId());
                url.append("&type=" + this.getType());
                final EvoType aaa = evoTypesByName.get(this.getType());
                final Map<String, List<String>> before_e = new HashMap<>();
                final List<Object> leftSideLocations = new ArrayList<>();
                res.put("leftSideLocations", leftSideLocations);
                for (final DescRange e : this.getBefore()) {
                    final Map<String, Object> o = new HashMap<>();
                    leftSideLocations.add(o);
                    o.put("filePath", e.getTarget().getFile().getPath());
                    o.put("start", e.getTarget().getStart());
                    o.put("end", e.getTarget().getEnd());
                    final CtElement e_ori = (CtElement) e.getTarget().getOriginal();
                    if (e_ori != null) {
                        o.put("type", Utils.formatedType(e_ori));
                    } else {
                        o.put("type", "evo null");
                    }
                    o.put("description", e.getDescription());
                    e.getDescription();

                    for (int i = 0; i < aaa.left.size(); i++) {
                        if (aaa.left.get(i).description.equals(e.getDescription())) {
                            final String tmp = e.getTarget().getFile().getPath() + ":"
                                    + Integer.toString(e.getTarget().getStart()) + "-"
                                    + Integer.toString(e.getTarget().getEnd());
                            final String key = Integer.toString(i);
                            final List<String> tmp2 = before_e.getOrDefault(key, new ArrayList<>());
                            tmp2.add(tmp);
                            before_e.put(key, tmp2);
                            break;
                        }
                    }
                }
                for (final Entry<String, List<String>> entry : before_e.entrySet()) {
                    entry.getValue().sort(new Comparator<String>() {
                        @Override
                        public int compare(final String a, final String b) {
                            return a.compareTo(b);
                        }
                    });
                    evofields.put("before" + entry.getKey(), entry.getValue());
                    for (final String s : entry.getValue()) {
                        url.append("&before" + entry.getKey() + "=" + s);
                    }
                }
                final Map<String, List<String>> after_e = new HashMap<>();
                final List<Object> rightSideLocations = new ArrayList<>();
                res.put("rightSideLocations", rightSideLocations);
                for (final DescRange e : this.getAfter()) {
                    final Map<String, Object> o = new HashMap<>();
                    rightSideLocations.add(o);
                    o.put("filePath", e.getTarget().getFile().getPath());
                    o.put("start", e.getTarget().getStart());
                    o.put("end", e.getTarget().getEnd());
                    final CtElement e_ori = (CtElement) e.getTarget().getOriginal();
                    if (e_ori != null) {
                        o.put("type", Utils.formatedType(e_ori));
                    } else {
                        o.put("type", "evo null");
                    }
                    o.put("description", e.getDescription());
                    e.getDescription();
                    for (int i = 0; i < aaa.right.size(); i++) {
                        if (aaa.right.get(i).description.equals(e.getDescription())) {
                            final String tmp = e.getTarget().getFile().getPath() + ":"
                                    + Integer.toString(e.getTarget().getStart()) + "-"
                                    + Integer.toString(e.getTarget().getEnd());
                            final String key = Integer.toString(i);
                            final List<String> tmp2 = after_e.getOrDefault(key, new ArrayList<>());
                            tmp2.add(tmp);
                            after_e.put(key, tmp2);
                            break;
                        }
                    }
                }
                for (final Entry<String, List<String>> entry : after_e.entrySet()) {
                    entry.getValue().sort(new Comparator<String>() {
                        @Override
                        public int compare(final String a, final String b) {
                            return a.compareTo(b);
                        }
                    });
                    evofields.put("after" + entry.getKey(), entry.getValue());
                    for (

                    final String s : entry.getValue()) {
                        url.append("&after" + entry.getKey() + "=" + s);
                    }
                }
                evofields.put("url", url.toString());
                return res;
            }
        }

        void addEvolution(Refactoring refact, Project<?> astBefore, Project<?> astAfter) {
            List<ImmutablePair<Range, String>> before = aux(refact.leftSide(), astBefore);
            List<ImmutablePair<Range, String>> after = aux(refact.rightSide(), astAfter);
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
            Range tmp = proj.getRange(range.getFilePath().replaceAll("/", systemFileSeparator), range.getStartOffset(), range.getEndOffset() - 1);
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
            Map<String, Set<Evolution>> tmp = new HashMap<>();
            for (Evolution evolution : toSet()) {
                String cidb = evolution.getCommitBefore().getId();
                tmp.putIfAbsent(cidb, new HashSet<>());
                tmp.get(cidb).add(evolution);
            }
            Map<Commit, Evolutions> r = new HashMap<>();
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