WITH $json as data, $tool as tool
UNWIND data as e

MERGE (repo:Repository {url:e.content.repository})

MERGE (commit:Commit {repo:e.content.repository, sha1:e.content.commitId})
MERGE (commit)-[:IS_COMMIT_OF]->(repo)
MERGE (commit)<-[:CONTAIN_COMMIT]-(repo)

MERGE (project:Project {
    groupId:e.content.groupId,
    artifactId:e.content.artifactId
})

MERGE (project)-[rel:RELEASED_FROM {
    version:e.version,
    packaging:e.packaging,
    id:e.id
}]->(commit)
ON MATCH SET 
    rel.path = e.content.path,
    rel.srcs = e.content.srcs

FOREACH (l IN e.dependencies |
	MERGE (snap:FileSnapshot {path:l.filePath, repo:e.content.repository, commitId:e.content.commitIdBefore})
	MERGE (snap)-[:IS_SNAPSHOT_IN]->(commit)
	MERGE (r:Range {range:[toInteger(l.start),toInteger(l.end)], start:toInteger(l.start), end:toInteger(l.end), path:l.filePath, repo:e.content.repository, commitId:e.content.commitIdBefore})
	ON MATCH SET r.type = l.type
	ON CREATE SET r.type = l.type
	MERGE (r)-[:IS_RANGE_IN]->(snap)
	MERGE (desc:Kind {desc:l.description})
	MERGE (evo)-[:BEFORE {desc:l.description}]->(r)
	MERGE (r)-[:DESC]->(desc)
)