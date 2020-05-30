UNWIND $data as content
MATCH (t:Range {isTest:True, repo:content.repo, commitId:content.commitId, path:content.path, start:content.start,end:content.end})
RETURN distinct t as test, 
[(t)<-[]-(:Impact {type:"expand to executable"})-[]->(:Range)<-[]-(:Impact {type: "call"})-[]->(:Range)<-[]-(:Impact {type:"adjustment"})-[]->(:Range)<-[:BEFORE]-(e:Evo) | (e)-[:BEFORE]->()] as longCall, 
[(t)<-[]-(:Impact {type:"expand to executable"})-[]->(:Range)<-[]-(:Impact {type: "call"})-[]->(:Range)<-[:BEFORE]-(e:Evo) |  (e)-[:BEFORE]->()] as shortCall, 
[(t)<-[:BEFORE]-(e:Evo) | (e)-[:BEFORE]->()] as short, 
[(t)<-[]-(:Impact {type:"expand to executable"})-[]->(:Range)<-[:BEFORE]-(e:Evo) | (e)-[:BEFORE]->()] as long,
[(t)<-[]-(:Impact {type:"adjustment"})-[]->(:Range)<-[:BEFORE]-(e:Evo) | (e)-[:BEFORE]->()] as shortAdjusted

// combinations
MATCH (t:Range {isTest:True})
WITH distinct t, 
[(t)<-[]-(:Impact {type:"expand to executable"})-[]->(:Range)<-[]-(:Impact {type: "call"})-[]->(:Range)<-[]-(:Impact {type:"adjustment"})-[]->(:Range)<-[:BEFORE]-(e:Evo) | e] as longCall, 
[(t)<-[]-(:Impact {type:"expand to executable"})-[]->(:Range)<-[]-(:Impact {type: "call"})-[]->(:Range)<-[:BEFORE]-(e:Evo) |  e] as shortCall, 
[(t)<-[:BEFORE]-(e:Evo) | e] as short, 
[(t)<-[]-(:Impact {type:"expand to executable"})-[]->(:Range)<-[:BEFORE]-(e:Evo) | e] as long,
[(t)<-[]-(:Impact {type:"adjustment"})-[]->(:Range)<-[:BEFORE]-(e:Evo) | e] as shortAdjusted
WITH distinct apoc.coll.sort([x in apoc.coll.toSet(longCall+shortCall) | x.type]) as throughCall,
apoc.coll.sort([x in apoc.coll.toSet(short+long+shortAdjusted) | x.type]) as direct, 
count(*) as c
WITH apoc.coll.flatten([b IN apoc.coll.frequencies(throughCall) | [c IN range(1,b.count)|{item:b.item,index:c}]]) as throughCall, direct, c
WITH apoc.coll.combinations(throughCall,1,size(throughCall))+[[]] as throughCallComb, throughCall, direct, c
UNWIND throughCallComb as throughCall0
WITH [x IN apoc.coll.removeAll(throughCall,throughCall0)| x.item] as inv,
[x IN throughCall0 | x.item] as throughCall0, direct, c, 
CASE size(throughCall0) WHEN size(throughCall) THEN c ELSE 0 END as real
WITH apoc.coll.combinations(direct,1,size(direct))+[[]] as direct,
throughCall0, inv, c, real
UNWIND direct as direct0
WITH apoc.coll.combinations(inv,1,size(inv))+[[]] as inv,
throughCall0, direct0, c, real
UNWIND inv as inv0
WHERE size(throughCall0)>0
RETURN distinct apoc.coll.sort(throughCall0) as cause, 
apoc.coll.sort(inv0) as resolutionThroughCall, 
apoc.coll.sort(direct0) as resolutionDirect, sum(c) as c, sum(real)
ORDER BY c DESC, size(cause) DESC, size(resolutionDirect) ASC, size(resolutionThroughCall) ASC

// filtercases with no cause or no resolution
MATCH (t:Range {isTest:True})
WITH distinct t, 
[(t)<-[]-(:Impact {type:"expand to executable"})-[]->(:Range)<-[]-(:Impact {type: "call"})-[]->(:Range)<-[]-(:Impact {type:"adjustment"})-[]->(:Range)<-[:BEFORE]-(e:Evo) | e] as longCall, 
[(t)<-[]-(:Impact {type:"expand to executable"})-[]->(:Range)<-[]-(:Impact {type: "call"})-[]->(:Range)<-[:BEFORE]-(e:Evo) |  e] as shortCall, 
[(t)<-[:BEFORE]-(e:Evo) | e] as short, 
[(t)<-[]-(:Impact {type:"expand to executable"})-[]->(:Range)<-[:BEFORE]-(e:Evo) | e] as long,
[(t)<-[]-(:Impact {type:"adjustment"})-[]->(:Range)<-[:BEFORE]-(e:Evo) | e] as shortAdjusted
WITH distinct apoc.coll.sort([x in apoc.coll.toSet(longCall+shortCall) | x.type]) as throughCall,
apoc.coll.sort([x in apoc.coll.toSet(short+long+shortAdjusted) | x.type]) as direct, 
count(*) as c
WITH apoc.coll.flatten([b IN apoc.coll.frequencies(throughCall) | [c IN range(1,b.count)|{item:b.item,index:c}]]) as throughCall, direct, c
WITH apoc.coll.combinations(throughCall,1,size(throughCall)) as throughCallComb, throughCall, direct, c
UNWIND throughCallComb as throughCall0
WITH [x IN apoc.coll.removeAll(throughCall,throughCall0)| x.item] as inv,
[x IN throughCall0 | x.item] as throughCall0, direct, c, 
CASE size(throughCall0) WHEN size(throughCall) THEN c ELSE 0 END as real
WITH apoc.coll.combinations(direct,1,size(direct))+[[]] as direct,
throughCall0, inv, c, real
UNWIND direct as direct0
WITH apoc.coll.combinations(inv,1,size(inv))+[[]] as inv,
throughCall0, direct0, c, real
WHERE size(inv)>0 OR size(direct0)>0
WITH inv,throughCall0, direct0, c, real
UNWIND inv as inv0
RETURN distinct apoc.coll.sort(throughCall0) as cause, 
apoc.coll.sort(inv0) as resolutionThroughCall, 
apoc.coll.sort(direct0) as resolutionDirect, sum(c) as c, sum(real)
ORDER BY c DESC, size(cause) DESC, size(resolutionDirect) ASC, size(resolutionThroughCall) ASC

// useful to debug
MATCH (t:Range {isTest:True})
WITH distinct t, 
[(t)<-[]-(:Impact {type:"expand to executable"})-[]->(:Range)<-[]-(:Impact {type: "call"})-[]->(:Range)<-[]-(:Impact {type:"adjustment"})-[]->(:Range)<-[:BEFORE]-(e:Evo) | e] as longCall, 
[(t)<-[]-(:Impact {type:"expand to executable"})-[]->(:Range)<-[]-(:Impact {type: "call"})-[]->(:Range)<-[:BEFORE]-(e:Evo) |  e] as shortCall, 
[(t)<-[:BEFORE]-(e:Evo) | e] as short, 
[(t)<-[]-(:Impact {type:"expand to executable"})-[]->(:Range)<-[:BEFORE]-(e:Evo) | e] as long,
[(t)<-[]-(:Impact {type:"adjustment"})-[]->(:Range)<-[:BEFORE]-(e:Evo) | e] as shortAdjusted
WITH apoc.coll.sort([x in apoc.coll.toSet(longCall+shortCall) | x.type]) as throughCall,
apoc.coll.sort([x in apoc.coll.toSet(short+long+shortAdjusted) | x.type]) as direct, 
1 as c
WHERE "Extract Method" in throughCall
WITH apoc.coll.flatten([b IN apoc.coll.frequencies(throughCall) | [c IN range(1,b.count)|{item:b.item,index:c}]]) as throughCall, direct, c
WITH apoc.coll.combinations(throughCall,1,size(throughCall)) as throughCallComb, throughCall, direct, c
UNWIND throughCallComb as throughCall0
WITH apoc.coll.toSet([x IN apoc.coll.removeAll(throughCall,throughCall0)| x.item]) as inv,
apoc.coll.toSet([x IN throughCall0 | x.item]) as throughCall0, direct, c,throughCall, 
CASE size(throughCall0) WHEN size(throughCall) THEN c ELSE 0 END as real
WITH throughCall0, apoc.coll.sort(inv) as inv, c, real,throughCall
WITH apoc.coll.combinations(inv,1,size(inv))+[[]] as inv,
throughCall0, c, real,throughCall
WHERE size(inv)>0
WITH inv,throughCall0, c, real,throughCall
UNWIND inv as inv0
RETURN distinct apoc.coll.sort(throughCall0) as cause, 
apoc.coll.sort(inv0) as resolutionThroughCall, sum(c) as c, sum(real), [x IN apoc.coll.frequencies(collect(throughCall))| [x.count,size(x.item)]]
ORDER BY c DESC, size(cause) DESC, size(resolutionThroughCall) ASC

// ignore combinations between same types in same columns
MATCH (t:Range {isTest:True})
WITH distinct t, 
[(t)<-[]-(:Impact {type:"expand to executable"})-[]->(:Range)<-[]-(:Impact {type: "call"})-[]->(:Range)<-[]-(:Impact {type:"adjustment"})-[]->(:Range)<-[:BEFORE]-(e:Evo) | e] as longCall, 
[(t)<-[]-(:Impact {type:"expand to executable"})-[]->(:Range)<-[]-(:Impact {type: "call"})-[]->(:Range)<-[:BEFORE]-(e:Evo) |  e] as shortCall, 
[(t)<-[:BEFORE]-(e:Evo) | e] as short, 
[(t)<-[]-(:Impact {type:"expand to executable"})-[]->(:Range)<-[:BEFORE]-(e:Evo) | e] as long,
[(t)<-[]-(:Impact {type:"adjustment"})-[]->(:Range)<-[:BEFORE]-(e:Evo) | e] as shortAdjusted
WITH distinct apoc.coll.sort([x in apoc.coll.toSet(longCall+shortCall) | x.type]) as throughCall,
apoc.coll.sort([x in apoc.coll.toSet(short+long+shortAdjusted) | x.type]) as direct, 
count(*) as c
WITH apoc.coll.flatten([b IN apoc.coll.frequencies(throughCall) | [c IN range(1,b.count)|{item:b.item,index:c}]]) as throughCall, direct, c
WITH apoc.coll.combinations(throughCall,1,size(throughCall)) as throughCallComb, throughCall, direct, c
UNWIND throughCallComb as throughCall0
WITH apoc.coll.toSet([x IN apoc.coll.removeAll(throughCall,throughCall0)| x.item]) as inv,
apoc.coll.toSet([x IN throughCall0 | x.item]) as throughCall0, direct, c, 
CASE size(throughCall0) WHEN size(throughCall) THEN c ELSE 0 END as real
WITH apoc.coll.combinations(direct,1,size(direct))+[[]] as direct,
throughCall0, inv, c, real
UNWIND direct as direct0
WITH apoc.coll.combinations(inv,1,size(inv))+[[]] as inv,
throughCall0, direct0, c, real
UNWIND inv as inv0
WITH inv0, throughCall0, direct0, c, real
WHERE size(inv0)>0 OR size(direct0)>0
WITH inv0, throughCall0, direct0, c, real
RETURN distinct apoc.coll.sort(throughCall0) as cause, 
apoc.coll.sort(inv0) as resolutionThroughCall, 
apoc.coll.sort(direct0) as resolutionDirect, sum(c) as c, sum(real)
ORDER BY c DESC, size(cause) DESC, size(resolutionDirect) ASC, size(resolutionThroughCall) ASC

// remove rename parameters, rename variable

MATCH (t:Range {isTest:True})
WITH distinct t, 
[(t)<-[]-(:Impact {type:"expand to executable"})-[]->(:Range)<-[]-(:Impact {type: "call"})-[]->(:Range)<-[]-(:Impact {type:"adjustment"})-[]->(:Range)<-[:BEFORE]-(e:Evo) WHERE e.type<>"Rename Parameter" AND e.type<>"Rename Variable" | e] as longCall, 
[(t)<-[]-(:Impact {type:"expand to executable"})-[]->(:Range)<-[]-(:Impact {type: "call"})-[]->(:Range)<-[:BEFORE]-(e:Evo) WHERE e.type<>"Rename Parameter" AND e.type<>"Rename Variable" |  e] as shortCall, 
[(t)<-[:BEFORE]-(e:Evo) WHERE e.type<>"Rename Parameter" AND e.type<>"Rename Variable" | e] as short, 
[(t)<-[]-(:Impact {type:"expand to executable"})-[]->(:Range)<-[:BEFORE]-(e:Evo) WHERE e.type<>"Rename Parameter" AND e.type<>"Rename Variable" | e] as long,
[(t)<-[]-(:Impact {type:"adjustment"})-[]->(:Range)<-[:BEFORE]-(e:Evo) WHERE e.type<>"Rename Parameter" AND e.type<>"Rename Variable" | e] as shortAdjusted
WITH distinct apoc.coll.sort([x in apoc.coll.toSet(longCall+shortCall) | x.type]) as throughCall,
apoc.coll.sort([x in apoc.coll.toSet(short+long+shortAdjusted) | x.type]) as direct, 
count(*) as c
WITH apoc.coll.flatten([b IN apoc.coll.frequencies(throughCall) | [c IN range(1,b.count)|{item:b.item,index:c}]]) as throughCall, direct, c
WITH apoc.coll.combinations(throughCall,1,size(throughCall)) as throughCallComb, throughCall, direct, c
UNWIND throughCallComb as throughCall0
WITH apoc.coll.toSet([x IN apoc.coll.removeAll(throughCall,throughCall0)| x.item]) as inv,
apoc.coll.toSet([x IN throughCall0 | x.item]) as throughCall0, direct, c, 
CASE size(throughCall0) WHEN size(throughCall) THEN c ELSE 0 END as real
WITH apoc.coll.combinations(direct,1,size(direct))+[[]] as direct,
throughCall0, inv, c, real
UNWIND direct as direct0
WITH apoc.coll.combinations(inv,1,size(inv))+[[]] as inv,
throughCall0, direct0, c, real
UNWIND inv as inv0
WITH inv0, throughCall0, direct0, c, real
WHERE size(inv0)>0 OR size(direct0)>0
WITH inv0, throughCall0, direct0, c, real
RETURN distinct apoc.coll.sort(throughCall0) as cause, 
apoc.coll.sort(inv0) as resolutionThroughCall, 
apoc.coll.sort(direct0) as resolutionDirect, sum(c) as c, sum(real)
ORDER BY c DESC, size(cause) DESC, size(resolutionDirect) ASC, size(resolutionThroughCall) ASC