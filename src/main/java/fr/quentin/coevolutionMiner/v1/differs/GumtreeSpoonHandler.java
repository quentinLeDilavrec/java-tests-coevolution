package fr.quentin.coevolutionMiner.v1.differs;

import gumtree.spoon.AstComparator;
import gumtree.spoon.diff.Diff;
import spark.QueryParamsMap;

public class GumtreeSpoonHandler implements DiffRoute {
	// public static Function<String, JsonElement> spoonHandler = (String code) -> {
	// // Json4SpoonGenerator x = new Json4SpoonGenerator();
	// // AstComparator comp = new AstComparator();
	// // Gson gson = new GsonBuilder().setPrettyPrinting().create();
	// JsonObject r = new JsonObject();// =
	// x.getJSONasJsonObject(comp.getCtType(code));
	// // System.err.println(comp.getCtType(code));
	// // System.err.println(r);
	// return r;
	// // return gson.toJson(r);
	// };
	// public static BiFunction<BeforeAfter<String, String>, QueryParamsMap,
	// JsonElement> diffHandler = (
	// BeforeAfter<String, String> beforeafter, QueryParamsMap qm) -> {
	// };

	@Override
	public Object simplifiedHandler(String before, String after, QueryParamsMap qm) {
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
}