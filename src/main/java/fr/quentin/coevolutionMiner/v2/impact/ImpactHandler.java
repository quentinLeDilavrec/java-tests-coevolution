package fr.quentin.coevolutionMiner.v2.impact;

import fr.quentin.coevolutionMiner.v2.evolution.EvolutionHandler;
import fr.quentin.coevolutionMiner.v2.evolution.Evolutions;
import fr.quentin.coevolutionMiner.v2.impact.miners.MyImpactsMiner;
import fr.quentin.coevolutionMiner.v2.impact.storages.Neo4jImpactsStorage;
import fr.quentin.coevolutionMiner.v2.sources.SourcesHandler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import fr.quentin.coevolutionMiner.v2.Data;
import fr.quentin.coevolutionMiner.v2.ast.AST;
import fr.quentin.coevolutionMiner.v2.ast.ASTHandler;

public class ImpactHandler implements AutoCloseable {

    private Neo4jImpactsStorage neo4jStore;
    private ASTHandler astHandler;
    private EvolutionHandler evoHandler;

    private Map<Impacts.Specifier, Data<Impacts>> memoizedImpacts = new ConcurrentHashMap<>();
    private SourcesHandler sourcesHandler;

    public ImpactHandler(SourcesHandler sourcesHandler, ASTHandler astHandler, EvolutionHandler evoHandler) {
        this.neo4jStore = new Neo4jImpactsStorage();
        this.astHandler = astHandler;
        this.evoHandler = evoHandler;
        this.sourcesHandler = sourcesHandler;
    }

    public Impacts.Specifier buildSpec(AST.Specifier ast_id, Evolutions.Specifier evo_id) {
        return buildSpec(ast_id, evo_id, "myMiner");
    }

    public Impacts.Specifier buildSpec(AST.Specifier ast_id, Evolutions.Specifier evo_id, String miner) {
        return new Impacts.Specifier(ast_id, evo_id, miner);
    }

    public Impacts handle(Impacts.Specifier spec) {
        return handle(spec, "Neo4j");
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
            ImpactsStorage db = null;
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
                    MyImpactsMiner minerInst = new MyImpactsMiner(spec, astHandler, evoHandler);
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