package fr.quentin.coevolutionMiner.v2.impact.storages;

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
import fr.quentin.coevolutionMiner.v2.evolution.EvolutionHandler;
import fr.quentin.coevolutionMiner.v2.impact.Impacts;
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
        List<Map<String, Object>> processed = impacts.asListofMaps();
        new ChunckedUploadEvos(impacts.spec, processed);
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
            return Utils.memoizedReadResource("impacts_cypher.sql");
        }

        @Override
        public Value format(Collection<Map<String, Object>> chunk) {
            return parameters("json", chunk, "tool", spec.miner + 2);
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
}