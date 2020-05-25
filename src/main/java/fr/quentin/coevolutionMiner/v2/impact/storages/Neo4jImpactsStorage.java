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

import fr.quentin.coevolutionMiner.v2.impact.Impacts;
import fr.quentin.coevolutionMiner.v2.impact.ImpactsStorage;

public class Neo4jImpactsStorage implements ImpactsStorage {

    @Override
    public void put(Impacts impacts) {
        Map<String, Object> value = impacts.getValue();
        try (Session session = driver.session()) {
            String done = session.writeTransaction(new TransactionWork<String>() {
                @Override
                public String execute(Transaction tx) {
                    Result result = tx.run(getCypher(), value);
                    result.consume();
                    return "done impact";
                }
            });
            System.out.println(done);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private final Driver driver;

    public Neo4jImpactsStorage(String uri, String user, String password) {
        driver = GraphDatabase.driver(uri, AuthTokens.basic(user, password));
    }

    public Neo4jImpactsStorage() {
        this("bolt://localhost:7687", "neo4j", "neo4j");
    }

    @Override
    public Impacts get(Impacts.Specifier impacts_spec) {
        // TODO Auto-generated method stub
        // TODO need to add root cause to impacts to be able to retrieve them, 
        // maybe as a list in impacts
        return null;
    }

    private static String getCypher() {
        try {
            return new String(Files.readAllBytes(
                    Paths.get(Neo4jImpactsStorage.class.getClassLoader().getResource("impacts_cypher.sql").getFile())));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() throws Exception {
        driver.close();
    }
}