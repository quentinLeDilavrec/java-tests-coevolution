package fr.quentin.coevolutionMiner.v2.utils;

import java.util.HashMap;
import java.util.Map;

import fr.quentin.coevolutionMiner.v2.ast.Project.AST.FileSnapshot;
import fr.quentin.coevolutionMiner.v2.ast.Project.AST.FileSnapshot.Range;
import fr.quentin.coevolutionMiner.v2.dependency.Dependencies.Dependency;

public class DbUtils {

	public static Map<String, Object> makeRange(Dependency.DescRange descRange) {
		Range range = descRange.getTarget();
		Map<String, Object> o = range.toMap();
		o.put("description", descRange.getDescription());
		return o;
	}

}