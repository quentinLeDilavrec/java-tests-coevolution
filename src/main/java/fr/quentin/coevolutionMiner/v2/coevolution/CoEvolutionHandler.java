package fr.quentin.coevolutionMiner.v2.coevolution;

import fr.quentin.coevolutionMiner.v2.evolution.EvolutionHandler;
import fr.quentin.coevolutionMiner.v2.evolution.Evolutions;
import fr.quentin.coevolutionMiner.v2.impact.ImpactHandler;
import fr.quentin.coevolutionMiner.v2.coevolution.miners.MyCoEvolutionsMiner;
import fr.quentin.coevolutionMiner.v2.coevolution.storages.Neo4jCoEvolutionsStorage;
import fr.quentin.coevolutionMiner.v2.sources.Sources;
import fr.quentin.coevolutionMiner.v2.sources.SourcesHandler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import fr.quentin.coevolutionMiner.v2.Data;
import fr.quentin.coevolutionMiner.v2.ast.ProjectHandler;

public class CoEvolutionHandler implements AutoCloseable {

    private Neo4jCoEvolutionsStorage neo4jStore;
    private ProjectHandler astHandler;
    private EvolutionHandler evoHandler;

    private Map<CoEvolutions.Specifier, Data<CoEvolutions>> memoizedImpacts = new ConcurrentHashMap<>();
    private SourcesHandler sourcesHandler;
    private ImpactHandler impactHandler;

    public CoEvolutionHandler(SourcesHandler sourcesHandler, ProjectHandler astHandler, EvolutionHandler evoHandler, ImpactHandler impactHandler) {
        this.astHandler = astHandler;
        this.evoHandler = evoHandler;
        this.sourcesHandler = sourcesHandler;
        this.impactHandler = impactHandler;
        this.neo4jStore = new Neo4jCoEvolutionsStorage(sourcesHandler,astHandler,evoHandler,impactHandler);
    }

    public static CoEvolutions.Specifier buildSpec(Sources.Specifier src_id, Evolutions.Specifier evo_id) {
        return buildSpec(src_id, evo_id, "myMiner");
    }

    public static CoEvolutions.Specifier buildSpec(Sources.Specifier src_id, Evolutions.Specifier evo_id, String miner) {
        return new CoEvolutions.Specifier(src_id, evo_id, miner);
    }

    public CoEvolutions handle(CoEvolutions.Specifier spec) {
        return handle(spec, "Neo4j");
    }

    private CoEvolutions handle(CoEvolutions.Specifier spec, String storeName) {
        CoEvolutions res = null;
        memoizedImpacts.putIfAbsent(spec, new Data<>());
        Data<CoEvolutions> tmp = memoizedImpacts.get(spec);
        tmp.lock.lock();
        try {
            res = tmp.get();
            if (res != null) {
                return res;
            }
            CoEvolutionsStorage db = null;
            switch (storeName) {
                case "Neo4j":
                    res = neo4jStore.get(spec);
                    db = neo4jStore;
                    break;
                default:
                    break;
            }
            if (res != null) {
                tmp.set(res);
                return res;
            }

            // CAUTION miners should mind about circular deps of data given by handlers
            switch (spec.miner) {
                case "myMiner":
                    MyCoEvolutionsMiner minerInst = new MyCoEvolutionsMiner(spec, sourcesHandler, astHandler, evoHandler, impactHandler, (CoEvolutionsStorage) neo4jStore);
                    res = minerInst.compute();
                    break;
                default:
                    throw new RuntimeException(spec.miner + " is not a registered impacts miner.");
            }
            if (db != null) {
                db.put(res);
            }
            tmp.set(res);
            return res;
        } catch (Exception e) {
			throw e;
		} finally {
            tmp.lock.unlock();
        }
    }

    @Override
    public void close() throws Exception {
        neo4jStore.close();
    }
}