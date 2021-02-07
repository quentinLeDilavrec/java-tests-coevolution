WITH $data as data, $tool as tool
MERGE (t:Tool {name:tool.name, version:tool.version})
WITH data as data, t as t
UNWIND data as e

MATCH (wanted)
WHERE ID(wanted) = e.id
SET wanted += e.more

MERGE (wanted)-[:WAS_MINED_BY]->(t)