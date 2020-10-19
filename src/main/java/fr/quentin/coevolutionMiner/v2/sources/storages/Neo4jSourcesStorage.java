package fr.quentin.coevolutionMiner.v2.sources.storages;

import static org.neo4j.driver.Values.parameters;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.Transaction;
import org.neo4j.driver.TransactionWork;
import org.neo4j.driver.exceptions.TransientException;

import fr.quentin.coevolutionMiner.utils.MyProperties;
import fr.quentin.coevolutionMiner.v2.impact.Impacts;
import fr.quentin.coevolutionMiner.v2.sources.Sources;
import fr.quentin.coevolutionMiner.v2.sources.SourcesHandler;
import fr.quentin.coevolutionMiner.v2.sources.SourcesMiner;
import fr.quentin.coevolutionMiner.v2.sources.SourcesStorage;
import fr.quentin.coevolutionMiner.v2.sources.Sources.Repository;
import fr.quentin.coevolutionMiner.v2.sources.miners.JgitMiner;
import fr.quentin.coevolutionMiner.v2.utils.Utils;

public class Neo4jSourcesStorage implements SourcesStorage {

    @Override
    public void put(Sources sources) {
        Map<String, Object> value = new HashMap<>();
        Repository adfa = sources.getRepository();
        value.put("url", adfa.getUrl());
        value.put("releases", adfa.getReleases());
        try (Session session = driver.session()) {
            String done = session.writeTransaction(new TransactionWork<String>() {
                @Override
                public String execute(Transaction tx) {
                    Result result = tx.run(getCypher(), value);
                    result.consume();
                    return "done pushing sources metadata of " + sources.spec.repository;
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
    public Sources get(Sources.Specifier spec) {
        Map<String, Object> value = new HashMap<>();
        value.put("url", spec.repository);
        SourcesMiner inst = SourcesHandler.minerBuilder(spec);
        Sources res = inst.compute();
        try (Session session = driver.session()) {
            List<String> releases = session.readTransaction(new TransactionWork<List<String>>() {
                @Override
                public List<String> execute(Transaction tx) {
                    Result result = tx.run(getMiner(), value);
                    return result.list(x -> x.get("releases").asString());
                }
            });
            res.getRepository().addReleases(releases);
        } catch (TransientException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return res;
    }

    private static String getCypher() {
        return Utils.memoizedReadResource("jgit_cypher.cql");
    }

    private static String getMiner() {
        return Utils.memoizedReadResource("jgit_miner.cql");
    }

    public final Driver driver;

    public Neo4jSourcesStorage(String uri, String user, String password) {
        driver = GraphDatabase.driver(uri, AuthTokens.basic(user, password));
    }

    public Neo4jSourcesStorage() {
        this(MyProperties.getPropValues().getProperty("neo4jAddress"),
                MyProperties.getPropValues().getProperty("neo4jId"),
                MyProperties.getPropValues().getProperty("neo4jPwd"));
    }

    @Override
    public void close() throws Exception {
        driver.close();
    }
}