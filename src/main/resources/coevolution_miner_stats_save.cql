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

// more efficient and count
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
WITH CASE size(throughCall)>0 WHEN True THEN [throughCall] ELSE [] END + apoc.coll.combinations(throughCall,1,apoc.coll.min([size(throughCall)-1,3])) as throughCallComb, throughCall, direct, c
UNWIND throughCallComb as throughCall0
WITH apoc.coll.toSet([x IN apoc.coll.removeAll(throughCall,throughCall0)| x.item]) as inv,
apoc.coll.toSet([x IN throughCall0 | x.item]) as throughCall0, direct, c, 
CASE size(throughCall0) WHEN size(throughCall) THEN c ELSE 0 END as real
WITH CASE size(direct)>0 WHEN True THEN [direct] ELSE [] END + apoc.coll.combinations(direct,1,apoc.coll.min([size(direct)-1,3]))+[[]] as direct,
throughCall0, inv, c, real
UNWIND direct as direct0
WITH CASE size(inv)>0 WHEN True THEN [inv] ELSE [] END + apoc.coll.combinations(inv,1,apoc.coll.min([size(inv)-1,3]))+[[]] as inv,
throughCall0, direct0, c, real
UNWIND inv as inv0
WITH inv0, throughCall0, direct0, c, real
WHERE size(inv0)>0 OR size(direct0)>0
WITH inv0, throughCall0, direct0, c, real
WITH distinct apoc.coll.sort(throughCall0) as cause, 
apoc.coll.sort(inv0) as resolutionThroughCall, 
apoc.coll.sort(direct0) as resolutionDirect, sum(c) as c, sum(real) as r
RETURN count(*)


// MASTER count
MATCH (t:Range {isTest:True})
WITH distinct t, 
[(t)<-[]-(:Impact {type:"expand to executable"})-[]->(:Range)<-[]-(:Impact {type: "call"})-[]->(:Range)<-[]-(:Impact {type:"adjustment"})-[]->(:Range)<-[:BEFORE]-(e:Evo) WHERE e.type<>"Rename Parameter" AND e.type<>"Rename Variable" | e] as longCall, 
[(t)<-[]-(:Impact {type:"expand to executable"})-[]->(:Range)<-[]-(:Impact {type: "call"})-[]->(:Range)<-[:BEFORE]-(e:Evo) WHERE e.type<>"Rename Parameter" AND e.type<>"Rename Variable" |  e] as shortCall, 
[(t)<-[:BEFORE]-(e:Evo) WHERE e.type<>"Rename Parameter" AND e.type<>"Rename Variable" | e] as short, 
[(t)<-[]-(:Impact {type:"expand to executable"})-[]->(:Range)<-[:BEFORE]-(e:Evo) WHERE e.type<>"Rename Parameter" AND e.type<>"Rename Variable" | e] as long,
[(t)<-[]-(:Impact {type:"adjustment"})-[]->(:Range)<-[:BEFORE]-(e:Evo) WHERE e.type<>"Rename Parameter" AND e.type<>"Rename Variable" | e] as shortAdjusted
RETURN count(*)

// more efficient not count
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
WITH CASE size(throughCall)>0 WHEN True THEN [throughCall] ELSE [] END + apoc.coll.combinations(throughCall,1,apoc.coll.min([size(throughCall)-1,4])) as throughCallComb, throughCall, direct, c
UNWIND throughCallComb as throughCall0
WITH apoc.coll.toSet([x IN apoc.coll.removeAll(throughCall,throughCall0)| x.item]) as inv,
apoc.coll.toSet([x IN throughCall0 | x.item]) as throughCall0, direct, c, 
CASE size(throughCall0) WHEN size(throughCall) THEN c ELSE 0 END as real
WITH CASE size(direct)>0 WHEN True THEN [direct] ELSE [] END + apoc.coll.combinations(direct,1,apoc.coll.min([size(direct)-1,4]))+[[]] as direct,
throughCall0, inv, c, real
UNWIND direct as direct0
WITH CASE size(inv)>0 WHEN True THEN [inv] ELSE [] END + apoc.coll.combinations(inv,1,apoc.coll.min([size(inv)-1,4]))+[[]] as inv,
throughCall0, direct0, c, real
UNWIND inv as inv0
WITH inv0, throughCall0, direct0, c, real
WHERE size(inv0)>0 OR size(direct0)>0
WITH inv0, throughCall0, direct0, c, real
RETURN distinct apoc.coll.sort(throughCall0) as cause, 
apoc.coll.sort(inv0) as resolutionThroughCall, 
apoc.coll.sort(direct0) as resolutionDirect, sum(c) as c, sum(real) as r
ORDER BY c DESC, size(cause) DESC, size(resolutionDirect) ASC, size(resolutionThroughCall) ASC

// filter outliers
MATCH (t:Range {isTest:True})
WITH distinct t, 
[(t)<-[]-(:Impact {type:"expand to executable"})-[]->(:Range)<-[]-(:Impact {type: "call"})-[]->(:Range)<-[]-(:Impact {type:"adjustment"})-[]->(:Range)<-[:BEFORE]-(e:Evo) WHERE e.type<>"Rename Parameter" AND e.type<>"Rename Variable" | e] as longCall, 
[(t)<-[]-(:Impact {type:"expand to executable"})-[]->(:Range)<-[]-(:Impact {type: "call"})-[]->(:Range)<-[:BEFORE]-(e:Evo) WHERE e.type<>"Rename Parameter" AND e.type<>"Rename Variable" |  e] as shortCall, 
[(t)<-[:BEFORE]-(e:Evo) WHERE e.type<>"Rename Parameter" AND e.type<>"Rename Variable" | e] as short, 
[(t)<-[]-(:Impact {type:"expand to executable"})-[]->(:Range)<-[:BEFORE]-(e:Evo) WHERE e.type<>"Rename Parameter" AND e.type<>"Rename Variable" | e] as long,
[(t)<-[]-(:Impact {type:"adjustment"})-[]->(:Range)<-[:BEFORE]-(e:Evo) WHERE e.type<>"Rename Parameter" AND e.type<>"Rename Variable" | e] as shortAdjusted
WITH distinct apoc.coll.sort([x in apoc.coll.toSet(longCall+shortCall) | x.type]) as throughCall,
apoc.coll.sort([x in apoc.coll.toSet(short+long+shortAdjusted) | x.type]) as direct, 
count(*) as c, count(distinct [t.repo,t.commitId]) as metric2
WITH apoc.coll.flatten([b IN apoc.coll.frequencies(throughCall) | [c IN range(1,apoc.coll.min([b.count,metric2*5]))|{item:b.item,index:c}]]) as throughCall, direct, c
WITH CASE size(throughCall)>0 WHEN True THEN [throughCall] ELSE [] END + apoc.coll.combinations(throughCall,1,apoc.coll.min([size(throughCall)-1,3])) as throughCallComb, throughCall, direct, c
UNWIND throughCallComb as throughCall0
WITH apoc.coll.toSet([x IN apoc.coll.removeAll(throughCall,throughCall0)| x.item]) as inv,
apoc.coll.toSet([x IN throughCall0 | x.item]) as throughCall0, direct, c, 
CASE size(throughCall0) WHEN size(throughCall) THEN c ELSE 0 END as real
WITH CASE size(direct)>0 WHEN True THEN [direct] ELSE [] END + apoc.coll.combinations(direct,1,apoc.coll.min([size(direct)-1,3]))+[[]] as direct,
throughCall0, inv, c, real
UNWIND direct as direct0
WITH CASE size(inv)>0 WHEN True THEN [inv] ELSE [] END + apoc.coll.combinations(inv,1,apoc.coll.min([size(inv)-1,3]))+[[]] as inv,
throughCall0, direct0, c, real
UNWIND inv as inv0
WITH inv0, throughCall0, direct0, c, real
WHERE size(inv0)>0 OR size(direct0)>0
WITH inv0, throughCall0, direct0, c, real
RETURN distinct apoc.coll.sort(throughCall0) as cause, 
apoc.coll.sort(inv0) as resolutionThroughCall, 
apoc.coll.sort(direct0) as resolutionDirect, sum(c) as c, sum(real) as r
ORDER BY c DESC, size(cause) DESC, size(resolutionDirect) ASC, size(resolutionThroughCall) ASC

// more stats apoc.number.format(, '0.##E0')
MATCH (e:Evo)
WITH distinct e,
[(e)-[:BEFORE]->(:Range)<-[:IMPACT_CAUSE]-(:Impact {type:"adjustment"})-[:IMPACT_EFFECT]->(:Range)<-[:IMPACT_CAUSE]-(:Impact {type: "call"})-[:IMPACT_EFFECT]->(:Range)<-[:IMPACT_CAUSE]-(:Impact {type:"expand to executable"})-[:IMPACT_EFFECT]->(r:Range)|r] as longCall, 
[(e)-[:BEFORE]->(:Range)<-[:IMPACT_CAUSE]-(:Impact {type: "call"})-[:IMPACT_EFFECT]->(:Range)<-[:IMPACT_CAUSE]-(:Impact {type:"expand to executable"})-[:IMPACT_EFFECT]->(r:Range)|r] as shortCall, 
[(e)-[:BEFORE]->(r:Range)|r] as short, 
[(e)-[:BEFORE]->(:Range)<-[:IMPACT_CAUSE]-(:Impact {type:"expand to executable"})-[:IMPACT_EFFECT]->(r:Range)|r] as long,
[(e)-[:BEFORE]->(:Range)<-[:IMPACT_CAUSE]-(:Impact {type: "adjustment"})-[:IMPACT_EFFECT]->(r:Range)|r] as shortAdjusted
WITH distinct e, size(apoc.coll.toSet(longCall)) as longCall, size([x IN apoc.coll.toSet(longCall) WHERE x.isTest=True|x]) as longCallT, 
size(apoc.coll.toSet( shortCall)) as shortCall, size([x IN apoc.coll.toSet(shortCall) WHERE x.isTest=True|x]) as shortCallT, 
size(apoc.coll.toSet( short)) as short, size([x IN apoc.coll.toSet(short) WHERE x.isTest=True|x]) as shortT,
size(apoc.coll.toSet( long)) as long, size([x IN apoc.coll.toSet(long) WHERE x.isTest=True|x]) as longT, 
size(apoc.coll.toSet( shortAdjusted)) as shortAdjusted, size([x IN apoc.coll.toSet(shortAdjusted) WHERE x.isTest=True|x]) as shortAdjustedT,
size(apoc.coll.toSet( longCall+shortCall+short+long+shortAdjusted)) as all, size([x IN apoc.coll.toSet(longCall+shortCall+short+long+shortAdjusted) WHERE x.isTest=True|x]) as allT
RETURN distinct e.type, count(distinct e), 
[apoc.number.format(avg(longCall), '0.##E0'),apoc.number.format( avg(longCallT), '0.##E0'),apoc.agg.percentiles(longCall),apoc.agg.percentiles(longCallT)] as longCall,
[apoc.number.format(avg(shortCall), '0.##E0'),apoc.number.format(avg(shortCallT), '0.##E0'),apoc.agg.percentiles(shortCall),apoc.agg.percentiles(shortCallT)] as shortCall,
[apoc.number.format(avg(short), '0.##E0'),apoc.number.format(avg(shortT), '0.##E0'),apoc.agg.percentiles(short),apoc.agg.percentiles(shortT)] as short,
[apoc.number.format(avg(long), '0.##E0'),apoc.number.format(avg(longT), '0.##E0'),apoc.agg.percentiles(long),apoc.agg.percentiles(longT)] as long,
[apoc.number.format(avg(shortAdjusted), '0.##E0'),apoc.number.format(avg(shortAdjustedT), '0.##E0'),apoc.agg.percentiles(shortAdjusted),apoc.agg.percentiles(shortAdjustedT)] as shortAdjusted,
[apoc.number.format(avg(all), '0.##E0'),apoc.number.format(avg(allT), '0.##E0'),apoc.agg.percentiles(all),apoc.agg.percentiles(allT)] as all

//new
MATCH (e:Evo)
WITH distinct e,
[(e)-[:BEFORE]->(:Range)<-[:IMPACT_CAUSE]-(:Impact {type:"adjustment"})-[:IMPACT_EFFECT]->(:Range)<-[:IMPACT_CAUSE]-(:Impact {type: "call"})-[:IMPACT_EFFECT]->(:Range)<-[:IMPACT_CAUSE]-(:Impact {type:"expand to executable"})-[:IMPACT_EFFECT]->(r:Range)|r] as longCall, 
[(e)-[:BEFORE]->(:Range)<-[:IMPACT_CAUSE]-(:Impact {type: "call"})-[:IMPACT_EFFECT]->(:Range)<-[:IMPACT_CAUSE]-(:Impact {type:"expand to executable"})-[:IMPACT_EFFECT]->(r:Range)|r] as shortCall, 
[(e)-[:BEFORE]->(r:Range)|r] as short, 
[(e)-[:BEFORE]->(:Range)<-[:IMPACT_CAUSE]-(:Impact {type:"expand to executable"})-[:IMPACT_EFFECT]->(r:Range)|r] as long,
[(e)-[:BEFORE]->(:Range)<-[:IMPACT_CAUSE]-(:Impact {type: "adjustment"})-[:IMPACT_EFFECT]->(r:Range)|r] as shortAdjusted
UNWIND ["longCall","shortCall","short","long","shortAdjusted","all"] as shape
WITH e, shape, apoc.coll.toSet(CASE shape 
WHEN "longCall" THEN longCall 
WHEN "shortCall" THEN shortCall 
WHEN "short" THEN short 
WHEN "long" THEN long 
WHEN "shortAdjusted" THEN shortAdjusted 
ELSE longCall+shortCall+short+long+shortAdjusted END) as l
WITH e, shape, [x IN l WHERE x.isTest IS NULL OR x.isTest<>True|x] as notTest, [x IN l WHERE x.isTest=True|x] as test
UNWIND [False,True] as isTest
WITH e, shape, isTest, size(CASE isTest WHEN False THEN notTest ELSE test END) as v
RETURN distinct e.type as type, count(distinct e) as `evo count`, shape, isTest,
sum(v) as sum,
min(v) as min,
max(v) as max,
stdev(v) as stdev,
apoc.number.format(avg(v), '0.##E0') as mean,
apoc.agg.percentiles(v) as `percentiles [0.5, 0.75, 0.9, 0.95, 0.99]`

//
MATCH (e:Evo)-[]->(:Range)-[]->(:FileSnapshot)-[]->(c:Commit)
WITH distinct e, c
OPTIONAL MATCH (e)-[]->(r:Range)<-[]-(i:Impact)
OPTIONAL MATCH (r)-[]->(r:FileSnapshot)-[]->(i:Commit)
WITH distinct e, c, collect(distinct i)[0]  as i
WITH distinct e, c, CASE WHEN i is NULL THEN i WHEN exists((c)<-[]-(:FileSnapshot)<-[]-(:Range)<-[]-(:Impact)) THEN c ELSE NULL END as i
RETURN distinct e.type as type, count(*) as evolutions,
count(distinct c) as commits, count(distinct i) as `commits with imp`

//
RETURN 
size(apoc.coll.toSet([(:Commit)-[]->(x:Repository)|x])),
size(apoc.coll.toSet([(:Evo)-[]->(:Range)-[]->(:FileSnapshot)-[]->(:Commit)-[]->(x:Repository)|x])),
size(apoc.coll.toSet([(:Impact)-[]->(:Range)-[]->(:FileSnapshot)-[]->(:Commit)-[]->(x:Repository)|x])),
size(apoc.coll.toSet([(:Impact {type:"call"})-[]->(:Range)-[]->(:FileSnapshot)-[]->(:Commit)-[]->(x:Repository)|x])),
size(apoc.coll.toSet([(x:Commit)-[]->(:Repository)|x])),
size(apoc.coll.toSet([(:Evo)-[]->(:Range)-[]->(:FileSnapshot)-[]->(x:Commit)-[]->(:Repository)|x])),
size(apoc.coll.toSet([(:Impact)-[]->(:Range)-[]->(:FileSnapshot)-[]->(x:Commit)-[]->(:Repository)|x])),
size(apoc.coll.toSet([(:Impact {type:"call"})-[]->(:Range)-[]->(:FileSnapshot)-[]->(x:Commit)-[]->(:Repository)|x])),
size(apoc.coll.toSet([(x:Range)-[]->(:FileSnapshot)-[]->(:Commit)-[]->(:Repository)|x])),
size(apoc.coll.toSet([(:Evo)-[]->(x:Range)-[]->(:FileSnapshot)-[]->(:Commit)-[]->(:Repository)|x])),
size(apoc.coll.toSet([(:Impact)-[]->(x:Range)-[]->(:FileSnapshot)-[]->(:Commit)-[]->(:Repository)|x])),
size(apoc.coll.toSet([(x:Evo)-[]->(:Range)-[]->(:FileSnapshot)-[]->(:Commit)-[]->(:Repository)|x])),
size(apoc.coll.toSet([(x:Impact)-[]->(:Range)-[]->(:FileSnapshot)-[]->(:Commit)-[]->(:Repository)|x])),
size(apoc.coll.toSet([(x:Impact {type:"call"})-[]->(:Range)-[]->(:FileSnapshot)-[]->(:Commit)-[]->(:Repository)|x])),
size(apoc.coll.toSet([(x:Impact {type:"expand to executable"})-[]->(:Range)-[]->(:FileSnapshot)-[]->(:Commit)-[]->(:Repository)|x])),
size(apoc.coll.toSet([(x:Impact {type:"adjustment"})-[]->(:Range)-[]->(:FileSnapshot)-[]->(:Commit)-[]->(:Repository)|x]))

//
MATCH (e:Evo)-[]->(:Range)-[]->(:FileSnapshot)-[]->(c:Commit)
WITH distinct e, c
WHERE exists((e)-[]->(:Range)<-[]-(:Impact)) OR exists((c)<-[]-(:FileSnapshot)<-[]-(:Range)<-[]-(:Impact))
WITH distinct e,
[(e)-[:BEFORE]->(:Range)<-[:IMPACT_CAUSE]-(:Impact {type:"adjustment"})-[:IMPACT_EFFECT]->(:Range)<-[:IMPACT_CAUSE]-(:Impact {type: "call"})-[:IMPACT_EFFECT]->(:Range)<-[:IMPACT_CAUSE]-(:Impact {type:"expand to executable"})-[:IMPACT_EFFECT]->(r:Range) WHERE exists(r.sig)|r] as longCall, 
[(e)-[:BEFORE]->(:Range)<-[:IMPACT_CAUSE]-(:Impact {type: "call"})-[:IMPACT_EFFECT]->(:Range)<-[:IMPACT_CAUSE]-(:Impact {type:"expand to executable"})-[:IMPACT_EFFECT]->(r:Range) WHERE exists(r.sig)|r] as shortCall, 
[(e)-[:BEFORE]->(r:Range) WHERE exists(r.sig)|r] as short, 
[(e)-[:BEFORE]->(:Range)<-[:IMPACT_CAUSE]-(:Impact {type:"expand to executable"})-[:IMPACT_EFFECT]->(r:Range) WHERE exists(r.sig)|r] as long,
[(e)-[:BEFORE]->(:Range)<-[:IMPACT_CAUSE]-(:Impact {type: "adjustment"})-[:IMPACT_EFFECT]->(r:Range) WHERE exists(r.sig)|r] as shortAdjusted
UNWIND ["longCall","shortCall","short","long","shortAdjusted","all"] as shape
WITH e, shape, apoc.coll.toSet(CASE shape 
WHEN "longCall" THEN longCall 
WHEN "shortCall" THEN shortCall 
WHEN "short" THEN short 
WHEN "long" THEN long 
WHEN "shortAdjusted" THEN shortAdjusted 
ELSE longCall+shortCall+short+long+shortAdjusted END) as l
WITH e, shape, [x IN l WHERE x.isTest IS NULL OR x.isTest<>True|x] as notTest, [x IN l WHERE x.isTest=True|x] as test
UNWIND [False,True] as isTest
WITH e, shape, isTest, size(CASE isTest WHEN False THEN notTest ELSE test END) as v
RETURN distinct e.type as type, count(distinct e) as `evo count`, 
shape, isTest,
sum(v) as sum,
min(v) as min,
max(v) as max,
stdev(v) as stdev,
apoc.number.format(avg(v), '0.##E0') as mean,
apoc.agg.percentiles(v) as `percentiles [0.5, 0.75, 0.9, 0.95, 0.99]`

//
MATCH (e:Evo)-[]->(:Range)-[]->(:FileSnapshot)-[]->(c:Commit)
WITH distinct e, c
WITH distinct e,
[(e)-[:BEFORE]->(:Range)<-[:IMPACT_CAUSE]-(:Impact {type:"adjustment"})-[:IMPACT_EFFECT]->(:Range)<-[:IMPACT_CAUSE]-(:Impact {type: "call"})-[:IMPACT_EFFECT]->(:Range)<-[:IMPACT_CAUSE]-(:Impact {type:"expand to executable"})-[:IMPACT_EFFECT]->(r:Range) WHERE exists(r.sig)|r] as longCall, 
[(e)-[:BEFORE]->(:Range)<-[:IMPACT_CAUSE]-(:Impact {type: "call"})-[:IMPACT_EFFECT]->(:Range)<-[:IMPACT_CAUSE]-(:Impact {type:"expand to executable"})-[:IMPACT_EFFECT]->(r:Range) WHERE exists(r.sig)|r] as shortCall, 
[(e)-[:BEFORE]->(r:Range) WHERE exists(r.sig)|r] as short, 
[(e)-[:BEFORE]->(:Range)<-[:IMPACT_CAUSE]-(:Impact {type:"expand to executable"})-[:IMPACT_EFFECT]->(r:Range) WHERE exists(r.sig)|r] as long,
[(e)-[:BEFORE]->(:Range)<-[:IMPACT_CAUSE]-(:Impact {type: "adjustment"})-[:IMPACT_EFFECT]->(r:Range) WHERE exists(r.sig)|r] as shortAdjusted
UNWIND ["longCall","shortCall","short","long","shortAdjusted","all"] as shape
WITH e, shape, apoc.coll.toSet(CASE shape 
WHEN "longCall" THEN longCall 
WHEN "shortCall" THEN shortCall 
WHEN "short" THEN short 
WHEN "long" THEN long 
WHEN "shortAdjusted" THEN shortAdjusted 
ELSE longCall+shortCall+short+long+shortAdjusted END) as l
WITH e, shape, [x IN l WHERE x.isTest IS NULL OR x.isTest<>True|x] as notTest, [x IN l WHERE x.isTest=True|x] as test
RETURN count(distinct e), count(distinct test)

0000000000000000000000000



    \draw[draw opacity=0.0, fill=red!80, fill opacity=0.5]
        ([xshift=-0pt,yshift=5pt]$(pic cs:b1) + (0pt,0pt)$) rectangle ([xshift=0pt,yshift=-2pt]$(pic cs:b2)+(0pt,-0pt)$);
    \draw[draw opacity=0.0, fill=green!80, fill opacity=0.5]
        ([xshift=-0pt,yshift=5pt]$(pic cs:b3) + (0pt,0pt)$) rectangle ([xshift=0pt,yshift=-2pt]$(pic cs:b4)+(0pt,-0pt)$);
    \draw[draw opacity=0.0, fill=red!80, fill opacity=0.5]
        ([xshift=-0pt,yshift=5pt]$(pic cs:c1) + (0pt,0pt)$) rectangle ([xshift=0pt,yshift=-2pt]$(pic cs:c2)+(0pt,-0pt)$);
    \draw[draw opacity=0.0, fill=green!80, fill opacity=0.5]
        ([xshift=-0pt,yshift=5pt]$(pic cs:c2) + (0pt,0pt)$) rectangle ([xshift=0pt,yshift=-2pt]$(pic cs:c3)+(0pt,-0pt)$);
        
    \draw[-latex, line width = 1pt] ($(pic cs:x0) + (-2pt,1pt)$) .. controls ($(pic cs:x0) + (-13pt,-100pt)$) and ($(pic cs:x1)+(-20pt,1pt)$) .. ($(pic cs:x1)+(-3pt,1pt)$);
    
    \draw[draw opacity=0.0, fill=red!80, fill opacity=0.5]
        ([xshift=-0pt,yshift=5pt]$(pic cs:d1) + (0pt,0pt)$) rectangle ([xshift=0pt,yshift=-2pt]$(pic cs:d2)+(0pt,-0pt)$);
    \draw[draw opacity=0.0, fill=green!80, fill opacity=0.5]
        ([xshift=-0pt,yshift=5pt]$(pic cs:d2) + (0pt,0pt)$) rectangle ([xshift=0pt,yshift=-2pt]$(pic cs:d3)+(0pt,-0pt)$);
    \draw[draw opacity=0.0, fill=red!80, fill opacity=0.5]
        ([xshift=-0pt,yshift=5pt]$(pic cs:e1) + (0pt,0pt)$) rectangle ([xshift=0pt,yshift=-2pt]$(pic cs:e2)+(0pt,-0pt)$);
    \draw[draw opacity=0.0, fill=green!80, fill opacity=0.5]
        ([xshift=-0pt,yshift=5pt]$(pic cs:e2) + (0pt,0pt)$) rectangle ([xshift=0pt,yshift=-2pt]$(pic cs:e3)+(0pt,-0pt)$);
    \draw[draw opacity=0.0, fill=red!80, fill opacity=0.5]
        ([xshift=-0pt,yshift=5pt]$(pic cs:f1) + (0pt,0pt)$) rectangle ([xshift=0pt,yshift=-2pt]$(pic cs:f2)+(0pt,-0pt)$);
    \draw[draw opacity=0.0, fill=green!80, fill opacity=0.5]
        ([xshift=-0pt,yshift=5pt]$(pic cs:f2) + (0pt,0pt)$) rectangle ([xshift=0pt,yshift=-2pt]$(pic cs:f3)+(0pt,-0pt)$);
    \draw[draw opacity=0.0, fill=red!80, fill opacity=0.5]
        ([xshift=-0pt,yshift=5pt]$(pic cs:g1) + (0pt,0pt)$) rectangle ([xshift=0pt,yshift=-2pt]$(pic cs:g2)+(0pt,-0pt)$);
    \draw[draw opacity=0.0, fill=green!80, fill opacity=0.5]
        ([xshift=-0pt,yshift=5pt]$(pic cs:g3) + (0pt,0pt)$) rectangle ([xshift=0pt,yshift=-2pt]$(pic cs:g4)+(0pt,-0pt)$);
    \draw[draw opacity=0.0, fill=red!80, fill opacity=0.5]
        ([xshift=-0pt,yshift=5pt]$(pic cs:h1) + (0pt,0pt)$) rectangle ([xshift=0pt,yshift=-2pt]$(pic cs:h2)+(0pt,-0pt)$);
    \draw[draw opacity=0.0, fill=green!80, fill opacity=0.5]
        ([xshift=-0pt,yshift=5pt]$(pic cs:h2) + (0pt,0pt)$) rectangle ([xshift=0pt,yshift=-2pt]$(pic cs:h3)+(0pt,-0pt)$);
    \draw[draw opacity=0.0, fill=red!80, fill opacity=0.5]
        ([xshift=-0pt,yshift=5pt]$(pic cs:i1) + (0pt,0pt)$) rectangle ([xshift=0pt,yshift=-2pt]$(pic cs:i2)+(0pt,-0pt)$);
    \draw[draw opacity=0.0, fill=green!80, fill opacity=0.5]
        ([xshift=-0pt,yshift=5pt]$(pic cs:i2) + (0pt,0pt)$) rectangle ([xshift=0pt,yshift=-2pt]$(pic cs:i3)+(0pt,-0pt)$);
    \draw[draw opacity=0.0, fill=red!80, fill opacity=0.5]
        ([xshift=-0pt,yshift=5pt]$(pic cs:j1) + (0pt,0pt)$) rectangle ([xshift=0pt,yshift=-2pt]$(pic cs:j2)+(0pt,-0pt)$);
    \draw[draw opacity=0.0, fill=green!80, fill opacity=0.5]
        ([xshift=-0pt,yshift=5pt]$(pic cs:k1) + (0pt,0pt)$) rectangle ([xshift=0pt,yshift=-2pt]$(pic cs:k2)+(0pt,-0pt)$);
    \draw[draw opacity=0.0, fill=red!80, fill opacity=0.5]
        ([xshift=-0pt,yshift=5pt]$(pic cs:l1) + (0pt,0pt)$) rectangle ([xshift=0pt,yshift=-2pt]$(pic cs:l2)+(0pt,-0pt)$);
    \draw[draw opacity=0.0, fill=green!80, fill opacity=0.5]
        ([xshift=-0pt,yshift=5pt]$(pic cs:l2) + (0pt,0pt)$) rectangle ([xshift=0pt,yshift=-2pt]$(pic cs:l3)+(0pt,-0pt)$);
    \draw[draw opacity=0.0, fill=green!80, fill opacity=0.5]
        ([xshift=-0pt,yshift=5pt]$(pic cs:m1) + (0pt,0pt)$) rectangle ([xshift=0pt,yshift=-2pt]$(pic cs:m2)+(0pt,-0pt)$);
    \draw[draw opacity=0.0, fill=red!80, fill opacity=0.5]
        ([xshift=-0pt,yshift=5pt]$(pic cs:o1) + (0pt,0pt)$) rectangle ([xshift=0pt,yshift=-2pt]$(pic cs:o2)+(0pt,-0pt)$);
    \draw[draw opacity=0.0, fill=green!80, fill opacity=0.5]
        ([xshift=-0pt,yshift=5pt]$(pic cs:o3) + (0pt,0pt)$) rectangle ([xshift=0pt,yshift=-2pt]$(pic cs:o4)+(0pt,-0pt)$);
    \draw[draw opacity=0.0, fill=red!80, fill opacity=0.5]
        ([xshift=-0pt,yshift=5pt]$(pic cs:p1) + (0pt,0pt)$) rectangle ([xshift=0pt,yshift=-2pt]$(pic cs:p2)+(0pt,-0pt)$);
    \draw[draw opacity=0.0, fill=green!80, fill opacity=0.5]
        ([xshift=-0pt,yshift=5pt]$(pic cs:p3) + (0pt,0pt)$) rectangle ([xshift=0pt,yshift=-2pt]$(pic cs:p4)+(0pt,-0pt)$);
    
    \draw[-latex, line width = 1pt] ($(pic cs:y0) + (-2pt,1pt)$) .. controls ($(pic cs:y0) + (-13pt,-100pt)$) and ($(pic cs:y1)+(-20pt,1pt)$) .. ($(pic cs:y1)+(-3pt,1pt)$);
    
    \draw[-latex, line width = 1pt] ($(pic cs:z0) + (-2pt,1pt)$) .. controls ($(pic cs:z0) + (-13pt,-30pt)$) and ($(pic cs:z1)+(-20pt,1pt)$) .. ($(pic cs:z1)+(-3pt,1pt)$);
    
    \draw[-latex, line width = 1pt] ($(pic cs:create) + (2pt,1pt)$) .. controls ($(pic cs:create) + (13pt,0pt)$) and ($(pic cs:ta)+(-30pt,1pt)$) .. ($(pic cs:ta)+(-3pt,1pt)$);
    
    \draw[-latex, line width = 1pt] ($(pic cs:create) + (2pt,1pt)$) .. controls ($(pic cs:create) + (13pt,0pt)$) and ($(pic cs:tb)+(-30pt,1pt)$) .. ($(pic cs:tb)+(-3pt,1pt)$);
    
    \draw[draw opacity=0.0, fill=red!80, fill opacity=0.5]
        ([xshift=-0pt,yshift=5pt]$(pic cs:q1) + (0pt,0pt)$) rectangle ([xshift=0pt,yshift=-2pt]$(pic cs:q2)+(0pt,-0pt)$);
    \draw[draw opacity=0.0, fill=green!80, fill opacity=0.5]
        ([xshift=-0pt,yshift=5pt]$(pic cs:q2) + (0pt,0pt)$) rectangle ([xshift=0pt,yshift=-2pt]$(pic cs:q3)+(0pt,-0pt)$);
    \draw[draw opacity=0.0, fill=red!80, fill opacity=0.5]
        ([xshift=-0pt,yshift=5pt]$(pic cs:r1) + (0pt,0pt)$) rectangle ([xshift=0pt,yshift=-2pt]$(pic cs:r2)+(0pt,-0pt)$);
    \draw[draw opacity=0.0, fill=green!80, fill opacity=0.5]
        ([xshift=-0pt,yshift=5pt]$(pic cs:s1) + (0pt,0pt)$) rectangle ([xshift=0pt,yshift=-2pt]$(pic cs:s2)+(0pt,-0pt)$);
    \draw[draw opacity=0.0, fill=red!80, fill opacity=0.5]
        ([xshift=-0pt,yshift=5pt]$(pic cs:t1) + (0pt,0pt)$) rectangle ([xshift=0pt,yshift=-2pt]$(pic cs:t2)+(0pt,-0pt)$);
    \draw[draw opacity=0.0, fill=green!80, fill opacity=0.5]
        ([xshift=-0pt,yshift=5pt]$(pic cs:aaa1) + (0pt,0pt)$) rectangle ([xshift=0pt,yshift=-2pt]$(pic cs:aaa2)+(0pt,-0pt)$);
    \draw[draw opacity=0.0, fill=red!80, fill opacity=0.5]
        ([xshift=-0pt,yshift=5pt]$(pic cs:aaa2) + (0pt,0pt)$) rectangle ([xshift=0pt,yshift=-2pt]$(pic cs:aaa3)+(0pt,-0pt)$);
        
    \draw[-latex, line width = 1pt] ($(pic cs:create) + (2pt,1pt)$) .. controls ($(pic cs:create) + (13pt,0pt)$) and ($(pic cs:tc)+(-30pt,1pt)$) .. ($(pic cs:tc)+(-3pt,1pt)$);
    
    \draw[draw opacity=0.0, fill=green!80, fill opacity=0.5]
        ([xshift=-0pt,yshift=5pt]$(pic cs:u1) + (0pt,0pt)$) rectangle ([xshift=0pt,yshift=-2pt]$(pic cs:u2)+(0pt,-0pt)$);
    \draw[draw opacity=0.0, fill=red!80, fill opacity=0.5]
        ([xshift=-0pt,yshift=5pt]$(pic cs:u2) + (0pt,0pt)$) rectangle ([xshift=0pt,yshift=-2pt]$(pic cs:u3)+(0pt,-0pt)$);
    \draw[draw opacity=0.0, fill=green!80, fill opacity=0.5]
        ([xshift=-0pt,yshift=5pt]$(pic cs:v1) + (0pt,0pt)$) rectangle ([xshift=0pt,yshift=-2pt]$(pic cs:v2)+(0pt,-0pt)$);
    \draw[draw opacity=0.0, fill=red!80, fill opacity=0.5]
        ([xshift=-0pt,yshift=5pt]$(pic cs:v2) + (0pt,0pt)$) rectangle ([xshift=0pt,yshift=-2pt]$(pic cs:v3)+(0pt,-0pt)$);
        
    \draw[-latex, line width = 1pt] ($(pic cs:loadGraph) + (2pt,1pt)$) .. controls ($(pic cs:loadGraph) + (13pt,0pt)$) and ($(pic cs:td)+(-30pt,1pt)$) .. ($(pic cs:td)+(-3pt,1pt)$);
    



aaaaaaaaaaaaaaaaaaaaaaaaa whole


\begin{tikzpicture}[remember picture,overlay]
    \draw[draw opacity=0.0, fill=red!80, fill opacity=0.5]
        ([xshift=-0pt,yshift=5pt]$(pic cs:a1) + (0pt,0pt)$) rectangle ([xshift=0pt,yshift=-2pt]$(pic cs:a2)+(0pt,-0pt)$);
    \draw[draw opacity=0.0, fill=green!80, fill opacity=0.5]
        ([xshift=-0pt,yshift=5pt]$(pic cs:a2) + (0pt,0pt)$) rectangle ([xshift=0pt,yshift=-2pt]$(pic cs:a3)+(0pt,-0pt)$);
    \draw[draw opacity=0.0, fill=red!80, fill opacity=0.5]
        ([xshift=-0pt,yshift=5pt]$(pic cs:b1) + (0pt,0pt)$) rectangle ([xshift=0pt,yshift=-2pt]$(pic cs:b2)+(0pt,-0pt)$);
    \draw[draw opacity=0.0, fill=green!80, fill opacity=0.5]
        ([xshift=-0pt,yshift=5pt]$(pic cs:b3) + (0pt,0pt)$) rectangle ([xshift=0pt,yshift=-2pt]$(pic cs:b4)+(0pt,-0pt)$);
    \draw[draw opacity=0.0, fill=red!80, fill opacity=0.5]
        ([xshift=-0pt,yshift=5pt]$(pic cs:c1) + (0pt,0pt)$) rectangle ([xshift=0pt,yshift=-2pt]$(pic cs:c2)+(0pt,-0pt)$);
    \draw[draw opacity=0.0, fill=green!80, fill opacity=0.5]
        ([xshift=-0pt,yshift=5pt]$(pic cs:c2) + (0pt,0pt)$) rectangle ([xshift=0pt,yshift=-2pt]$(pic cs:c3)+(0pt,-0pt)$);
        
    \draw[-latex, line width = 1pt] ($(pic cs:x0) + (-2pt,1pt)$) .. controls ($(pic cs:x0) + (-13pt,-100pt)$) and ($(pic cs:x1)+(-20pt,1pt)$) .. ($(pic cs:x1)+(-3pt,1pt)$);
    
    \draw[draw opacity=0.0, fill=red!80, fill opacity=0.5]
        ([xshift=-0pt,yshift=5pt]$(pic cs:d1) + (0pt,0pt)$) rectangle ([xshift=0pt,yshift=-2pt]$(pic cs:d2)+(0pt,-0pt)$);
    \draw[draw opacity=0.0, fill=green!80, fill opacity=0.5]
        ([xshift=-0pt,yshift=5pt]$(pic cs:d2) + (0pt,0pt)$) rectangle ([xshift=0pt,yshift=-2pt]$(pic cs:d3)+(0pt,-0pt)$);
    \draw[draw opacity=0.0, fill=red!80, fill opacity=0.5]
        ([xshift=-0pt,yshift=5pt]$(pic cs:e1) + (0pt,0pt)$) rectangle ([xshift=0pt,yshift=-2pt]$(pic cs:e2)+(0pt,-0pt)$);
    \draw[draw opacity=0.0, fill=green!80, fill opacity=0.5]
        ([xshift=-0pt,yshift=5pt]$(pic cs:e2) + (0pt,0pt)$) rectangle ([xshift=0pt,yshift=-2pt]$(pic cs:e3)+(0pt,-0pt)$);
    \draw[draw opacity=0.0, fill=red!80, fill opacity=0.5]
        ([xshift=-0pt,yshift=5pt]$(pic cs:f1) + (0pt,0pt)$) rectangle ([xshift=0pt,yshift=-2pt]$(pic cs:f2)+(0pt,-0pt)$);
    \draw[draw opacity=0.0, fill=green!80, fill opacity=0.5]
        ([xshift=-0pt,yshift=5pt]$(pic cs:f2) + (0pt,0pt)$) rectangle ([xshift=0pt,yshift=-2pt]$(pic cs:f3)+(0pt,-0pt)$);
    \draw[draw opacity=0.0, fill=red!80, fill opacity=0.5]
        ([xshift=-0pt,yshift=5pt]$(pic cs:g1) + (0pt,0pt)$) rectangle ([xshift=0pt,yshift=-2pt]$(pic cs:g2)+(0pt,-0pt)$);
    \draw[draw opacity=0.0, fill=green!80, fill opacity=0.5]
        ([xshift=-0pt,yshift=5pt]$(pic cs:g3) + (0pt,0pt)$) rectangle ([xshift=0pt,yshift=-2pt]$(pic cs:g4)+(0pt,-0pt)$);
    \draw[draw opacity=0.0, fill=red!80, fill opacity=0.5]
        ([xshift=-0pt,yshift=5pt]$(pic cs:h1) + (0pt,0pt)$) rectangle ([xshift=0pt,yshift=-2pt]$(pic cs:h2)+(0pt,-0pt)$);
    \draw[draw opacity=0.0, fill=green!80, fill opacity=0.5]
        ([xshift=-0pt,yshift=5pt]$(pic cs:h2) + (0pt,0pt)$) rectangle ([xshift=0pt,yshift=-2pt]$(pic cs:h3)+(0pt,-0pt)$);
    \draw[draw opacity=0.0, fill=red!80, fill opacity=0.5]
        ([xshift=-0pt,yshift=5pt]$(pic cs:i1) + (0pt,0pt)$) rectangle ([xshift=0pt,yshift=-2pt]$(pic cs:i2)+(0pt,-0pt)$);
    \draw[draw opacity=0.0, fill=green!80, fill opacity=0.5]
        ([xshift=-0pt,yshift=5pt]$(pic cs:i2) + (0pt,0pt)$) rectangle ([xshift=0pt,yshift=-2pt]$(pic cs:i3)+(0pt,-0pt)$);
    \draw[draw opacity=0.0, fill=red!80, fill opacity=0.5]
        ([xshift=-0pt,yshift=5pt]$(pic cs:j1) + (0pt,0pt)$) rectangle ([xshift=0pt,yshift=-2pt]$(pic cs:j2)+(0pt,-0pt)$);
    \draw[draw opacity=0.0, fill=green!80, fill opacity=0.5]
        ([xshift=-0pt,yshift=5pt]$(pic cs:k1) + (0pt,0pt)$) rectangle ([xshift=0pt,yshift=-2pt]$(pic cs:k2)+(0pt,-0pt)$);
    \draw[draw opacity=0.0, fill=red!80, fill opacity=0.5]
        ([xshift=-0pt,yshift=5pt]$(pic cs:l1) + (0pt,0pt)$) rectangle ([xshift=0pt,yshift=-2pt]$(pic cs:l2)+(0pt,-0pt)$);
    \draw[draw opacity=0.0, fill=green!80, fill opacity=0.5]
        ([xshift=-0pt,yshift=5pt]$(pic cs:l2) + (0pt,0pt)$) rectangle ([xshift=0pt,yshift=-2pt]$(pic cs:l3)+(0pt,-0pt)$);
    \draw[draw opacity=0.0, fill=green!80, fill opacity=0.5]
        ([xshift=-0pt,yshift=5pt]$(pic cs:m1) + (0pt,0pt)$) rectangle ([xshift=0pt,yshift=-2pt]$(pic cs:m2)+(0pt,-0pt)$);
    \draw[draw opacity=0.0, fill=red!80, fill opacity=0.5]
        ([xshift=-0pt,yshift=5pt]$(pic cs:o1) + (0pt,0pt)$) rectangle ([xshift=0pt,yshift=-2pt]$(pic cs:o2)+(0pt,-0pt)$);
    \draw[draw opacity=0.0, fill=green!80, fill opacity=0.5]
        ([xshift=-0pt,yshift=5pt]$(pic cs:o3) + (0pt,0pt)$) rectangle ([xshift=0pt,yshift=-2pt]$(pic cs:o4)+(0pt,-0pt)$);
    \draw[draw opacity=0.0, fill=red!80, fill opacity=0.5]
        ([xshift=-0pt,yshift=5pt]$(pic cs:p1) + (0pt,0pt)$) rectangle ([xshift=0pt,yshift=-2pt]$(pic cs:p2)+(0pt,-0pt)$);
    \draw[draw opacity=0.0, fill=green!80, fill opacity=0.5]
        ([xshift=-0pt,yshift=5pt]$(pic cs:p3) + (0pt,0pt)$) rectangle ([xshift=0pt,yshift=-2pt]$(pic cs:p4)+(0pt,-0pt)$);
    
    \draw[-latex, line width = 1pt] ($(pic cs:y0) + (-2pt,1pt)$) .. controls ($(pic cs:y0) + (-13pt,-100pt)$) and ($(pic cs:y1)+(-20pt,1pt)$) .. ($(pic cs:y1)+(-3pt,1pt)$);
    
    \draw[-latex, line width = 1pt] ($(pic cs:z0) + (-2pt,1pt)$) .. controls ($(pic cs:z0) + (-13pt,-30pt)$) and ($(pic cs:z1)+(-20pt,1pt)$) .. ($(pic cs:z1)+(-3pt,1pt)$);
    
    \draw[-latex, line width = 1pt] ($(pic cs:create) + (2pt,1pt)$) .. controls ($(pic cs:create) + (13pt,0pt)$) and ($(pic cs:ta)+(-30pt,1pt)$) .. ($(pic cs:ta)+(-3pt,1pt)$);
    
    \draw[-latex, line width = 1pt] ($(pic cs:create) + (2pt,1pt)$) .. controls ($(pic cs:create) + (13pt,0pt)$) and ($(pic cs:tb)+(-30pt,1pt)$) .. ($(pic cs:tb)+(-3pt,1pt)$);
    
    \draw[draw opacity=0.0, fill=red!80, fill opacity=0.5]
        ([xshift=-0pt,yshift=5pt]$(pic cs:q1) + (0pt,0pt)$) rectangle ([xshift=0pt,yshift=-2pt]$(pic cs:q2)+(0pt,-0pt)$);
    \draw[draw opacity=0.0, fill=green!80, fill opacity=0.5]
        ([xshift=-0pt,yshift=5pt]$(pic cs:q2) + (0pt,0pt)$) rectangle ([xshift=0pt,yshift=-2pt]$(pic cs:q3)+(0pt,-0pt)$);
    \draw[draw opacity=0.0, fill=red!80, fill opacity=0.5]
        ([xshift=-0pt,yshift=5pt]$(pic cs:r1) + (0pt,0pt)$) rectangle ([xshift=0pt,yshift=-2pt]$(pic cs:r2)+(0pt,-0pt)$);
    \draw[draw opacity=0.0, fill=green!80, fill opacity=0.5]
        ([xshift=-0pt,yshift=5pt]$(pic cs:s1) + (0pt,0pt)$) rectangle ([xshift=0pt,yshift=-2pt]$(pic cs:s2)+(0pt,-0pt)$);
    \draw[draw opacity=0.0, fill=red!80, fill opacity=0.5]
        ([xshift=-0pt,yshift=5pt]$(pic cs:t1) + (0pt,0pt)$) rectangle ([xshift=0pt,yshift=-2pt]$(pic cs:t2)+(0pt,-0pt)$);
    \draw[draw opacity=0.0, fill=green!80, fill opacity=0.5]
        ([xshift=-0pt,yshift=5pt]$(pic cs:aaa1) + (0pt,0pt)$) rectangle ([xshift=0pt,yshift=-2pt]$(pic cs:aaa2)+(0pt,-0pt)$);
    \draw[draw opacity=0.0, fill=red!80, fill opacity=0.5]
        ([xshift=-0pt,yshift=5pt]$(pic cs:aaa2) + (0pt,0pt)$) rectangle ([xshift=0pt,yshift=-2pt]$(pic cs:aaa3)+(0pt,-0pt)$);
        
    \draw[-latex, line width = 1pt] ($(pic cs:create) + (2pt,1pt)$) .. controls ($(pic cs:create) + (13pt,0pt)$) and ($(pic cs:tc)+(-30pt,1pt)$) .. ($(pic cs:tc)+(-3pt,1pt)$);
    
    \draw[draw opacity=0.0, fill=green!80, fill opacity=0.5]
        ([xshift=-0pt,yshift=5pt]$(pic cs:u1) + (0pt,0pt)$) rectangle ([xshift=0pt,yshift=-2pt]$(pic cs:u2)+(0pt,-0pt)$);
    \draw[draw opacity=0.0, fill=red!80, fill opacity=0.5]
        ([xshift=-0pt,yshift=5pt]$(pic cs:u2) + (0pt,0pt)$) rectangle ([xshift=0pt,yshift=-2pt]$(pic cs:u3)+(0pt,-0pt)$);
    \draw[draw opacity=0.0, fill=green!80, fill opacity=0.5]
        ([xshift=-0pt,yshift=5pt]$(pic cs:v1) + (0pt,0pt)$) rectangle ([xshift=0pt,yshift=-2pt]$(pic cs:v2)+(0pt,-0pt)$);
    \draw[draw opacity=0.0, fill=red!80, fill opacity=0.5]
        ([xshift=-0pt,yshift=5pt]$(pic cs:v2) + (0pt,0pt)$) rectangle ([xshift=0pt,yshift=-2pt]$(pic cs:v3)+(0pt,-0pt)$);
        
    \draw[-latex, line width = 1pt] ($(pic cs:loadGraph) + (2pt,1pt)$) .. controls ($(pic cs:loadGraph) + (13pt,0pt)$) and ($(pic cs:td)+(-30pt,1pt)$) .. ($(pic cs:td)+(-3pt,1pt)$);
    
        
\end{tikzpicture}

WITH distinct e, c
WHERE exists((e)-[]->(:Range)<-[]-(:Impact)) OR exists((c)<-[]-(:FileSnapshot)<-[]-(:Range)<-[]-(:Impact))
WITH distinct e,
[(e)-[:BEFORE]->(:Range)<-[:IMPACT_CAUSE]-(:Impact {type:"adjustment"})-[:IMPACT_EFFECT]->(:Range)<-[:IMPACT_CAUSE]-(:Impact {type: "call"})-[:IMPACT_EFFECT]->(:Range)<-[:IMPACT_CAUSE]-(:Impact {type:"expand to executable"})-[:IMPACT_EFFECT]->(r:Range) WHERE exists(r.sig)|r] as longCall, 
[(e)-[:BEFORE]->(:Range)<-[:IMPACT_CAUSE]-(:Impact {type: "call"})-[:IMPACT_EFFECT]->(:Range)<-[:IMPACT_CAUSE]-(:Impact {type:"expand to executable"})-[:IMPACT_EFFECT]->(r:Range) WHERE exists(r.sig)|r] as shortCall, 
[(e)-[:BEFORE]->(r:Range) WHERE exists(r.sig)|r] as short, 
[(e)-[:BEFORE]->(:Range)<-[:IMPACT_CAUSE]-(:Impact {type:"expand to executable"})-[:IMPACT_EFFECT]->(r:Range) WHERE exists(r.sig)|r] as long,
[(e)-[:BEFORE]->(:Range)<-[:IMPACT_CAUSE]-(:Impact {type: "adjustment"})-[:IMPACT_EFFECT]->(r:Range) WHERE exists(r.sig)|r] as shortAdjusted
UNWIND ["longCall","shortCall","short","long","shortAdjusted","all"] as shape
WITH e, shape, apoc.coll.toSet(CASE shape 
WHEN "longCall" THEN longCall 
WHEN "shortCall" THEN shortCall 
WHEN "short" THEN short 
WHEN "long" THEN long 
WHEN "shortAdjusted" THEN shortAdjusted 
ELSE longCall+shortCall+short+long+shortAdjusted END) as l
WITH e, shape, [x IN l WHERE x.isTest IS NULL OR x.isTest<>True|x] as notTest, [x IN l WHERE x.isTest=True|x] as test
RETURN count(distinct e), count(distinct test), count(distinct notTest)



\begin{center}\renewcommand*{\arraystretch}{.5}
\begin{longtable}{|p{2.3cm}|p{.8cm}|p{.8cm}|p{.8cm}|}
\caption{\label{tbl:patterns:initial}
Initial patterns represented in an adjacency matrix [white caps: cause or reso, black caps: resolution]}
\\
\hline
\diagbox[width=2.7cm,height=2.4cm]{Causes}{Resolutions} & \hspace*{-1.2}\ResoCapsule{CVT} & \hspace*{-1.2}\ResoCapsule{RC} & \hspace*{-1.2}\ResoCapsule{RM}\\
\hline
\endfirsthead
\multicolumn{4}{l}{Continued from previous page} \\
\hline

\diagbox[width=2.7cm,height=2.4cm]{Causes}{Resolutions} & \hspace*{-1.2}\ResoCapsule{CVT} & \hspace*{-1.2}\ResoCapsule{RC} & \hspace*{-1.2}\ResoCapsule{RM} \\

\hline
\endhead
\hline\multicolumn{4}{r}{Continued on next page} \\
\endfoot
\endlastfoot
\hline
\EvoCapsule{CPT} & \cellcolor{red!56!white}\sfrac{96}{112} & 0 & 0\\
\EvoCapsule{CPT} \EvoCapsule{CRT} & \cellcolor{red!7!white}\sfrac{2}{2} & 0 & 0\\
\EvoCapsule{CPT} \EvoCapsule{CVT} & \cellcolor{red!11!white}\sfrac{2}{4} & 0 & 0\\
\EvoCapsule{CPT} \EvoCapsule{RC} & \cellcolor{red!17!white}\sfrac{10}{10} & 0 & 0\\
\EvoCapsule{CRT} & \cellcolor{red!26!white}\sfrac{24}{24} & 0 & 0\\
\EvoCapsule{CRT} \EvoCapsule{CVT} & \cellcolor{red!5!white}\sfrac{1}{1} & 0 & 0\\
\EvoCapsule{CRT} \EvoCapsule{RC} & \cellcolor{red!7!white}\sfrac{1}{2} & \cellcolor{red!5!white}\sfrac{0}{1} & 0\\
\EvoCapsule{CVT} & \cellcolor{red!18!white}\sfrac{10}{12} & 0 & \cellcolor{red!12!white}\sfrac{4}{5}\\
\EvoCapsule{RC} & \cellcolor{red!33!white}\sfrac{35}{40} & \cellcolor{red!20!white}\sfrac{0}{15} & 0\\
\EvoCapsule{RM} & \cellcolor{red!11!white}\sfrac{4}{4} & 0 & \cellcolor{red!17!white}\sfrac{0}{11}\\
\hline
\end{longtable}
\end{center}


/////////

\begin{center}\renewcommand*{\arraystretch}{.5}
\begin{minipage}{.8\textwidth}
\begin{center}
\begin{longtable}{|p{2.3cm}|p{.9cm}|p{.9cm}|p{.9cm}|p{.9cm}|p{.9cm}|p{.9cm}|p{.9cm}|}
\caption{\label{tbl:patterns:validated}
Validated Co-evolutions represented in an adjacency matrix [white capsule: cause or resolution, black capsule: resolution]}
\\
\hline
\diagbox[width=2.7cm,height=2.4cm]{Causes}{Resolutions} & \hspace*{-2.8pt}\ResoCapsule{CVT} & \hspace*{-2.8pt}\ResoCapsule{EV} & \hspace*{-2.8pt}\ResoCapsule{MARC} & \hspace*{-2.8pt}\ResoCapsule{MC} & \hspace*{-2.8pt}\ResoCapsule{RC} & \hspace*{-2.8pt}\ResoCapsule{RM}\\
\hline
\endfirsthead
\multicolumn{7}{l}{Continued from previous page} \\
\hline

\diagbox[width=2.7cm,height=2.4cm]{Causes}{Resolutions} & \hspace*{-2.8pt}\ResoCapsule{CVT} & \hspace*{-2.8pt}\ResoCapsule{EV} & \hspace*{-2.8pt}\ResoCapsule{MARC} & \hspace*{-2.8pt}\ResoCapsule{MC} & \hspace*{-2.8pt}\ResoCapsule{RC} & \hspace*{-2.8pt}\ResoCapsule{RM} \\

\hline
\endhead
\hline\multicolumn{7}{r}{Continued on next page} \\
\endfoot
\endlastfoot
\hline
\EvoCapsule{CPT} & \cellcolor{red!84!white}\sfrac{24}{28} & \cellcolor{red!16!white}\sfrac{0}{1} & 0 & 0 & 0 & 0\\
\EvoCapsule{CPT} \EvoCapsule{CRT} & \cellcolor{red!22!white}\sfrac{2}{2} & 0 & 0 & 0 & 0 & 0\\
\EvoCapsule{CPT} \EvoCapsule{CRT} \EvoCapsule{RC} & \cellcolor{red!16!white}\sfrac{1}{1} & 0 & 0 & 0 & 0 & 0\\
\EvoCapsule{CPT} \EvoCapsule{CRT} \EvoCapsule{RM} & \cellcolor{red!22!white}\sfrac{2}{2} & 0 & 0 & 0 & 0 & 0\\
\EvoCapsule{CPT} \EvoCapsule{CVT} & \cellcolor{red!32!white}\sfrac{2}{4} & 0 & 0 & 0 & 0 & 0\\
\EvoCapsule{CPT} \EvoCapsule{RC} & \cellcolor{red!35!white}\sfrac{5}{5} & 0 & 0 & 0 & 0 & 0\\
\EvoCapsule{CPT} \EvoCapsule{RC} \EvoCapsule{RM} & \cellcolor{red!16!white}\sfrac{1}{1} & 0 & 0 & 0 & 0 & 0\\
\EvoCapsule{CPT} \EvoCapsule{RM} & \cellcolor{red!27!white}\sfrac{3}{3} & 0 & 0 & 0 & 0 & 0\\
\EvoCapsule{CRT} & \cellcolor{red!45!white}\sfrac{8}{8} & \cellcolor{red!16!white}\sfrac{0}{1} & 0 & 0 & 0 & 0\\
\EvoCapsule{CRT} \EvoCapsule{CVT} & \cellcolor{red!16!white}\sfrac{1}{1} & 0 & 0 & 0 & 0 & 0\\
\EvoCapsule{CRT} \EvoCapsule{ES} & \cellcolor{red!27!white}\sfrac{3}{3} & 0 & 0 & 0 & 0 & 0\\
\EvoCapsule{CRT} \EvoCapsule{RC} & \cellcolor{red!22!white}\sfrac{1}{2} & 0 & 0 & 0 & \cellcolor{red!16!white}\sfrac{0}{1} & 0\\
\EvoCapsule{CRT} \EvoCapsule{RM} & \cellcolor{red!27!white}\sfrac{3}{3} & 0 & 0 & 0 & 0 & 0\\
\EvoCapsule{CVT} & \cellcolor{red!39!white}\sfrac{5}{6} & 0 & 0 & \cellcolor{red!32!white}\sfrac{0}{4} & 0 & \cellcolor{red!35!white}\sfrac{4}{5}\\
\EvoCapsule{CVT} \EvoCapsule{MC} & 0 & 0 & 0 & 0 & 0 & \cellcolor{red!27!white}\sfrac{2}{3}\\
\EvoCapsule{EI} & \cellcolor{red!22!white}\sfrac{2}{2} & 0 & 0 & 0 & 0 & 0\\
\EvoCapsule{ES} \EvoCapsule{MARC} \EvoCapsule{PDM} & 0 & 0 & \cellcolor{red!16!white}\sfrac{0}{1} & 0 & 0 & 0\\
\EvoCapsule{ES} \EvoCapsule{PDM} & 0 & 0 & \cellcolor{red!22!white}\sfrac{0}{2} & 0 & 0 & 0\\
\EvoCapsule{MARC} & \cellcolor{red!16!white}\sfrac{1}{1} & 0 & \cellcolor{red!16!white}\sfrac{0}{1} & 0 & 0 & 0\\
\EvoCapsule{MC} & 0 & 0 & 0 & \cellcolor{red!100!white}\sfrac{0}{40} & 0 & 0\\
\EvoCapsule{RC} & \cellcolor{red!45!white}\sfrac{7}{8} & 0 & 0 & 0 & \cellcolor{red!35!white}\sfrac{0}{5} & \cellcolor{red!16!white}\sfrac{1}{1}\\
\EvoCapsule{RM} & \cellcolor{red!32!white}\sfrac{4}{4} & 0 & 0 & 0 & 0 & \cellcolor{red!52!white}\sfrac{0}{11}\\
\hline
\end{longtable}
\end{center}
\end{minipage}
\end{center}