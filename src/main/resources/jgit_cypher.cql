WITH $url as url, $releases as releases
MERGE (r: Repository {url:url})
ON MATCH r.releases=releases
ON CREATE r.releases=releases