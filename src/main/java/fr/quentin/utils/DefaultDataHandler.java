package fr.quentin.utils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import org.apache.log4j.Logger;
import org.eclipse.jgit.transport.URIish;

import gumtree.spoon.AstComparator;
import gumtree.spoon.builder.Json4SpoonGenerator;
import spark.QueryParamsMap;
import spark.Request;
import spark.Response;
import spark.Route;

public class DefaultDataHandler implements Route {

	public class DataQuery {
		public String path;
		public String repo;
		public String commitId;
	}
	@Override
	public String handle(Request req, Response res) throws Exception {
		System.out.println("=========" + this.getClass().getSimpleName() + "=========");
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		DataQuery body = gson.fromJson(req.body(), DataQuery.class);
		JsonObject o = new JsonObject();
		Path root = Paths.get("/tmp/DiffVersions");
		// body.commitId
		// body.repo
		String repoRawPath = new URIish(body.repo).getRawPath();
		repoRawPath = repoRawPath.substring(0, repoRawPath.length() - 4);
		Path path = Paths.get("/tmp/DiffVersions", repoRawPath, body.commitId, body.path).normalize();
		if (!path.startsWith(root)) {
			o.addProperty("error", "File not found");
		} else {
			try {
				String content = new String(Files.readAllBytes(path));
				o.addProperty("content", content);
			} catch (Exception e) {
				o.addProperty("error", "File not found");
				System.out.println(body.repo+body.commitId+body.path);
				e.printStackTrace();
			}
			// TODO secure it, should be a subpath of "/tmp/DiffVersions" and no absolute
			// path and . and .. , maybe make an util of git materialization
		}
		return gson.toJson(o);
	}

}