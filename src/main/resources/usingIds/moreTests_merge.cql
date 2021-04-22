WITH $data as data
UNWIND data as test

MERGE (repository:Repository {repository:test.repository})
MERGE (commit:Commit {repository:test.repository, commitId:test.commitId})
MERGE (commit)-[:IS_COMMIT_OF]->(repository)
MERGE (snap:FileSnapshot {path:test.file, repository:test.repository, commitId:test.commitId})
SET snap.isTest = COALESCE(snap.isTest,test.isInTestCu)
MERGE (snap)-[:IS_SNAPSHOT_IN]->(commit)
MERGE (r:Range {range:[toInteger(test.start),toInteger(test.end)],
                start:toInteger(test.start), end:toInteger(test.end), 
                path:test.file, repository:test.repository, commitId:test.commitId})
ON MATCH SET r.isTest = test.isTest, r.type = test.type,
    r.failureLog = test.report.what, r.failWhen = COALESCE(test.report.`when`, false), r.failWhere = test.report.`where`,
    r.qualifiedName=test.qualifiedName,
    r.isMore=(r.failWhen is null)
ON CREATE SET r.isTest = test.isTest, r.type = test.type,
    r.failureLog = test.report.what, r.failWhen = COALESCE(test.report.`when`, false), r.failWhere = test.report.`where`,
    r.qualifiedName=test.qualifiedName,
    r.isMore=true
MERGE (r)-[:IS_RANGE_IN]->(snap)
RETURN ID(r)