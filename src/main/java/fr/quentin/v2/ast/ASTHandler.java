package fr.quentin.v2.ast;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import fr.quentin.v2.Data;
import fr.quentin.v2.ast.AST;
import fr.quentin.v2.ast.miners.SpoonMiner;
import fr.quentin.v2.sources.Sources;
import fr.quentin.v2.sources.SourcesHandler;

public class ASTHandler {

	private ASTHandler astHandler;
	private SourcesHandler srcHandler;

	private Map<AST.Specifier, Data<AST>> memoizedAST = new ConcurrentHashMap<>();

	public ASTHandler(SourcesHandler srcHandler) {
		this.srcHandler = srcHandler;
	}

	public AST.Specifier buildSpec(Sources.Specifier sources, String commitId) {
		return buildSpec(sources, commitId, "Spoon");
	}

	private AST.Specifier buildSpec(Sources.Specifier sources, String commitId, String miner) {
		return new AST.Specifier(sources, commitId, miner);
	}

	public AST handle(AST.Specifier spec, String miner) {
		AST res = null;
		memoizedAST.putIfAbsent(spec, new Data<>());
		Data<AST> tmp = memoizedAST.get(spec);
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
					break;
				default:
					throw new RuntimeException(spec.miner + " is not a registered AST miner.");
			}

			tmp.set(res);
			return res;
		} finally {
			tmp.lock.unlock();
		}
	}

}
