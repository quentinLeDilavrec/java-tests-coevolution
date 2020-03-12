package fr.quentin.impacts;

import java.util.Base64;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;

import spark.QueryParamsMap;
import spark.Request;
import spark.Response;
import spark.Route;

public interface ImpactRoute extends Route {

	public class ImpactQuery {
		public String before;
		public String after;
		public String repo;
		public String commitIdBefore;
		public String commitIdAfter;
		public String tagBefore;
		public String tagAfter;
	}

	@Override
	public default String handle(Request req, Response res) throws Exception {
		System.out.println("=========" + this.getClass().getSimpleName() + "=========");
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		ImpactQuery body = gson.fromJson(req.body(), ImpactQuery.class);
		Object r;
		if (body.before != null && body.after != null) {
			String before = new String(Base64.getDecoder().decode(body.before));
			String after = new String(Base64.getDecoder().decode(body.after));
			r = simplifiedHandler(before, after, req.queryMap());
		} else if (body.repo != null && body.commitIdBefore != null && body.commitIdBefore != null) {
			r = commitHandler(body.repo, body.commitIdBefore, body.commitIdAfter, req.queryMap());
		} else if (body.repo != null && body.tagBefore != null && body.tagBefore != null) {
			r = tagHandler(body.repo, body.tagBefore, body.tagAfter, req.queryMap());
		} else {
			return "\"nothing\"";
		}

		if (r instanceof JsonElement) {
			return gson.toJson((JsonElement) r);
		} else if (r instanceof String) {
			return (String) r;
		} else {
			return r.toString();
		}
	}

	public Object tagHandler(String repo, String tagBefore, String tagAfter, QueryParamsMap queryMap);

	public Object commitHandler(String repo, String commitIdBefore, String commitIdAfter, QueryParamsMap queryMap);

	public Object simplifiedHandler(String before, String after, QueryParamsMap queryParamsMap);

}