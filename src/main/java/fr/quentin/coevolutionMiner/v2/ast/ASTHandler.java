package fr.quentin.coevolutionMiner.v2.ast;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import fr.quentin.coevolutionMiner.v2.Data;
import fr.quentin.coevolutionMiner.v2.ast.Project;
import fr.quentin.coevolutionMiner.v2.ast.miners.SpoonMiner;
import fr.quentin.coevolutionMiner.v2.sources.Sources;
import fr.quentin.coevolutionMiner.v2.sources.SourcesHandler;

public class ASTHandler {

	private ASTHandler astHandler;
	private SourcesHandler srcHandler;

	private Map<Project.Specifier, Data<Project>> memoizedAST = new ConcurrentHashMap<>();

	public ASTHandler(SourcesHandler srcHandler) {
		this.srcHandler = srcHandler;
	}

	public Project.Specifier buildSpec(Sources.Specifier sources, String commitId) {
		return buildSpec(sources, commitId, "Spoon");
	}

	private Project.Specifier buildSpec(Sources.Specifier sources, String commitId, String miner) {
		return new Project.Specifier(sources, commitId, miner);
	}

	public Project handle(Project.Specifier spec, String miner) {
		Project res = null;
		memoizedAST.putIfAbsent(spec, new Data<>());
		Data<Project> tmp = memoizedAST.get(spec);
		tmp.lock.lock();
		try {
			res = tmp.get();
			if (res != null) {
				return res;
			}
			// CAUTION miners should mind about circular deps of data given by handlers
			switch (spec.miner) {
				case "Spoon":
					SpoonMiner minerInst = new SpoonMiner(spec, srcHandler);
					res = minerInst.compute();
					populate(res);
					break;
				default:
					throw new RuntimeException(spec.miner + " is not a registered AST miner.");
			}
			tmp.set(res);
			return res;
		} catch (Exception e) {
			throw e;
		} finally {
			tmp.lock.unlock();
		}
	}


	private void populate(Project evolutions) {
		for (Project x : evolutions.getModules()) {
			memoizedAST.putIfAbsent(x.spec, new Data<>());
			Data<Project> tmp = memoizedAST.get(x.spec);
			tmp.lock.lock();
			try {
				tmp.set(x);
			} finally {
				tmp.lock.unlock();
			}
		}
	}
}
