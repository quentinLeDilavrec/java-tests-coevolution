WITH $data as data
UNWIND data as e

UNWIND e.sources as x
OPTIONAL MATCH (r:Range)<-[rel:SOURCE]-(wanted:Dependency {type:e.type})
WHERE ID(r) = x.id AND rel.description = x.description
WITH distinct e as e, wanted as wanted, count(distinct r) as s
WHERE s = size(e.sources)

UNWIND e.targets as x
OPTIONAL MATCH (r:Range)<-[rel:TARGET]-(wanted:Dependency {type:e.type})
WHERE ID(r) = x.id AND rel.description = x.description
WITH distinct e as e, wanted as wanted, count(distinct r) as s
WHERE s = size(e.targets)

WITH distinct e as e, collect(ID(wanted)) as ids
RETURN (CASE WHEN size(ids)>0 THEN ids[0] ELSE null END) as id