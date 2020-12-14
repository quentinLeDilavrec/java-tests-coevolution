WITH $coevoSimp as coevoSimp
UNWIND coevoSimp as coevo
WITH coevo as coevo
MERGE (coevolution:CoEvolution {causes:coevo.content.causes, resolutions:coevo.content.resolutions})
ON CREATE SET coevolution.validated = coevo.content.validated
ON MATCH SET coevolution.validated = coevo.content.validated

MERGE (t:Tool {name:$tool})
ON CREATE SET t.name = $tool

MERGE (coevolution)-[rel:WAS_MINED_BY]->(t)
ON CREATE SET rel.validated = coevo.content.validated
ON MATCH SET rel.validated = coevo.content.validated

WITH coevolution as coevolution, coevo.pointed as pointed
UNWIND pointed as l
WITH l as l, coevolution as coevolution
// MERGE (evo:Evo {url:l.content.url})
CALL apoc.merge.node(['Evo'], l.content.content) YIELD node as evo

WITH l as l, coevolution as coevolution, evo as evo
MERGE (coevolution)-[:COMPOSE {type:l.type}]->(evo)