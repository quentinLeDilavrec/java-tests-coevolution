package fr.quentin.coevolutionMiner.v2.evolution;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.Gson;
import com.google.gson.JsonElement;

import org.refactoringminer.api.Refactoring;

import fr.quentin.impactMiner.Evolution;
import fr.quentin.coevolutionMiner.v2.Data;
import fr.quentin.coevolutionMiner.v2.ast.ProjectHandler;
import fr.quentin.coevolutionMiner.v2.coevolution.miners.MyCoEvolutionsMiner;
import fr.quentin.coevolutionMiner.v2.sources.Sources;
import fr.quentin.coevolutionMiner.v2.sources.SourcesHandler;
import fr.quentin.coevolutionMiner.v2.evolution.miners.RefactoringMiner;
import fr.quentin.coevolutionMiner.v2.evolution.storages.Neo4jEvolutionsStorage;
import fr.quentin.coevolutionMiner.v2.impact.ImpactsStorage;

public class EvolutionHandler implements AutoCloseable {

	private Neo4jEvolutionsStorage neo4jStore;
	private ProjectHandler astHandler;
	private SourcesHandler srcHandler;

	private Map<Evolutions.Specifier, Data<Evolutions>> memoizedEvolutions = new ConcurrentHashMap<>();

	public EvolutionHandler(SourcesHandler srcHandler, ProjectHandler astHandler) {
		this.neo4jStore = new Neo4jEvolutionsStorage();
		this.srcHandler = srcHandler;
		this.astHandler = astHandler;
	}

	public static Evolutions.Specifier buildSpec(Sources.Specifier sources, String commitIdBefore,
			String commitIdAfter) {
		return buildSpec(sources, commitIdBefore, commitIdAfter, "RefactoringMiner");
	}

	public static Evolutions.Specifier buildSpec(Sources.Specifier sources, String commitIdBefore, String commitIdAfter,
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
					populate(res);
					break;
				default:
					throw new RuntimeException(spec.miner + " is not a registered Evolutions miner.");
			}
			if (db != null) {
				db.put(spec, res);
			}
			tmp.set(res);
			return res;
		} catch (Exception e) {
			throw e;
		} finally {
			tmp.lock.unlock();
		}
	}

	private void populate(Evolutions evolutions) {
		for (Evolutions x : evolutions.perBeforeCommit()) {
			memoizedEvolutions.putIfAbsent(x.spec, new Data<>());
			Data<Evolutions> tmp = memoizedEvolutions.get(x.spec);
			tmp.lock.lock();
			try {
				tmp.set(x);
			} finally {
				tmp.lock.unlock();
			}
		}
	}

	@Override
	public void close() throws Exception {
		neo4jStore.close();
	}

}
