package fr.quentin.v2.impact;

import java.util.Base64;
import java.util.Map;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import spark.Request;
import spark.Response;
import spark.Route;

import fr.quentin.v2.evolution.EvolutionHandler;
import fr.quentin.v2.sources.Sources;
import fr.quentin.v2.sources.SourcesHandler;
import fr.quentin.utils.SourcesHelper;
import fr.quentin.v2.ast.ASTHandler;

public class ImpactRoute implements Route {

    private ImpactHandler impactsHandler;
    private EvolutionHandler evoHandler;
    private ASTHandler astHandler;
    private SourcesHandler sourcesHandler;
    private String minerId;

    public ImpactRoute(SourcesHandler srcH, ASTHandler astH, EvolutionHandler evoH, ImpactHandler impactH,
            String minerId) {
        this.sourcesHandler = srcH;
        this.astHandler = astH;
        this.evoHandler = evoH;
        this.impactsHandler = impactH;
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
        // res.raw().sendError(102);
        // res.raw().sendError(102); // TODO test if it works (maybe other codes also
        // work?)
        if(body==null){
            JsonObject tmp = new JsonObject();
            tmp.addProperty("error", "wrong request");
            r = tmp;
        }else if (body.before != null && body.after != null) {
            String before = new String(Base64.getDecoder().decode(body.before));
            String after = new String(Base64.getDecoder().decode(body.after));
            JsonObject tmp = new JsonObject();
            tmp.addProperty("error", "simplified impact handler not implemented yet");
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
            r = impactsHandler.handle(impactsHandler.buildSpec(astHandler.buildSpec(srcSpec, body.commitIdBefore),
                    evoHandler.buildSpec(srcSpec, body.commitIdBefore, body.commitIdAfter), minerId)).toJson();
        } else if (body.repo != null && body.tagBefore != null && body.tagAfter != null) {
            JsonObject tmp = new JsonObject();
            tmp.addProperty("error", "tag impact handler not implemented yet");
            r = tmp;
        } else {
            JsonObject tmp = new JsonObject();
            tmp.addProperty("error", "wrong request");
            r = tmp;
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

    // public Set<Impacts> handle(String repo, String commitId) {
    // Set<Impacts> impacts = impact_store.get(new Impacts.Specifier(repo,
    // commitId));
    // if (impacts == null) {
    // AST ast = ast_store.get(new AST.Specifier(repo, commitId));
    // if (ast == null) {
    // // ast = ASTHandler()
    // }
    // impacts = handler.compute(ast);

    // }
    // return null;
    // }

    // public Set<Impacts> handle(String repo, String commitId, String
    // startingPoints) {
    // Set<Impacts> impacts = handler.compute(ast_store.get(new AST.Specifier(repo,
    // commitId)),
    // evo_store.get(new Evolutions.Specifier(repo, commitId)));
    // return null;
    // }

}