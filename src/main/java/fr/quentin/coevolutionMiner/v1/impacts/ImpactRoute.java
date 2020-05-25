package fr.quentin.coevolutionMiner.v1.impacts;

import java.util.Base64;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;

import spark.QueryParamsMap;
import spark.Request;
import spark.Response;
import spark.Route;

public interface ImpactRoute extends Route {

	public class Range {
		public String commitId;
		public String file;
		public Integer start;
		public Integer end;
		public String desc;
	}
	public class Case {
		public String type;
		public Map<String,Range[]> before;
		public Map<String,Range[]> after;
	}

	public class ImpactQuery {
		public String before;
		public String after;
		public String repo;
		public String commitIdBefore;
		public String commitIdAfter;
		public String tagBefore;
		public String tagAfter;
		/**
		 * wanted cases
		 */
		public Case[] cases;
	}

	@Override
	public default String handle(Request req, Response res) throws Exception {
		System.out.println("=========" + this.getClass().getSimpleName() + "=========");
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		ImpactQuery body = gson.fromJson(req.body(), ImpactQuery.class);
		Object r;
		// res.raw().sendError(102);
		// res.raw().sendError(102); // TODO test if it works (maybe other codes also
		// work?)
		if (body.before != null && body.after != null) {
			String before = new String(Base64.getDecoder().decode(body.before));
			String after = new String(Base64.getDecoder().decode(body.after));
			r = simplifiedHandler(before, after, body, req.queryMap());
		} else if (body.repo != null && body.commitIdBefore != null && body.commitIdAfter != null) {
			r = commitHandler(body.repo, body.commitIdBefore, body.commitIdAfter, body, req.queryMap());
		} else if (body.repo != null && body.tagBefore != null && body.tagAfter != null) {
			r = tagHandler(body.repo, body.tagBefore, body.tagAfter, body, req.queryMap());
		} else {
			return "\"nothing\"";
		}
		// res.raw().sendError(102);
		// res.raw().sendError(102);

		if (r instanceof JsonElement) {
			return gson.toJson((JsonElement) r);
		} else if (r instanceof String) {
			return (String) r;
		} else {
			return r.toString();
		}
	}

	public Object tagHandler(String repo, String tagBefore, String tagAfter, ImpactQuery body, QueryParamsMap queryMap);

	public Object commitHandler(String repo, String commitIdBefore, String commitIdAfter, ImpactQuery body, QueryParamsMap queryMap);

	public Object simplifiedHandler(String before, String after, ImpactQuery body, QueryParamsMap queryMap);

}