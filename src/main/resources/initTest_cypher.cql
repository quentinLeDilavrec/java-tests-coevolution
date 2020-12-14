UNWIND $initTests as initTest

MERGE (r:Range {range:[toInteger(initTest.start),toInteger(initTest.end)], start:toInteger(initTest.start), end:toInteger(initTest.end), 
                path:initTest.file, repo:initTest.repository, commitId:initTest.commitId})
ON MATCH SET r.isTest = true, r.failureReport = initTest.what, r.failWhen = COALESCE(initTest.`when`, false), r.failWhere = initTest.`where`
ON CREATE SET r.isTest = true, r.failureReport = initTest.what, r.failWhen = COALESCE(initTest.`when`, false), r.failWhere = initTest.`where`