package fr.quentin.coevolutionMiner.v2.sources;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import fr.quentin.coevolutionMiner.utils.SourcesHelper;
import fr.quentin.coevolutionMiner.v2.Data;
import fr.quentin.coevolutionMiner.v2.sources.miners.JgitMiner;
import fr.quentin.coevolutionMiner.v2.sources.storages.Neo4jSourcesStorage;

public class SourcesHandler implements AutoCloseable {
    private Neo4jSourcesStorage neo4jStore;

    private Map<Sources.Specifier, Data<Sources>> memoizedSources = new ConcurrentHashMap<>();

    public SourcesHandler() {
        this.neo4jStore = new Neo4jSourcesStorage();
    }

    public Sources.Specifier buildSpec(String repository) {
        return buildSpec(repository, "JGit");
    }

    private Sources.Specifier buildSpec(String repository, String miner) {
        return new Sources.Specifier(repository, miner);
    }

    public Sources.Specifier buildSpec(String repository, Integer stars) {
        return new Sources.Specifier(repository, "JGit", stars);
    }

    public Neo4jSourcesStorage getNeo4jStore() {
        return neo4jStore;
    }

    public Sources handle(Sources.Specifier spec) {
        return handle(spec, "Neo4j");
    }

    public Sources handle(Sources.Specifier spec, String storeName) {
        Sources res = null;
        memoizedSources.putIfAbsent(spec, new Data<>());
        Data<Sources> tmp = memoizedSources.get(spec);
        tmp.lock.lock();

        try {
            res = tmp.get();
            if (res != null) {
                return res;
            }
            SourcesStorage db = null;
            switch (storeName) {
                case "Neo4j":
                default:
                    db = neo4jStore;
                    res = db.get(spec);
                    break;
            }

            if (res != null) {
                tmp.set(res);
                return res;
            }

            // CAUTION miners should mind about circular deps of data given by handlers
            SourcesMiner minerInst = minerBuilder(spec);
            res = minerInst.compute();

            if (db != null) {
                db.put(res);
            }
            tmp.set(res);
            return res;
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            tmp.lock.unlock();
        }
    }

    public static final SourcesMiner minerBuilder(Sources.Specifier spec) {
        SourcesMiner minerInst;
        switch (spec.miner) {
            case "JGit":
                minerInst = new JgitMiner(spec);
                break;
            default:
                throw new RuntimeException(spec.miner + " is not a registered Sources miner.");
        }
        return minerInst;
    }

    @Override
    public void close() throws Exception {
        neo4jStore.close();
    }
}