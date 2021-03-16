package fr.quentin.coevolutionMiner.v2.dependency;

import fr.quentin.coevolutionMiner.v2.evolution.EvolutionHandler;
import fr.quentin.coevolutionMiner.v2.evolution.Evolutions;
import fr.quentin.coevolutionMiner.v2.sources.SourcesHandler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.neo4j.driver.Driver;

import fr.quentin.coevolutionMiner.v2.Data;
import fr.quentin.coevolutionMiner.v2.ast.Project;
import fr.quentin.coevolutionMiner.v2.ast.ProjectHandler;
import fr.quentin.coevolutionMiner.v2.dependency.miners.MyDependenciesMiner;
import fr.quentin.coevolutionMiner.v2.dependency.storages.Neo4jDependenciesStorage;

public class DependencyHandler implements AutoCloseable {

    private Neo4jDependenciesStorage neo4jStore;
    private ProjectHandler astHandler;
    private EvolutionHandler evoHandler;

    private Map<Dependencies.Specifier, Data<Dependencies>> memoizedImpacts = new ConcurrentHashMap<>();
    private SourcesHandler sourcesHandler;

    public DependencyHandler(Driver neo4jDriver, SourcesHandler sourcesHandler, ProjectHandler astHandler, EvolutionHandler evoHandler) {
        this.neo4jStore = new Neo4jDependenciesStorage(neo4jDriver);
        this.astHandler = astHandler;
        this.evoHandler = evoHandler;
        this.sourcesHandler = sourcesHandler;
    }

    public Dependencies.Specifier buildSpec(Project.Specifier ast_id, Evolutions.Specifier evo_id) {
        return buildSpec(ast_id, evo_id, "myMiner");
    }

    public Dependencies.Specifier buildSpec(Project.Specifier ast_id, Evolutions.Specifier evo_id, String miner) {
        return new Dependencies.Specifier(ast_id, evo_id, miner);
    }

    public Dependencies handle(Dependencies.Specifier spec) {
        return handle(spec, "");
    }

    private Dependencies handle(Dependencies.Specifier spec, String storeName) {
        Dependencies res = null;
        memoizedImpacts.putIfAbsent(spec, new Data<>());
        Data<Dependencies> tmp = memoizedImpacts.get(spec);
        tmp.lock.lock();
        try {
            res = tmp.get();
            if (res != null) {
                return res;
            }
            DependenciesStorage db = neo4jStore;
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
            DependenciesMiner minerInst = minerBuilder(spec, astHandler, evoHandler);

            res = minerInst.compute();
            populate((MyDependenciesMiner.ImpactsExtension) res);

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

    public static final DependenciesMiner minerBuilder(Dependencies.Specifier spec, ProjectHandler astHandler,
            EvolutionHandler evoHandler) {
        DependenciesMiner minerInst;
        switch (spec.miner) {
            case "myMiner":
                minerInst = new MyDependenciesMiner(spec, astHandler, evoHandler);
                break;
            default:
                throw new RuntimeException(spec.miner + " is not a registered impacts miner.");
        }
        return minerInst;
    }

    private void populate(MyDependenciesMiner.ImpactsExtension impacts) {
        for (MyDependenciesMiner.ImpactsExtension x : impacts.getModules()) {
            memoizedImpacts.putIfAbsent(x.spec, new Data<>());
            Data<Dependencies> tmp = memoizedImpacts.get(x.spec);
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
    }
}