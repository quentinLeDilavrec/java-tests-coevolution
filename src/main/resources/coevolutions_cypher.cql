WITH $json as data, $tool as tool
UNWIND data as coevo

MERGE (coevolution:CoEvolution {causes:coevo.content.causes, resolutions:coevo.content.resolutions})

FOREACH (content IN coevo.causes |

//	WITH l as content, coevolution as coevolution
	CALL apoc.merge.node(['Evo'], content) YIELD node as evo

    MERGE (coevolution)-[:COEVO_CAUSE]->(evo)
)

FOREACH (content IN coevo.resolutions |

//	WITH l as content, coevolution as coevolution
	CALL apoc.merge.node(['Evo'], content) YIELD node as evo

    MERGE (coevolution)-[:RESOLUTIONS]->(evo)
)

MERGE (t:Tool {name:tool})
ON CREATE SET t.name = tool

MERGE (coevolution)-[:WAS_MINED_BY]->(t)