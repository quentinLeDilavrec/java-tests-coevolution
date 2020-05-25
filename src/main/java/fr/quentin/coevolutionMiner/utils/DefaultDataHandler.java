package fr.quentin.coevolutionMiner.utils;

import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import org.apache.log4j.Logger;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.URIish;

import gumtree.spoon.AstComparator;
import gumtree.spoon.builder.Json4SpoonGenerator;
import spark.QueryParamsMap;
import spark.Request;
import spark.Response;
import spark.Route;

public class DefaultDataHandler implements Route {

	static String VERSIONS_PATH = "/home/quentin/resources/Versions";
	static boolean materialized = false;

	public class DataQuery {
		public String repo;
		public String commitId;
		public String path;
	}

	@Override
	public String handle(Request req, Response res) throws Exception {
		System.out.println("=========" + this.getClass().getSimpleName() + "=========");
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		DataQuery body;
		if (req.body().length()>0) {
			body = gson.fromJson(req.body(), DataQuery.class);
		} else {
			body = new DataQuery();
			body.repo = req.queryParams("repo");
			body.commitId = req.queryParams("commitId");
			body.path = req.queryParams("path");
		}
		JsonObject o = new JsonObject();
		try {
			if (materialized) {
				Path root = Paths.get(VERSIONS_PATH);
				String repoRawPath = new URIish(body.repo).getRawPath();
				repoRawPath = repoRawPath.substring(0, repoRawPath.length() - 4);
				Path path = Paths.get(VERSIONS_PATH, repoRawPath, body.commitId, body.path).normalize();
				if (!path.startsWith(root)) {
					o.addProperty("error", "File not found");
					return gson.toJson(o);
				}
				String content = new String(Files.readAllBytes(path));
				o.addProperty("content", content);
			} else {
				if (body.repo == null) {
					o.addProperty("error", "missing repository");
					return gson.toJson(o);
				}
				if (body.commitId == null) {
					o.addProperty("error", "missing commitId");
					return gson.toJson(o);
				}
				if (body.path == null) {
					o.addProperty("error", "missing path");
					return gson.toJson(o);
				}
				try (SourcesHelper helper = new SourcesHelper(body.repo);) {
					o.addProperty("content", helper.getContent(body.commitId, body.path));
				}
			}
		} catch (Exception e) {
			o.addProperty("error", "File not found: "+ body.repo +";"+ body.commitId + ";" + body.path);
			System.out.println(body.repo + body.commitId + body.path);
			e.printStackTrace();
		}
		return gson.toJson(o);
	}
}