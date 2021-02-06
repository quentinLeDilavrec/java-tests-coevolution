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

WITH distinct e as e, head(collect(ID(wanted))) as id
RETURN id