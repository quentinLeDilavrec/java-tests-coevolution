package fr.quentin.coevolutionMiner.v2.utils;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.github.gumtreediff.tree.ITree;
import com.github.gumtreediff.tree.Version;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.logging.log4j.Logger;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.Transaction;
import org.neo4j.driver.TransactionConfig;
import org.neo4j.driver.TransactionWork;
import org.neo4j.driver.Value;
import org.neo4j.driver.exceptions.TransientException;

import fr.quentin.coevolutionMiner.utils.SourcesHelper;
import fr.quentin.coevolutionMiner.v2.ast.Project;
import fr.quentin.coevolutionMiner.v2.ast.Project.AST.FileSnapshot.Range;
import fr.quentin.coevolutionMiner.v2.ast.RangeMatchingException;
import fr.quentin.coevolutionMiner.v2.ast.miners.SpoonMiner.ProjectSpoon.SpoonAST;
import fr.quentin.coevolutionMiner.v2.coevolution.CoEvolutions.CoEvolution;
import fr.quentin.coevolutionMiner.v2.evolution.Evolutions;
import fr.quentin.coevolutionMiner.v2.evolution.miners.MultiGTSMiner;
import fr.quentin.coevolutionMiner.v2.evolution.storages.Neo4jEvolutionsStorage;
import fr.quentin.coevolutionMiner.v2.sources.Sources;
import fr.quentin.coevolutionMiner.v2.sources.Sources.Commit;
import fr.quentin.impactMiner.Evolution;
import fr.quentin.impactMiner.ImpactAnalysis;
import fr.quentin.impactMiner.ImpactElement;
import fr.quentin.impactMiner.Position;
import gumtree.spoon.apply.MyUtils;
import gumtree.spoon.apply.operations.MyScriptGenerator;
import gumtree.spoon.builder.CtWrapper;
import spoon.reflect.CtModelImpl.CtRootPackage;
import spoon.reflect.cu.SourcePosition;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtModule;
import spoon.reflect.declaration.CtPackage;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtPackageReference;

public class Utils {

	public static abstract class SimpleChunckedUpload<U> extends ChunckedUpload<U> {
		public SimpleChunckedUpload(Driver driver, int TIMEOUT) {
			super(driver, TIMEOUT);
		}

		protected abstract Value format(Collection<U> chunk);

		protected abstract String getCypher();

		@Override
		protected String put(Session session, List<U> chunk, Logger logger) {
			try {
				String done = session.writeTransaction(new TransactionWork<String>() {
					@Override
					public String execute(Transaction tx) {
						tx.run(getCypher(), format(chunk)).consume();
						return whatIsUploaded();
					}
				}, config);
				return done;
			} catch (TransientException e) {
				logger.error(whatIsUploaded() + " could not be uploaded", e);
				return null;
			} catch (Exception e) {
				logger.error(whatIsUploaded() + " could not be uploaded", e);
				return null;
			}
		}
	}

	public static abstract class ChunckedUpload<U> {
		private int TIMEOUT;

		protected abstract String whatIsUploaded();

		protected abstract String put(Session session, List<U> chunk, Logger logger);

		public ChunckedUpload(Driver driver, int TIMEOUT) {
			this.driver = driver;
			this.TIMEOUT = TIMEOUT;
			config = TransactionConfig.builder().withTimeout(Duration.ofMinutes(TIMEOUT)).build();
		}

		protected void execute(Logger logger, int STEP, List<U> processed) {
			int index = 0;
			int step = STEP;
			try (Session session = driver.session()) {
				logger.info("starting to upload " + processed.size() + " " + whatIsUploaded());
				while (index < processed.size()) {
					long start = System.nanoTime();
					String done = put(session, processed.subList(index, Math.min(index + step, processed.size())),
							logger);
					if (done != null) {
						logger.info(done + ": " + Math.min(index + step, processed.size()) + "/" + processed.size());
						index += step;
						if (((System.nanoTime() - start) / 1000000 / 60 < (TIMEOUT / 2))) {
							step = step * 2;
						}
					} else if (step == 1) {
						throw new RuntimeException(
								"took too long to upload " + whatIsUploaded() + " even one element is to much");
					} else {
						logger.info("took too long to upload " + whatIsUploaded() + " with a chunk of size " + step);
						step = step / 2;
					}
				}
			} catch (TransientException e) {
				logger.error(whatIsUploaded() + " could not be uploaded", e);
			} catch (Exception e) {
				logger.error(whatIsUploaded() + " could not be uploaded", e);
			}
		}

		private final Driver driver;
		protected final TransactionConfig config;
	}

	public enum Spanning {
		PER_COMMIT, ONCE
	}

	public static CtElement matchExactChild(CtElement parent, int start, int end) {
		return fr.quentin.impactMiner.Utils.matchExact(parent, start, end);
	}

	public static CtElement matchExactChild(SpoonAST ast, String path, int start, int end) {
		CtCompilationUnit cu = ast.getCu(path);
		for (CtType<?> type : cu.getDeclaredTypes()) {
			if (fr.quentin.impactMiner.Utils.isContainingType(type, start, end)) {
				return fr.quentin.impactMiner.Utils.matchExact(type, start, end);
			}
		}
		return null;
	}

	public static CtElement matchApproxChild(CtElement parent, int start, int end) {
		return fr.quentin.impactMiner.Utils.matchApprox(parent, start, end);
	}

	public static CtElement matchApproxChild(SpoonAST ast, String path, int start, int end) {
		CtCompilationUnit cu = ast.getCu(path);
		if (cu == null) {
			throw new RuntimeException("missing cu in " + ast.rootDir.toString() + " for path " + path.toString());
		}
		for (CtType<?> type : cu.getDeclaredTypes()) {
			if (fr.quentin.impactMiner.Utils.isContainingType(type, start, end)) {
				return fr.quentin.impactMiner.Utils.matchApprox(type, start, end);
			}
		}
		return null;
	}

	public static List<String> getCommitIdBeforeAndAfter(ImpactElement rootCause) {
		String commitIdBefore = null;
		String commitIdAfter = null;
		for (Entry<Object, Position> entry : rootCause.getEvolutionWithNonCorrectedPosition().entrySet()) {
			Evolutions.Evolution evo;
			if (entry.getKey() instanceof Evolutions.Evolution.DescRange) {
				evo = ((Evolutions.Evolution.DescRange) entry.getKey()).getSource();
			} else {
				continue;
			}
			if (commitIdBefore == null) {
				commitIdBefore = evo.getCommitBefore().getId();
			} else if (!commitIdBefore.equals(evo.getCommitBefore().getId())) {
				throw new RuntimeException("wrong commitIdBefore");
			}
			if (commitIdAfter == null) {
				commitIdAfter = evo.getCommitAfter().getId();
			} else if (!commitIdAfter.equals(evo.getCommitAfter().getId())) {
				throw new RuntimeException("wrong commitIdAfter");
			}
		}
		return Arrays.asList(commitIdBefore, commitIdAfter);
	}

	public static Position temporaryFix(Position position, Path rootDir) {
		try {
			return new Position(rootDir.relativize(Paths.get(position.getFilePath())).toString(), position.getStart(),
					position.getEnd() + 1);
		} catch (Exception e) {
			return new Position(position.getFilePath(), position.getStart(), position.getEnd() + 1);
		}
	}

	public static boolean isTest(CtElement e) {
		if (e == null) {
			return false;
		} else if (e instanceof CtExecutable) {
			return ImpactAnalysis.isTest((CtExecutable) e);
		} else {
			return false;
		}
	}

	public static boolean isParentTest(CtElement e) {
		if (e == null) {
			return false;
		}
		CtMethod p = e.getParent(CtMethod.class);
		if (p == null) {
			return false;
		}
		return ImpactAnalysis.isTest(p);
	}

	private static Map<String, String> memory = new HashMap<>();

	public static String memoizedReadResource(String resource) {
		String tmp = memory.get(resource);
		if (tmp == null) {
			tmp = readResource(resource);
			memory.put(resource, tmp);
		}
		return tmp;
	}

	public static String readResource(String resource) {
		try {
			return new String(
					Files.readAllBytes(Paths.get(Utils.class.getClassLoader().getResource(resource).toURI())));
		} catch (IOException | URISyntaxException e) {
			throw new RuntimeException(e);
		}
	}

	public static String formatedType(Object original) { // TODO put in utils
		if (original instanceof CtWrapper) {
			original = ((CtWrapper) original).getValue();
		}
		String name;
		try {
			name = original.getClass().getSimpleName();
		} catch (NoClassDefFoundError e) {
			return "";
		}
		name = name.endsWith("Impl") ? name.substring(0, name.length() - "Impl".length()) : name;
		name = name.startsWith("Ct") ? name.substring("Ct".length()) : name;
		name = name.endsWith("Wrapper") ? name.substring(0, name.length() - "Wrapper".length()) : name;
		return name;
	}

	public static <T, U> Map<T, U> map(Object... keyValues) {
		Map<T, U> r = new LinkedHashMap<>();
		Iterator<Object> it = Arrays.stream(keyValues).iterator();
		while (it.hasNext()) {
			r.put((T) it.next(), (U) it.next());
		}
		return r;
	}

	public static Map<String, Object> formatRangeWithType(Range targetR) {
		final Map<String, Object> rDescR = targetR.toMap();
		final CtElement e_ori = (CtElement) targetR.getOriginal();
		if (e_ori != null) {
			rDescR.put("type", formatedType(e_ori));
		}
		return rDescR;
	}

	// TODO extract functionality in utils
	public static <T> Range toRange(Project<T> proj, ITree tree, Version version) throws RangeMatchingException {
	    ImmutablePair<CtElement, SourcePosition> pair = gumtree.spoon.apply.MyUtils.toNormalizedPreciseSpoon(tree,
	            version);
	    if (pair == null) {
	    } else if (pair.left != null && pair.right == null) {
	        String path = "";
	        CtElement e = pair.left;
	        if (e instanceof CtPackageReference) {
	            e = e.getParent();
	        }
	        while (e.isParentInitialized()) {
	            if (e instanceof CtPackage) {
	                path = ((CtPackage) e).getSimpleName() + "/" + path;
	            }
	            e = e.getParent();
	            if (e instanceof CtModule || e instanceof CtRootPackage) {
	                break;
	            }
	        }
	        String tmp = proj.spec.relPath.toString();
	        path = tmp.endsWith("/") ? tmp + path : tmp + "/" + path;
	        return proj.getRange(path, 0, 0, pair.left);
	    } else if (pair.left == null || pair.right == null) {
	    } else if (pair.right.getFile() == null) {
	    } else {
	        String path = proj.getAst().rootDir.relativize(pair.right.getFile().toPath()).toString();
	        if (path.startsWith("../")) {
	            MultiGTSMiner.logger.warn("wrong project of " + proj + " for " + tree + " at " + pair.right.getFile()
	                    + " given the following spoon obj per version "
	                    + tree.getMetadata(MyScriptGenerator.ORIGINAL_SPOON_OBJECT_PER_VERSION) + " and ele position "
	                    + pair.left.getPosition());
	        }
	        return proj.getRange(path, pair.right.getSourceStart(), pair.right.getSourceEnd(), pair.left);
	    }
	    return null;
	}

	public static List<Commit> getCommitList(Sources sources , String before, String after) {
	    List<Commit> commits;
	    try (SourcesHelper helper = sources.open()) {
	        commits = sources.getCommitsBetween(before, after);
	    } catch (Exception e) {
	        throw new RuntimeException(e);
		}
		if (commits == null) {
			return Collections.emptyList();
		}
	    return commits;
	}
}