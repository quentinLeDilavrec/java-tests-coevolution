package fr.quentin.coevolutionMiner.v2.impact.storages;

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
import org.neo4j.driver.internal.DriverFactory;

import fr.quentin.coevolutionMiner.utils.MyProperties;
import fr.quentin.coevolutionMiner.v2.ast.ProjectHandler;
import fr.quentin.coevolutionMiner.v2.ast.Project.AST.FileSnapshot.Range;
import fr.quentin.coevolutionMiner.v2.evolution.EvolutionHandler;
import fr.quentin.coevolutionMiner.v2.evolution.storages.Neo4jEvolutionsStorage;
import fr.quentin.coevolutionMiner.v2.impact.Impacts;
import fr.quentin.coevolutionMiner.v2.impact.Impacts.Impact;
import fr.quentin.coevolutionMiner.v2.impact.Impacts.Specifier;
import fr.quentin.coevolutionMiner.v2.utils.Utils;
import spoon.support.util.internal.MapUtils;
import fr.quentin.coevolutionMiner.v2.impact.ImpactsStorage;

public class Neo4jImpactsStorage implements ImpactsStorage {
    static Logger logger = LogManager.getLogger();
    static final int STEP = 512;
    static final int TIMEOUT = 10;

    @Override
    public void put(Impacts impacts) {
        // List<Map<String, Object>> processed = impacts.asListofMaps();
        List<Impact> list = new ArrayList<Impact>();
        impacts.forEach(list::add);
        new ChunckedUploadEvos(impacts.spec, list);
    }

    static final String CYPHER_RANGES_MERGE = Utils.memoizedReadResource("usingIds/ranges_merge.cql");
    static final String CYPHER_DEPENDENCY_MATCH = Utils.memoizedReadResource("usingIds/dependency_match.cql");
    static final String CYPHER_DEPENDENCY_UPDATE = Utils.memoizedReadResource("usingIds/update.cql");
    static final String CYPHER_DEPENDENCY_CREATE = Utils.memoizedReadResource("usingIds/dependency_create.cql");

    class ChunckedUploadEvos extends Utils.ChunckedUpload<Impact> {
        private final Specifier spec;
        private final Map<String, Object> tool;

        public ChunckedUploadEvos(Specifier spec, List<Impact> processed) {
            super(driver, 10);
            this.spec = spec;
            tool = Utils.map("name", spec.miner, "version", 0);
            execute(logger, 512, processed);
        }

        @Override
        protected String put(Session session, List<Impact> chunk, Logger logger) {
            Map<Range, Integer> idsByRange = idsByRangeFromDependency(chunk);
            List<Map<String, Object>> formatedRanges = new ArrayList<>();
            Set<Range> keySet = new LinkedHashSet<>();
            for (Entry<Range, Integer> entry : idsByRange.entrySet()) {
                if (entry.getValue() == -1) {
                    Range r = entry.getKey();
                    keySet.add(r);
                    formatedRanges.add(r.toMap());
                }
            }
            try (Transaction tx = session.beginTransaction(config);) {
                Neo4jEvolutionsStorage.mergeAndGetRangeIds(idsByRange, formatedRanges, keySet, tx);

                List<Map<String, Object>> toMatch = new ArrayList<>();
                for (Impact evo : chunk) {
                    toMatch.add(formatImpactWithRangesAsIds(idsByRange, evo));
                }

                List<Integer> toUpdate = new ArrayList<>();
                List<Map<String, Object>> toCreate = new ArrayList<>();
                List<Integer> evolutionsId = tx.run(CYPHER_DEPENDENCY_MATCH, parameters("data", toMatch))
                        .list(x1 -> x1.get("id", -1));
                for (int i = 0; i < evolutionsId.size(); i++) {
                    Integer id = evolutionsId.get(i);
                    Map<String, Object> formatedDep = toMatch.get(i);
                    if (id == -1) {
                        toCreate.add(formatedDep);
                    } else {
                        toUpdate.add(id);
                    }
                }
                if (toUpdate.size() > 0) {
                    tx.run(CYPHER_DEPENDENCY_UPDATE, parameters("data", toUpdate, "tool", tool)).consume();
                }
                if (toCreate.size() > 0) {
                    tx.run(CYPHER_DEPENDENCY_CREATE, parameters("data", toCreate, "tool", tool)).consume();
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
            return "impacts of " + spec.evoSpec.sources.repository;
        }

    }

    private final Driver driver;

    public Neo4jImpactsStorage(String uri, String user, String password) {
        driver = GraphDatabase.driver(uri, AuthTokens.basic(user, password));
    }

    public Neo4jImpactsStorage() {
        this(MyProperties.getPropValues().getProperty("neo4jAddress"),
                MyProperties.getPropValues().getProperty("neo4jId"),
                MyProperties.getPropValues().getProperty("neo4jPwd"));
    }

    @Override
    public Impacts get(Specifier impacts_spec, ProjectHandler astHandler, EvolutionHandler evoHandler) {
        // TODO Auto-generated method stub
        // TODO need to add root cause to impacts to be able to retrieve them,
        // maybe as a list in impacts
        return null;
    }

    private static String getCypher() {
        return Utils.memoizedReadResource("impacts_cypher.sql");
    }

    @Override
    public void close() throws Exception {
        driver.close();
    }

    public static Map<Range, Integer> idsByRangeFromDependency(List<Impact> chunk) {
        Map<Range, Integer> idsByRange = new LinkedHashMap<>();
        for (Impact evolution : chunk) {
            for (Impact.DescRange dr : evolution.getCauses()) {
                Range target = dr.getTarget();
                idsByRange.put(target, -1); // target.neo4jId
            }
            for (Impact.DescRange dr : evolution.getEffects()) {
                Range target = dr.getTarget();
                idsByRange.put(target, -1); // target.neo4jId
            }
        }
        return idsByRange;
    }

    public static Map<String, Object> formatImpactWithRangesAsIds(Map<Range, Integer> idsByRange, Impact evo) {
        final Map<String, Object> res = new HashMap<>();
        res.put("type", evo.getType());

        final List<Map<String, Object>> before = new ArrayList<>();
        for (final Impact.DescRange descR : evo.getCauses()) {
            Range targetR = descR.getTarget();
            // final Map<String, Object> rDescR = Utils.formatRangeWithType(targetR);
            final Map<String, Object> rDescR = new HashMap<>();
            rDescR.put("description", descR.getDescription());
            Integer id = idsByRange.get(targetR);
            rDescR.put("id", id);
            before.add(rDescR);
        }
        res.put("sources", before);

        final List<Map<String, Object>> after = new ArrayList<>();
        for (final Impact.DescRange descR : evo.getEffects()) {
            Range targetR = descR.getTarget();
            // final Map<String, Object> rDescR = Utils.formatRangeWithType(targetR);
            final Map<String, Object> rDescR = new HashMap<>();
            rDescR.put("description", descR.getDescription());
            Integer id = idsByRange.get(targetR);
            rDescR.put("id", id);
            after.add(rDescR);
        }
        res.put("targets", after);

        return res;
    }
}