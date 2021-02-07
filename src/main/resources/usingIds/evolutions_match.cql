WITH $data as data
UNWIND data as e

UNWIND e.before as x
OPTIONAL MATCH (r:Range)<-[rel:BEFORE]-(wanted:Evolution {type:e.type})
WHERE ID(r) = x.id AND rel.description = x.description
WITH distinct e as e, wanted as wanted, count(distinct r) as s
WITH distinct e as e, (CASE WHEN s = size(e.before) THEN wanted ELSE null END) as wanted

UNWIND e.after as x
OPTIONAL MATCH (r:Range)<-[rel:AFTER]-(wanted:Evolution {type:e.type})
WHERE ID(r) = x.id AND rel.description = x.description
WITH distinct e as e, wanted as wanted, count(distinct r) as s
WITH distinct e as e, (CASE WHEN s = size(e.after) THEN wanted ELSE null END) as wanted

WITH distinct e as e, head(collect(ID(wanted))) as id
RETURN id