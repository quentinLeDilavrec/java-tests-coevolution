package fr.quentin.coevolutionMiner.v1.parsers;

import java.util.Base64;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import spark.QueryParamsMap;
import spark.Request;
import spark.Response;
import spark.Route;

public interface AstRoute extends Route {

	@Override
	public default String handle(Request req, Response res) throws Exception {
		System.out.println("=========" + this.getClass().getSimpleName() + "=========");
		String code = new String(Base64.getDecoder().decode(req.body()));
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		return gson.toJson(simplifiedHandler(code, req.queryMap()));
	}

	public Object simplifiedHandler(String s, QueryParamsMap queryParamsMap);
}