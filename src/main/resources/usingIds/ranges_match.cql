WITH $data as data
UNWIND data as l
MATCH (r:Range {
    repository:l.repository,
    commitId:l.commitId, 
    path:l.file,
    start:toInteger(l.start), 
    end:toInteger(l.end)})
RETURN distinct ID(r) as id