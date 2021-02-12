WITH $data as data
UNWIND data as e

UNWIND e.sources as x
OPTIONAL MATCH (r:Range)<-[rel:SOURCE]-(wanted:Dependency {type:e.type, hash:e.hash})
WHERE ID(r) = x.id AND rel.description = x.description
WITH distinct e as e, wanted as wanted, count(distinct r) as s
WITH distinct e as e, (CASE WHEN s = size(e.sources) THEN wanted ELSE null END) as wanted

UNWIND e.targets as x
OPTIONAL MATCH (r:Range)<-[rel:TARGET]-(wanted:Dependency {type:e.type, hash:e.hash})
WHERE ID(r) = x.id AND rel.description = x.description
WITH distinct e as e, wanted as wanted, count(distinct r) as s
WITH distinct e as e, (CASE WHEN s = size(e.targets) THEN wanted ELSE null END) as wanted

WITH distinct e as e, head(collect(ID(wanted))) as id
RETURN id