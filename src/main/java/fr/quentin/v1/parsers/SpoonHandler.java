package fr.quentin.v1.parsers;

import gumtree.spoon.AstComparator;
import gumtree.spoon.builder.Json4SpoonGenerator;
import spark.QueryParamsMap;

public class SpoonHandler implements AstRoute {

    @Override
    public Object simplifiedHandler(String s, QueryParamsMap queryParamsMap) {
        try {
            Json4SpoonGenerator x = new Json4SpoonGenerator();
            AstComparator comp = new AstComparator();
            return x.getJSONasJsonObject(comp.getCtType(s));
        } catch (Exception ee) {
            System.err.println(ee);
            return "{\"error\":\"" + ee.toString() + "\"}";
        }
    }

}