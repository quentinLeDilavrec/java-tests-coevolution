package fr.quentin.v2.evolution.miners;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import org.refactoringminer.api.GitHistoryRefactoringMiner;
import org.refactoringminer.api.Refactoring;
import org.refactoringminer.api.RefactoringHandler;
import org.refactoringminer.rm1.GitHistoryRefactoringMinerImpl;

import fr.quentin.Evolution;
import fr.quentin.Position;
import fr.quentin.utils.SourcesHelper;
import fr.quentin.v2.ast.ASTHandler;
import fr.quentin.v2.evolution.Evolutions;
import fr.quentin.v2.evolution.EvolutionsMiner;
import fr.quentin.v2.evolution.Evolutions.Specifier;
import fr.quentin.v2.sources.Sources;
import fr.quentin.v2.sources.SourcesHandler;
import gr.uom.java.xmi.diff.CodeRange;

public class RefactoringMiner implements EvolutionsMiner {
    Logger logger = Logger.getLogger("ImpactRM commitHandler");

    private ASTHandler astHandler;
    private SourcesHandler srcHandler;
    private Evolutions.Specifier spec;

    // TODO instanciate filters correctly and make use of them
    private List<Object> filters;

    public RefactoringMiner(Evolutions.Specifier spec, SourcesHandler srcHandler, ASTHandler astHandler) {
        this.spec = spec;
        this.astHandler = astHandler;
        this.srcHandler = srcHandler;
    }

    public RefactoringMiner(Evolutions.Specifier spec, SourcesHandler srcHandler, ASTHandler astHandler,
            List<Object> filters) {
        this(spec, srcHandler, astHandler);
        this.filters = filters;
    }

    @Override
    public Evolutions compute() {
        Sources src = srcHandler.handle(spec.sources, "jgit");
        GitHistoryRefactoringMiner miner = new GitHistoryRefactoringMinerImpl();

        List<Refactoring> detectedRefactorings = new ArrayList<Refactoring>();
        List<Evolution<Refactoring>> evolutions = new ArrayList<Evolution<Refactoring>>();

        try (SourcesHelper helper = src.open()) {
            miner.detectBetweenCommits(helper.getRepo(), spec.commitIdBefore, spec.commitIdAfter,
                    new RefactoringHandler() {
                        @Override
                        public void handle(String commitId, List<Refactoring> refactorings) {
                            detectedRefactorings.addAll(refactorings);
                            for (Refactoring op : refactorings) {
                                String before;
                                try {
                                    before = helper.getBeforeCommit(commitId);
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                                OtherEvolution tmp = new OtherEvolution(op, before, commitId);
                                evolutions.add(tmp);
                                logger.info("O- " + tmp.op + "\n" + tmp.pre.size());
                            }
                        }

                    });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return new Evolutions() {
            @Override
            public List<Evolution<Refactoring>> toList() {
                return evolutions;
            }

            @Override
            public JsonElement toJson() {
                // TODO optimize (serializing then deserializing is a dirty hack)
                Object diff = new Gson().fromJson(
                        JSON(spec.sources.repository, spec.commitIdAfter, detectedRefactorings),
                        new TypeToken<Object>() {
                        }.getType());
                return new Gson().toJsonTree(diff);
            }
        };
    }

    static class OtherEvolution implements Evolution<Refactoring> {
        Set<Position> pre = new HashSet<>();
        Set<Position> post = new HashSet<>();
        Map<String, Set<Position>> preDesc = new HashMap<>();
        Map<String, Set<Position>> postDesc = new HashMap<>();
        private Refactoring op;
        private String commitIdBefore;
        private String commitIdAfter;

        OtherEvolution(Refactoring op, String commitIdBefore, String commitIdAfter) {
            this.op = op;
            this.commitIdBefore = commitIdBefore;
            this.commitIdAfter = commitIdAfter;
            for (CodeRange range : op.leftSide()) {
                Position pos = new Position(range.getFilePath(), range.getStartOffset(), range.getEndOffset());
                this.preDesc.putIfAbsent(range.getDescription(), new HashSet<>());
                this.preDesc.get(range.getDescription()).add(pos);
                this.pre.add(pos);
            }
            for (CodeRange range : op.rightSide()) {
                Position pos = new Position(range.getFilePath(), range.getStartOffset(), range.getEndOffset());
                this.postDesc.putIfAbsent(range.getDescription(), new HashSet<>());
                this.postDesc.get(range.getDescription()).add(pos);
                this.post.add(pos);
            }
        }

        public Map<String, Set<Position>> getPreEvolutionPositionsDesc() {
            return preDesc;
        }

        public Map<String, Set<Position>> getPostEvolutionPositionsDesc() {
            return postDesc;
        }

        @Override
        public Set<Position> getPreEvolutionPositions() {
            return pre;
        }

        @Override
        public Set<Position> getPostEvolutionPositions() {
            return post;
        }

        @Override
        public Refactoring getOriginal() {
            return op;
        }

        @Override
        public String getCommitIdBefore() {
            return commitIdBefore;
        }

        @Override
        public String getCommitIdAfter() {
            return commitIdAfter;
        }

        @Override
        public JsonObject toJson() {
            JsonObject r = new JsonObject();
            r.addProperty("type", op.getName());
            r.addProperty("commitIdBefore", getCommitIdBefore());
            r.addProperty("commitIdAfter", getCommitIdAfter());
            JsonArray before = new JsonArray();
            for (CodeRange p : op.leftSide()) {
                JsonObject o = new JsonObject();
                before.add(o);
                o.addProperty("file", p.getFilePath());
                o.addProperty("start", p.getStartOffset());
                o.addProperty("end", p.getEndOffset());
                o.addProperty("type", p.getCodeElementType().getName());
                o.addProperty("desc", p.getDescription());
            }
            r.add("before", before);
            JsonArray after = new JsonArray();
            for (CodeRange p : op.rightSide()) {
                JsonObject o = new JsonObject();
                after.add(o);
                o.addProperty("file", p.getFilePath());
                o.addProperty("start", p.getStartOffset());
                o.addProperty("end", p.getEndOffset());
                o.addProperty("type", p.getCodeElementType().getName());
                o.addProperty("desc", p.getDescription());
            }
            r.add("after", after);
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
        String url = "https://github.com/" + gitURL.substring(19, gitURL.indexOf(".git")) + "/commit/"
                + currentCommitId;
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
}