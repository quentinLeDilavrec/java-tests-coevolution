WITH $repository as repository, $releases as releases
MERGE (r: Repository {repository:repository})
ON MATCH SET r.releases=releases
ON CREATE SET r.releases=releases