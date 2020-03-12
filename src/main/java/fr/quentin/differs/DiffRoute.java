package fr.quentin.differs;

import java.util.Base64;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;

import spark.QueryParamsMap;
import spark.Request;
import spark.Response;
import spark.Route;

public interface DiffRoute extends Route {

	@Override
	public default String handle(Request req, Response res) throws Exception {
		System.out.println("=========" + this.getClass().getSimpleName() + "=========");
		String[] body = req.body().split("\n", 2);
		String before = new String(Base64.getDecoder().decode(body[0]));
		String after = new String(Base64.getDecoder().decode(body[1]));
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		Object r = simplifiedHandler(before, after, req.queryMap());
		if (r instanceof JsonElement) {
			return gson.toJson((JsonElement) r);
		} else {
			return r.toString();
		}
	}

	public Object simplifiedHandler(String before, String after, QueryParamsMap queryParamsMap);
	
}