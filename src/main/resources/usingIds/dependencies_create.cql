WITH $data as data, $tool as tool
MERGE (t:Tool {name:tool.name, version:tool.version})
WITH data as data, t as t
UNWIND data as e

CREATE (wanted:Dependency {type:e.type, hash:e.hash})

CREATE (wanted)-[:WAS_MINED_BY]->(t)

WITH e as e, wanted as wanted
UNWIND e.sources as dr
MATCH (r:Range)
WHERE ID(r) = dr.id
CREATE (wanted)-[:SOURCE {description:dr.description}]->(r)
WITH distinct wanted as wanted, e as e

UNWIND e.targets as dr
MATCH (r:Range)
WHERE ID(r) = dr.id
CREATE (wanted)-[:TARGET {description:dr.description}]->(r)