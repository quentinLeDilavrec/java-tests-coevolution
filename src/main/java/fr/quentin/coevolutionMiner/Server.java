package fr.quentin.coevolutionMiner;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import com.google.gson.JsonElement;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;

// import fr.quentin.coevolutionMiner.differs.ChangeDistillerHandler;
import fr.quentin.coevolutionMiner.v1.differs.GumtreeSpoonHandler;
import fr.quentin.coevolutionMiner.v1.impacts.ImpactGumtreeSpoonHandler;
import fr.quentin.coevolutionMiner.v1.impacts.ImpactRMinerHandler;
import fr.quentin.coevolutionMiner.v1.parsers.SpoonHandler;
import fr.quentin.coevolutionMiner.v2.ast.ProjectHandler;
import fr.quentin.coevolutionMiner.v2.ast.ProjectRoute;
import fr.quentin.coevolutionMiner.v2.ast.miners.SpoonMiner;
import fr.quentin.coevolutionMiner.v2.coevolution.CoEvolutionHandler;
import fr.quentin.coevolutionMiner.v2.coevolution.CoEvolutionRoute;
import fr.quentin.coevolutionMiner.v2.coevolution.miners.MultiCoEvolutionsMiner;
import fr.quentin.coevolutionMiner.v2.dependency.DependencyHandler;
import fr.quentin.coevolutionMiner.v2.dependency.DependencyRoute;
import fr.quentin.coevolutionMiner.v2.evolution.EvolutionHandler;
import fr.quentin.coevolutionMiner.v2.evolution.EvolutionRoute;
import fr.quentin.coevolutionMiner.v2.evolution.miners.GumTreeSpoonMiner;
import fr.quentin.coevolutionMiner.v2.evolution.miners.MixedMiner;
import fr.quentin.coevolutionMiner.v2.evolution.miners.MultiGTSMiner;
import fr.quentin.coevolutionMiner.v2.evolution.miners.RefactoringMiner;
import fr.quentin.coevolutionMiner.v2.sources.SourcesHandler;
import fr.quentin.coevolutionMiner.v2.sources.SourcesRoute;
import spark.Spark;
import fr.quentin.coevolutionMiner.utils.DefaultDataHandler;
import fr.quentin.coevolutionMiner.utils.MyProperties;

import static spark.Spark.*;

/**
 * Server
 */
public class Server {
	public final static int DEFAULT_PORT = 8095;
	public final static List<String> DEFAULT_ORIGINS = Collections.unmodifiableList(
			Arrays.asList("http://131.254.17.96:8080", "http://127.0.0.1:8080", "http://127.0.0.1:8087",
					"http://localhost:8080", "http://176.180.199.146:50000", "http://192.168.1.53:8080"));
	// private static Route aaa = (req, res) -> {
	// System.out.println("=========gumtree=========");

	// // AstComparator comp = new AstComparator();
	// // Gson gson = new GsonBuilder().setPrettyPrinting().create();
	// // final Diff diff = comp.compare(old, neww);
	// // // System.out.println(old);
	// // // System.out.println(neww);
	// // // System.out.println(diff.getAllOperations().get(0));
	// // JsonElement o = comp.formatDiff(diff.getRootOperations());

	// // System.out.println(o);
	// // return gson.toJson(o);
	// };

	public static void main(String[] args) {
		int serverport = DEFAULT_PORT;
		HashSet<String> allowedOrigins = new HashSet<String>(DEFAULT_ORIGINS);
		String neo4jAddr;
		if (args.length > 0) {
			serverport = Integer.valueOf(args[0]);
			neo4jAddr = MyProperties.getPropValues().getProperty(args[1]);
			allowedOrigins.addAll(Arrays.asList(Arrays.copyOfRange(args, 2, args.length)));
		} else {
			neo4jAddr = MyProperties.getPropValues().getProperty("neo4jAddress");
		}
		// validOrigins.add("http://127.0.0.1:8080");
		// validOrigins.add("http://localhost:8080");
		// validOrigins.add("http://127.0.0.1:8081");
		// validOrigins.add("http://localhost:8081");
		// validOrigins.add("http://131.254.17.96:8080");

		Server.serve(serverport, neo4jAddr, allowedOrigins);
	}

	/**
	 * 
	 * @param serverport
	 * @param autorizedOrigins like "http://131.254.17.96:8080"
	 */
	public static void serve(int serverport, String neo4jAddr, Set<String> autorizedOrigins) {
		final String user = MyProperties.getPropValues().getProperty("neo4jId");
		final String pwd = MyProperties.getPropValues().getProperty("neo4jPwd");
		final Driver neo4jDriver = GraphDatabase.driver(neo4jAddr, AuthTokens.basic(user, pwd));
		port(serverport);

		before((req, res) -> {
			System.out.println(req.headers("Origin"));
			res.header("Content-Type", "application/json");
			if (autorizedOrigins.contains(req.headers("Origin"))) {
				res.header("Access-Control-Allow-Origin", req.headers("Origin"));
			}
			res.header("Access-Control-Allow-Credentials", "true");
			res.header("Access-Control-Allow-Methods", "OPTIONS,PUT,GET");
		});

		path("/api/v1", () -> {
			path("/data", () -> {
				put("/default", new DefaultDataHandler());
				get("/default", new DefaultDataHandler());
				// put("/", );
			});

			path("/ast", () -> {
				put("/spoon", new SpoonHandler());
				// put("/", );
			});

			path("/diff", () -> {
				// put("/ChangeDistiller", new ChangeDistillerHandler());
				put("/RefactoringMiner", new ImpactRMinerHandler());
				// put("/gumtree", new GumtreeSpoonHandler());
				put("/gumtree", new ImpactGumtreeSpoonHandler());
			});
			// String accessControlRequestHeaders =
			// "Origin,Content-Type,Access-Control-Allow-Origin,
			// Access-Control-Allow-Credentials";
			options("/*", (req, res) -> {
				String accessControlRequestHeaders = req.headers("Access-Control-Request-Headers");
				if (accessControlRequestHeaders != null) {
					res.header("Access-Control-Allow-Headers", accessControlRequestHeaders);
				}
				String accessControlRequestMethod = req.headers("Access-Control-Request-Method");
				if (accessControlRequestMethod != null) {
					res.header("Access-Control-Allow-Methods", accessControlRequestMethod);
				}
				return "OK";
			});
		});

		path("/api/v2", () -> {
			SourcesHandler srcH = new SourcesHandler(neo4jDriver);
			path("/data", () -> {
				SourcesRoute srcR = new SourcesRoute(srcH, "JGit");
				put("/default", srcR);
				put("/JGit", srcR);
			});

			ProjectHandler astH = new ProjectHandler(neo4jDriver, srcH);
			path("/ast", () -> {
				ProjectRoute astR = new ProjectRoute(srcH, astH, SpoonMiner.class);
				put("/Spoon", astR);
				put("/default", astR);
			});

			EvolutionHandler evoH = new EvolutionHandler(neo4jDriver, srcH, astH);
			path("/evolution", () -> {
				EvolutionRoute evoGtsR = new EvolutionRoute(srcH, astH, evoH, MultiGTSMiner.class);
				put("/GumtreeSpoon", evoGtsR);
				EvolutionRoute evoRmR = new EvolutionRoute(srcH, astH, evoH, RefactoringMiner.class);
				put("/RefactoringMiner", evoRmR);
				EvolutionRoute evoMR = new EvolutionRoute(srcH, astH, evoH, MixedMiner.class);
				put("/Mixed", evoMR);
				put("/default", evoRmR);
			});

			DependencyHandler impactH = new DependencyHandler(neo4jDriver, srcH, astH, evoH);
			path("/impact", () -> {
				DependencyRoute impactR = new DependencyRoute(srcH, astH, evoH, impactH, "myMiner");
				put("/myMiner", impactR);
				put("/default", impactR);
			});

			CoEvolutionHandler coevoH = new CoEvolutionHandler(neo4jDriver, srcH, astH, evoH, impactH);
			path("/coevolution", () -> {
				CoEvolutionRoute coevoR = new CoEvolutionRoute(srcH, astH, evoH, impactH, coevoH,
						MultiCoEvolutionsMiner.class);
				put("/myMiner", coevoR);
				put("/default", coevoR);
			});

			options("/*", (req, res) -> {
				String accessControlRequestHeaders = req.headers("Access-Control-Request-Headers");
				if (accessControlRequestHeaders != null) {
					res.header("Access-Control-Allow-Headers", accessControlRequestHeaders);
				}
				String accessControlRequestMethod = req.headers("Access-Control-Request-Method");
				if (accessControlRequestMethod != null) {
					res.header("Access-Control-Allow-Methods", accessControlRequestMethod);
				}
				return "OK";
			});
		});

		internalServerError((req, res) -> {
			System.out.println("Error");
			System.out.println(req);
			System.out.println(res);
			res.type("application/json");
			return "{\"message\":\"Custom 500 handling\"}";
		});
		exception(Exception.class, (e, req, res) -> {
			// Handle the exception here
			System.out.println("Exception");
			System.out.println(e.getMessage());
			e.printStackTrace();
			System.out.println(req);
			System.out.println(res);
			res.status(500);
			StringBuilder r = new StringBuilder();
			r.append(new LinkedList<Throwable>(Arrays.asList(e.getSuppressed())).stream().map((x) -> {
				return x.getMessage();
			}).collect(Collectors.toList()));
			r.append("---------------------").append(e.getMessage()).append(e.getStackTrace());
			res.body(r.toString()); // TODO dangerous if not only for dev
		});
		Spark.awaitStop();
		neo4jDriver.close();
	}

	// private static Route astHandler(BiFunction<String, QueryParamsMap,
	// JsonElement> astHandler) {

	// // return (req, res) -> {
	// // System.out.println("=========" + astHandler.getClass().getSimpleName() +
	// "=========");
	// // String code = new String(Base64.getDecoder().decode(req.body()));
	// // Gson gson = new GsonBuilder().setPrettyPrinting().create();
	// // return gson.toJson(astHandler.apply(code, req.queryMap()));
	// // };
	// }

	// private static Route diffHandler(BiFunction<BeforeAfter<String, String>,
	// QueryParamsMap, JsonElement> diffhandler) {
	// return (req, res) -> {
	// System.out.println("=========" + diffhandler.getClass().getSimpleName() +
	// "=========");
	// String[] body = req.body().split("\n", 2);
	// BeforeAfter<String, String> code = new BeforeAfter<String, String>(
	// new String(Base64.getDecoder().decode(body[0])), new
	// String(Base64.getDecoder().decode(body[1])));
	// Gson gson = new GsonBuilder().setPrettyPrinting().create();
	// return gson.toJson(diffhandler.apply(code, req.queryMap()));
	// };
	// }
}