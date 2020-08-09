WITH $json as data, $tool as tool
UNWIND data as e

MERGE (repo:Repository {url:e.content.repository})
ON CREATE SET
	repo.markedRelease = CASE rel is null WHEN True THEN repo.released ELSE true END
ON MATCH SET
	repo.markedRelease = CASE rel is null WHEN True THEN repo.released ELSE tre END

MERGE (commit:Commit {repo:e.content.repository, sha1:e.content.commitId})
ON CREATE SET 
	commit.exception = e.exception
	commit.released = CASE rel is null WHEN True THEN commit.released ELSE e.released END
ON MATCH SET 
	commit.exception = e.exception
	commit.released = CASE rel is null WHEN True THEN commit.released ELSE e.released END
MERGE (commit)-[:IS_COMMIT_OF]->(repo)
MERGE (commit)<-[:CONTAIN_COMMIT]-(repo)

WITH e, tool, repo, commit
WHERE NOT(e.content.groupId is null OR e.content.artifactId is null)

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
	rel.testsCompile = e.stats.testsCompile,
	rel.testSuite = e.stats.testSuite,
	rel.classes = e.stats.classes,
	rel.executables = e.stats.executables,
	rel.tests = e.stats.tests,
	rel.codeAST = e.stats.codeAST,
	rel.testsAST = e.stats.codeAST
ON MATCH SET 
	rel.path = e.content.path,
	rel.srcs = e.content.srcs,
	rel.loC = e.stats.loC,
	rel.javaLoC = e.stats.javaLoC,
	rel.testCoveredLoC = e.stats.testCoveredLoC,
	rel.codeCompile = e.stats.codeCompile,
	rel.testsCompile = e.stats.testsCompile,
	rel.testSuite = e.stats.testSuite,
	rel.classes = e.stats.classes,
	rel.executables = e.stats.executables,
	rel.tests = e.stats.tests,
	rel.codeAST = e.stats.codeAST,
	rel.testsAST = e.stats.codeAST

MERGE (parent:Project {
		groupId: coalesce(e.parent.groupId, ""),
		artifactId: coalesce(e.parent.artifactId, "")
	})

MERGE (commit)-[par:PARENT{
		groupId: coalesce(e.content.groupId, ""),// relative to project not parent
		artifactId: coalesce(e.content.artifactId, "")// relative to project not parent
}]->(parent)
ON CREATE SET
	par.id= e.parent.id,
	par.relativePath= e.parent.relativePath,
	par.version = e.parent.version
ON MATCH SET
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