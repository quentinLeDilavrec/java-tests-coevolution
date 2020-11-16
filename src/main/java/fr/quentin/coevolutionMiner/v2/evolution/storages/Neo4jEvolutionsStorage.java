package fr.quentin.coevolutionMiner.v2.evolution.storages;

import org.apache.felix.resolver.util.ArrayMap;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Query;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.Transaction;
import org.neo4j.driver.TransactionConfig;
import org.neo4j.driver.TransactionWork;
import org.neo4j.driver.exceptions.TransientException;

import fr.quentin.coevolutionMiner.utils.MyProperties;
// import fr.quentin.impactMiner.Evolution;
import fr.quentin.coevolutionMiner.v2.evolution.Evolutions;
import fr.quentin.coevolutionMiner.v2.evolution.Evolutions.Evolution;
import fr.quentin.coevolutionMiner.v2.evolution.Evolutions.Specifier;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.logging.Logger;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class Neo4jEvolutionsStorage implements EvolutionsStorage {
    static Logger logger = Logger.getLogger(Neo4jEvolutionsStorage.class.getName());
    static final int STEP = 512;
    static final int TIMEOUT = 10;

    @Override
    public void put(Specifier evos_spec, Evolutions evolutions) {
        List<Map<String, Object>> tmp = evolutions.asListofMaps();
        int index = 0;
        int step = STEP;
        boolean[] success = new boolean[] { false };
        while (index < tmp.size()) {
            final int findex = index;
            final int fstep = step;
            success[0] = false;

            long start = System.nanoTime();
            try (Session session = driver.session()) {
                String done = session.writeTransaction(new TransactionWork<String>() {
                    @Override
                    public String execute(Transaction tx) {
                        Result result = tx.run(getCypher(), parameters("json", tmp.subList(findex, Math.min(findex + fstep, tmp.size())),
                                "tool", evos_spec.miner.getSimpleName() + 3));
                        result.consume();
                        success[0] = true;
                        return "uploaded evolutions chunk of " + evolutions.spec.sources.repository + ": " + Math.min(findex + fstep, tmp.size()) + "/" + tmp.size();
                    }
                }, TransactionConfig.builder().withTimeout(Duration.ofMinutes(TIMEOUT)).build());
                logger.info(done);
            } catch (TransientException e) {
                e.printStackTrace();
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (success[0]) {
                index += step;
                if (((System.nanoTime() - start) / 1000000 / 60 < (TIMEOUT/2))) {
                    step = step * 2;
                }
            } else {
                logger.info(
                    "took too long to upload evolutions of " + evolutions.spec.sources.repository + " with a chunk of size " + step);
                step = step / 2;
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
            System.out.println(done);
        } catch (TransientException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public Evolutions get(Specifier impacts_spec) {
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

    private static String getCypher() {
        return Utils.memoizedReadResource("evolutions_cypher.sql");
    }

    protected String getCommitCypher() {
        return Utils.memoizedReadResource("commits_cypher.cql");
    }

    @Override
    public void close() throws Exception {
        driver.close();
    }
}