package fr.quentin.impacts;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import org.apache.maven.shared.invoker.DefaultInvocationRequest;
import org.apache.maven.shared.invoker.DefaultInvoker;
import org.apache.maven.shared.invoker.InvocationRequest;
import org.apache.maven.shared.invoker.Invoker;
import org.apache.maven.shared.invoker.MavenInvocationException;
import org.eclipse.jdt.core.compiler.CategorizedProblem;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.URIish;
import org.refactoringminer.api.GitHistoryRefactoringMiner;

import fr.quentin.Evolution;
import fr.quentin.Position;
import fr.quentin.utils.DiffHelper;
import fr.quentin.utils.GitHelper;
import fr.quentin.utils.SourcesHelper;
import gr.uom.java.xmi.diff.CodeRange;

// import org.refactoringminer.api.GitService;
import org.refactoringminer.api.Refactoring;
import org.refactoringminer.api.RefactoringHandler;
import org.refactoringminer.api.RefactoringType;
import org.refactoringminer.rm1.GitHistoryRefactoringMinerImpl;
import org.refactoringminer.util.GitServiceImpl;

import spark.QueryParamsMap;
import spoon.MavenLauncher;
import spoon.reflect.declaration.CtConstructor;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.compiler.jdt.JDTBasedSpoonCompiler;

public class ImpactRMinerHandler implements ImpactRoute {
	Logger logger = Logger.getLogger("ImpactRM commitHandler");

	@Override
	public Object simplifiedHandler(String before, String after, QueryParamsMap queryMap) {
		try {
			return "{}";
		} catch (Exception e) {
			System.err.println(e);
			e.printStackTrace();
			return "{\"error\":\"" + e.toString() + "\"}";
		}
	}

	@Override
	public Object commitHandler(String gitURL, String commitIdBefore, String commitIdAfter, QueryParamsMap queryMap) {

		System.out.println(gitURL);
		System.out.println(commitIdBefore);
		System.out.println(commitIdAfter);

		List<Refactoring> detectedRefactorings = new ArrayList<Refactoring>();

		GitHistoryRefactoringMiner miner = new GitHistoryRefactoringMinerImpl();
		JsonObject r = new JsonObject();
        try (SourcesHelper helper = new SourcesHelper(gitURL);) {
            Path path = helper.materialize(commitIdBefore);
            MavenLauncher launcher = new MavenLauncher(path.toString(), MavenLauncher.SOURCE_TYPE.ALL_SOURCE);
            launcher.getEnvironment().setLevel("INFO");
            launcher.getFactory().getEnvironment().setLevel("INFO");

            // Compile with maven to get deps
			SourcesHelper.prepare(path);
			
			try {
				miner.detectBetweenCommits(helper.getRepo(), commitIdBefore, commitIdAfter,
						new RefactoringHandler() {
							@Override
							public void handle(String commitId, List<Refactoring> refactorings) {
								detectedRefactorings.addAll(refactorings);
							}
						});
			} catch (Exception e) {
				// throw new RuntimeException(e);
			}

			try {
				launcher.buildModel();
			} catch (Exception e) {
				for (CategorizedProblem pb : ((JDTBasedSpoonCompiler) launcher
						.getModelBuilder()).getProblems()) {
					System.err.println(pb.toString());
				}
				throw new RuntimeException(e);
			}

			r.add("impact", impactAnalysis(path, launcher, detectedRefactorings));
		} catch (Exception e) {
			e.printStackTrace();
			return "{\"error\":\"" + e.toString() + "\"}";
		}

		logger.info("Build result");
		r.addProperty("handler", "ImpactRMinerHandler");
		// detectedRefactorings.get(0).rightSide().get(0).
		logger.info("Send the result");
		Object diff = new Gson().fromJson(JSON(gitURL, commitIdAfter, detectedRefactorings), new TypeToken<Object>() {
		}.getType());
		r.add("diff", new Gson().toJsonTree(diff));
		return r;
	}

	// private JsonElement JSON(String gitURL, String commitIdAfter,
	// List<Refactoring> detectedRefactorings) {
	// JsonObject r = new JsonObject();

	// return r;
	// }

	@Override
	public Object tagHandler(String repo, String tagBefore, String tagAfter, QueryParamsMap queryMap) {
		// TODO Auto-generated method stub
		return null;
	}

	public JsonElement impactAnalysis(Path root, MavenLauncher launcher, List<Refactoring> evolutions) throws IOException {
		List<Evolution> processedEvolutions = new ArrayList<>();

		for (Refactoring op : evolutions) {
			if (op.getRefactoringType().equals(RefactoringType.MOVE_OPERATION)) {
				List<CodeRange> src = op.leftSide();
				logger.info(src.getClass().toString());
				MoveMethodEvolution tmp = new MoveMethodEvolution(
					root.toAbsolutePath().toString(), src, op);
				processedEvolutions.add(tmp);
				Logger.getLogger("ImpactAna").info("- " + tmp.op + "\n" + tmp.impacts.size());
			} else {
				List<CodeRange> src = op.leftSide();
				logger.info(src.getClass().toString());
				OtherEvolution tmp = new OtherEvolution(
					root.toAbsolutePath().toString(), src, op);
				processedEvolutions.add(tmp);
				Logger.getLogger("ImpactAna").info("- " + tmp.op + "\n" + tmp.impacts.size());
			}
		}
		Logger.getLogger("ImpactAna")
				.info("Number of executable refs mapped to positions " + processedEvolutions.size());
		return SourcesHelper.impactAnalysis(root, launcher, processedEvolutions);
	}

	static class MoveMethodEvolution implements Evolution {
		Set<Position> impacts = new HashSet<>();
		private Refactoring op;

		MoveMethodEvolution(String root, List<CodeRange> holders, Refactoring op) {
			this.op = op;
			for (CodeRange range : holders) {
				this.impacts.add(new Position(Paths.get(root, range.getFilePath()).toString(), range.getStartOffset(),
						range.getEndOffset()));
			}
		}

		@Override
		public Set<Position> getImpactingPositions() {
			return impacts;
		}

	}
    
    static class OtherEvolution implements Evolution {
        Set<Position> impacts = new HashSet<>();
        private Refactoring op;

        OtherEvolution(String root, List<CodeRange> holders, Refactoring op) {
            this.op = op;
            for (CodeRange range : holders) {
                System.out.println("postion");
                System.out.println(Paths.get(root, range.getFilePath()).toString());
                System.out.println(range.getStartOffset());
                System.out.println(range.getEndOffset());
                this.impacts.add(new Position(Paths.get(root, range.getFilePath()).toString(), range.getStartOffset(),
                        range.getEndOffset()));
            }
        }

        @Override
        public Set<Position> getImpactingPositions() {
            return impacts;
        }

    }

	public static String JSON(String gitURL, String currentCommitId, List<Refactoring> refactoringsAtRevision) {
		StringBuilder sb = new StringBuilder();
		sb.append("{").append("\n");
		sb.append("\"").append("commits").append("\"").append(": ");
		sb.append("[");
		sb.append("{");
		sb.append("\t").append("\"").append("repository").append("\"").append(": ").append("\"").append(gitURL)
				.append("\"").append(",").append("\n");
		sb.append("\t").append("\"").append("sha1").append("\"").append(": ").append("\"").append(currentCommitId)
				.append("\"").append(",").append("\n");
		String url = "https://github.com/" + gitURL.substring(19, gitURL.indexOf(".git")) + "/commit/"
				+ currentCommitId;
		sb.append("\t").append("\"").append("url").append("\"").append(": ").append("\"").append(url).append("\"")
				.append(",").append("\n");
		sb.append("\t").append("\"").append("refactorings").append("\"").append(": ");
		sb.append("[");
		int counter = 0;
		for (Refactoring refactoring : refactoringsAtRevision) {
			sb.append(refactoring.toJSON());
			if (counter < refactoringsAtRevision.size() - 1) {
				sb.append(",");
			}
			sb.append("\n");
			counter++;
		}
		sb.append("]");
		sb.append("}");
		sb.append("]").append("\n");
		sb.append("}");
		return sb.toString();
	}
}
// String start = req.queryParams("start");
// String end = req.queryParams("end");
// Repository repo = gitService.cloneIfNotExists(
// "/tmp/refactoring-toy-example",
// gitURL);

// miner.detectBetweenCommits(repo, start, end, new RefactoringHandler() {
// @Override
// public void handle(String commitId, List<Refactoring> refactorings) {
// detectedRefactorings.addAll(refactorings);
// }
// });

// String response = JSON(gitURL, end, detectedRefactorings);
// System.out.println(response);
// return response;