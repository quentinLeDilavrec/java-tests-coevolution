package fr.quentin.coevolutionMiner.v1.impacts;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import org.eclipse.jdt.core.compiler.CategorizedProblem;
import org.refactoringminer.api.GitHistoryRefactoringMiner;
// import org.refactoringminer.api.GitService;
import org.refactoringminer.api.Refactoring;
import org.refactoringminer.api.RefactoringHandler;
import org.refactoringminer.api.RefactoringType;
import org.refactoringminer.rm1.GitHistoryRefactoringMinerImpl;

import fr.quentin.impactMiner.Evolution;
import fr.quentin.impactMiner.Position;
import fr.quentin.coevolutionMiner.utils.SourcesHelper;
import gr.uom.java.xmi.diff.CodeRange;
import gr.uom.java.xmi.diff.MoveOperationRefactoring;
import spark.QueryParamsMap;
import spoon.MavenLauncher;
import spoon.support.compiler.jdt.JDTBasedSpoonCompiler;

public class ImpactRMinerHandler implements ImpactRoute {
	Logger logger = Logger.getLogger("ImpactRM commitHandler");

	@Override
	public Object simplifiedHandler(String before, String after, ImpactQuery body, QueryParamsMap queryMap) {
		try {
			return "{}";
		} catch (Exception e) {
			System.err.println(e);
			e.printStackTrace();
			return "{\"error\":\"" + e.toString() + "\"}";
		}
	}

	@Override
	public Object commitHandler(String gitURL, String commitIdBefore, String commitIdAfter, ImpactQuery body,
			QueryParamsMap queryMap) {

		System.out.println(gitURL);
		System.out.println(commitIdBefore);
		System.out.println(commitIdAfter);

		List<Refactoring> detectedRefactorings = new ArrayList<Refactoring>();
		List<Evolution<Refactoring>> wantedEvolutions = new ArrayList<Evolution<Refactoring>>();
		List<Evolution<Refactoring>> nonWantedEvolutions = new ArrayList<Evolution<Refactoring>>();

		Map<String, List<Case>> wantedCases = new HashMap<>();
		if (body.cases != null)
			for (Case c : body.cases) {
				if(c.type!=null){
				List<Case> tmp = wantedCases.getOrDefault(c.type, new ArrayList<>());
				tmp.add(c);
				wantedCases.put(c.type, tmp);
			}
		}

		GitHistoryRefactoringMiner miner = new GitHistoryRefactoringMinerImpl();
		JsonObject r = new JsonObject();
		try (SourcesHelper helper = new SourcesHelper(gitURL);) {
			Path path = helper.materialize(commitIdBefore);
			MavenLauncher launcher = new MavenLauncher(path.toString(), MavenLauncher.SOURCE_TYPE.ALL_SOURCE);
			launcher.getEnvironment().setLevel("INFO");
			launcher.getFactory().getEnvironment().setLevel("INFO");

			// Compile with maven to get deps
		    StringBuilder prepareResult = new StringBuilder();
		    SourcesHelper.prepare(path, x -> {
		        prepareResult.append(x + "\n");
		    });
			logger.finer(prepareResult.toString());

			try {
				miner.detectBetweenCommits(helper.getRepo(), commitIdBefore, commitIdAfter, new RefactoringHandler() {
					@Override
					public void handle(String commitId, List<Refactoring> refactorings) {
						detectedRefactorings.addAll(refactorings);
						for (Refactoring op : refactorings) {
							String before;
							try {
								before = helper.getBeforeCommit(commitId);
							} catch (IOException e) {
								throw new RuntimeException(e);
							}
							// if (op instanceof MoveOperationRefactoring) {
							// assert (op.getRefactoringType().equals(RefactoringType.MOVE_OPERATION));
							// MoveMethodEvolution tmp = new
							// MoveMethodEvolution(path.toAbsolutePath().toString(),
							// (MoveOperationRefactoring) op, before, commitId);
							// wantedEvolutions.add((Evolution) tmp);
							// Logger.getLogger("ImpactAna").info("M- " + tmp.op + "\n" + tmp.pre.size());
							// } else {
							OtherEvolution tmp = new OtherEvolution(path.toAbsolutePath().toString(), op, before,
									commitId);
							if (isWanted(tmp)) {
								wantedEvolutions.add(tmp);
							} else {
								nonWantedEvolutions.add(tmp);
							}
							Logger.getLogger("ImpactAna").info("O- " + tmp.op + "\n" + tmp.pre.size());
							// }
						}
					}

					private boolean isWanted(OtherEvolution tmp) {
						if (wantedCases.size() > 0) {
							List<Case> aaaa = wantedCases.get(tmp.getOriginal().getRefactoringType().name());
							if (aaaa != null && aaaa.size() > 0) {
								for (Case c : aaaa) {
									for (String key : c.before.keySet()) {
										for (Range range_wanted : c.before.get(key)) {
											Set<Position> bbbb = tmp.getPreEvolutionPositionsDesc()
													.get(range_wanted.desc);
											for (Position pos : bbbb) {
												if (range_wanted.file != null
														&& !range_wanted.file.equals(pos.getFilePath()))
													continue;
												if (range_wanted.end == null && null == range_wanted.start) {
													return true;
												}
												if (!(range_wanted.end < pos.getStart()
														|| pos.getEnd() < range_wanted.start)) {
													return true;
												}
											}
										}
									}
									for (String key : c.after.keySet()) {
										for (Range range_wanted : c.after.get(key)) {
											Set<Position> bbbb = tmp.getPostEvolutionPositionsDesc()
													.get(range_wanted.desc);
											for (Position pos : bbbb) {
												if (range_wanted.file != null
														&& !range_wanted.file.equals(pos.getFilePath()))
													continue;
												if (range_wanted.end == null && null == range_wanted.start) {
													return true;
												}
												if (!(range_wanted.end > pos.getStart()
														|| pos.getEnd() > range_wanted.start)) {
													return true;
												}
											}
										}
									}
									if (c.before.size() == 0 && c.after.size() == 0) {
										return true;
									}
								}
								return false;
							} else {
								return false;
							}
						} else {
							return true;
						}
					}
				});
			} catch (Exception e) {
				e.printStackTrace();
				// throw new RuntimeException(e);
			}

			try {
				launcher.buildModel();
			} catch (Exception e) {
				for (CategorizedProblem pb : ((JDTBasedSpoonCompiler) launcher.getModelBuilder()).getProblems()) {
					System.err.println(pb.toString());
				}
				throw new RuntimeException(e);
			}

			r.add("impact", SourcesHelper.impactAnalysis(path, launcher, wantedEvolutions));
		} catch (

		Exception e) {
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
	public Object tagHandler(String repo, String tagBefore, String tagAfter, ImpactQuery body,
			QueryParamsMap queryMap) {
		// TODO Auto-generated method stub
		return null;
	}

	// static class MoveMethodEvolution implements
	// Evolution<MoveOperationRefactoring> {
	// Set<Position> pre = new HashSet<>();
	// Set<Position> post = new HashSet<>();
	// Map<String,Set<Position>> preDesc = new HashMap<>();
	// Map<String,Set<Position>> postDesc = new HashMap<>();
	// private MoveOperationRefactoring op;
	// private String commitIdBefore;
	// private String commitIdAfter;

	// MoveMethodEvolution(String root, MoveOperationRefactoring op, String
	// commitIdBefore, String commitIdAfter) {
	// this.op = op;
	// this.commitIdBefore = commitIdBefore;
	// this.commitIdAfter = commitIdAfter;

	// for (CodeRange range : op.leftSide()) {
	// Position pos = new Position(range.getFilePath(), range.getStartOffset(),
	// range.getEndOffset());
	// this.preDesc.putIfAbsent(range.getDescription(),new HashSet<>());
	// this.preDesc.get(range.getDescription()).add(pos);
	// this.pre.add(pos);
	// }
	// for (CodeRange range : op.rightSide()) {
	// Position pos = new Position(range.getFilePath(), range.getStartOffset(),
	// range.getEndOffset());
	// this.postDesc.putIfAbsent(range.getDescription(),new HashSet<>());
	// this.postDesc.get(range.getDescription()).add(pos);
	// this.post.add(pos);
	// }
	// }

	// public Map<String, Set<Position>> getPreEvolutionPositionsDesc() {
	// return preDesc;
	// }

	// public Map<String, Set<Position>> getPostEvolutionPositionsDesc() {
	// return postDesc;
	// }

	// @Override
	// public Set<Position> getPreEvolutionPositions() {
	// return pre;
	// }

	// @Override
	// public Set<Position> getPostEvolutionPositions() {
	// return post;
	// }

	// @Override
	// public MoveOperationRefactoring getOriginal() {
	// return op;
	// }

	// @Override
	// public JsonObject toJson() {
	// JsonObject r = (JsonObject) Evolution.super.toJson();
	// assert (op.getName().equals("Move Method"));
	// r.addProperty("type", "Move Method");
	// return r;
	// }

	// @Override
	// public String getCommitIdBefore() {
	// return commitIdBefore;
	// }

	// @Override
	// public String getCommitIdAfter() {
	// return commitIdAfter;
	// }

	// }

	static class OtherEvolution implements Evolution<Refactoring> {
		Set<Position> pre = new HashSet<>();
		Set<Position> post = new HashSet<>();
		Map<String, Set<Position>> preDesc = new HashMap<>();
		Map<String, Set<Position>> postDesc = new HashMap<>();
		private Refactoring op;
		private String commitIdBefore;
		private String commitIdAfter;

		OtherEvolution(String root, Refactoring op, String commitIdBefore, String commitIdAfter) {
			this.op = op;
			this.commitIdBefore = commitIdBefore;
			this.commitIdAfter = commitIdAfter;
			for (CodeRange range : op.leftSide()) {
				Position pos = new Position(range.getFilePath(), range.getStartOffset(), range.getEndOffset());
				this.preDesc.putIfAbsent(range.getDescription(), new HashSet<>());
				this.preDesc.get(range.getDescription()).add(pos);
				this.pre.add(pos);
			}
			for (CodeRange range : op.rightSide()) {
				Position pos = new Position(range.getFilePath(), range.getStartOffset(), range.getEndOffset());
				this.postDesc.putIfAbsent(range.getDescription(), new HashSet<>());
				this.postDesc.get(range.getDescription()).add(pos);
				this.post.add(pos);
			}
		}

		public Map<String, Set<Position>> getPreEvolutionPositionsDesc() {
			return preDesc;
		}

		public Map<String, Set<Position>> getPostEvolutionPositionsDesc() {
			return postDesc;
		}

		@Override
		public Set<Position> getPreEvolutionPositions() {
			return pre;
		}

		@Override
		public Set<Position> getPostEvolutionPositions() {
			return post;
		}

		@Override
		public Refactoring getOriginal() {
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

		@Override
		public JsonObject toJson() {
			JsonObject r = new JsonObject();
			r.addProperty("type", op.getName());
			r.addProperty("commitIdBefore", getCommitIdBefore());
			r.addProperty("commitIdAfter", getCommitIdAfter());
			JsonArray before = new JsonArray();
			for (CodeRange p : op.leftSide()) {
				JsonObject o = new JsonObject();
				before.add(o);
				o.addProperty("file", p.getFilePath());
				o.addProperty("start", p.getStartOffset());
				o.addProperty("end", p.getEndOffset());
				o.addProperty("type", p.getCodeElementType().getName());
				o.addProperty("desc", p.getDescription());
			}
			r.add("before", before);
			JsonArray after = new JsonArray();
			for (CodeRange p : op.rightSide()) {
				JsonObject o = new JsonObject();
				after.add(o);
				o.addProperty("file", p.getFilePath());
				o.addProperty("start", p.getStartOffset());
				o.addProperty("end", p.getEndOffset());
				o.addProperty("type", p.getCodeElementType().getName());
				o.addProperty("desc", p.getDescription());
			}
			r.add("after", after);
			return r;
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