package fr.quentin.v2.sources;

import spark.Request;
import spark.Response;
import spark.Route;

public class SourcesRoute implements Route {

    private SourcesHandler sourcesHandler;
    private String minerId;

    public SourcesRoute(SourcesHandler sourcesHandler, String minerId) {
        this.sourcesHandler = sourcesHandler;
        this.minerId = minerId;
    }

    @Override
    public Object handle(Request request, Response response) throws Exception {
        // TODO Auto-generated method stub
        return null;
    }

}