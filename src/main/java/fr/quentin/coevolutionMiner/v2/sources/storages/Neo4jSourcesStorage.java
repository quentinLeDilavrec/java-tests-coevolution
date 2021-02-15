package fr.quentin.coevolutionMiner.v2.sources.storages;

import static org.neo4j.driver.Values.parameters;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.Transaction;
import org.neo4j.driver.TransactionWork;
import org.neo4j.driver.Value;
import org.neo4j.driver.exceptions.TransientException;

import fr.quentin.coevolutionMiner.utils.MyProperties;
import fr.quentin.coevolutionMiner.v2.dependency.Dependencies;
import fr.quentin.coevolutionMiner.v2.sources.Sources;
import fr.quentin.coevolutionMiner.v2.sources.SourcesHandler;
import fr.quentin.coevolutionMiner.v2.sources.SourcesMiner;
import fr.quentin.coevolutionMiner.v2.sources.SourcesStorage;
import fr.quentin.coevolutionMiner.v2.sources.Sources.Commit;
import fr.quentin.coevolutionMiner.v2.sources.Sources.Repository;
import fr.quentin.coevolutionMiner.v2.sources.miners.JgitMiner;
import fr.quentin.coevolutionMiner.v2.utils.Utils;

public class Neo4jSourcesStorage implements SourcesStorage {
    public static Logger logger = LogManager.getLogger();

    static final String CYPHER_REPOSITORY_MERGE = Utils.memoizedReadResource("usingIds/repository_merge.cql");

    @Override
    public void put(Sources sources) {
        Map<String, Object> value = new HashMap<>();
        Repository adfa = sources.getRepository();
        value.put("repository", adfa.getUrl());
        value.put("releases", adfa.getReleases());
        try (Session session = driver.session()) {
            String done = session.writeTransaction(new TransactionWork<String>() {
                @Override
                public String execute(Transaction tx) {
                    Result result = tx.run(CYPHER_REPOSITORY_MERGE, value);
                    result.consume();
                    return "repository of " + sources.spec.repository + " 1/1";
                }
            });
            logger.info(done);
        } catch (TransientException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
        sources.onCommitsUpdate(this);
        sources.uploadCommits();
    }

    @Override
    public Sources get(Sources.Specifier spec) {
        return null;
        // Map<String, Object> value = new HashMap<>();
        // value.put("url", spec.repository);
        // SourcesMiner inst = SourcesHandler.minerBuilder(spec);
        // Sources res = inst.compute();
        // try (Session session = driver.session()) {
        //     List<String> releases = session.readTransaction(new TransactionWork<List<String>>() {
        //         @Override
        //         public List<String> execute(Transaction tx) {
        //             Result result = tx.run(getMiner(), value);
        //             return result.list(x -> x.get("releases").asString());
        //         }
        //     });
        //     res.getRepository().addReleases(releases);
        // } catch (TransientException e) {
        //     e.printStackTrace();
        // } catch (Exception e) {
        //     e.printStackTrace();
        // }
        // return res;
    }

    static final String CYPHER_COMMITS_MERGE = Utils.memoizedReadResource("usingIds/commits_merge.cql");

    class ChunckedUploadCommits extends Utils.SimpleChunckedUpload<Map<String, Object>> {
        private final Sources.Specifier spec;

        public ChunckedUploadCommits(Sources.Specifier spec, List<Map<String, Object>> processed) {
            super(driver, 10);
            this.spec = spec;
            execute(logger, 256, processed);
        }

        @Override
        protected String getCypher() {
            return CYPHER_COMMITS_MERGE;
        }

        @Override
        public Value format(Collection<Map<String, Object>> chunk) {
            return parameters("data", chunk);
        }

        @Override
        protected String whatIsUploaded() {
            return "commits of " + spec.repository;
        }

    }

    @Override
    public void putUpdatedCommits(Set<Commit> commits) {
        List<Map<String, Object>> fcommits = new ArrayList<>();
        Sources.Specifier spec = null;
        for (Commit commit : commits) {
            spec = commit.getRepository().getEnclosingInstance().spec;
            Map<String, Object> o = new HashMap<>();
            o.put("repository", commit.getRepository().getUrl());
            o.put("commitId", commit.getId());
            o.put("parents", commit.getParents().stream().map(x -> x.getId()).collect(Collectors.toList()));
        }
        new ChunckedUploadCommits(spec, fcommits);
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