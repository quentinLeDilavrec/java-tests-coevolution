package fr.quentin.coevolutionMiner.v2.coevolution.storages;

import static org.neo4j.driver.Values.parameters;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.Transaction;
import org.neo4j.driver.Value;
import org.neo4j.driver.exceptions.TransientException;
import org.neo4j.driver.types.Node;
import org.neo4j.driver.types.Path.Segment;
import org.neo4j.driver.types.Relationship;

import fr.quentin.coevolutionMiner.utils.MyProperties;
import fr.quentin.coevolutionMiner.v2.ast.Project;
import fr.quentin.coevolutionMiner.v2.ast.Project.AST.FileSnapshot.Range;
import fr.quentin.coevolutionMiner.v2.ast.ProjectHandler;
import fr.quentin.coevolutionMiner.v2.ast.RangeMatchingException;
import fr.quentin.coevolutionMiner.v2.coevolution.CoEvolutions;
import fr.quentin.coevolutionMiner.v2.coevolution.CoEvolutions.CoEvolution;
import fr.quentin.coevolutionMiner.v2.coevolution.CoEvolutions.Specifier;
import fr.quentin.coevolutionMiner.v2.coevolution.CoEvolutionsStorage;
import fr.quentin.coevolutionMiner.v2.coevolution.miners.EImpact;
import fr.quentin.coevolutionMiner.v2.coevolution.miners.EImpact.FailureReport;
import fr.quentin.coevolutionMiner.v2.coevolution.miners.EImpact.ImpactedRange;
import fr.quentin.coevolutionMiner.v2.dependency.DependencyHandler;
import fr.quentin.coevolutionMiner.v2.evolution.EvolutionHandler;
import fr.quentin.coevolutionMiner.v2.evolution.Evolutions;
import fr.quentin.coevolutionMiner.v2.evolution.Evolutions.Evolution;
import fr.quentin.coevolutionMiner.v2.evolution.storages.Neo4jEvolutionsStorage;
import fr.quentin.coevolutionMiner.v2.sources.SourcesHandler;
import fr.quentin.coevolutionMiner.v2.utils.Utils;

public class Neo4jCoEvolutionsStorage implements CoEvolutionsStorage {
    public static Logger logger = LogManager.getLogger();
    private static final boolean NO_UPDATE = true;

    static final String CYPHER_EVOLUTIONS_MATCH = Utils.memoizedReadResource("usingIds/evolutions_match.cql");
    static final String CYPHER_COEVOLUTIONS_MATCH = Utils.memoizedReadResource("usingIds/coevolutions_match.cql");
    static final String CYPHER_COEVOLUTIONS_UPDATE = Utils.memoizedReadResource("usingIds/update.cql");
    static final String CYPHER_COEVOLUTIONS_CREATE = Utils.memoizedReadResource("usingIds/coevolutions_create.cql");

    class ChunckedUploadCoEvos extends Utils.ChunckedUpload<CoEvolution> {
        private final Specifier spec;
        private final Map<String, Object> tool;

        public ChunckedUploadCoEvos(Specifier spec, List<CoEvolution> processed) {
            super(driver, 10);
            this.spec = spec;
            tool = Utils.map("name", spec.miner.getSimpleName(), "version", 0);
            execute(logger, 256, processed);
        }

        @Override
        protected String whatIsUploaded() {
            return "coevolutions of " + spec.srcSpec.repository;
        }

        @Override
        protected String put(Session session, List<CoEvolution> chunk, Logger logger) {
            Map<Evolution, Integer> idsByEvo = new LinkedHashMap<>();
            for (CoEvolution coEvolution : chunk) {
                for (Evolution e : coEvolution.getCauses()) {
                    idsByEvo.put(e, -1);
                }
                for (Evolution e : coEvolution.getResolutions()) {
                    idsByEvo.put(e, -1);
                }
            }
            Set<Evolution> keySet = new LinkedHashSet<>();
            for (Entry<Evolution, Integer> entry : idsByEvo.entrySet()) {
                if (entry.getValue() == -1) {
                    Evolution e = entry.getKey();
                    keySet.add(e);
                }
            }
            try (Transaction tx = session.beginTransaction(config);) {

                if (keySet.size() > 0) {
                    Map<Range, Integer> idsByRange = Neo4jEvolutionsStorage.idsByRangeFromEvos(keySet);
                    List<Map<String, Object>> formatedRanges2 = new ArrayList<>();
                    Set<Range> keySetRForEvos = new LinkedHashSet<>();
                    for (Entry<Range, Integer> entry : idsByRange.entrySet()) {
                        if (entry.getValue() == -1) {
                            Range r = entry.getKey();
                            keySetRForEvos.add(r);
                            formatedRanges2.add(r.toMap());
                        }
                    }
                    matchAndGetRangeIds(idsByRange, formatedRanges2, keySetRForEvos, tx);

                    List<Map<String, Object>> evoToMatch = new ArrayList<>();
                    for (Evolution evo : keySet) {
                        evoToMatch.add(Neo4jEvolutionsStorage.formatEvolutionWithRangesAsIds(idsByRange, evo));
                    }

                    List<Integer> matchedEvoIds = new ArrayList<>();
                    Neo4jEvolutionsStorage.matchExistingEvolutions(tx, evoToMatch, matchedEvoIds);

                    int i = 0;
                    for (Evolution r : keySet) {
                        idsByEvo.put(r, (Integer) matchedEvoIds.get(i));
                        i++;
                    }
                }

                List<Map<String, Object>> toMatch = new ArrayList<>();
                for (CoEvolution coevo : chunk) {
                    toMatch.add(formatCoEvolutionWithEvolutionsAsIds(idsByEvo, coevo));
                }

                Result matchResult = tx.run(CYPHER_COEVOLUTIONS_MATCH, parameters("data", toMatch));
                List<Integer> coevolutionsId = matchResult.list(x -> x.get("id", -1));

                List<Map<String, Object>> toCreate = new ArrayList<>();
                if (NO_UPDATE) {
                    for (int i = 0; i < coevolutionsId.size(); i++) {
                        Integer id = coevolutionsId.get(i);
                        Map<String, Object> formatedCoEvo = toMatch.get(i);
                        if (id == -1) {
                            toCreate.add(formatedCoEvo);
                        }
                    }
                } else {
                    List<Map<String, Object>> toUpdate = new ArrayList<>();
                    for (int i = 0; i < coevolutionsId.size(); i++) {
                        Integer id = coevolutionsId.get(i);
                        Map<String, Object> formatedCoEvo = toMatch.get(i);
                        if (id == -1) {
                            toCreate.add(formatedCoEvo);
                        } else {
                            toUpdate.add(Utils.map("id", id));
                        }
                    }
                    if (toUpdate.size() > 0) {
                        tx.run(CYPHER_COEVOLUTIONS_UPDATE, parameters("data", toUpdate, "tool", tool)).consume();
                    }
                }
                if (toCreate.size() > 0) {
                    tx.run(CYPHER_COEVOLUTIONS_CREATE, parameters("data", toCreate, "tool", tool)).consume();
                }
                tx.commit();
                return whatIsUploaded();
            } catch (TransientException e) {
                logger.error(whatIsUploaded() + " could not be uploaded", e);
                return null;
            } catch (Exception e) {
                logger.error(whatIsUploaded() + " could not be uploaded", e);
                return null;
            }
        }

    }

    static final String CYPHER_RANGES_MERGE = Utils.memoizedReadResource("usingIds/ranges_merge.cql");
    static final String CYPHER_RANGES_MATCH = Utils.memoizedReadResource("usingIds/ranges_match.cql");
    static final String CYPHER_IMPACTS_MATCH = Utils.memoizedReadResource("usingIds/impacts_match.cql");
    static final String CYPHER_IMPACTS_UPDATE = Utils.memoizedReadResource("usingIds/update.cql");
    static final String CYPHER_IMPACTS_CREATE = Utils.memoizedReadResource("usingIds/impacts_create.cql");

    class ChunckedUploadImpacts extends Utils.ChunckedUpload<EImpact> {
        private final Specifier spec;
        private final Map<String, Object> tool;

        public ChunckedUploadImpacts(Specifier spec, List<EImpact> processed) {
            super(driver, 10);
            this.spec = spec;
            tool = Utils.map("name", spec.miner.getSimpleName(), "version", 0);
            execute(logger, 128, processed);
        }

        @Override
        protected String whatIsUploaded() {
            return "impacts of " + spec.srcSpec.repository;
        }

        @Override
        protected String put(Session session, List<EImpact> chunk, Logger logger) {
            Map<Evolution, Integer> idsByEvo = new LinkedHashMap<>();
            Map<Range, Integer> idsByTest = new LinkedHashMap<>();
            for (EImpact eimpact : chunk) {
                for (Evolution e : eimpact.getEvolutions()) {
                    idsByEvo.put(e, -1);
                }
                for (Range entry : eimpact.getSharedTests()) {
                    idsByTest.put(entry, -1);
                    idsByTest.put(eimpact.getSharingTest(entry).range, -1);
                }
            }
            Set<Evolution> evoKeySet = new LinkedHashSet<>();
            for (Entry<Evolution, Integer> entry : idsByEvo.entrySet()) {
                if (entry.getValue() == -1) {
                    Evolution e = entry.getKey();
                    evoKeySet.add(e);
                }
            }
            List<Map<String, Object>> formatedTests = new ArrayList<>();
            Set<Range> keySet = new LinkedHashSet<>();
            for (Entry<Range, Integer> entry : idsByTest.entrySet()) {
                if (entry.getValue() == -1) {
                    Range r = entry.getKey();
                    keySet.add(r);
                    formatedTests.add(Utils.formatRangeWithType(r));
                }
            }
            try (Transaction tx = session.beginTransaction(config);) {

                Neo4jEvolutionsStorage.mergeAndGetRangeIds(idsByTest, formatedTests, keySet, tx);

                if (evoKeySet.size() > 0) {
                    Map<Range, Integer> idsByRange = Neo4jEvolutionsStorage.idsByRangeFromEvos(evoKeySet);
                    List<Map<String, Object>> formatedRanges2 = new ArrayList<>();
                    Set<Range> keySetRForEvos = new LinkedHashSet<>();
                    for (Entry<Range, Integer> entry : idsByRange.entrySet()) {
                        if (entry.getValue() == -1) {
                            Range r = entry.getKey();
                            keySetRForEvos.add(r);
                            formatedRanges2.add(r.toMap());
                        }
                    }
                    matchAndGetRangeIds(idsByRange, formatedRanges2, keySetRForEvos, tx);

                    List<Map<String, Object>> evoToMatch = new ArrayList<>();
                    for (Evolution evo : evoKeySet) {
                        evoToMatch.add(Neo4jEvolutionsStorage.formatEvolutionWithRangesAsIds(idsByRange, evo));
                    }

                    List<Integer> matchedEvoIds = new ArrayList<>();
                    Neo4jEvolutionsStorage.matchExistingEvolutions(tx, evoToMatch, matchedEvoIds);

                    int i = 0;
                    for (Evolution r : evoKeySet) {
                        idsByEvo.put(r, (Integer) matchedEvoIds.get(i));
                        i++;
                    }
                }

                List<Map<String, Object>> toMatch = new ArrayList<>();
                Set<Map<String, Object>> toMatchS = new HashSet<>();

                for (EImpact imp : chunk) {
                    Map<String, Object> f = formatImpactWithRangesAndEvolutionsAsIds(idsByTest, idsByEvo, imp);
                    if (!toMatchS.contains(f)) {
                        toMatch.add(f);
                    }
                }

                Result matchResult = tx.run(CYPHER_IMPACTS_MATCH, parameters("data", toMatch));
                List<Integer> impactssId = matchResult.list(x -> x.get("id", -1));

                List<Map<String, Object>> toCreate = new ArrayList<>();
                if (NO_UPDATE) {
                    for (int i = 0; i < impactssId.size(); i++) {
                        Integer id = impactssId.get(i);
                        Map<String, Object> formatedCoEvo = toMatch.get(i);
                        if (id == -1) {
                            toCreate.add(formatedCoEvo);
                        }
                    }
                } else {
                    List<Map<String, Object>> toUpdate = new ArrayList<>();
                    for (int i = 0; i < impactssId.size(); i++) {
                        Integer id = impactssId.get(i);
                        Map<String, Object> formatedCoEvo = toMatch.get(i);
                        if (id == -1) {
                            toCreate.add(formatedCoEvo);
                        } else {
                            toUpdate.add(Utils.map("id", id));
                        }
                    }
                    if (toUpdate.size() > 0) {
                        tx.run(CYPHER_IMPACTS_UPDATE, parameters("data", toUpdate, "tool", tool)).consume();
                    }
                }

                if (toCreate.size() > 0) {
                    tx.run(CYPHER_IMPACTS_CREATE, parameters("data", toCreate, "tool", tool)).consume();
                }
                tx.commit();
                return whatIsUploaded();
            } catch (TransientException e) {
                logger.error(whatIsUploaded() + " could not be uploaded", e);
                return null;
            } catch (Exception e) {
                logger.error(whatIsUploaded() + " could not be uploaded", e);
                return null;
            }
        }

    }

    static final String CYPHER_INITTEST_MERGE = Utils.memoizedReadResource("usingIds/initTest_merge.cql");

    class ChunckedUploadInitTests extends Utils.SimpleChunckedUpload<Map<String, Object>> {
        private final Specifier spec;

        public ChunckedUploadInitTests(Specifier spec, List<Map<String, Object>> processed) {
            super(driver, 10);
            this.spec = spec;
            execute(logger, 256, processed);
        }

        @Override
        protected String getCypher() {
            return CYPHER_INITTEST_MERGE;
        }

        @Override
        public Value format(Collection<Map<String, Object>> chunk) {
            return parameters("data", chunk);
        }

        @Override
        protected String whatIsUploaded() {
            return "initial tests of " + spec.srcSpec.repository;
        }

    }

    @Override
    public synchronized void put(CoEvolutions value) {
        putInitTests(value);

        Set<CoEvolution> coevos = value.getCoEvolutions();
        new ChunckedUploadCoEvos(value.spec, new ArrayList<>(coevos));

        // List<Map<String, Object>> eImpacts = new ArrayList<>();
        // for (EImpact eImpact : value.getEImpacts()) {
        //     if (eImpact.getEvolutions().size() > 0) {
        //         eImpacts.add(basifyEImpacts(eImpact));
        //     }
        // }
        new ChunckedUploadImpacts(value.spec, new ArrayList<>(value.getEImpacts()));
    }

    public void putInitTests(CoEvolutions value) {
        List<Map<String, Object>> initTests = new ArrayList<>();
        for (ImpactedRange initTest : value.getInitialTests()) {
            initTests.add(basifyInitTests(initTest));
        }
        new ChunckedUploadInitTests(value.spec, initTests);
    }

    static Map<String, Object> basifyInitTests(ImpactedRange initialTest) {
        Map<String, Object> r = Utils.formatRangeWithType(initialTest.range);
        Map<String, Object> report = basifyReport(initialTest.report);
        r.put("report", report);
        return r;
    }

    static Map<String, Object> basifyReport(FailureReport fr) {
        Map<String, Object> report = new HashMap<>();
        if (fr != null) {
            report.put("what", fr.what);
            report.put("where", fr.where);
            report.put("when", fr.when);
        }
        return report;
    }

    private final Driver driver;

    public Neo4jCoEvolutionsStorage(Driver driver) {
        this.driver = driver;
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
        Range range;
        try {
            range = ast.getRange(curr.get("path").asString(), curr.get("start").asInt(), curr.get("end").asInt());
        } catch (RangeMatchingException e) {
            logger.fatal("", e);
            return null;
        }
        return new ImmutablePair<Project.AST.FileSnapshot.Range, String>(range, type);
    }

    public static Map<String, Object> formatCoEvolutionWithEvolutionsAsIds(Map<Evolution, Integer> idsByEvo,
            CoEvolution coevo) {
        final Map<String, Object> res = new HashMap<>();

        res.put("hash", coevo.hashCode());

        // TODO compute a type
        final List<Map<String, Object>> causes = new ArrayList<>();
        for (final Evolution cause : coevo.getCauses()) {
            final Map<String, Object> dr = new HashMap<>();
            // TODO compute a description
            Integer id = idsByEvo.get(cause);
            dr.put("id", id);
            causes.add(dr);
        }
        res.put("causes", causes);

        final List<Map<String, Object>> resolutions = new ArrayList<>();
        for (final Evolution resolution : coevo.getResolutions()) {
            final Map<String, Object> dr = new HashMap<>();
            // TODO compute a description
            Integer id = idsByEvo.get(resolution);
            dr.put("id", id);
            resolutions.add(dr);
        }
        res.put("resolutions", resolutions);

        return res;
    }

    public static Map<String, Object> formatImpactWithRangesAndEvolutionsAsIds(Map<Range, Integer> idsByRange,
            Map<Evolution, Integer> idsByEvo, EImpact imp) {
        Map<String, Object> res = new HashMap<>();
        res.put("hash", imp.hashCode());

        List<Map<String, Object>> testsSame = new ArrayList<>();
        res.put("testsSame", testsSame);
        List<Map<String, Object>> testsChanged = new ArrayList<>();
        res.put("testsChanged", testsChanged);
        for (Range t : imp.getSharedTests()) {
            final Map<String, Object> test = new HashMap<>();
            EImpact.ImpactedRange impacted = imp.getSharingTest(t);
            Integer id = idsByRange.get(impacted.range);
            test.put("id", id);
            if (t != impacted.range) {
                testsChanged.add(test);
                test.put("before", idsByRange.get(t));
            } else {
                testsSame.add(test);
            }
            Map<String, Object> report = basifyReport(impacted.report);
            test.put("report", report);
        }
        List<Map<String, Object>> evolutions = new ArrayList<>();
        res.put("evolutions", evolutions);
        for (Evolution evo : imp.getEvolutions()) {
            Integer id = idsByEvo.get(evo);
            evolutions.add(Utils.map("id", id));
        }
        return res;
    }

    public static void matchAndGetRangeIds(Map<Range, Integer> idsByRange, List<Map<String, Object>> formatedRanges,
            Set<Range> keySet, Transaction tx) {
        if (keySet.size() > 0) {
            List<Integer> rangesId = tx.run(CYPHER_RANGES_MATCH, parameters("data", formatedRanges))
                    .list(x -> x.get("id", -1));

            int i = 0;
            for (Range r : keySet) {
                if (rangesId.get(i) == -1) {
                    throw new RuntimeException("all needed ranges should already be there");
                }
                idsByRange.put(r, rangesId.get(i));
                i++;
            }
        }
    }

}