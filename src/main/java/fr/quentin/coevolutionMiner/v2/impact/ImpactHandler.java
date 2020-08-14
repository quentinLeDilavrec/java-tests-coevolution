package fr.quentin.coevolutionMiner.v2.impact;

import fr.quentin.coevolutionMiner.v2.evolution.EvolutionHandler;
import fr.quentin.coevolutionMiner.v2.evolution.Evolutions;
import fr.quentin.coevolutionMiner.v2.impact.miners.MyImpactsMiner;
import fr.quentin.coevolutionMiner.v2.impact.storages.Neo4jImpactsStorage;
import fr.quentin.coevolutionMiner.v2.sources.SourcesHandler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import fr.quentin.coevolutionMiner.v2.Data;
import fr.quentin.coevolutionMiner.v2.ast.Project;
import fr.quentin.coevolutionMiner.v2.ast.ProjectHandler;

public class ImpactHandler implements AutoCloseable {

    private Neo4jImpactsStorage neo4jStore;
    private ProjectHandler astHandler;
    private EvolutionHandler evoHandler;

    private Map<Impacts.Specifier, Data<Impacts>> memoizedImpacts = new ConcurrentHashMap<>();
    private SourcesHandler sourcesHandler;

    public ImpactHandler(SourcesHandler sourcesHandler, ProjectHandler astHandler, EvolutionHandler evoHandler) {
        this.neo4jStore = new Neo4jImpactsStorage();
        this.astHandler = astHandler;
        this.evoHandler = evoHandler;
        this.sourcesHandler = sourcesHandler;
    }

    public Impacts.Specifier buildSpec(Project.Specifier ast_id, Evolutions.Specifier evo_id) {
        return buildSpec(ast_id, evo_id, "myMiner");
    }

    public Impacts.Specifier buildSpec(Project.Specifier ast_id, Evolutions.Specifier evo_id, String miner) {
        return new Impacts.Specifier(ast_id, evo_id, miner);
    }

    public Impacts handle(Impacts.Specifier spec) {
        return handle(spec, "");
    }

    private Impacts handle(Impacts.Specifier spec, String storeName) {
        Impacts res = null;
        memoizedImpacts.putIfAbsent(spec, new Data<>());
        Data<Impacts> tmp = memoizedImpacts.get(spec);
        tmp.lock.lock();
        try {
            res = tmp.get();
            if (res != null) {
                return res;
            }
            ImpactsStorage db = neo4jStore;
            switch (storeName) {
                case "Neo4j":
                    db = neo4jStore;
                    res = db.get(spec, astHandler, evoHandler);
                    break;
                default:
                    break;
            }

            if (res != null) {
                tmp.set(res);
                return res;
            }

            // CAUTION miners should mind about circular deps of data given by handlers
            ImpactsMiner minerInst = minerBuilder(spec, astHandler, evoHandler);

            res = minerInst.compute();
            populate((MyImpactsMiner.ImpactsExtension) res);

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

    public static final ImpactsMiner minerBuilder(Impacts.Specifier spec, ProjectHandler astHandler,
            EvolutionHandler evoHandler) {
        ImpactsMiner minerInst;
        switch (spec.miner) {
            case "myMiner":
                minerInst = new MyImpactsMiner(spec, astHandler, evoHandler);
                break;
            default:
                throw new RuntimeException(spec.miner + " is not a registered impacts miner.");
        }
        return minerInst;
    }

    private void populate(MyImpactsMiner.ImpactsExtension impacts) {
        for (MyImpactsMiner.ImpactsExtension x : impacts.getModules()) {
            memoizedImpacts.putIfAbsent(x.spec, new Data<>());
            Data<Impacts> tmp = memoizedImpacts.get(x.spec);
            tmp.lock.lock();
            try {
                tmp.set(x);
            } finally {
                tmp.lock.unlock();
            }
            populate(x);
        }
    }

    @Override
    public void close() throws Exception {
        neo4jStore.close();
    }
}