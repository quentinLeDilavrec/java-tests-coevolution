package fr.quentin.v2.impact.storages;

import static org.neo4j.driver.Values.parameters;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.Transaction;
import org.neo4j.driver.TransactionWork;

import fr.quentin.Evolution;
import fr.quentin.ImpactAnalysis;
import fr.quentin.ImpactElement;
import fr.quentin.Position;
import fr.quentin.Impacts.Relations;
import fr.quentin.v2.impact.Impacts;
import fr.quentin.v2.impact.ImpactsStorage;
import fr.quentin.v2.impact.Impacts.Specifier;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtMethod;

public class Neo4jImpactsStorage implements ImpactsStorage {

    @Override
    public void put(Impacts.Specifier impacts_spec, Impacts value) {
        way2(impacts_spec, value);
    }

    private void way2(Impacts.Specifier impacts_spec, Impacts value) {
        Map<ImpactElement, Map<ImpactElement, Relations>> aaa = value.getPerRootCause();
        List<Object> tmp = new ArrayList<>();
        List<Object> ranges_to_type = new ArrayList<>();
        for (Entry<ImpactElement, Map<ImpactElement, Relations>> rootEntry : aaa.entrySet()) {
            List<String> getCommitIdBeforeAndAfter = getCommitIdBeforeAndAfter(rootEntry.getKey());
            tmp.addAll(basifyRootCauseImpact(impacts_spec.astSpec.sources.repository, getCommitIdBeforeAndAfter.get(0),
                    value.getCauseRootDir(), rootEntry.getKey(), ranges_to_type));
            for (Entry<ImpactElement, Relations> verticeEntry : rootEntry.getValue().entrySet()) {
                tmp.addAll(basifyImpact(impacts_spec.astSpec.sources.repository, getCommitIdBeforeAndAfter.get(0),
                        value.getCauseRootDir(), rootEntry.getKey(), verticeEntry.getValue()));
            }
        }
        try (Session session = driver.session()) {
            String done = session.writeTransaction(new TransactionWork<String>() {
                @Override
                public String execute(Transaction tx) {
                    Result result = tx.run(getCypher(),
                            parameters("json", tmp, "tool", impacts_spec.miner, "rangesToType", ranges_to_type));
                    result.consume();
                    return "done impact";
                }
            });
            System.out.println(done);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private List<String> getCommitIdBeforeAndAfter(ImpactElement rootCause) {
        String commitIdBefore = null;
        String commitIdAfter = null;
        for (Entry<Evolution<Object>, Position> entry : rootCause.getEvolutionWithNonCorrectedPosition().entrySet()) {
            if (commitIdBefore == null) {
                commitIdBefore = entry.getKey().getCommitIdBefore();
            } else if (!commitIdBefore.equals(entry.getKey().getCommitIdBefore())) {
                throw new RuntimeException("wrong commitIdBefore");
            }
            if (commitIdAfter == null) {
                commitIdAfter = entry.getKey().getCommitIdAfter();
            } else if (!commitIdAfter.equals(entry.getKey().getCommitIdAfter())) {
                throw new RuntimeException("wrong commitIdAfter");
            }
        }
        return Arrays.asList(commitIdBefore, commitIdAfter);
    }

    private Position temporaryFix(Position position, Path rootDir) {
        try {
            return new Position(rootDir.relativize(Paths.get(position.getFilePath())).toString(), position.getStart(),
                    position.getEnd() + 1);
        } catch (Exception e) {
            return new Position(position.getFilePath(), position.getStart(), position.getEnd() + 1);
        }
    }

    private boolean isTest(CtElement e) {
        if (e instanceof CtExecutable) {
            return ImpactAnalysis.isTest((CtExecutable) e);
        } else {
            return false;
        }
    }

    private boolean isParentTest(CtElement e) {
        if (e == null) {
            return false;
        }
        CtMethod p = e.getParent(CtMethod.class);
        if (p == null) {
            return false;
        }
        return ImpactAnalysis.isTest(p);
    }

    protected List<Object> basifyRootCauseImpact(String repository, String commitId, Path cause_rootDir,
            ImpactElement rootCause, List<Object> ranges_to_type) {
        List<Object> res = new ArrayList<>();
        List<Object> causes = new ArrayList<>();
        Position effect_pos = temporaryFix(rootCause.getPosition(), cause_rootDir);
        for (Entry<Evolution<Object>, Position> entry : rootCause.getEvolutionWithNonCorrectedPosition().entrySet()) {
            // TODO add evolution in graph? would need to pull-up helper from from
            // Neo4jEvolutionStorage.java
            Position cause_pos = entry.getValue();
            Map<String, Object> o = new HashMap<>();
            if (effect_pos.equals(cause_pos))
                ranges_to_type.add(o);
            else
                causes.add(o);

            // o.put("type", "unknown"); // not sure
            o.put("repository", repository);
            o.put("commitId", commitId);// root.relativize(y.getPosition().getFile().toPath()).toString()
            // try {
            // o.put("file",
            // cause_rootDir.relativize(Paths.get(cause_pos.getFilePath())).toString());
            // } catch (Exception e) {
            o.put("file", cause_pos.getFilePath());
            // }
            o.put("start", cause_pos.getStart());
            o.put("end", cause_pos.getEnd());
            if (rootCause.getContent() instanceof CtMethod) {
                o.put("sig", ((CtMethod) rootCause.getContent()).getSignature());
            }
            if (isTest(rootCause.getContent())) {
                o.put("isTest", "parent");
            } else if (isParentTest(rootCause.getContent())) {
                o.put("isTest", "parent");
            }
        }

        Map<String, Object> rootImpact = new HashMap<>();
        if (causes.size() > 0)
            res.add(rootImpact);
        Map<String, Object> content = new HashMap<>();
        rootImpact.put("content", content);
        content.put("type", "expand declaration");
        content.put("repository", repository);
        content.put("commitId", commitId);
        // try {
        // content.put("file",
        // cause_rootDir.relativize(Paths.get(effect_pos.getFilePath())).toString());
        // } catch (Exception e) {
        content.put("file", effect_pos.getFilePath());
        // }
        content.put("start", effect_pos.getStart());
        content.put("end", effect_pos.getEnd());

        rootImpact.put("causes", causes);

        List<Object> effect = new ArrayList<>();
        rootImpact.put("effects", effect);
        Map<String, Object> o = new HashMap<>();
        effect.add(o);
        o.put("type", rootCause.getContent().getClass().getSimpleName());
        o.put("repository", repository);
        o.put("commitId", commitId);
        // try {
        // o.put("file",
        // cause_rootDir.relativize(Paths.get(effect_pos.getFilePath())).toString());
        // } catch (Exception e) {
        o.put("file", effect_pos.getFilePath());
        // }
        o.put("start", effect_pos.getStart());
        o.put("end", effect_pos.getEnd());
        if (rootCause.getContent() instanceof CtMethod) {
            o.put("sig", ((CtMethod) rootCause.getContent()).getSignature());
        }
        if (isTest(rootCause.getContent())) {
            o.put("isTest", true);
        } else if (isParentTest(rootCause.getContent())) {
            o.put("isTest", "parent");
        }
        return res;
    }

    private List<Object> makeImpact(String repository, String commitIdBefore, Path rootDir, ImpactElement cause,
            CtMethod<?> parentTestMethod, String type) {
        List<Object> res = new ArrayList<>();

        List<Object> causes = new ArrayList<>();
        List<Object> effects = new ArrayList<>();
        Map<String, Object> callImpact = new HashMap<>();
        res.add(callImpact);
        Position cause_pos = temporaryFix(cause.getPosition(), rootDir);
        Position effect_pos = temporaryFix(
                new Position(parentTestMethod.getPosition().getFile().getPath(),
                        parentTestMethod.getPosition().getSourceStart(), parentTestMethod.getPosition().getSourceEnd()),
                rootDir);

        Map<String, Object> content = new HashMap<>();
        callImpact.put("content", content);
        content.put("type", "expand to declaration");
        content.put("repository", repository);
        content.put("commitId", commitIdBefore);
        content.put("file", effect_pos.getFilePath());
        content.put("start", effect_pos.getStart());
        content.put("end", effect_pos.getEnd());

        callImpact.put("effects", effects);
        Map<String, Object> o_e = new HashMap<>();
        effects.add(o_e);
        o_e.put("type", parentTestMethod.getClass().getSimpleName());
        o_e.put("repository", repository);
        o_e.put("commitId", commitIdBefore);
        o_e.put("file", effect_pos.getFilePath());
        o_e.put("start", effect_pos.getStart());
        o_e.put("end", effect_pos.getEnd());
        o_e.put("sig", parentTestMethod.getSignature());
        if (isTest(parentTestMethod)) {
            o_e.put("isTest", true);
        } else if (isParentTest(parentTestMethod)) {
            o_e.put("isTest", "parent");
        }
        callImpact.put("causes", causes);
        Map<String, Object> o_c = new HashMap<>();
        causes.add(o_c);
        o_c.put("type", cause.getContent().getClass().getSimpleName());
        o_c.put("repository", repository);
        o_c.put("commitId", commitIdBefore);
        o_c.put("file", cause_pos.getFilePath());
        o_c.put("start", cause_pos.getStart());
        o_c.put("end", cause_pos.getEnd());
        if (isTest(cause.getContent())) {
            o_c.put("isTest", true);
        } else if (isParentTest(cause.getContent())) {
            o_c.put("isTest", "parent");
        }
        return res;
    }

    protected List<Object> basifyImpact(String repository, String commitIdBefore, Path cause_rootDir,
            ImpactElement rootCause, Relations vertice) { // TODO commitIdAfter useless ?
        List<Object> res = new ArrayList<>();

        // Map<String, Object> expandImpact = new HashMap<>();
        // res.add(expandImpact);
        // expandImpact.put("type", "expand to declaration");
        // expandImpact.put("causes", null);
        // expandImpact.put("effects", null);

        for (Entry<String, Set<ImpactElement>> entry : vertice.getEffects().entrySet()) {
            if (!entry.getKey().equals("call")) {
                continue; // TODO handle the rest
            }
            List<Object> cause = new ArrayList<>();
            List<Object> effects = new ArrayList<>();
            Map<String, Object> callImpact = new HashMap<>();
            res.add(callImpact);
            Position cause_pos = temporaryFix(vertice.getVertice().getPosition(), cause_rootDir);

            Map<String, Object> content = new HashMap<>();
            callImpact.put("content", content);
            content.put("type", "call impact");
            content.put("repository", repository);
            content.put("commitId", commitIdBefore);
            // try {
            // content.put("file",
            // cause_rootDir.relativize(Paths.get(cause_pos.getFilePath())).toString());
            // } catch (Exception e) {
            content.put("file", cause_pos.getFilePath());
            // }
            content.put("start", cause_pos.getStart());
            content.put("end", cause_pos.getEnd());

            callImpact.put("causes", cause);
            Map<String, Object> o_c = new HashMap<>();
            cause.add(o_c);
            o_c.put("type", vertice.getVertice().getContent().getClass().getSimpleName());
            o_c.put("repository", repository);
            o_c.put("commitId", commitIdBefore);
            // try {
            // o_c.put("file",
            // cause_rootDir.relativize(Paths.get(cause_pos.getFilePath())).toString());
            // } catch (Exception e) {
            o_c.put("file", cause_pos.getFilePath());
            // }
            o_c.put("start", cause_pos.getStart());
            o_c.put("end", cause_pos.getEnd());
            if (vertice.getVertice().getContent() instanceof CtMethod) {
                o_c.put("sig", ((CtMethod) vertice.getVertice().getContent()).getSignature());
            }
            if (isTest(vertice.getVertice().getContent())) {
                o_c.put("isTest", true);
            } else if (isParentTest(vertice.getVertice().getContent())) {
                o_c.put("isTest", "parent");
                res.addAll(makeImpact(repository, commitIdBefore, cause_rootDir, vertice.getVertice(),
                        vertice.getVertice().getContent().getParent(CtMethod.class), "expand to declaration"));
            }
            callImpact.put("effects", effects);
            for (ImpactElement e : entry.getValue()) {
                Position effect_pos = temporaryFix(e.getPosition(), cause_rootDir);
                Map<String, Object> o = new HashMap<>();
                effects.add(o);
                o.put("type", e.getContent().getClass().getSimpleName());
                o.put("repository", repository);
                o.put("commitId", commitIdBefore);
                // try {
                // o.put("file",
                // cause_rootDir.relativize(Paths.get(effect_pos.getFilePath())).toString());
                // } catch (Exception ee) {
                o.put("file", effect_pos.getFilePath());
                // }
                o.put("start", effect_pos.getStart());
                o.put("end", effect_pos.getEnd());
                if (e.getContent() instanceof CtMethod) {
                    o.put("sig", ((CtMethod) e.getContent()).getSignature());
                }
                if (isTest(e.getContent())) {
                    o.put("isTest", true);
                } else if (isParentTest(e.getContent())) {
                    o.put("isTest", "parent");
                    res.addAll(makeImpact(repository, commitIdBefore, cause_rootDir, e,
                            e.getContent().getParent(CtMethod.class), "expand to declaration"));
                }
            }
        }
        return res;
    }

    private final Driver driver;

    public Neo4jImpactsStorage(String uri, String user, String password) {
        driver = GraphDatabase.driver(uri, AuthTokens.basic(user, password));
        // driver.close(); // TODO should be closed at some point

    }

    public Neo4jImpactsStorage() {
        this("bolt://localhost:7687", "neo4j", "neo4j");
    }

    @Override
    public Impacts get(Impacts.Specifier impacts_spec) {
        // TODO Auto-generated method stub
        return null;
    }

    private static String getCypher() {
        try {
            return new String(Files.readAllBytes(
                    Paths.get(Neo4jImpactsStorage.class.getClassLoader().getResource("impacts_cypher.sql").getFile())));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}