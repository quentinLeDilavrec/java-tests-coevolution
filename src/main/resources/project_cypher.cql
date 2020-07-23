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
    rel.srcs = e.content.srcs,
	rel.loC = e.stats.loC,
	rel.javaLoC = e.stats.javaLoC,
	rel.testCoveredLoC = e.stats.testCoveredLoC,
	rel.compile = e.stats.compile,
	rel.testCompile = e.stats.testCompile,
	rel.testSuite = e.stats.testSuite,
	rel.classes = e.stats.classes,
	rel.executables = e.stats.executables,
	rel.tests = e.stats.tests
ON CREATE SET 
    rel.path = e.content.path,
    rel.srcs = e.content.srcs,
	rel.loC = e.stats.loC,
	rel.javaLoC = e.stats.javaLoC,
	rel.testCoveredLoC = e.stats.testCoveredLoC,
	rel.compile = e.stats.compile,
	rel.testCompile = e.stats.testCompile,
	rel.testSuite = e.stats.testSuite,
	rel.classes = e.stats.classes,
	rel.executables = e.stats.executables,
	rel.tests = e.stats.tests

MERGE (parent:Project {
		groupId: e.parent.groupId,
		artifactId: e.parent.artifactId
	})
MERGE (commit)-[:PARENT {
	groupId:e.content.groupId,// relative to project not dependency
	artifactId:e.content.artifactId,// relative to project not dependency
	version: e.parent.version,
	id: e.parent.id,
}]->(parent)
FOREACH (l IN e.dependencies |
	MERGE (dependency:Project {
    		groupId: l.groupId,
    		artifactId: l.artifactId
		})
	MERGE (commit)-[:DEPENDENCY {
		groupId:e.content.groupId,// relative to project not dependency
		artifactId:e.content.artifactId,// relative to project not dependency
		version: l.version,
		scope: l.scope,
		type: l.type,
		classifier: l.classifier,
		managementKey: l.managementKey,
		optional: l.optional,
		exclusions: l.exclusions
	}]->(dependency)
)