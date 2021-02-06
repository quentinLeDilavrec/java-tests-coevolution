WITH $data as data
UNWIND data as e

UNWIND e.causes as evoId
OPTIONAL MATCH (evo:Evolution)<-[rel:CAUSE]-(wanted:CoEvolution)
WHERE ID(evo) = evoId
WITH distinct e as e, wanted as wanted, count(distinct evo) as s
WHERE s = size(e.causes)

UNWIND e.resolutions as evoId
OPTIONAL MATCH (evo:Evolution)<-[rel:RESOLUTION]-(wanted:CoEvolution)
WHERE ID(evo) = evoId
WITH distinct e as e, wanted as wanted, count(distinct evo) as s
WHERE s = size(e.resolutions)

WITH distinct e as e, collect(ID(wanted)) as ids
RETURN (CASE WHEN size(ids)>0 THEN ids[0] ELSE null) as id