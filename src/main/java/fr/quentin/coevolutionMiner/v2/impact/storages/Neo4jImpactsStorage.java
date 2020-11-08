package fr.quentin.coevolutionMiner.v2.impact.storages;

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
import fr.quentin.coevolutionMiner.v2.ast.ProjectHandler;
import fr.quentin.coevolutionMiner.v2.evolution.EvolutionHandler;
import fr.quentin.coevolutionMiner.v2.impact.Impacts;
import fr.quentin.coevolutionMiner.v2.impact.Impacts.Specifier;
import fr.quentin.coevolutionMiner.v2.utils.Utils;
import fr.quentin.coevolutionMiner.v2.impact.ImpactsStorage;

public class Neo4jImpactsStorage implements ImpactsStorage {

    @Override
    public void put(Impacts impacts) {
        Map<String, Object> value = impacts.asMap();
        try (Session session = driver.session()) {
            String done = session.writeTransaction(new TransactionWork<String>() {
                @Override
                public String execute(Transaction tx) {
                    Result result = tx.run(getCypher(), value);
                    result.consume();
                    return "done impact on " + impacts.spec.evoSpec.sources.repository;
                }
            });
            System.out.println(done);
        } catch (TransientException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
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