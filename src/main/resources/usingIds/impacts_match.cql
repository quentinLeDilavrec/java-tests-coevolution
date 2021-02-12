WITH $data as data
UNWIND data as e

UNWIND (CASE WHEN size(e.evolutions)>0 THEN e.evolutions ELSE [{id:-1}] END) as x
OPTIONAL MATCH (r:Evolution)<-[rel:APPLY]-(wanted:Impact {hash:e.hash})
WHERE ID(r) = x.id OR x.id=-1
WITH distinct e as e, wanted as wanted, count(distinct rel) as s
WITH distinct e as e, (CASE WHEN 0 = size(e.evolutions) OR s = size(e.evolutions) THEN wanted ELSE null END) as wanted

UNWIND (CASE WHEN size(e.testsSame)>0 THEN e.testsSame ELSE [{id:-1}] END) as x
OPTIONAL MATCH (r:Range)<-[rel:IMPACT]-(wanted:Impact {hash:e.hash})
WHERE ID(r) = x.id OR x.id=-1
WITH distinct e as e, wanted as wanted, count(distinct rel) as s
WITH distinct e as e, (CASE WHEN 0 = size(e.testsSame) OR s = size(e.testsSame) THEN wanted ELSE null END) as wanted

UNWIND (CASE WHEN size(e.testsChanged)>0 THEN e.testsChanged ELSE [{id:-1}] END) as x
OPTIONAL MATCH (ra:Range)<-[rel:IMPACT]-(wanted:Impact {hash:e.hash}), (rb:Range)<-[:TEST_BEFORE]-(wanted:Impact {hash:e.hash})
WHERE x.id=-1 OR (ID(ra) = x.id AND ID(rb) = x.before AND EXISTS((rb)<-[:TEST_FROM]-(ra)))
WITH distinct e as e, wanted as wanted, count(distinct rel) as s
WITH distinct e as e, (CASE WHEN 0 = size(e.testsChanged) OR s = size(e.testsChanged) THEN wanted ELSE null END) as wanted

WITH distinct e as e, head(collect(ID(wanted))) as id
RETURN id