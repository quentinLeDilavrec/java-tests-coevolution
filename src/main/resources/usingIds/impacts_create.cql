WITH $data as data, $tool as tool
MERGE (t:Tool {name:tool.name, version:tool.version})
WITH data as data, t as t
UNWIND data as e

CREATE (wanted:Impact)

CREATE (wanted)-[:WAS_MINED_BY]->(t)

WITH e as e, wanted as wanted
UNWIND e.evolutions as x
MATCH (evo:Evolution)
WHERE ID(evo) = x.id
CREATE (wanted)-[:APPLY]->(evo)
WITH distinct wanted as wanted, e as e

UNWIND e.testsSame as x
MATCH (r:Range)
WHERE ID(r) = x.id
CREATE (wanted)-[imp:IMPACT]->(r)
SET imp.failureLog = x.report.what, imp.failWhere = x.report.`where`, imp.failWhen = x.report.`when`
WITH distinct wanted as wanted, e as e

UNWIND e.testsChanged as x
MATCH (ra:Range)
WHERE ID(ra) = x.id
CREATE (wanted)-[imp:IMPACT]->(ra)
SET imp.failureLog = x.report.what, imp.failWhere = x.report.`where`, imp.failWhen = x.report.`when`
MATCH (rb:Range)
WHERE ID(rb) = x.before
MERGE (wanted)-[:TEST_BEFORE]->(rb)
MERGE (ra)-[:TEST_FROM]->(rb)