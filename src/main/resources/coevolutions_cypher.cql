WITH $json as data, $tool as tool
UNWIND data as coevo

MERGE (coevolution:CoEvolution {causes:coevo.content.causes, resolutions:coevo.content.resolutions})

MERGE (t:Tool {name:tool})
ON CREATE SET t.name = tool

MERGE (coevolution)-[:WAS_MINED_BY]->(t)

UNWIND coevo.pointed as l
WITH l as l, coevolution as coevolution
CALL apoc.merge.node(['Evo'], l.content) YIELD node as evo

MERGE (coevolution)-[:COMPOSE {type:l.type}]->(evo)