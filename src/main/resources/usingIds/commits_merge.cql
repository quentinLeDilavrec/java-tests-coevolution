WITH $data as data
UNWIND data as e

MERGE (repo:Repository {repository:e.repository})

MERGE (commit:Commit {repository:e.repository, commitId:e.commitId})
MERGE (commit)-[:IS_COMMIT_OF]->(repo)
MERGE (commit)<-[:CONTAIN_COMMIT]-(repo)

WITH e as e, commit as commit
UNWIND e.parents as commitId
MERGE (parent:Commit {repository:e.repository, commitId:commitId})
MERGE (parent)-[:IS_COMMIT_OF]->(repo)
MERGE (parent)<-[:CONTAIN_COMMIT]-(repo)

MERGE (parent)<-[:PARENT]-(commitC)