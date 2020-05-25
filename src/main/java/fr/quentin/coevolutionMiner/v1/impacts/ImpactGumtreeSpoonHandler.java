package fr.quentin.coevolutionMiner.v1.impacts;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import org.apache.log4j.Logger;
import org.refactoringminer.api.Refactoring;

import fr.quentin.impactMiner.Evolution;
import fr.quentin.impactMiner.ImpactAnalysis;
import fr.quentin.impactMiner.ImpactChain;
import fr.quentin.impactMiner.Impacts;
import fr.quentin.impactMiner.JsonSerializable;
import fr.quentin.impactMiner.Position;
import fr.quentin.impactMiner.ToJson;
import fr.quentin.coevolutionMiner.utils.DiffHelper;
import fr.quentin.coevolutionMiner.utils.FilePathFilter;
import gumtree.spoon.AstComparator;
import gumtree.spoon.diff.Diff;
import gumtree.spoon.diff.operations.MoveOperation;
import gumtree.spoon.diff.operations.Operation;
import spark.QueryParamsMap;
import spoon.MavenLauncher;
import spoon.SpoonModelBuilder;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.cu.SourcePosition;
import spoon.reflect.cu.SourcePositionHolder;
import spoon.reflect.declaration.CtConstructor;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class ImpactGumtreeSpoonHandler implements ImpactRoute {
	static int count = 0;

	Logger logger = Logger.getLogger("ImpactGTS commitHandler " + count++);
	private DiffHelper diffHelperInst;

	@Override
	public Object simplifiedHandler(String before, String after, ImpactQuery body, QueryParamsMap qm) {
		try {
			AstComparator comp = new AstComparator();
			Diff diff = comp.compare(before, after);
			return comp.formatDiff(diff.getRootOperations());
		} catch (Exception ee) {
			System.err.println(ee);
			ee.printStackTrace();
			return "{\"error\":\"" + ee.toString() + "\"}";
		}
	}

	@Override
	public Object commitHandler(String gitURL, String commitIdBefore, String commitIdAfter, ImpactQuery body, QueryParamsMap queryMap) {

		logger.info(gitURL);
		logger.info(commitIdBefore);
		logger.info(commitIdAfter);
		try {
			this.diffHelperInst = new DiffHelper(gitURL, commitIdBefore, commitIdAfter);
			logger.info("Get the repo");
			// BEFORE
			logger.info("Get the before version");
			MavenLauncher launcherBefore = diffHelperInst.getLauncherBefore();

			logger.info("Build the before model");
			CtType<?> before = gumtreeHackyPreprocess(launcherBefore);

			// AFTER
			logger.info("Get the after version");
			MavenLauncher launcherAfter = diffHelperInst.getLauncherAfter();

			logger.info("Build the after model");
			CtType<?> after = gumtreeHackyPreprocess(launcherAfter);

			// DIFF
			AstComparator comp = new AstComparator();
			logger.info("Compare");
			Diff diff = comp.compare(before, after);

			// Impact Analysis
			logger.info("ImpactAnalysis");
			JsonElement impact = impactAnalysis(launcherBefore, diff);

			logger.info("Build result");
			JsonObject r = new JsonObject();
			r.addProperty("handler", "ImpactGumtreeSpoonHandler");
			r.add("impact", impact);
			r.add("diff", comp.formatDiff(diff.getRootOperations()));
			logger.info("Send the result");
			return r;
		} catch (Exception ee) {
			ee.printStackTrace();
			return "{\"error\":\"" + ee.toString() + "\"}";
		}
	}

	private CtType gumtreeHackyPreprocess(MavenLauncher launcher) {
		Factory factory = launcher.getFactory();
		factory.getModel().setBuildModelIsFinished(false);
		factory.getEnvironment().setCommentEnabled(false);
		factory.getEnvironment().setLevel("INFO");

		SpoonModelBuilder compiler = launcher.getModelBuilder();
		compiler.getFactory().getEnvironment().setLevel("INFO");
		// launcher.getEnvironment().setLevel("INFO");
		// compiler.addInputSource(resource);

		// try {
		compiler.build();
		// } catch (Exception e) {
		// e.printStackTrace();
		// }

		if (factory.Type().getAll().size() == 0) {
			return null;
		}

		// let's first take the first type.
		CtType type = factory.Type().getAll().get(0);
		// Now, let's ask to the factory the type (which it will set up the
		// corresponding
		// package)
		return factory.Type().get(type.getQualifiedName());
	}

	// (global-undo-tree-mode 1)
	// ;; make ctrl-z undo
	// (global-set-key (kbd "C-z") 'undo)
	// ;; make ctrl-Z redo
	// (defalias 'redo 'undo-tree-redo)
	// (global-set-key (kbd "C-S-z") 'redo)
	@Override
	public Object tagHandler(String repo, String tagBefore, String tagAfter, ImpactQuery body, QueryParamsMap queryMap) {
		// TODO Auto-generated method stub
		return null;
	}

	// public static Repository cloneIfNotExists(File folder, String cloneUrl)
	// throws Exception {
	// Repository repository;
	// if (folder.exists()) {
	// RepositoryBuilder builder = new RepositoryBuilder();
	// repository = builder.setGitDir(new File(folder,
	// ".git")).readEnvironment().findGitDir().build();

	// } else {
	// Git git =
	// Git.cloneRepository().setDirectory(folder).setURI(cloneUrl).setCloneAllBranches(true).call();
	// repository = git.getRepository();
	// }
	// return repository;
	// }

	public JsonElement impactAnalysis(MavenLauncher launcher, Diff evolutions) throws IOException {
		List<CtExecutableReference<?>> allExecutableReference = new ArrayList<>();
		for (CtMethod<?> m : launcher.getModel().getElements(new TypeFilter<>(CtMethod.class))) {
			allExecutableReference.add(m.getReference());
		}
		for (CtConstructor<?> m : launcher.getModel().getElements(new TypeFilter<>(CtConstructor.class))) {
			allExecutableReference.add(m.getReference());
		}
		Logger.getLogger("ImpactAna").info("Number of executable refs " + allExecutableReference.size());

		List<Evolution<MoveOperation>> processedEvolutions = new ArrayList<>();

		for (Operation<?> op : evolutions.getRootOperations()) {
			try {
				if (op instanceof MoveOperation) {
					CtElement src = op.getSrcNode();
					logger.info(src.getClass());
					if (src.getPosition().isValidPosition()) {
						System.out.println(src.getPosition().hashCode());
						processedEvolutions.add(new MoveEvolution((MoveOperation) op));
					}
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		Logger.getLogger("ImpactAna")
				.info("Number of executable refs mapped to positions " + processedEvolutions.size());
		return this.diffHelperInst.impactAnalysis(launcher, processedEvolutions);
	}

	static class MoveEvolution implements Evolution<MoveOperation> {
		Set<Position> impacts = new HashSet<>();
		Set<Position> post = new HashSet<>();
		private MoveOperation op;
		private String commitIdBefore;
		private String commitIdAfter;

		MoveEvolution(MoveOperation op) throws IOException {
			SourcePosition p = op.getSrcNode().getPosition();
			this.op = op;
			this.impacts.add(new Position(p.getFile().getCanonicalPath(), p.getSourceStart(), p.getSourceEnd()));
			SourcePosition pDst = op.getDstNode().getPosition();
			this.post.add(new Position(pDst.getFile().getCanonicalPath(), pDst.getSourceStart(), pDst.getSourceEnd()));
		}

		@Override
		public Set<Position> getPreEvolutionPositions() {
			return impacts;
		}

		@Override
		public Set<Position> getPostEvolutionPositions() {
			return post;
		}

		@Override
		public MoveOperation getOriginal() {
			return op;
		}

		@Override
		public String getCommitIdBefore() {
			return commitIdBefore;
		}

		@Override
		public String getCommitIdAfter() {
			return commitIdAfter;
		}

	}

	public Object commitHandlerOld(String gitRepoAddress, String commitIdBefore, String commitIdAfter,
			QueryParamsMap queryMap) {

		logger.info(gitRepoAddress);
		logger.info(commitIdBefore);
		logger.info(commitIdAfter);
		// try {
		// DiffHelper diffHelperInst = new
		// DiffHelper(gitRepoAddress,commitIdBefore,commitIdAfter);

		// // 1 URIish parsedRepoURI = new URIish(gitRepoAddress);
		// // 1 String repoRawPath = parsedRepoURI.getRawPath();
		// logger.info("Get the repo");
		// //1 try (Repository repo = GitHelper.cloneIfNotExists(
		// //1 REPOS_PATH + repoRawPath.substring(0, repoRawPath.length() - 4),
		// //1 gitRepoAddress)) {
		// // try (Repository repo = diffHelperInst.cloneIfNotExists()) {

		// // try (RevWalk rw = new RevWalk(repo)) {
		// // for (RevCommit revCommit : rw) {
		// // logger.info(revCommit.getId());
		// // }
		// // repo.resolve(commitIdBefore);
		// // // RevCommit commitBefore = rw.parseCommit(repo.resolve(commitIdBefore));
		// // // RevCommit commitAfter = rw.parseCommit(repo.resolve(commitIdAfter));
		// // // GitHelper.getSourcesBeforeAndAfterCommit(repo, commitBefore,
		// commitAfter,
		// // // ALLOW_ALL);
		// // }

		// // PairBeforeAfter<SourceFileSet> beforeAndAfter =
		// GitHelper.getSourcesBeforeAndAfterCommit(repo,
		// // commitIdBefore, commitIdAfter, ALLOW_ALL);
		// // // return comparator.compare(beforeAndAfter);
		// // // // new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss").format(new Date())
		// PairBeforeAfter<SourceFileSet> beforeAndAfter =
		// GitHelper.getSourcesBeforeAndAfterCommit(repo,
		// commitIdBefore, commitIdAfter, ALLOW_ALL);

		// // BEFORE
		// logger.info("Materialize the before version");
		// this.pathBefore = Paths.get(VERSIONS_PATH, repoRawPath.substring(0,
		// repoRawPath.length() - 4),
		// commitIdBefore);
		// beforeAndAfter.getBefore().materializeAt(pathBefore);
		// MavenLauncher launcherBefore = new MavenLauncher(pathBefore.toString(),
		// MavenLauncher.SOURCE_TYPE.ALL_SOURCE);

		// logger.info("Build the before model");
		// // launcherBefore.buildModel();
		// // CtModel modelBefore = launcherBefore.getModel();
		// // CtElement before = modelBefore.getRootPackage();
		// CtType<?> before = bbbb(launcherBefore);

		// // AFTER
		// logger.info("Materialize the after version");
		// MavenLauncher launcherAfter = makeLauncher(commitIdAfter, repoRawPath,
		// beforeAndAfter);
		// logger.info("Build the after model");
		// // launcherAfter.buildModel();
		// // logger.info("ImpactAnalysis");
		// // JsonElement impact = impactAnalysis(launcherAfter);
		// CtType<?> after = bbbb(launcherAfter);
		// // CtModel modelAfter = launcherAfter.getModel();
		// // logger.info("Get Model");
		// // CtElement after = modelAfter.getRootPackage();
		// // logger.info("Get RootPackage");

		// // DIFF
		// AstComparator comp = new AstComparator();
		// logger.info("Compare");
		// Diff diff = comp.compare(before, after);

		// // Impact Analysis
		// logger.info("ImpactAnalysis");
		// JsonElement impact = impactAnalysis(launcherBefore, diff);
		// // Gson gson = new GsonBuilder().setPrettyPrinting().create();
		// // logger.info("ImpactAnalysis Done\n" + gson.toJson(impact));

		// logger.info("Build result");
		// JsonObject r = new JsonObject();
		// r.add("impact", impact);
		// r.add("diff", comp.formatDiff(diff.getRootOperations()));
		// logger.info("Send the result");
		// return r;
		// }
		// } catch (Exception ee) {
		// ee.printStackTrace();
		// return "{\"error\":\"" + ee.toString() + "\"}";
		// }
		return "{}";
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
// Logger.getLogger("ImpactAna").info(response);
// return response;
