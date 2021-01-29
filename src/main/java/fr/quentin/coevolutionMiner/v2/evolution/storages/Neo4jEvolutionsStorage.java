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
import java.util.Collection;
import java.util.HashMap;
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
    public void put(Specifier evos_spec, Evolutions evolutions) {
        List<Map<String, Object>> processed = evolutions.asListofMaps();
        new ChunckedUploadEvos(evos_spec, processed);
    }

    class ChunckedUploadEvos extends Utils.ChunckedUpload<Map<String, Object>> {
        private final Specifier spec;

        public ChunckedUploadEvos(Specifier spec, List<Map<String, Object>> processed) {
            super(driver, 10);
            this.spec = spec;
            execute(logger, 512, processed);
        }

        @Override
        protected String getCypher() {
            return Utils.memoizedReadResource("evolutions_cypher.sql");
        }

        @Override
        public Value format(Collection<Map<String, Object>> chunk) {
            return parameters("json", chunk, "tool", spec.miner.getSimpleName() + 3);
        }

        @Override
        protected String whatIsUploaded() {
            return "evolutions of " + spec.sources.repository;
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

    protected String getCommitCypher() {
        return Utils.memoizedReadResource("commits_cypher.cql");
    }

    @Override
    public void close() throws Exception {
        driver.close();
    }
}