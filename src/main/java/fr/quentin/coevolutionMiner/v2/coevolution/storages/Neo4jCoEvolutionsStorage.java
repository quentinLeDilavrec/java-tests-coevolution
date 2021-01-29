package fr.quentin.coevolutionMiner.v2.coevolution.storages;

import static org.neo4j.driver.Values.parameters;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Value;
import org.neo4j.driver.types.Node;
import org.neo4j.driver.types.Path.Segment;
import org.neo4j.driver.types.Relationship;

import fr.quentin.coevolutionMiner.utils.MyProperties;
import fr.quentin.coevolutionMiner.v2.ast.Project;
// import fr.quentin.impactMiner.ImpactAnalysis;
// import fr.quentin.impactMiner.ImpactChain;
// import fr.quentin.impactMiner.ImpactElement;
// import fr.quentin.impactMiner.Position;
// import fr.quentin.impactMiner.Impacts.Relations;
import fr.quentin.coevolutionMiner.v2.ast.Project.AST.FileSnapshot.Range;
import fr.quentin.coevolutionMiner.v2.ast.ProjectHandler;
import fr.quentin.coevolutionMiner.v2.coevolution.CoEvolutions;
import fr.quentin.coevolutionMiner.v2.coevolution.CoEvolutions.CoEvolution;
import fr.quentin.coevolutionMiner.v2.coevolution.CoEvolutions.Specifier;
import fr.quentin.coevolutionMiner.v2.coevolution.CoEvolutionsStorage;
import fr.quentin.coevolutionMiner.v2.coevolution.miners.EImpact;
import fr.quentin.coevolutionMiner.v2.coevolution.miners.EImpact.FailureReport;
import fr.quentin.coevolutionMiner.v2.evolution.EvolutionHandler;
import fr.quentin.coevolutionMiner.v2.evolution.Evolutions;
import fr.quentin.coevolutionMiner.v2.evolution.Evolutions.Evolution;
import fr.quentin.coevolutionMiner.v2.impact.ImpactHandler;
import fr.quentin.coevolutionMiner.v2.sources.SourcesHandler;
import fr.quentin.coevolutionMiner.v2.utils.Utils;

public class Neo4jCoEvolutionsStorage implements CoEvolutionsStorage {
    public static Logger logger = LogManager.getLogger();

    class ChunckedUploadCoEvos extends Utils.ChunckedUpload<Map<String, Object>> {
        private final Specifier spec;

        public ChunckedUploadCoEvos(Specifier spec, List<Map<String, Object>> processed) {
            super(driver, 10);
            this.spec = spec;
            execute(logger, 256, processed);
        }

        @Override
        protected String getCypher() {
            return Utils.memoizedReadResource("coevolutions_cypher.cql");
        }

        @Override
        public Value format(Collection<Map<String, Object>> chunk) {
            return parameters("coevoSimp", chunk, "tool", spec.miner);
        }

        @Override
        protected String whatIsUploaded() {
            return "coevolutions of " + spec.srcSpec.repository;
        }

    }

    class ChunckedUploadEImpacts extends Utils.ChunckedUpload<Map<String, Object>> {
        private final Specifier spec;

        public ChunckedUploadEImpacts(Specifier spec, List<Map<String, Object>> processed) {
            super(driver, 10);
            this.spec = spec;
            execute(logger, 256, processed);
        }

        @Override
        protected String getCypher() {
            return Utils.memoizedReadResource("eimpact_cypher.cql");
        }

        @Override
        public Value format(Collection<Map<String, Object>> chunk) {
            return parameters("eImpacts", chunk, "tool", spec.miner);
        }

        @Override
        protected String whatIsUploaded() {
            return "eimpact of " + spec.srcSpec.repository;
        }

    }

    class ChunckedUploadInitTests extends Utils.ChunckedUpload<Map<String, Object>> {
        private final Specifier spec;

        public ChunckedUploadInitTests(Specifier spec, List<Map<String, Object>> processed) {
            super(driver, 10);
            this.spec = spec;
            execute(logger, 256, processed);
        }

        @Override
        protected String getCypher() {
            return Utils.memoizedReadResource("initTest_cypher.cql");
        }

        @Override
        public Value format(Collection<Map<String, Object>> chunk) {
            return parameters("initTests", chunk, "tool", spec.miner);
        }

        @Override
        protected String whatIsUploaded() {
            return "initial tests of " + spec.srcSpec.repository;
        }

    }

    @Override
    public void put(CoEvolutions value) {
        List<Map<String, Object>> initTests = new ArrayList<>();
        for (ImmutablePair<Range, FailureReport> initTest : value.getInitialTests()) {
            initTests.add(basifyInitTests(initTest));
        }
        new ChunckedUploadInitTests(value.spec, initTests);

        Set<CoEvolution> coevos = value.getCoEvolutions();
        List<Map<String, Object>> coevoSimp = new ArrayList<>();
        for (CoEvolution coevolution : coevos) {
            coevoSimp.add(basifyCoevo(coevolution, true, value.spec.srcSpec.repository));
        }
        new ChunckedUploadCoEvos(value.spec, coevoSimp);

        List<Map<String, Object>> eImpacts = new ArrayList<>();
        for (EImpact eImpact : value.getEImpacts()) {
            if (eImpact.getEvolutions().size() > 0) {
                eImpacts.add(basifyEImpacts(eImpact));
            }
        }
        new ChunckedUploadEImpacts(value.spec, eImpacts);
    }

    private Map<String, Object> basifyInitTests(ImmutablePair<Range, FailureReport> initialTest) {
        Map<String, Object> r = initialTest.left.toMap();
        FailureReport fr = initialTest.right;
        r.put("what", fr == null ? null : fr.what);
        r.put("where", fr == null ? null : fr.where);
        r.put("when", fr == null ? null : fr.when);
        return r;
    }

    private Map<String, Object> basifyEImpacts(EImpact eImpact) {
        Map<String, Object> r = new HashMap<>();
        List<Map<String, Object>> tests = new ArrayList<>();
        r.put("tests", tests);
        for (Entry<Range, ImmutablePair<Range, FailureReport>> t : eImpact.getTests().entrySet()) {
            Map<String, Object> test = t.getValue().left.toMap();
            tests.add(test);
            FailureReport fr = t.getValue().right;
            test.put("what", fr == null ? null : fr.what);
            test.put("where", fr == null ? null : fr.where);
            test.put("when", fr == null ? null : fr.when);
            List<Map<String, Object>> before = new ArrayList<>();
            test.put("before", before);
            if (t.getKey() != t.getValue().left) {
                before.add(t.getKey().toMap());
            }
        }
        List<String> evolutions_url = new ArrayList<>();
        List<Map<String, Object>> evolutions = new ArrayList<>();
        r.put("evolutions", evolutions);
        for (Entry<Evolution, ?> evo : eImpact.getEvolutions().entrySet()) {
            Map<String, Object> evolution = new HashMap<>();
            evolutions.add(evolution);
            Map<String, Object> tmp = evo.getKey().asMap();
            evolution.put("content", tmp);
            // TODO show fraction? for now it is always at 1
            evolutions_url.add((String) (((Map<String, Object>) tmp.get("content")).get("url")));
        }
        Map<String, Object> content = new HashMap<>();
        evolutions_url.sort((a, b) -> a.compareTo(b));
        content.put("evolutions", evolutions_url);
        r.put("content", content);

        return r;
    }

    private Map<String, Object> basifyCoevo(CoEvolution coevolution, boolean validated, String repository) { // TODO
                                                                                                             // refactor
        Map<String, Object> coevo = new HashMap<>();
        List<String> causes_url = new ArrayList<>();
        List<Map<String, Object>> pointed = new ArrayList<>();
        for (Evolution evolution : coevolution.getCauses()) {
            Map<String, Object> tmp = evolution.asMap();
            Map<String, Object> o = new HashMap<>();
            o.put("content", tmp);
            o.put("type", "cause");
            pointed.add(o);
            causes_url.add((String) (((Map<String, Object>) tmp.get("content")).get("url")));
        }
        List<String> resolutions_url = new ArrayList<>();
        for (Evolution evolution : coevolution.getResolutions()) {
            Map<String, Object> tmp = evolution.asMap();
            Map<String, Object> o = new HashMap<>();
            o.put("content", tmp);
            o.put("type", "resolution");
            pointed.add(o);
            resolutions_url.add((String) (((Map<String, Object>) tmp.get("content")).get("url")));
        }
        Map<String, Object> content = new HashMap<>();
        content.put("validated", validated);
        causes_url.sort((a, b) -> a.compareTo(b));
        content.put("causes", causes_url);
        resolutions_url.sort((a, b) -> a.compareTo(b));
        content.put("resolutions", resolutions_url);
        coevo.put("content", content);
        coevo.put("pointed", pointed);

        return coevo;
    }

    private final Driver driver;
    private ProjectHandler astHandler;
    private EvolutionHandler evoHandler;
    private SourcesHandler sourcesHandler;
    private ImpactHandler impactHandler;

    public Neo4jCoEvolutionsStorage(String uri, String user, String password) {
        driver = GraphDatabase.driver(uri, AuthTokens.basic(user, password));
    }

    public Neo4jCoEvolutionsStorage(SourcesHandler sourcesHandler, ProjectHandler astHandler,
            EvolutionHandler evoHandler, ImpactHandler impactHandler) {
        this(MyProperties.getPropValues().getProperty("neo4jAddress"),
                MyProperties.getPropValues().getProperty("neo4jId"),
                MyProperties.getPropValues().getProperty("neo4jPwd"));
        this.astHandler = astHandler;
        this.evoHandler = evoHandler;
        this.sourcesHandler = sourcesHandler;
        this.impactHandler = impactHandler;
    }

    @Override
    public CoEvolutions get(CoEvolutions.Specifier spec) {
        // TODO Auto-generated method stub
        return null;
    }

    // private static boolean compareEvolution(String repository, Value value,
    // Evolution other) {
    // if (other.getOriginal() instanceof Refactoring) {
    // return Evolution.makeEvoUrl(repository, (Evolution)
    // other).equals(value.get("url").asString());
    // }
    // return false;
    // }

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
     * Evolutions is on a single commit
     */
    @Override
    public void construct(CoEvolutions.Specifier spec) {
        // Evolutions evolutions = evoHandler.handle(spec.evoSpec);
        // Project astBefore = astHandler.handle(astHandler.buildSpec(spec.srcSpec, spec.evoSpec.commitIdBefore));
        // Project astAfter = astHandler.handle(astHandler.buildSpec(spec.srcSpec, spec.evoSpec.commitIdAfter));
        // Set<Range> startingTests = null;
        // final List<Map<String, Object>> list = startingTests.stream().map(x -> {
        //     Map<String, Object> r = new HashMap<>();
        //     r.put("repo", spec.srcSpec.repository);
        //     r.put("commitId", spec.evoSpec.commitIdBefore);
        //     r.put("path", x.getFile().getPath());
        //     r.put("start", x.getStart());
        //     r.put("end", x.getEnd());
        //     return r;
        // }).collect(Collectors.toList());
        // CoEvolutionsExtension r = new CoEvolutionsExtension(spec, evolutions, astBefore, astAfter);
        // try (Session session = driver.session()) {
        //     List<CoEvolution> done = session.readTransaction(new TransactionWork<List<CoEvolution>>() {
        //         @Override
        //         public List<CoEvolution> execute(Transaction tx) {
        //             Result queryResult = tx.run(getMinerCypher(), parameters("data", list));
        //             return queryResult.list(x -> {
        //                 Value beforeTestRaw = x.get("test");
        //                 Project.AST.FileSnapshot.Range testBefore = astBefore.getRange(
        //                         beforeTestRaw.get("path").asString(), beforeTestRaw.get("start").asInt(),
        //                         beforeTestRaw.get("end").asInt());

        //                 Set<Evolution> causes = new HashSet<>();
        //                 causes.addAll(getEvo(x.get("shortCall"), evolutions, astBefore, astAfter));
        //                 causes.addAll(getEvo(x.get("longCall"), evolutions, astBefore, astAfter));

        //                 Set<Evolution> evosLong = getEvo(x.get("long"), evolutions, astBefore, astAfter);
        //                 // for (Evolution aaa : evosLong) {
        //                 // if (aaa.getType().equals("Move Method")||aaa.getType().equals("Change
        //                 // Variable Type")||aaa.getType().equals("Rename Variable")) {
        //                 // tmp.TestAfter = aaa.getAfter().get(0).getTarget();
        //                 // }
        //                 // }
        //                 Set<Evolution> evosShort = getEvo(x.get("short"), evolutions, astBefore, astAfter);

        //                 Set<Evolution> evosShortAdjusted = getEvo(x.get("shortAdjusted"), evolutions, astBefore,
        //                         astAfter);

        //                 // for (Evolution aaa : evosShort) {
        //                 // if (aaa.getType().equals("Move Method")||aaa.getType().equals("Change
        //                 // Variable Type")||aaa.getType().equals("Rename Variable")) {
        //                 // tmp.TestAfter = aaa.getAfter().get(0).getTarget();
        //                 // }
        //                 // }

        //                 r.addCoevolution(causes, evosLong, evosShort, evosShortAdjusted,
        //                         Collections.singleton(testBefore), null);

        //                 return null;
        //             });
        //         }
        //     });
        // } catch (Exception e) {
        //     throw new RuntimeException(e);
        // }
    }

    // TODO use after ranges to make the match
    protected Set<Evolution> getEvo(Value value, Evolutions evolutions, Project<?> projBefore, Project<?> projAfter) {
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
                ImmutablePair<Range, String> descRange = getEvoAux(projBefore.commit.getRepository().getUrl(),
                        projBefore.commit.getId(), projBefore, rangeNode, relType);
                if (descRange != null)
                    before.add(descRange);
            }
            try {
                r.addAll(evolutions.getEvolution(evoType, projAfter, before, projAfter, Collections.emptyList()));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return r;
    }

    private ImmutablePair<Range, String> getEvoAux(String repoURL, String commitId, Project ast, Node curr,
            String type) {
        if (!repoURL.equals(curr.get("repo").asString()))
            return null;
        if (!commitId.equals(curr.get("commitId").asString()))
            return null;
        Range range = ast.getRange(curr.get("path").asString(), curr.get("start").asInt(), curr.get("end").asInt());
        return new ImmutablePair<Project.AST.FileSnapshot.Range, String>(range, type);
    }

    public static String getMinerCypher() {
        return Utils.memoizedReadResource("coevolution_miner.cql");
    }

    @Override
    public void close() throws Exception {
        driver.close();
    }
}