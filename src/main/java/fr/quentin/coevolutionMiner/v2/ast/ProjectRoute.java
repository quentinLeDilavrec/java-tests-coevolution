package fr.quentin.coevolutionMiner.v2.ast;

import fr.quentin.coevolutionMiner.v2.sources.SourcesHandler;
import spark.Request;
import spark.Response;
import spark.Route;

public class ProjectRoute implements Route {

    private SourcesHandler sourcesHandler;
    private ProjectHandler astHandler;
    private String minerId;

    public ProjectRoute(SourcesHandler sourcesHandler, ProjectHandler astHandler, String minerId) {
        this.sourcesHandler = sourcesHandler;
        this.astHandler = astHandler;
        this.minerId = minerId;
    }

    @Override
    public Object handle(Request request, Response response) throws Exception {
        // TODO Auto-generated method stub
        return null;
    }

}