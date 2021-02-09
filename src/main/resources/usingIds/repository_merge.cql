WITH $repository as repository, $releases as releases
MERGE (r: Repository {repository:repository})
ON MATCH r.releases=releases
ON CREATE r.releases=releases