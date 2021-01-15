package fr.quentin.coevolutionMiner.v2.utils;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Logger;

import org.neo4j.driver.Driver;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.Transaction;
import org.neo4j.driver.TransactionConfig;
import org.neo4j.driver.TransactionWork;
import org.neo4j.driver.Value;
import org.neo4j.driver.exceptions.TransientException;

import fr.quentin.coevolutionMiner.v2.ast.miners.SpoonMiner.ProjectSpoon.SpoonAST;
import fr.quentin.coevolutionMiner.v2.evolution.Evolutions;
import fr.quentin.coevolutionMiner.v2.evolution.storages.Neo4jEvolutionsStorage;
import fr.quentin.impactMiner.Evolution;
import fr.quentin.impactMiner.ImpactAnalysis;
import fr.quentin.impactMiner.ImpactElement;
import fr.quentin.impactMiner.Position;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;

public class Utils {

	public static abstract class ChunckedUpload<U> {
	    private int TIMEOUT;

		protected abstract String whatIsUploaded();
	    
	    protected abstract String getCypher();
	    
	    protected abstract Value format(Collection<U> chunk);
	
	    public ChunckedUpload(Driver driver, int TIMEOUT) {
			this.driver = driver;
			this.TIMEOUT = TIMEOUT;
			config = TransactionConfig.builder().withTimeout(Duration.ofMinutes(TIMEOUT)).build();
		}
		
		protected void execute(Logger logger, int STEP, List<U> processed) {
	        int index = 0;
	        int step = STEP;
	        while (index < processed.size()) {
	            long start = System.nanoTime();
	            String done = put(processed.subList(index, Math.min(index + step, processed.size())));
	            if (done != null) {
	                logger.info(done + ": " + Math.min(index + step, processed.size()) + "/" + processed.size());
	                index += step;
	                if (((System.nanoTime() - start) / 1000000 / 60 < (TIMEOUT / 2))) {
	                    step = step * 2;
	                }
	            } else {
	                logger.info("took too long to upload " + whatIsUploaded()
	                        + " with a chunk of size " + step);
	                step = step / 2;
	            }
	        }
		}
	
	    private final Driver driver;
		private final TransactionConfig config;
	    private String put(Collection<U> chunk) {
	        try (Session session = driver.session()) {
	            String done = session.writeTransaction(new TransactionWork<String>() {
	                @Override
	                public String execute(Transaction tx) {
						tx.run(getCypher(), ChunckedUpload.this.format(chunk)).consume();
						return whatIsUploaded();
	                }
	            }, config);
	            return done;
	        } catch (TransientException e) {
	            e.printStackTrace();
	        } catch (Exception e) {
	            e.printStackTrace();
	        }
	        return null;
	    }
	}

	public static CtElement matchExactChild(CtElement parent, int start, int end) {
		return fr.quentin.impactMiner.Utils.matchExact(parent, start, end);
	}

	public static CtElement matchExactChild(SpoonAST ast, String path, int start, int end) {
		CtType<?> type = ast.getTop(path);
		return fr.quentin.impactMiner.Utils.matchExact(type, start, end);
	}

	public static CtElement matchApproxChild(CtElement parent, int start, int end) {
		return fr.quentin.impactMiner.Utils.matchApprox(parent, start, end);
	}

	public static CtElement matchApproxChild(SpoonAST ast, String path, int start, int end) {
		CtType<?> type = ast.getTop(path);
		return fr.quentin.impactMiner.Utils.matchApprox(type, start, end);
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
}