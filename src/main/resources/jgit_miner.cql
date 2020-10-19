WITH $url as url
MATCH (r: Repository {url:url})
RETURN r.releases