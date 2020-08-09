package fr.quentin.coevolutionMiner.v2.ast;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import fr.quentin.coevolutionMiner.v2.Data;
import fr.quentin.coevolutionMiner.v2.ast.Project;
import fr.quentin.coevolutionMiner.v2.ast.miners.SpoonMiner;
import fr.quentin.coevolutionMiner.v2.ast.storages.Neo4jProjectStorage;
import fr.quentin.coevolutionMiner.v2.sources.Sources;
import fr.quentin.coevolutionMiner.v2.sources.SourcesHandler;

public class ProjectHandler {

	private ProjectHandler astHandler;
	private SourcesHandler srcHandler;

	private Map<Project.Specifier<?>, Data<Project<?>>> memoizedAST = new ConcurrentHashMap<>();
	private Neo4jProjectStorage neo4jStore;

	public ProjectHandler(SourcesHandler srcHandler) {
        this.neo4jStore = new Neo4jProjectStorage();
		this.srcHandler = srcHandler;
	}

	public Project.Specifier<SpoonMiner> buildSpec(Sources.Specifier sources, String commitId, Boolean isRelease) {
		return buildSpec(sources, commitId, isRelease, SpoonMiner.class);
	}
	public Project.Specifier<SpoonMiner> buildSpec(Sources.Specifier sources, String commitId) {
		return buildSpec(sources, commitId, null, SpoonMiner.class);
	}

	private <U,T extends ProjectMiner<U>> Project.Specifier<T> buildSpec(Sources.Specifier sources, String commitId, Boolean isRelease, Class<T> miner) {
		return new Project.Specifier<>(sources, commitId, isRelease, miner);
	}

	private <U,T extends ProjectMiner<U>> Project.Specifier<T> buildSpec(Sources.Specifier sources, String commitId, Class<T> miner) {
		return new Project.Specifier<>(sources, commitId, null, miner);
	}

	enum Miners {
		SpoonMiner
	}

	public static String storeName = "Neo4j";

	public <U,T extends ProjectMiner<U>> Project<U> handle(Project.Specifier<T> spec) {
		Project<U> res = null;
		memoizedAST.putIfAbsent(spec, new Data<>());
		Data<Project<?>> tmp = memoizedAST.get(spec);
		tmp.lock.lock();
		try {
			res = (Project<U>) tmp.get();
			if (res != null) {
				return res;
			}
            ProjectStorage db = null;
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
			Miners z = Miners.valueOf(spec.miner.getSimpleName());
			switch (z) {
				case SpoonMiner:
					SpoonMiner minerInst = new SpoonMiner(spec, srcHandler);
					res = (Project<U>) minerInst.compute();
					populate(res);
					break;
				default:
					throw new RuntimeException(spec.miner + " is not a registered AST miner.");
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


	private void populate(Project<?> evolutions) {
		for (Project<?> x : evolutions.getModules()) {
			memoizedAST.putIfAbsent(x.spec, new Data<>());
			Data<Project<?>> tmp = memoizedAST.get(x.spec);
			tmp.lock.lock();
			try {
				tmp.set(x);
			} finally {
				tmp.lock.unlock();
			}
		}
	}
}
