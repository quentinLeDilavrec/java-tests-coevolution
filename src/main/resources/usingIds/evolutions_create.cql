WITH $data as data, $tool as tool
CREATE (wanted)-[:WAS_MINED_BY]->(t)
UNWIND data as e

WITH e as e, tool as tool, keys(e.before) as bk

CREATE (wanted:Evolution {type:e.type})

MERGE (t:Tool {name:tool.name, version:tool.version})


MATCH (r:Range)
WHERE ID(r) IN bk
CREATE (wanted)-[:BEFORE {description:e.before[ID(r)].description}]->(r)
WITH distinct wanted as wanted, e as e, keys(e.after) as ak

MATCH (r:Range)
WHERE ID(r) IN ak
CREATE (wanted)-[:AFTER {description:e.after[ID(r)].description}]->(r)