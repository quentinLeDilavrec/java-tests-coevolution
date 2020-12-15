UNWIND $eImpacts as eImp
    MERGE (eImpact:EImpact {evolutions:eImp.content.evolutions, tests:eImp.content.tests})

    MERGE (t:Tool {name:$tool})
    ON CREATE SET t.name = $tool

    MERGE (eImpact)-[rel:WAS_MINED_BY]->(t)

    WITH eImpact as eImpact, eImp as eImp, eImp.tests as tests
    UNWIND tests as test

        WITH eImpact as eImpact, eImp as eImp, test as test
        MERGE (r:Range {range:[toInteger(test.start),toInteger(test.end)], start:toInteger(test.start), end:toInteger(test.end),
                        path:test.file, repo:test.repository, commitId:test.commitId})
        ON MATCH SET r.isTest = true
        ON CREATE SET r.isTest = true

        WITH eImpact as eImpact, eImp as eImp, test as test, r as r
        MERGE (eImpact)-[imp:IMPACT]->(r)
        ON MATCH SET imp.failureReport = test.report.what, imp.failAt = test.report.`when`
        ON CREATE SET imp.failureReport = test.report.what, imp.failAt = test.report.`when`

        WITH eImpact as eImpact, eImp as eImp, test as test, r as r
        UNWIND test.before as before

            WITH eImpact as eImpact, eImp as eImp, test as test, r as r, before as before
            MERGE (rbef:Range {range:[toInteger(before.start),toInteger(before.end)], start:toInteger(before.start), end:toInteger(before.end),
                               path:before.file, repo:before.repository, commitId:before.commitId})
            ON MATCH SET rbef.isTest = true
            ON CREATE SET rbef.isTest = true

            WITH eImpact as eImpact, eImp as eImp, test as test, r as r, rbef as rbef
            MERGE (eImpact)-[imp:TEST_BEFORE]->(rbef)

            WITH eImpact as eImpact, eImp as eImp, test as test, r as r, rbef as rbef
            MERGE (r)-[:ORIGIN]->(rbef)

    WITH eImpact as eImpact, eImp as eImp
    UNWIND eImp.evolutions as l
        WITH l as l, eImpact as eImpact
        // MERGE (evo:Evo {url:l.content.url})
        CALL apoc.merge.node(['Evo'], l.content.content) YIELD node as evo

        WITH l as l, eImpact as eImpact, evo as evo
        MERGE (eImpact)-[:COMPOSE_EIMP]->(evo)