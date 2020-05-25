UNWIND $commits as commit
MERGE (repo:Repository {url:commit.repository})
MERGE (curr:Commit {repo:commit.repository, sha1:commit.sha1})
MERGE (curr)-[:IS_COMMIT_OF]->(repo)
MERGE (curr)<-[:CONTAIN_COMMIT]-(repo)

FOREACH (x IN commit.children |
	MERGE (aaa:Commit {repo:commit.repository, sha1:x})
	MERGE (aaa)-[:IS_COMMIT_OF]->(repo)
	MERGE (aaa)<-[:CONTAIN_COMMIT]-(repo)
	MERGE (aaa)-[:PARENT]->(curr)
)
FOREACH (x IN commit.parents |
	MERGE (aaa:Commit {repo:commit.repository, sha1:x})
	MERGE (aaa)-[:IS_COMMIT_OF]->(repo)
	MERGE (aaa)<-[:CONTAIN_COMMIT]-(repo)
	MERGE (curr)-[:PARENT]->(aaa)
)