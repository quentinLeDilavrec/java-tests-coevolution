WITH $data as data, $tool as tool
MERGE (t:Tool {name:tool.name, version:tool.version})
WITH data as data, t as t
UNWIND data as e

CREATE (wanted:CoEvolution {hash:e.hash})

CREATE (wanted)-[:WAS_MINED_BY]->(t)

WITH e as e, wanted as wanted
UNWIND e.causes as x
MATCH (evo:Evolution)
WHERE ID(evo) = x.id
CREATE (wanted)-[:CAUSE]->(evo)
WITH distinct wanted as wanted, e as e

UNWIND e.resolutions as x
MATCH (evo:Evolution)
WHERE ID(evo) = x.id
CREATE (wanted)-[:RESOLUTION]->(evo)