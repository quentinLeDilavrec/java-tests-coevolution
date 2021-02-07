package fr.quentin.coevolutionMiner.v2.evolution.storages;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Query;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.Transaction;
import org.neo4j.driver.TransactionConfig;
import org.neo4j.driver.TransactionWork;
import org.neo4j.driver.Value;
import org.neo4j.driver.exceptions.TransientException;

import fr.quentin.coevolutionMiner.utils.MyProperties;
import fr.quentin.coevolutionMiner.v2.ast.Project.AST.FileSnapshot.Range;
// import fr.quentin.impactMiner.Evolution;
import fr.quentin.coevolutionMiner.v2.evolution.Evolutions;
import fr.quentin.coevolutionMiner.v2.evolution.Evolutions.Evolution;
import fr.quentin.coevolutionMiner.v2.evolution.Evolutions.Specifier;
import fr.quentin.coevolutionMiner.v2.evolution.Evolutions.Evolution.DescRange;
import fr.quentin.coevolutionMiner.v2.sources.Sources;
import fr.quentin.coevolutionMiner.v2.sources.Sources.Commit;
import fr.quentin.coevolutionMiner.v2.utils.Utils;
import gr.uom.java.xmi.diff.CodeRange;
import fr.quentin.coevolutionMiner.v2.evolution.EvolutionsStorage;

import static org.neo4j.driver.Values.parameters;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class Neo4jEvolutionsStorage implements EvolutionsStorage {
    public static Logger logger = LogManager.getLogger();

    @Override
    public synchronized void put(Specifier evos_spec, Evolutions evolutions) {
        // List<Map<String, Object>> processed = evolutions.asListofMaps();
        List<Evolution> list = new ArrayList<Evolution>();
        evolutions.forEach(list::add);
        new ChunckedUploadEvos(evos_spec, list);
    }

    static final String CYPHER_RANGES_MERGE = Utils.memoizedReadResource("usingIds/ranges_merge.cql");
    static final String CYPHER_EVOLUTIONS_MATCH = Utils.memoizedReadResource("usingIds/evolutions_match.cql");
    static final String CYPHER_EVOLUTIONS_UPDATE = Utils.memoizedReadResource("usingIds/update.cql");
    static final String CYPHER_EVOLUTIONS_CREATE = Utils.memoizedReadResource("usingIds/evolutions_create.cql");

    class ChunckedUploadEvos extends Utils.ChunckedUpload<Evolution> {
        private final Specifier spec;
        private final Map<String, Object> tool;

        public ChunckedUploadEvos(Specifier spec, List<Evolution> processed) {
            super(driver, 10);
            this.spec = spec;
            tool = Utils.map("name", spec.miner.getSimpleName(), "version", 0);
            execute(logger, 512, processed);
        }

        @Override
        protected String put(Session session, List<Evolution> chunk, Logger logger) {
            Map<Range, Integer> idsByRange = Neo4jEvolutionsStorage.idsByRangeFromEvos(chunk);
            List<Map<String, Object>> formatedRanges = new ArrayList<>();
            Set<Range> keySet = new LinkedHashSet<>();
            for (Entry<Range, Integer> entry : idsByRange.entrySet()) {
                if (entry.getValue() == -1) {
                    Range r = entry.getKey();
                    keySet.add(r);
                    formatedRanges.add(Utils.formatRangeWithType(r));
                }
            }
            try (Transaction tx = session.beginTransaction(config);) {
                mergeAndGetRangeIds(idsByRange, formatedRanges, keySet, tx);

                List<Map<String, Object>> toMatch = new ArrayList<>();
                for (Evolution evo : chunk) {
                    toMatch.add(formatEvolutionWithRangesAsIds(idsByRange, evo));
                }

                List<Integer> toUpdate = new ArrayList<>();
                List<Map<String, Object>> toCreate = new ArrayList<>();
                matchEvolutions(tx, toMatch, toUpdate, toCreate);
                if (toUpdate.size() > 0) {
                    tx.run(CYPHER_EVOLUTIONS_UPDATE, parameters("data", toUpdate, "tool", tool)).consume();
                }
                if (toCreate.size() > 0) {
                    tx.run(CYPHER_EVOLUTIONS_CREATE, parameters("data", toCreate, "tool", tool)).consume();
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

        @Override
        protected String whatIsUploaded() {
            return "evolutions of " + spec.sources.repository;
        }

    }

    public static void matchEvolutions(Transaction tx, List<Map<String, Object>> toMatch, List<Integer> matched,
            List<Map<String, Object>> notMatched) {
        List<Integer> evolutionsId = tx.run(CYPHER_EVOLUTIONS_MATCH, parameters("data", toMatch))
                .list(x -> x.get("id", -1));
        // List<Evolution> toId = new ArrayList<>();
        for (int i = 0; i < evolutionsId.size(); i++) {
            Integer id = evolutionsId.get(i);
            Map<String, Object> formatedEvo = toMatch.get(i);
            if (id == -1) {
                notMatched.add(formatedEvo);
                // toId.add(chunk.get(i));
            } else {
                matched.add(id);
                // chunk.get(i).neo4jId = id;
            }
        }
    }

    private void putCommits(Specifier evos_spec, Evolutions value) {
        List<Map<String, Object>> commits = new ArrayList<>();
        try {
            for (Commit commit : value.getSources().getCommitsBetween(evos_spec.commitIdBefore,
                    evos_spec.commitIdAfter)) {
                Map<String, Object> o = new HashMap<>();
                o.put("repository", commit.getRepository().getUrl());
                o.put("sha1", commit.getId());
                o.put("children", commit.getChildrens().stream().map(x -> x.getId()).collect(Collectors.toList()));
                o.put("parents", commit.getParents().stream().map(x -> x.getId()).collect(Collectors.toList()));
            }
        } catch (Exception e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        }
        try (Session session = driver.session()) {
            String done = session.writeTransaction(new TransactionWork<String>() {
                @Override
                public String execute(Transaction tx) {
                    Result result2 = tx.run(getCommitCypher(), parameters("commits", commits));
                    result2.consume();
                    return "done evolution on " + value.spec.sources.repository;
                }
            });
            logger.info(done);
        } catch (TransientException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static Map<String, Object> formatEvolutionWithRangesAsIds(Map<Range, Integer> idsByRange, Evolution evo) {
        final Map<String, Object> res = new HashMap<>();
        res.put("type", evo.getType());

        final List<Map<String, Object>> before = new ArrayList<>();
        for (final DescRange descR : evo.getBefore()) {
            Range targetR = descR.getTarget();
            // final Map<String, Object> rDescR = Utils.formatRangeWithType(targetR);
            final Map<String, Object> rDescR = new HashMap<>();
            rDescR.put("description", descR.getDescription());
            Integer id = idsByRange.get(targetR);
            rDescR.put("id", id);
            before.add(rDescR);
        }
        res.put("before", before);

        final List<Map<String, Object>> after = new ArrayList<>();
        for (final DescRange descR : evo.getAfter()) {
            Range targetR = descR.getTarget();
            // final Map<String, Object> rDescR = Utils.formatRangeWithType(targetR);
            final Map<String, Object> rDescR = new HashMap<>();
            rDescR.put("description", descR.getDescription());
            Integer id = idsByRange.get(targetR);
            rDescR.put("id", id);
            after.add(rDescR);
        }
        res.put("after", after);

        return res;
    }

    @Override
    public Evolutions get(Specifier evo_spec) {
        // TODO Auto-generated method stub
        return null;
    }

    private final Driver driver;

    public Neo4jEvolutionsStorage(String uri, String user, String password) {
        driver = GraphDatabase.driver(uri, AuthTokens.basic(user, password));
        // driver.close(); // TODO should be closed at some point

    }

    public Neo4jEvolutionsStorage() {
        this(MyProperties.getPropValues().getProperty("neo4jAddress"),
                MyProperties.getPropValues().getProperty("neo4jId"),
                MyProperties.getPropValues().getProperty("neo4jPwd"));
    }

    protected String getCommitCypher() {
        return Utils.memoizedReadResource("commits_cypher.cql");
    }

    @Override
    public void close() throws Exception {
        driver.close();
    }

    public static Map<Range, Integer> idsByRangeFromEvos(Iterable<Evolution> evolutions) {
        Map<Range, Integer> idsByRange = new LinkedHashMap<>();
        for (Evolution evolution : evolutions) {
            for (DescRange dr : evolution.getBefore()) {
                Range target = dr.getTarget();
                idsByRange.put(target, -1); // target.neo4jId
            }
            for (DescRange dr : evolution.getAfter()) {
                Range target = dr.getTarget();
                idsByRange.put(target, -1); // target.neo4jId
            }
        }
        return idsByRange;
    }

    public static void mergeAndGetRangeIds(Map<Range, Integer> idsByRange, List<Map<String, Object>> formatedRanges,
            Set<Range> keySet, Transaction tx) {
        if (keySet.size() > 0) {
            List<Integer> rangesId = tx.run(CYPHER_RANGES_MERGE, parameters("data", formatedRanges))
                    .list(x -> x.get("id", -1));

            int i = 0;
            for (Range r : keySet) {
                idsByRange.put(r, rangesId.get(i));
                i++;
            }
        }
    }
}