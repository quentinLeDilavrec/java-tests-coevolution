UNWIND $data as content
MATCH (t:Range {isTest:True, repo:content.repo, commitId:content.commitId, path:content.path, start:content.start,end:content.end})
RETURN distinct t as test, 
[(t)<-[]-(:Impact {type:"expand to executable"})-[]->(:Range)<-[]-(:Impact {type: "call"})-[]->(:Range)<-[]-(:Impact {type:"adjustment"})-[]->(:Range)<-[:BEFORE]-(e:Evo) | (e)-[:BEFORE]->()] as longCall, 
[(t)<-[]-(:Impact {type:"expand to executable"})-[]->(:Range)<-[]-(:Impact {type: "call"})-[]->(:Range)<-[:BEFORE]-(e:Evo) |  (e)-[:BEFORE]->()] as shortCall, 
[(t)<-[:BEFORE]-(e:Evo) | (e)-[:BEFORE]->()] as short, 
[(t)<-[]-(:Impact {type:"expand to executable"})-[]->(:Range)<-[:BEFORE]-(e:Evo) | (e)-[:BEFORE]->()] as long,
[(t)<-[]-(:Impact {type:"adjustment"})-[]->(:Range)<-[:BEFORE]-(e:Evo) | (e)-[:BEFORE]->()] as shortAdjusted