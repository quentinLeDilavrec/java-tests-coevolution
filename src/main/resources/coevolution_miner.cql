WITH $json as data, $tool as tool
// UNWIND data as e

// MERGE (repo:Repository {url:e.content.repository})

// MERGE (commitIdBefore:Commit {repo:e.content.repository, sha1:e.content.commitIdBefore})
// MERGE (commitIdBefore)-[:IS_COMMIT_OF]->(repo)
// MERGE (commitIdBefore)<-[:CONTAIN_COMMIT]-(repo)
// MERGE (commitIdAfter:Commit {repo:e.content.repository, sha1:e.content.commitIdAfter})
// MERGE (commitIdAfter)-[:IS_COMMIT_OF]->(repo)
// MERGE (commitIdAfter)<-[:CONTAIN_COMMIT]-(repo)
// MERGE (commitIdAfter)-[:PARENT]->(commitIdBefore)

// MERGE (evoType:EvoType {name:e.content.type}) 
// ON CREATE SET evoType.name = e.content.type

// WITH e.content as content, e as e, tool as tool, evoType as evoType, commitIdBefore as commitIdBefore, commitIdAfter as commitIdAfter, repo as repo
// CALL apoc.merge.node(['Evo'], content) YIELD node as evo

// MERGE (evo)-[:EVO_TYPED_BY]->(evoType)

// MERGE (t:Tool {name:tool})
// ON CREATE SET t.name = tool

// MERGE (evo)-[:WAS_MINED_BY]->(t)

// FOREACH (l IN e.leftSideLocations |
// 	MERGE (snap:FileSnapshot {path:l.file, repo:e.content.repository, commitId:e.content.commitIdBefore})
// 	MERGE (snap)-[:IS_SNAPSHOT_IN]->(commitIdBefore)
// 	MERGE (r:Range {range:[toInteger(l.start),toInteger(l.end)], start:toInteger(l.start), end:toInteger(l.end), path:l.file, repo:e.content.repository, commitId:e.content.commitIdBefore})
// 	ON MATCH SET r.type = l.type
// 	ON CREATE SET r.type = l.type
// 	MERGE (r)-[:IS_RANGE_IN]->(snap)
// 	MERGE (desc:Kind {desc:l.description})
// 	MERGE (evo)-[:BEFORE {desc:l.description}]->(r)
// 	MERGE (r)-[:DESC]->(desc)
// )

// FOREACH (l IN e.rightSideLocations |
// 	MERGE (snap:FileSnapshot {path:l.file, repo:e.content.repository, commitId:e.content.commitIdAfter})
// 	MERGE (snap)-[:IS_SNAPSHOT_IN]->(commitIdAfter)
// 	MERGE (r:Range {range:[toInteger(l.start),toInteger(l.end)], start:toInteger(l.start), start:toInteger(l.start), end:toInteger(l.end), path:l.file, repo:e.content.repository, commitId:e.content.commitIdAfter})
// 	ON MATCH SET r.type = l.type
// 	ON CREATE SET r.type = l.type
// 	MERGE (r)-[:IS_RANGE_IN]->(snap)
// 	MERGE (desc:Kind {desc:l.description})
// 	MERGE (evo)-[:AFTER  {desc:l.description}]->(r)
// 	MERGE (r)-[:DESC]->(desc)
// )