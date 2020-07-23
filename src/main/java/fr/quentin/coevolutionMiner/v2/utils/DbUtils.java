package fr.quentin.coevolutionMiner.v2.utils;

import java.util.HashMap;
import java.util.Map;

import fr.quentin.coevolutionMiner.v2.ast.Project.AST.FileSnapshot;
import fr.quentin.coevolutionMiner.v2.ast.Project.AST.FileSnapshot.Range;
// import fr.quentin.coevolutionMiner.v2.evolution.Evolutions.Evolution.DescRange;
import fr.quentin.coevolutionMiner.v2.impact.Impacts.Impact;

public class DbUtils {

	public static Map<String, Object> makeRange(Impact.DescRange descRange) {
	    Range range = descRange.getTarget();
	    Map<String, Object> o =range.toMap();
	    o.put("description", descRange.getDescription());
	    return o;
    }
    
}