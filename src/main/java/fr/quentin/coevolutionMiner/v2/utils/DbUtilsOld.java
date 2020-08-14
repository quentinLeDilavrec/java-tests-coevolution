package fr.quentin.coevolutionMiner.v2.utils;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import fr.quentin.impactMiner.Evolution;
import fr.quentin.impactMiner.ImpactElement;
import fr.quentin.impactMiner.ImpactType;
import fr.quentin.impactMiner.Impacts.Relations;
import fr.quentin.impactMiner.Position;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtMethod;

public class DbUtilsOld {

	public static Set<Map<String, String>> computeRelationInfo(Evolution<?> evo, Position cause_pos, CtElement content,
			Position effect_pos) {
		Set<Map<String, String>> r = new HashSet<>();
		// if (evo instanceof OtherEvolution) {
		// 	OtherEvolution oevo = (OtherEvolution) evo;
		// 	Set<String> descs = new HashSet<>();
		// 	for (Entry<String, Set<Position>> entry : oevo.getPreEvolutionPositionsDesc().entrySet()) {
		// 		if (entry.getValue().contains(cause_pos)) {
		// 			descs.add(entry.getKey());
		// 			break;
		// 		}
		// 	}
		// 	if (descs.size() == 0) {
		// 		throw new RuntimeException("no corresponding position found");
		// 	}
		// 	String adjusment = DbUtils.adjustment(cause_pos, effect_pos);
		// 	for (String desc : descs) {
		// 		HashMap<String, String> info = new HashMap<>();
		// 		info.put("causeDesc", desc);
		// 		info.put("adjustment", adjusment);
		// 		r.add(info);
		// 	}
		// }
		return r;
	}

	public static String adjustment(Position cause_pos, Position effect_pos) {
		String adjusment;
		if (cause_pos.getFilePath().equals(effect_pos.getFilePath())) {
			if (cause_pos.getStart() == effect_pos.getStart()
					&& (effect_pos.getEnd() == cause_pos.getEnd() || effect_pos.getEnd() == cause_pos.getEnd() + 1)) {
				// effect equals cause
				adjusment = "equals";
			} else if (cause_pos.getStart() < effect_pos.getStart() && effect_pos.getEnd() < cause_pos.getEnd()) {
				// effect included in cause
				adjusment = "included";
			} else if (effect_pos.getStart() < cause_pos.getStart() && cause_pos.getEnd() < effect_pos.getEnd()) {
				// cause included in effect
				adjusment = "including";
			} else if (cause_pos.getEnd() < effect_pos.getStart()) {
				// cause before effect
				adjusment = "after";
			} else if (cause_pos.getStart() > effect_pos.getEnd()) {
				// cause after effect
				adjusment = "before";
			} else {
				// else not covered
				adjusment = "mixed";
			}
		} else {
			adjusment = "different files";
		}
		return adjusment;
	}

	public static List<Object> makeImpact(String repository, String commitIdBefore, Path rootDir, ImpactElement cause,
			CtMethod<?> parentTestMethod, String type) {
		List<Object> res = new ArrayList<>();

		List<Object> causes = new ArrayList<>();
		List<Object> effects = new ArrayList<>();
		Map<String, Object> callImpact = new HashMap<>();
		res.add(callImpact);
		Position cause_pos = Utils.temporaryFix(cause.getPosition(), rootDir);
		Position effect_pos = Utils.temporaryFix(
				new Position(parentTestMethod.getPosition().getFile().getPath(),
						parentTestMethod.getPosition().getSourceStart(), parentTestMethod.getPosition().getSourceEnd()),
				rootDir);

		Map<String, Object> content = new HashMap<>();
		callImpact.put("content", content);
		content.put("type", type);
		content.put("repository", repository);
		content.put("commitId", commitIdBefore);
		content.put("file", effect_pos.getFilePath());
		content.put("start", effect_pos.getStart());
		content.put("end", effect_pos.getEnd());

		callImpact.put("effects", effects);
		Map<String, Object> o_e = new HashMap<>();
		effects.add(o_e);
		o_e.put("type", parentTestMethod.getClass().getSimpleName());
		o_e.put("repository", repository);
		o_e.put("commitId", commitIdBefore);
		o_e.put("file", effect_pos.getFilePath());
		o_e.put("start", effect_pos.getStart());
		o_e.put("end", effect_pos.getEnd());
		o_e.put("sig", parentTestMethod.getSignature());
		if (Utils.isTest(parentTestMethod)) {
			o_e.put("isTest", true);
		} else if (Utils.isParentTest(parentTestMethod)) {
			o_e.put("isTest", "parent");
		}
		callImpact.put("causes", causes);
		Map<String, Object> o_c = new HashMap<>();
		causes.add(o_c);
		o_c.put("type", cause.getContent().getClass().getSimpleName());
		o_c.put("repository", repository);
		o_c.put("commitId", commitIdBefore);
		o_c.put("file", cause_pos.getFilePath());
		o_c.put("start", cause_pos.getStart());
		o_c.put("end", cause_pos.getEnd());
		if (Utils.isTest(cause.getContent())) {
			o_c.put("isTest", true);
		} else if (Utils.isParentTest(cause.getContent())) {
			o_c.put("isTest", "parent");
		}
		return res;
	}

	public static Collection<? extends Map<String, Object>> makeImpact(String repository, String commitIdBefore, Path rootDir,
			ImpactElement cause, ImpactElement effect, String type, String uniqueness) {

		List<Map<String, Object>> res = new ArrayList<>();
		Map<String, Object> callImpact = new HashMap<>();
		res.add(callImpact);

		Position cause_pos = Utils.temporaryFix(cause.getPosition(), rootDir);
		Position effect_pos = Utils.temporaryFix(effect.getPosition(), rootDir);

		// Content
		Map<String, Object> content = new HashMap<>();
		callImpact.put("content", content);
		content.put("type", type);
		content.put("repository", repository);
		content.put("commitId", commitIdBefore);
		Position tmp = uniqueness.equals("effect") ? effect_pos : cause_pos;
		content.put("file", tmp.getFilePath());
		content.put("start", tmp.getStart());
		content.put("end", tmp.getEnd());
		// effects
		List<Object> effects = new ArrayList<>();
		callImpact.put("effects", effects);
		Map<String, Object> o_e = new HashMap<>();
		effects.add(o_e);
		o_e.put("type", effect.getContent().getClass().getSimpleName());
		o_e.put("repository", repository);
		o_e.put("commitId", commitIdBefore);
		o_e.put("file", effect_pos.getFilePath());
		o_e.put("start", effect_pos.getStart());
		o_e.put("end", effect_pos.getEnd());
		if (effect.getContent() instanceof CtExecutable) {
			o_e.put("sig", ((CtExecutable) effect.getContent()).getSignature());
		}
		if (Utils.isTest(effect.getContent())) {
			o_e.put("isTest", true);
		} else if (Utils.isParentTest(effect.getContent())) {
			o_e.put("isTest", "parent");
		}
		// causes
		List<Object> causes = new ArrayList<>();
		callImpact.put("causes", causes);
		Map<String, Object> o_c = new HashMap<>();
		causes.add(o_c);
		o_c.put("type", cause.getContent().getClass().getSimpleName());
		o_c.put("repository", repository);
		o_c.put("commitId", commitIdBefore);
		o_c.put("file", cause_pos.getFilePath());
		o_c.put("start", cause_pos.getStart());
		o_c.put("end", cause_pos.getEnd());
		if (effect.getContent() instanceof CtExecutable) {
			o_c.put("sig", ((CtExecutable) effect.getContent()).getSignature());
		}
		if (Utils.isTest(cause.getContent())) {
			o_c.put("isTest", true);
		} else if (Utils.isParentTest(cause.getContent())) {
			o_c.put("isTest", "parent");
		}
		return res;
	}

	public static Map<String, Object> makeImpact(Map<String, Object> content, List<Map<String, Object>> causes,
			List<Map<String, Object>> effects) {
		Map<String, Object> callImpact = new HashMap<>();
		// Content
		callImpact.put("content", content);
		// effects
		callImpact.put("effects", effects);
		// causes
		callImpact.put("causes", causes);
		return callImpact;
	}

	public static Map<String, Object> makeRange(String repository, String commitId, Path rootDir, ImpactElement element
	// List<Map<String,Object>> res
	) {
		Position position = Utils.temporaryFix(element.getPosition(), rootDir);
		Map<String, Object> o = makeRange(repository, commitId, position);
		o.put("type", element.getContent().getClass().getSimpleName());
		if (element.getContent() instanceof CtMethod) {
			o.put("sig", ((CtMethod) element.getContent()).getSignature());
		}
		if (Utils.isTest(element.getContent())) {
			o.put("isTest", true);
		} else if (Utils.isParentTest(element.getContent())) {
			o.put("isTest", "parent");
			// res.addAll(makeImpact(repository, commitIdBefore, rootDir, element,
			// element.getContent().getParent(CtMethod.class), "expand to test"));
		}
		return o;
	}

	public static Map<String, Object> makeRange(String repository, String commitId, Position position) {
		Map<String, Object> o = new HashMap<>();
		o.put("repository", repository);
		o.put("commitId", commitId);
		o.put("file", position.getFilePath());
		o.put("start", position.getStart());
		o.put("end", position.getEnd());
		return o;
	}

	public static Map<String, Object> makeContent(String repository, String commitId, Path rootDir,
			ImpactElement element) {
		Position position = Utils.temporaryFix(element.getPosition(), rootDir);
		return makeContent(repository, commitId, position);
	}

	public static Map<String, Object> makeContent(String repository, String commitId,
			Position position) {
		Map<String, Object> content = new HashMap<>();
		content.put("repository", repository);
		content.put("commitId", commitId);
		content.put("file", position.getFilePath());
		content.put("start", position.getStart());
		content.put("end", position.getEnd());
		return content;
	}

	public static List<Map<String, Object>> basifyImpact(String repository, String commitId, Path cause_rootDir,
	        ImpactElement rootCause, Relations vertice) {
	    List<Map<String, Object>> res = new ArrayList<>();
	
	    for (Entry<ImpactType, Set<ImpactElement>> entry : vertice.getEffects().entrySet()) {
	        if (entry.getKey().equals(ImpactType.CALL)) {
	
	            Map<String, Object> content = makeContent(repository, commitId, cause_rootDir,
	                    vertice.getVertice());
	            content.put("type", "call impact");
	
	            List<Map<String, Object>> causes = new ArrayList<>();
	            Map<String, Object> o_c = makeRange(repository, commitId, cause_rootDir,
	                    vertice.getVertice());
	            causes.add(o_c);
	
	            List<Map<String, Object>> effects = new ArrayList<>();
	            for (ImpactElement e : entry.getValue()) {
	                Map<String, Object> o = makeRange(repository, commitId, cause_rootDir, e);
	                effects.add(o);
	            }
	            res.add(makeImpact(content, causes, effects));
	        } else if (entry.getKey().equals(ImpactType.EXPAND)) {
	            for (ImpactElement elem : entry.getValue()) {
	                res.addAll(makeImpact(repository, commitId, cause_rootDir, vertice.getVertice(), elem,
	                        "expand to executable", "effect"));
	            }
	        }
	    }
	    return res;
	}

	public static List<Map<String, Object>> basifyRootCauseImpact(String repository, String commitId, Path cause_rootDir,
	        ImpactElement rootCause, List<Map<String, Object>> ranges_to_type) {
	    List<Map<String, Object>> res = new ArrayList<>();
	    List<Map<String, Object>> causes = new ArrayList<>();
	    Position effect_pos = Utils.temporaryFix(rootCause.getPosition(), cause_rootDir);
	    // for (Entry<Evolution<Object>, Position> entry : rootCause.getEvolutionWithNonCorrectedPosition().entrySet()) {
	    //     // TODO add evolution in graph? would need to pull-up helper from
	    //     // Neo4jEvolutionStorage.java
	    //     Position cause_pos = entry.getValue();
	    //     for (Map<String, String> elem : computeRelationInfo(entry.getKey(), cause_pos,
	    //             rootCause.getContent(), effect_pos)) {
	
	    //         Map<String, Object> o_c = makeRange(repository, commitId, cause_pos);
	    //         o_c.put("relationInfo", elem);
	    //         if (rootCause.getContent() instanceof CtMethod) {
	    //             o_c.put("sig", ((CtMethod) rootCause.getContent()).getSignature());
	    //         }
	    //         if (Utils.isTest(rootCause.getContent())) {
	    //             // o_c.put("isTest", "parent"); // TODO check it
	    //         } else if (Utils.isParentTest(rootCause.getContent())) {
	    //             o_c.put("isTest", "parent");
	    //         }
	    //         if (effect_pos.equals(cause_pos))
	    //             ranges_to_type.add(o_c);
	    //         else
	    //             causes.add(o_c);
	    //     }
	
	    // }
	
	    if (causes.size() <= 0)
	        return res;
	
	    Map<String, Object> content = makeContent(repository, commitId, effect_pos);
	    content.put("type", "rescope");
	
	    List<Map<String, Object>> effects = new ArrayList<>();
	    effects.add(makeRange(repository, commitId, cause_rootDir, rootCause));
	
	    res.add(makeImpact(content, causes, effects));
	
	    return res;
	}

}