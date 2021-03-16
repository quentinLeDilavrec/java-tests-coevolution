WITH $data as data
UNWIND data as l
MERGE (repository:Repository {repository:l.repository})
MERGE (commit:Commit {repository:l.repository, commitId:l.commitId})
MERGE (commit)-[:IS_COMMIT_OF]->(repository)
MERGE (snap:FileSnapshot {path:l.file, repository:l.repository, commitId:l.commitId})
MERGE (snap)-[:IS_SNAPSHOT_IN]->(commit)
MERGE (r:Range {
    repository:l.repository,
    commitId:l.commitId, 
    path:l.file,
    start:toInteger(l.start), 
    end:toInteger(l.end)})
ON MATCH SET r.type = l.type, r.isTest = l.isTest, r.astPath = l.astPath, r.value = l.value, r.range = [toInteger(l.start),toInteger(l.end)]
ON CREATE SET r.type = l.type, r.isTest = l.isTest, r.astPath = l.astPath, r.value = l.value, r.range = [toInteger(l.start),toInteger(l.end)]
MERGE (r)-[:IS_RANGE_IN]->(snap)
RETURN distinct ID(r) as id