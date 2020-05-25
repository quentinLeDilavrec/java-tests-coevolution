package fr.quentin.coevolutionMiner.v2.coevolution.storages;

import static org.neo4j.driver.Values.parameters;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.Transaction;
import org.neo4j.driver.TransactionWork;
import org.neo4j.driver.Value;
import org.neo4j.driver.types.Node;
import org.neo4j.driver.types.Relationship;
import org.neo4j.driver.types.Type;
import org.neo4j.driver.types.Path.Segment;
import org.refactoringminer.api.Refactoring;

import fr.quentin.coevolutionMiner.v2.ast.AST;
// import fr.quentin.impactMiner.ImpactAnalysis;
// import fr.quentin.impactMiner.ImpactChain;
// import fr.quentin.impactMiner.ImpactElement;
// import fr.quentin.impactMiner.Position;
// import fr.quentin.impactMiner.Impacts.Relations;
import fr.quentin.coevolutionMiner.v2.ast.AST.FileSnapshot.Range;
import fr.quentin.coevolutionMiner.v2.coevolution.CoEvolutions;
import fr.quentin.coevolutionMiner.v2.coevolution.CoEvolutions.CoEvolution;
import fr.quentin.coevolutionMiner.v2.coevolution.CoEvolutions.Specifier;
import fr.quentin.coevolutionMiner.v2.coevolution.miners.MyCoEvolutionsMiner.CoEvolutionsExtension;
import fr.quentin.coevolutionMiner.v2.evolution.Evolutions;
import fr.quentin.coevolutionMiner.v2.evolution.Evolutions.Evolution;
import fr.quentin.coevolutionMiner.v2.evolution.storages.Neo4jEvolutionsStorage;
import fr.quentin.coevolutionMiner.v2.evolution.storages.Neo4jEvolutionsStorage.EvoType;
import fr.quentin.coevolutionMiner.v2.impact.Impacts;
import fr.quentin.coevolutionMiner.v2.sources.Sources.Commit;
import fr.quentin.coevolutionMiner.v2.utils.Tuple;
import fr.quentin.coevolutionMiner.v2.coevolution.CoEvolutionsStorage;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtMethod;

public class Neo4jCoEvolutionsStorage implements CoEvolutionsStorage {

    @Override
    public void put(CoEvolutions value) {
        Set<CoEvolution> validated_coevos = value.getValidated();
        Set<CoEvolution> unvalidated_coevos = value.getUnvalidated();
        List<Map<String, Object>> tmp = new ArrayList<>();
        for (CoEvolution coevolution : validated_coevos) {
            tmp.add(basifyCoevo(coevolution, true, value.spec.srcSpec.repository));
        }
        for (CoEvolution coevolution : unvalidated_coevos) {
            tmp.add(basifyCoevo(coevolution, false, value.spec.srcSpec.repository));
        }
        try (Session session = driver.session()) {
            String done = session.writeTransaction(new TransactionWork<String>() {
                @Override
                public String execute(Transaction tx) {
                    Result result = tx.run(getCypher(), parameters("json", tmp, "tool", value.spec.miner));
                    result.consume();
                    return "done evolution";
                }
            });
            System.out.println(done);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Map<String, Object> basifyCoevo(CoEvolution coevolution, boolean validated, String repository) {
        Map<String, Object> coevo = new HashMap<>();
        List<String> causes_url = new ArrayList<>();
        List<Map<String, Object>> pointed = new ArrayList<>();
        for (Evolution evolution : coevolution.getCauses()) {
            Map<String, Object> tmp = Neo4jEvolutionsStorage.basifyEvo(repository, evolution);
            Map<String, Object> o = new HashMap<>();
            o.put("content", tmp);
            o.put("type", "cause");
            pointed.add(o);
            causes_url.add((String)(((Map<String, Object>)tmp.get("content")).get("url")));
        }
        List<String> resolutions_url = new ArrayList<>();
        for (Evolution evolution : coevolution.getResolutions()) {
            Map<String, Object> tmp = Neo4jEvolutionsStorage.basifyEvo(repository, evolution);
            Map<String, Object> o = new HashMap<>();
            o.put("content", tmp);
            o.put("type", "resolution");
            pointed.add(o);
            resolutions_url.add((String)(((Map<String, Object>)tmp.get("content")).get("url")));
        }
        Map<String, Object> content = new HashMap<>();
        content.put("causes", causes_url);
        content.put("resolutions", resolutions_url);
        coevo.put("content", content);
        coevo.put("pointed", pointed);

        return coevo;
    }

    private final Driver driver;

    public Neo4jCoEvolutionsStorage(String uri, String user, String password) {
        driver = GraphDatabase.driver(uri, AuthTokens.basic(user, password));
    }

    public Neo4jCoEvolutionsStorage() {
        this("bolt://localhost:7687", "neo4j", "neo4j");
    }

    @Override
    public CoEvolutions get(CoEvolutions.Specifier spec) {
        // TODO Auto-generated method stub
        return null;
    }

    private static boolean compareEvolution(String repository, Value value, Evolution other) {
        if (other.getOriginal() instanceof Refactoring) {
            return Neo4jEvolutionsStorage.makeEvoUrl(repository, (Evolution) other).equals(value.get("url").asString());
        }
        return false;
    }

    // private static Position toPosition(Value position) {
    // return new Position(position.get("path").asString(),
    // position.get("start").asInt(),
    // position.get("end").asInt());
    // }

    // public static Set<Evolution> toImpactChains(Value rawL, Impacts impacts) {
    // Set<Evolution> r = new HashSet<>();
    // // Map<ImpactElement, Map<ImpactElement, Relations>> perRootCause =
    // // impacts.getPerRootCause();
    // rawL.asList(raw -> {
    // Value rawEvo = raw.get(raw.size() - 1);
    // Evolution evo = null;
    // if (evo == null) {
    // Position impactingPos = toPosition(raw.get(raw.size() - 2));
    // ImpactElement ie = new ImpactElement(impactingPos);
    // for (Evolution<Object> currevo : ie.getEvolutions()) {
    // if (compareEvolution(impacts.spec.evoSpec.sources.repository, rawEvo,
    // currevo)) {
    // evo = currevo;
    // break;
    // }
    // }
    // }

    // // TODO make global var for constants
    // if (evo == null && raw.get(raw.size() -
    // 3).get("type").asString().equals("adjusted")) {
    // Position impactingPos = toPosition(raw.get(raw.size() - 7));
    // ImpactElement ie = new ImpactElement(impactingPos);
    // for (Evolution<Object> currevo : ie.getEvolutions()) {
    // if (compareEvolution(impacts.spec.evoSpec.sources.repository, rawEvo,
    // currevo)) {
    // evo = currevo;
    // break;
    // }
    // }
    // }

    // if (evo != null) {
    // r.add(evo);
    // }
    // return null;
    // });
    // return r;
    // }

    /**
     * evolutions is on a single commit
     */
    @Override
    public void construct(CoEvolutionsExtension.Builder coevoBuilder, Set<Range> startingTests) {
        // public CoEvolutions get(CoEvolutions.Specifier spec, Set<Range>
        // startingTests, Evolutions evolutions, AST astBefore, AST astAfter) {
        List<Map<String, Object>> list = startingTests.stream().map(x -> {
            Map<String, Object> r = new HashMap<>();
            r.put("repo", coevoBuilder.getSpec().srcSpec.repository);
            r.put("commitId", coevoBuilder.getSpec().evoSpec.commitIdBefore);
            r.put("path", x.getFile().getPath());
            r.put("start", x.getStart());
            r.put("end", x.getEnd());
            return r;
        }).collect(Collectors.toList());
        Evolutions evolutions = coevoBuilder.getEvolutions();
        AST astBefore = coevoBuilder.getAstBefore();
        AST astAfter = coevoBuilder.getAstAfter();
        try (Session session = driver.session()) {
            List<CoEvolution> done = session.readTransaction(new TransactionWork<List<CoEvolution>>() {
                @Override
                public List<CoEvolution> execute(Transaction tx) {
                    Result queryResult = tx.run(getMinerCypher(), parameters("data", list));
                    return queryResult.list(x -> {
                        Value beforeTestRaw = x.get("test");
                        AST.FileSnapshot.Range testBefore = astBefore.getRange(beforeTestRaw.get("path").asString(),
                                beforeTestRaw.get("start").asInt(), beforeTestRaw.get("end").asInt());

                        Set<Evolution> causes = new HashSet<>();
                        causes.addAll(getEvo(x.get("shortCall"), evolutions, astBefore, astAfter));
                        causes.addAll(getEvo(x.get("longCall"), evolutions, astBefore, astAfter));

                        Set<Evolution> evosLong = getEvo(x.get("long"), evolutions, astBefore, astAfter);
                        // for (Evolution aaa : evosLong) {
                        // if (aaa.getType().equals("Move Method")||aaa.getType().equals("Change
                        // Variable Type")||aaa.getType().equals("Rename Variable")) {
                        // tmp.TestAfter = aaa.getAfter().get(0).getTarget();
                        // }
                        // }
                        Set<Evolution> evosShort = getEvo(x.get("short"), evolutions, astBefore, astAfter);

                        Set<Evolution> evosShortAdjusted = getEvo(x.get("shortAdjusted"), evolutions, astBefore,
                                astAfter);

                        // for (Evolution aaa : evosShort) {
                        // if (aaa.getType().equals("Move Method")||aaa.getType().equals("Change
                        // Variable Type")||aaa.getType().equals("Rename Variable")) {
                        // tmp.TestAfter = aaa.getAfter().get(0).getTarget();
                        // }
                        // }

                        coevoBuilder.addCoevolution(causes, evosLong, evosShort, evosShortAdjusted, testBefore);

                        return null;
                    });
                }
            });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // TODO use after ranges to make the match
    protected Set<Evolution> getEvo(Value value, Evolutions evolutions, AST astBefore, AST astAfter) {
        Set<Evolution> r = new HashSet<>();
        for (Value x : value.asList(x -> x)) {
            List<ImmutablePair<Range, String>> before = new ArrayList<>();
            String evoType = "";
            for (org.neo4j.driver.types.Path y : x.asList(y -> y.asPath())) {
                Segment segment = y.iterator().next();
                evoType = segment.start().get("type").asString();
                Node rangeNode = segment.end();
                Relationship relation = segment.relationship();
                String relType = relation.get("desc").asString();
                ImmutablePair<Range, String> descRange = getEvoAux(astBefore.commit.getRepository().getUrl(),
                        astBefore.commit.getId(), astBefore, rangeNode, relType);
                if (descRange != null)
                    before.add(descRange);
            }
            r.add(evolutions.getEvolution(evoType, before));
        }
        return r;
    }

    private ImmutablePair<Range, String> getEvoAux(String repoURL, String commitId, AST ast, Node curr, String type) {
        if (!repoURL.equals(curr.get("repo").asString()))
            return null;
        if (!commitId.equals(curr.get("commitId").asString()))
            return null;
        Range range = ast.getRange(curr.get("path").asString(), curr.get("start").asInt(), curr.get("end").asInt());
        return new ImmutablePair<AST.FileSnapshot.Range, String>(range, type);
    }

    public static String getMinerCypher() {
        try {
            return new String(Files.readAllBytes(Paths.get(
                    Neo4jCoEvolutionsStorage.class.getClassLoader().getResource("coevolution_miner.cql").getFile())));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String getCypher() {
        try {
            return new String(Files.readAllBytes(Paths.get(
                    Neo4jCoEvolutionsStorage.class.getClassLoader().getResource("coevolutions_cypher.cql").getFile())));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() throws Exception {
        driver.close();
    }
}