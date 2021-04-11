package fr.quentin.coevolutionMiner.v2.coevolution;

import fr.quentin.coevolutionMiner.v2.evolution.EvolutionHandler;
import fr.quentin.coevolutionMiner.v2.evolution.Evolutions;
import fr.quentin.coevolutionMiner.v2.coevolution.miners.MultiCoEvolutionsMiner;
import fr.quentin.coevolutionMiner.v2.coevolution.miners.MyCoEvolutionsMiner;
import fr.quentin.coevolutionMiner.v2.coevolution.storages.Neo4jCoEvolutionsStorage;
import fr.quentin.coevolutionMiner.v2.dependency.DependencyHandler;
import fr.quentin.coevolutionMiner.v2.sources.Sources;
import fr.quentin.coevolutionMiner.v2.sources.SourcesHandler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.neo4j.driver.Driver;

import fr.quentin.coevolutionMiner.v2.Data;
import fr.quentin.coevolutionMiner.v2.ast.ProjectHandler;

public class CoEvolutionHandler implements AutoCloseable {

    private Neo4jCoEvolutionsStorage neo4jStore;
    private ProjectHandler astHandler;
    private EvolutionHandler evoHandler;

    private Map<CoEvolutions.Specifier, Data<CoEvolutions>> memoizedImpacts = new ConcurrentHashMap<>();
    private SourcesHandler sourcesHandler;
    private DependencyHandler impactHandler;

    public CoEvolutionHandler(Driver neo4jDriver, SourcesHandler sourcesHandler, ProjectHandler astHandler, EvolutionHandler evoHandler,
            DependencyHandler impactHandler) {
        this.astHandler = astHandler;
        this.evoHandler = evoHandler;
        this.sourcesHandler = sourcesHandler;
        this.impactHandler = impactHandler;
        this.neo4jStore = new Neo4jCoEvolutionsStorage(neo4jDriver);
    }

    public static CoEvolutions.Specifier buildSpec(Sources.Specifier src_id, Evolutions.Specifier evo_id) {
        return buildSpec(src_id, evo_id, MultiCoEvolutionsMiner.class);
    }

    public static CoEvolutions.Specifier buildSpec(Sources.Specifier src_id, Evolutions.Specifier evo_id,
            Class<? extends CoEvolutionsMiner> miner) {
        return new CoEvolutions.Specifier(src_id, evo_id, miner);
    }

    enum Miners {
        MultiCoEvolutionsMiner, MyCoEvolutionsMiner
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

            Miners z = Miners.valueOf(spec.miner.getSimpleName());
            // CAUTION miners should mind about circular deps of data given by handlers
            switch (z) {
                case MyCoEvolutionsMiner: {
                    MyCoEvolutionsMiner minerInst = new MyCoEvolutionsMiner(spec, sourcesHandler, astHandler,
                            evoHandler, impactHandler);
                    res = minerInst.compute();
                    if (db != null) {
                        db.put(res);
                    }
                    break;
                }
                case MultiCoEvolutionsMiner: {
                    MultiCoEvolutionsMiner minerInst = new MultiCoEvolutionsMiner(spec, sourcesHandler, astHandler,
                            evoHandler, impactHandler, this);
                    res = minerInst.compute();
                    // if (db != null) { // TODO maybe use it later for spreaded coevolutions
                    //     db.put(res);
                    // }
                    break;
                }
                default:
                    throw new RuntimeException(spec.miner + " is not a registered impacts miner.");
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
    }
}