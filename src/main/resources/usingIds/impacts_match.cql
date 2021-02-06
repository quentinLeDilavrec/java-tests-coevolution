WITH $data as data
UNWIND data as e

UNWIND e.evolutions as x
OPTIONAL MATCH (r:Evolution)<-[:APPLY]-(wanted:Impact)
WHERE ID(r) = x.id
WITH distinct e as e, wanted as wanted, count(distinct r) as s
WHERE s = size(e.evolutions)

UNWIND e.testsSame as x
OPTIONAL MATCH (r:Range)<-[:IMPACT {failureLog: x.report.what, failAt: x.report.`when`}]-(wanted:Impact)
WHERE ID(r) = x.id
WITH distinct e as e, wanted as wanted, count(distinct r) as s
WHERE s = size(e.testsSame)

UNWIND e.testsChanged as x
OPTIONAL MATCH (ra:Range)<-[:IMPACT {failureLog: x.report.what, failAt: x.report.`when`}]-(wanted:Impact), (rb:Range)<-[:TEST_BEFORE]-(wanted:Impact)
WHERE ID(ra) = x.id AND ID(rb) = x.before AND EXISTS((rb)<-[:TEST_FROM]-(ra))
WITH distinct e as e, wanted as wanted, count(distinct ra) as s
WHERE s = size(e.testsChanged)

WITH distinct e as e, collect(ID(wanted)) as ids
RETURN (CASE WHEN size(ids)>0 THEN ids[0] ELSE null) as id