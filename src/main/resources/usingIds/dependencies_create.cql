WITH $data as data, $tool as tool
MERGE (t:Tool {name:tool.name, version:tool.version})
UNWIND data as e

CREATE (wanted:Dependency)

CREATE (wanted)-[:WAS_MINED_BY]->(t)

UNWIND e.sources as dr
MATCH (r:Range)
WHERE ID(r) = dr.id
CREATE (wanted)-[:SOURCE {description:dr.description}]->(r)
WITH distinct wanted as wanted, e as e

UNWIND e.targets as dr
MATCH (r:Range)
WHERE ID(r) = dr.id
CREATE (wanted)-[:TARGET {description:dr.description}]->(r)