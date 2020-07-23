package fr.quentin.coevolutionMiner.v2.evolution;

import java.util.Base64;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import fr.quentin.coevolutionMiner.utils.SourcesHelper;
import fr.quentin.coevolutionMiner.v2.ast.Project;
import fr.quentin.coevolutionMiner.v2.ast.ProjectHandler;
import fr.quentin.coevolutionMiner.v2.sources.Sources;
import fr.quentin.coevolutionMiner.v2.sources.SourcesHandler;
import spark.Request;
import spark.Response;
import spark.Route;

public class EvolutionRoute implements Route {

    private SourcesHandler sourcesHandler;
    private ProjectHandler astHandler;
    private EvolutionHandler evoHandler;
    private String minerId;

    public EvolutionRoute(SourcesHandler sourcesHandler, ProjectHandler astHandler, EvolutionHandler evoHandler,
            String minerId) {
        this.sourcesHandler = sourcesHandler;
        this.astHandler = astHandler;
        this.evoHandler = evoHandler;
        this.minerId = minerId;
    }

    public class Range {
        public String commitId;
        public String file;
        public Integer start;
        public Integer end;
        public String desc;
    }

    public class Case {
        public String type;
        public Map<String, Range[]> before;
        public Map<String, Range[]> after;
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
    public String handle(Request req, Response res) throws Exception {
        System.out.println("=========" + this.getClass().getSimpleName() + "=========");
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        ImpactQuery body = gson.fromJson(req.body(), ImpactQuery.class);
        Object r;
        if (body.before != null && body.after != null) {
            String before = new String(Base64.getDecoder().decode(body.before));
            String after = new String(Base64.getDecoder().decode(body.after));
            JsonObject tmp = new JsonObject();
            tmp.addProperty("error", "simplified evolution handler not implemented yet");
            r = tmp;
        } else if (body.repo != null && body.commitIdAfter != null) {
            Sources.Specifier srcSpec = sourcesHandler.buildSpec(body.repo);
            System.out.println(body.commitIdBefore);
            if( body.commitIdBefore == null) {
                try (SourcesHelper helper = sourcesHandler.handle(srcSpec, "JGit").open();) {
                    body.commitIdBefore = helper.getBeforeCommit(body.commitIdAfter);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
            // TODO use body.cases to filter wanted evolutions
            r = evoHandler.handle(evoHandler.buildSpec(srcSpec, body.commitIdBefore, body.commitIdAfter, minerId))
                    .toJson();
        } else if (body.repo != null && body.tagBefore != null && body.tagAfter != null) {
            JsonObject tmp = new JsonObject();
            tmp.addProperty("error", "tag evolution handler not implemented yet");
            r = tmp;
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

}