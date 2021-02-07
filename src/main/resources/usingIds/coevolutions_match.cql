WITH $data as data
UNWIND data as e

UNWIND e.causes as evoId
OPTIONAL MATCH (evo:Evolution)<-[rel:CAUSE]-(wanted:CoEvolution)
WHERE ID(evo) = evoId
WITH distinct e as e, wanted as wanted, count(distinct evo) as s
WITH distinct e as e, (CASE WHEN s = size(e.causes) THEN wanted ELSE null END) as wanted

UNWIND e.resolutions as evoId
OPTIONAL MATCH (evo:Evolution)<-[rel:RESOLUTION]-(wanted:CoEvolution)
WHERE ID(evo) = evoId
WITH distinct e as e, wanted as wanted, count(distinct evo) as s
WITH distinct e as e, (CASE WHEN s = size(e.resolutions) THEN wanted ELSE null END) as wanted

WITH distinct e as e, head(collect(ID(wanted))) as id
RETURN id