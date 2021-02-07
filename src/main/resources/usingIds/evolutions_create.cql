WITH $data as data, $tool as tool
CREATE (wanted)-[:WAS_MINED_BY]->(t)
UNWIND data as e

WITH e as e, t

CREATE (wanted:Evolution {type:e.type})

MERGE (t:Tool {name:tool.name, version:tool.version})

UNWIND e.before as dr
MATCH (r:Range)
WHERE ID(r) = dr.id
CREATE (wanted)-[:BEFORE {description:dr.description}]->(r)
WITH distinct wanted as wanted, e as e

UNWIND e.after as dr
MATCH (r:Range)
WHERE ID(r) = dr
CREATE (wanted)-[:AFTER {description:dr.description}]->(r)