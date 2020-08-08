
FOREACH (l IN $rangesToType |
    MERGE (repoCause:Repository {url:l.repository})
    MERGE (commitIdCause:Commit {repo:l.repository, sha1:l.commitId})
    ON CREATE SET commitIdCause.isParsed = true
    ON MATCH SET commitIdCause.isParsed = true
    MERGE (commitIdCause)-[:IS_COMMIT_OF]->(repoCause)
    MERGE (commitIdCause)<-[:CONTAIN_COMMIT]-(repoCause)
    MERGE (cause:Range {range:[toInteger(l.start),toInteger(l.end)], start:toInteger(l.start), end:toInteger(l.end), 
    path:l.file, repo:l.repository, commitId:l.commitId})
    ON CREATE SET cause.type = l.type, cause.isTest = l.isTest, cause.sig = l.sig
    ON MATCH SET cause.isTest = l.isTest, cause.sig = l.sig
    MERGE (snapCause:FileSnapshot {path:l.file, repo:l.repository, commitId:l.commitId})
    MERGE (snapCause)-[:IS_SNAPSHOT_IN]->(commitIdCause)
    MERGE (cause)-[:IS_RANGE_IN]->(snapCause)
)
WITH $json as data, $tool as tool
UNWIND data as imp

WITH imp.content as content, imp as imp, tool as tool
CALL apoc.merge.node(['Impact'], content) YIELD node as impact

FOREACH (l IN imp.causes |
    MERGE (repoCause:Repository {url:l.repository})

    MERGE (commitIdCause:Commit {repo:l.repository, sha1:l.commitId})
    ON CREATE SET commitIdCause.isParsed = true
    ON MATCH SET commitIdCause.isParsed = true
    MERGE (commitIdCause)-[:IS_COMMIT_OF]->(repoCause)
    MERGE (commitIdCause)<-[:CONTAIN_COMMIT]-(repoCause)

    MERGE (cause:Range {range:[toInteger(l.start),toInteger(l.end)], start:toInteger(l.start), end:toInteger(l.end), 
    path:l.file, repo:l.repository, commitId:l.commitId})
    ON CREATE SET cause.type = l.type, cause.isTest = l.isTest, cause.sig = l.sig
    ON MATCH SET cause.isTest = l.isTest, cause.sig = l.sig
    MERGE (impact)-[:IMPACT_CAUSE {type: l.type}]->(cause)
    MERGE (snapCause:FileSnapshot {path:l.file, repo:l.repository, commitId:l.commitId})
    MERGE (snapCause)-[:IS_SNAPSHOT_IN]->(commitIdCause)
    MERGE (cause)-[:IS_RANGE_IN]->(snapCause)
)

FOREACH (l IN imp.effects |
    MERGE (repoEffect:Repository {url:l.repository})

    MERGE (commitIdEffect:Commit {repo:l.repository, sha1:l.commitId})
    ON CREATE SET commitIdEffect.isParsed = true
    ON MATCH SET commitIdEffect.isParsed = true
    MERGE (commitIdEffect)-[:IS_COMMIT_OF]->(repoEffect)
    MERGE (commitIdEffect)<-[:CONTAIN_COMMIT]-(repoEffect)

    MERGE (effect:Range {range:[toInteger(l.start),toInteger(l.end)],start:toInteger(l.start), end:toInteger(l.end), 
    path:l.file, repo:l.repository, commitId:l.commitId})
    ON CREATE SET effect.type = l.type, effect.isTest = l.isTest, effect.sig = l.sig
    ON MATCH SET effect.isTest = l.isTest, effect.sig = l.sig
    MERGE (impact)-[:IMPACT_EFFECT {type: l.type}]->(effect)
    MERGE (snapEffect:FileSnapshot {path:l.file, repo:l.repository, commitId:l.commitId})
    MERGE (snapEffect)-[:IS_SNAPSHOT_IN]->(commitIdEffect)
    MERGE (effect)-[:IS_RANGE_IN]->(snapEffect)
)
MERGE (t:Tool {name:tool})
ON CREATE SET t.name = tool

MERGE (impact)-[:WAS_MINED_BY]->(t)