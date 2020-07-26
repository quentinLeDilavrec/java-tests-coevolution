WITH $json as data, $tool as tool
UNWIND data as e

MERGE (repo:Repository {url:e.content.repository})

MERGE (commit:Commit {repo:e.content.repository, sha1:e.content.commitId})
MERGE (commit)-[:IS_COMMIT_OF]->(repo)
MERGE (commit)<-[:CONTAIN_COMMIT]-(repo)

MERGE (project:Project {
    groupId:coalesce(e.content.groupId, ""),
    artifactId:coalesce(e.content.artifactId, "")
})

MERGE (project)-[rel:RELEASED_FROM {
    version: coalesce(e.version, ""),
    packaging:coalesce(e.packaging, ""),
    id:coalesce(e.id, "")
}]->(commit)
ON CREATE SET 
    rel.path = e.content.path,
    rel.srcs = e.content.srcs,
	rel.loC = e.stats.loC,
	rel.javaLoC = e.stats.javaLoC,
	rel.testCoveredLoC = e.stats.testCoveredLoC,
	rel.codeCompile = e.stats.codeCompile,
	rel.testCompile = e.stats.testCompile,
	rel.testSuite = e.stats.testSuite,
	rel.classes = e.stats.classes,
	rel.executables = e.stats.executables,
	rel.tests = e.stats.tests
ON MATCH SET 
    rel.path = e.content.path,
    rel.srcs = e.content.srcs,
	rel.loC = e.stats.loC,
	rel.javaLoC = e.stats.javaLoC,
	rel.testCoveredLoC = e.stats.testCoveredLoC,
	rel.codeCompile = e.stats.codeCompile,
	rel.testCompile = e.stats.testCompile,
	rel.testSuite = e.stats.testSuite,
	rel.classes = e.stats.classes,
	rel.executables = e.stats.executables,
	rel.tests = e.stats.tests

MERGE (parent:Project {
		groupId: coalesce(e.parent.groupId, ""),
		artifactId: coalesce(e.parent.artifactId, "")
	})

MERGE (commit)-[par:PARENT]->(parent)
ON CREATE SET 
	par.groupId=e.content.groupId,// relative to project not dependency
	par.artifactId=e.content.artifactId,// relative to project not dependency
	par.id= e.parent.id,
	par.relativePath= e.parent.relativePath,
    par.version = e.parent.version
ON MATCH SET 
	par.groupId=e.content.groupId,// relative to project not dependency
	par.artifactId=e.content.artifactId,// relative to project not dependency
	par.id= e.parent.id,
	par.relativePath= e.parent.relativePath,
    par.version = e.parent.version
FOREACH (l IN e.dependencies |
	MERGE (dependency:Project {
    		groupId: coalesce(l.groupId, ""),
    		artifactId: coalesce(l.artifactId, "")
		})
	MERGE (commit)-[dep:DEPENDENCY {
		groupId:coalesce(e.content.groupId, ""),// relative to project not dependency
		artifactId:coalesce(e.content.artifactId, ""),// relative to project not dependency
		version: coalesce(l.version, "")
	}]->(dependency)
    ON CREATE SET
		dep.scope= l.scope,
		dep.type= l.type,
		dep.classifier= l.classifier,
		dep.managementKey= l.managementKey,
		dep.optional= l.optional,
		dep.exclusions= l.exclusions
    ON MATCH SET
		dep.scope= l.scope,
		dep.type= l.type,
		dep.classifier= l.classifier,
		dep.managementKey= l.managementKey,
		dep.optional= l.optional,
		dep.exclusions= l.exclusions
)