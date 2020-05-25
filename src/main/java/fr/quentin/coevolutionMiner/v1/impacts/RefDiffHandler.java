package fr.quentin.coevolutionMiner.v1.impacts;

import java.io.File;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.eclipse.jgit.lib.Repository;
import org.refactoringminer.api.GitService;
import org.refactoringminer.util.GitServiceImpl;

import refdiff.core.rm2.analysis.RefDiffConfig;
import refdiff.core.rm2.analysis.RefDiffConfigImpl;
import refdiff.core.rm2.analysis.GitHistoryStructuralDiffAnalyzer;
import refdiff.core.rm2.model.Relationship;
import refdiff.core.rm2.model.refactoring.SDRefactoring;
import refdiff.core.util.AstUtils;
// import refdiff.core.diff.CstDiff;

import gumtree.spoon.AstComparator;
import gumtree.spoon.diff.Diff;
import refdiff.core.RefDiff;
import spark.QueryParamsMap;

public class RefDiffHandler implements ImpactRoute {

	@Override
	public Object simplifiedHandler(String before, String after, ImpactQuery body, QueryParamsMap qm) {
		RefDiffConfigImpl config = new RefDiffConfigImpl();
		RefDiff refDiff = new RefDiff(config);
		GitService gitService = new GitServiceImpl();
		JsonArray a = new JsonArray();
		// new GitHistoryStructuralDiffAnalyzer().;
		try {
		// 	// JavaPlugin javaPlugin = new JavaPlugin(tempFolder);
		// 	String tempFolder;
		// 	try (Repository repository = gitService.cloneIfNotExists("eclipse-themes",
		// 			"https://github.com/jersey/jersey.git")) {
		// 		List<SDRefactoring> refactorings = refDiff.detectAtCommit(repository,
		// 				"d94ca2b27c9e8a5fa9fe19483d58d2f2ef024606");
		// 		for (SDRefactoring r : refactorings) {
		// 			// getStandardDescription
		// 			r.get
		// 			System.out.printf("%s\t%s\t%s\n", r.getRefactoringType().getDisplayName(),
		// 					r.getEntityBefore().key(), r.getEntityAfter().key());
		// 		}
		// 		// RefDiff refDiffJava = new RefDiff(javaPlugin);
		
		// // printRefactorings(
		// // 	"Refactorings found in eclipse-themes 72f61ec",
		// // 	refDiffJava.computeDiffForCommit(eclipseThemesRepo, "72f61ec"));
		// // 	}
			return null;
		} catch (Exception ee) {
			System.err.println(ee);
			ee.printStackTrace();
			return "{\"error\":\"" + ee.toString() + "\"}";
		}
	}

	@Override
	public Object commitHandler(String repo, String commitIdBefore, String commitIdAfter, ImpactQuery body, QueryParamsMap queryMap) {

		System.out.println(repo);
		System.out.println(commitIdBefore);
		System.out.println(commitIdAfter);
		return null;
	}

	@Override
	public Object tagHandler(String repo, String tagBefore, String tagAfter, ImpactQuery body, QueryParamsMap queryMap) {
		// TODO Auto-generated method stub
		return null;
	}


	// private static JsonElement refactorings2json(String headLine, CstDiff diff) {
	// 	JsonArray r = new JsonArray();

	// 	System.out.println(headLine);
	// 	// System.out.println(diff.getRelationships().size());
	// 	for (Relationship rel : diff.getRelationships()) {
	// 		JsonObject o = new JsonObject();
	// 		r.add(o);
	// 		o.addProperty("type", rel.getType().name());
	// 		o.addProperty("fromP", formatWithLineNum(rel.getNodeBefore()));
	// 		o.addProperty("toP", formatWithLineNum(rel.getNodeAfter()));
	// 		o.add("from", node2json(rel.getNodeBefore(), "left"));
	// 		o.add("to", node2json(rel.getNodeAfter(), "right"));
	// 		o.addProperty("fromJ", node2jsonBis(rel.getNodeBefore(), "left"));
	// 		o.addProperty("toJ", node2jsonBis(rel.getNodeAfter(), "right"));
	// 		// o.addProperty("similarity",rel.getSimilarity().toString());
	// 		o.addProperty("value", rel.getStandardDescription());
	// 	}
	// 	return r;
	// }

	// private static JsonObject node2json(CstNode node, String side) {
	// 	JsonObject o = node2json(node);
	// 	o.addProperty("side", side);
	// 	return o;
	// }

	// private static String node2jsonBis(CstNode node, String side) {
	// 	SimpleModule module = new SimpleModule();
	// 	ObjectMapper mapper = new ObjectMapper();
	// 	try {
	// 		return mapper.registerModule(module).writer(new DefaultPrettyPrinter()).writeValueAsString(node);
	// 	} catch (JsonProcessingException e) {
	// 		// TODO Auto-generated catch block
	// 		e.printStackTrace();
	// 	}
	// 	return "";
	// }
	
	// private static JsonObject node2json(CstNode node) {
	// 	JsonObject o = new JsonObject();
	// 	o.addProperty("type",node.getType());
	// 	o.addProperty("name",node.getLocalName());
	// 	o.addProperty("start",node.getLocation().getBegin());
	// 	o.addProperty("end",node.getLocation().getEnd());
	// 	o.addProperty("bstart",node.getLocation().getBodyBegin());
	// 	o.addProperty("bend",node.getLocation().getBodyEnd());
	// 	o.addProperty("file",node.getLocation().getFile());
	// 	o.add("loc",loc2json(node.getLocation()));
	// 	return o;
	// 	// String.format("%s %s at %s:%d", 
	// 	// node.getType().replace("Declaration", ""), 
	// 	// node.getLocalName(), 
	// 	// node.getLocation().getFile(), 
	// 	// node.getLocation().getLine());
	// }
	
	// private static JsonObject loc2json(Location loc) {
	// 	JsonObject o = new JsonObject();
	// 	JsonObject start = new JsonObject();
	// 	o.add("start", start);
	// 	start.addProperty("line",loc.getLine());
	// 	return o;
	// }
	
	// private static String formatWithLineNum(CstNode node) {
	// 	return String.format("%s %s at %s:%d", node.getType().replace("Declaration", ""), node.getLocalName(), node.getLocation().getFile(), node.getLocation().getLine());
	// }
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