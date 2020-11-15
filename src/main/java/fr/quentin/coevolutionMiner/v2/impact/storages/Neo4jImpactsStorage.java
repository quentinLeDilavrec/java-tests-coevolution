package fr.quentin.coevolutionMiner.v2.impact.storages;

import static org.neo4j.driver.Values.parameters;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.Transaction;
import org.neo4j.driver.TransactionConfig;
import org.neo4j.driver.TransactionWork;
import org.neo4j.driver.exceptions.TransientException;

import fr.quentin.coevolutionMiner.utils.MyProperties;
import fr.quentin.coevolutionMiner.v2.ast.ProjectHandler;
import fr.quentin.coevolutionMiner.v2.evolution.EvolutionHandler;
import fr.quentin.coevolutionMiner.v2.impact.Impacts;
import fr.quentin.coevolutionMiner.v2.impact.Impacts.Specifier;
import fr.quentin.coevolutionMiner.v2.utils.Utils;
import fr.quentin.coevolutionMiner.v2.impact.ImpactsStorage;

public class Neo4jImpactsStorage implements ImpactsStorage {
    static Logger logger = Logger.getLogger(Neo4jImpactsStorage.class.getName());
    static final int STEP = 512;
    static final int TIMEOUT = 10;

    @Override
    public void put(Impacts impacts) {
        Map<String, Object> value = impacts.asMap();
        List tmp = (List) value.get("json");
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
                                "tool", value.get("tool"), "rangesToType", value.get("rangesToType")));
                        result.consume();
                        success[0] = true;
                        return "uploaded impacts chunk of " + impacts.spec.evoSpec.sources.repository + ": " + (findex + fstep) + "/" + tmp.size();
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
                    "took too long to upload impacts of " + impacts.spec.evoSpec.sources.repository + " with a chunk of size " + step);
                step = step / 2;
            }
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
}