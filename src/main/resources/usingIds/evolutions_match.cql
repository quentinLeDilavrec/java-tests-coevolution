WITH $data as data
UNWIND data as e

UNWIND (CASE WHEN size(e.before)>0 THEN e.before ELSE [{id:-1}] END) as x
OPTIONAL MATCH (r:Range)<-[rel:BEFORE]-(wanted:Evolution {type:e.type, hash:e.hash})
WHERE ID(r) = x.id AND rel.description = x.description
WITH distinct e as e, wanted as wanted, count(distinct rel) as s
WITH distinct e as e, (CASE WHEN s = size(e.before) THEN wanted ELSE null END) as wanted0

UNWIND (CASE WHEN size(e.after)>0 THEN e.after ELSE [{id:-1}] END) as x
OPTIONAL MATCH (r:Range)<-[rel:AFTER]-(wanted:Evolution {type:e.type, hash:e.hash})
WHERE ID(r) = x.id AND rel.description = x.description AND (size(e.before)=0 OR wanted=wanted0)
WITH distinct e as e, wanted as wanted, wanted0 as wanted0, count(distinct rel) as s
WITH distinct e as e, (CASE WHEN 0 = size(e.after) THEN wanted0 WHEN s<>0 AND s = size(e.after) THEN wanted ELSE null END) as wanted

WITH distinct e as e, head(collect(ID(wanted))) as id
RETURN id