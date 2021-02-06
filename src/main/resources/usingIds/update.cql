WITH $data as data, $tool as tool
MERGE (t:Tool {name:tool.name, version:tool.version})
UNWIND data as e

MATCH (wanted)
WHERE ID(wanted) = e.id
SET wannted += e.content

MERGE (wanted)-[:WAS_MINED_BY]->(t)