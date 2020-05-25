UNWIND $data as content
MATCH (t:Range {isTest:True, repo:content.repo, commitId:content.commitId, path:content.path, start:content.start,end:content.end})
WITH distinct t, 
[(t)<-[]-(:Impact)-[]->(:Range)<-[]-(:Impact {type: "call impact"})-[]->(:Range)<-[]-(:Impact)-[]->(:Range)<-[:BEFORE]-(e:Evo)|e] as e, 
[(t)<-[]-(:Impact)-[]->(:Range)<-[]-(:Impact {type: "call impact"})-[]->(:Range)<-[:BEFORE]-(e2:Evo)|e2] as e2, 
[(t)<-[:BEFORE]-(g:Evo) | g] as g, 
[(t)<-[]-(:Impact)-[]->(:Range)<-[:BEFORE]-(h:Evo)|h] as h
RETURN distinct apoc.coll.sort([x in apoc.coll.toSet(e+e2) | x.type]) as evolutions,
apoc.coll.sort([x in apoc.coll.toSet(g+h) | x.type]) as resolutions, 
count(*)