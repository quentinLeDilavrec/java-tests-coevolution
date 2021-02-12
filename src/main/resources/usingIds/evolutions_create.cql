WITH $data as data, $tool as tool
MERGE (t:Tool {name:tool.name, version:tool.version})
WITH data as data, t as t
UNWIND data as e

CREATE (wanted:Evolution {type:e.type, hash:e.hash})

CREATE (wanted)-[:WAS_MINED_BY]->(t)

WITH e as e, wanted as wanted
UNWIND (CASE WHEN size(e.before)>0 THEN e.before ELSE [{id:-1}] END) as dr
OPTIONAL MATCH (r:Range)
WHERE ID(r) = dr.id
CALL apoc.do.when(
  r IS NOT NULL,
  'CREATE (wanted)-[:BEFORE {description:dr.description}]->(r) RETURN null',
  'RETURN null',
  {wanted:wanted, r:r, dr:dr}) YIELD value

WITH distinct wanted as wanted, e as e

UNWIND e.after as dr
MATCH (r:Range)
WHERE ID(r) = dr.id
CREATE (wanted)-[:AFTER {description:dr.description}]->(r)