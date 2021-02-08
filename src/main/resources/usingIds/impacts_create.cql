WITH $data as data, $tool as tool
MERGE (t:Tool {name:tool.name, version:tool.version})
WITH data as data, t as t
UNWIND data as e

CREATE (wanted:Impact)

CREATE (wanted)-[:WAS_MINED_BY]->(t)

WITH e as e, wanted as wanted
UNWIND CASE WHEN size(e.evolutions)>0 THEN e.evolutions ELSE [{id:-1}] END) as x
OPTIONAL MATCH (evo:Evolution)
WHERE ID(evo) = x.id
CALL apoc.do.when(
  evo IS NOT NULL,
  'CREATE (wanted)-[:APPLY]->(evo)',
  '',
  {wanted:wanted, evo:evo})
WITH distinct wanted as wanted, e as e

UNWIND e.testsSame as x
MATCH (r:Range)
WHERE ID(r) = x.id
CALL apoc.do.when(
  r IS NOT NULL,
  '(wanted)-[imp:IMPACT]->(r) SET imp.failureLog = x.report.what, imp.failWhere = x.report.`where`, imp.failWhen = x.report.`when`',
  '',
  {wanted:wanted, r:r})
CREATE 

WITH distinct wanted as wanted, e as e

UNWIND e.testsChanged as x
MATCH (ra:Range)
WHERE ID(ra) = x.id
CREATE (wanted)-[imp:IMPACT]->(ra)
SET imp.failureLog = x.report.what, imp.failWhere = x.report.`where`, imp.failWhen = x.report.`when`
WITH wanted as wanted, x as x
MATCH (rb:Range)
WHERE ID(rb) = x.before
MERGE (wanted)-[:TEST_BEFORE]->(rb)
MERGE (ra)-[:TEST_FROM]->(rb)