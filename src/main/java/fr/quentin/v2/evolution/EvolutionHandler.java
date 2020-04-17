package fr.quentin.v2.evolution;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import fr.quentin.v2.Data;
import fr.quentin.v2.ast.ASTHandler;
import fr.quentin.v2.sources.Sources;
import fr.quentin.v2.sources.SourcesHandler;
import fr.quentin.v2.evolution.miners.RefactoringMiner;
import fr.quentin.v2.evolution.storages.Neo4jEvolutionsStorage;

public class EvolutionHandler {

	private Neo4jEvolutionsStorage neo4jStore;
	private ASTHandler astHandler;
	private SourcesHandler srcHandler;

	private Map<Evolutions.Specifier, Data<Evolutions>> memoizedEvolutions = new ConcurrentHashMap<>();

	public EvolutionHandler(SourcesHandler srcHandler, ASTHandler astHandler) {
		this.neo4jStore = new Neo4jEvolutionsStorage();
		this.srcHandler = srcHandler;
		this.astHandler = astHandler;
	}

	public Evolutions.Specifier buildSpec(Sources.Specifier sources, String commitIdBefore, String commitIdAfter) {
		return buildSpec(sources, commitIdBefore, commitIdAfter, "RefactoringMiner");
	}

	public Evolutions.Specifier buildSpec(Sources.Specifier sources, String commitIdBefore, String commitIdAfter,
			String miner) {
		return new Evolutions.Specifier(sources, commitIdBefore, commitIdAfter, miner);
	}

	public Evolutions handle(Evolutions.Specifier spec) {
		return handle(spec, "Neo4j");
	}

	private Evolutions handle(Evolutions.Specifier spec, String storeName) {
		Evolutions res = null;
		memoizedEvolutions.putIfAbsent(spec, new Data<>());
		Data<Evolutions> tmp = memoizedEvolutions.get(spec);
		tmp.lock.lock();
		try {
			res = tmp.get();
			if (res != null) {
				return res;
			}
			EvolutionsStorage db = null;
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
				case "RefactoringMiner":
					RefactoringMiner minerInst = new RefactoringMiner(spec, srcHandler, astHandler);
					res = minerInst.compute();
					break;
				default:
					throw new RuntimeException(spec.miner + " is not a registered Evolutions miner.");
			}
			if (db != null) {
				db.put(spec, res);
			}
			tmp.set(res);
			return res;
		} finally {
			tmp.lock.unlock();
		}
	}

}
