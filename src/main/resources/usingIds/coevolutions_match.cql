WITH $data as data
UNWIND data as e

UNWIND (CASE WHEN size(e.causes)>0 THEN e.causes ELSE [{id:-1}] END) as x
OPTIONAL MATCH (evo:Evolution)<-[rel:CAUSE]-(wanted:CoEvolution, hash:e.hash)
WHERE x.id=-1 OR ID(evo) = x.id
WITH distinct e as e, wanted as wanted, count(distinct rel) as s
WITH distinct e as e, (CASE WHEN 0 = size(e.causes) OR s = size(e.causes) THEN wanted ELSE null END) as wanted

UNWIND (CASE WHEN size(e.resolutions)>0 THEN e.resolutions ELSE [{id:-1}] END) as x
OPTIONAL MATCH (evo:Evolution)<-[rel:RESOLUTION]-(wanted:CoEvolution, hash:e.hash)
WHERE x.id=-1 OR ID(evo) = x.id
WITH distinct e as e, wanted as wanted, count(distinct rel) as s
WITH distinct e as e, (CASE WHEN 0 = size(e.resolutions) OR s = size(e.resolutions) THEN wanted ELSE null END) as wanted

WITH distinct e as e, head(collect(ID(wanted))) as id
RETURN id