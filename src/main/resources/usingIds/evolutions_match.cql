WITH $data as data
UNWIND data as e

UNWIND e.before as x
OPTIONAL MATCH (r:Range)<-[rel:BEFORE]-(wanted:Evolution {type:e.type})
WHERE ID(r) = x.id AND rel.description = x.description
WITH distinct e as e, wanted as wanted, count(distinct r) as s
WHERE s = size(e.before)

UNWIND e.after as x
OPTIONAL MATCH (r:Range)<-[rel:AFTER]-(wanted:Evolution {type:e.type})
WHERE ID(r) = x.id AND rel.description = x.description
WITH distinct e as e, wanted as wanted, count(distinct r) as s
WHERE s = size(e.after)

WITH collect(ID(wanted)) as ids
WITH distinct e as e, (CASE WHEN size(ids)>0 THEN ids[0] ELSE null END) as id
RETURN id