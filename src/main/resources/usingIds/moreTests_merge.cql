WITH $data as data
UNWIND data as test

MERGE (r:Range {range:[toInteger(test.start),toInteger(test.end)],
                start:toInteger(test.start), end:toInteger(test.end), 
                path:test.file, repository:test.repository, commitId:test.commitId})
ON MATCH SET r.isTest = test.isTest, r.type = test.type,
    r.failureLog = test.report.what, r.failWhen = COALESCE(test.report.`when`, false), r.failWhere = test.report.`where`,
    r.signature=test.signature,
    r.isMore=(r.failWhen is null)
ON CREATE SET r.isTest = test.isTest, r.type = test.type,
    r.failureLog = test.report.what, r.failWhen = COALESCE(test.report.`when`, false), r.failWhere = test.report.`where`,
    r.signature=test.signature,
    r.isMore=true
RETURN ID(r)