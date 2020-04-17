package fr.quentin.v2.sources.miners;

import fr.quentin.utils.SourcesHelper;
import fr.quentin.v2.sources.Sources;
import fr.quentin.v2.sources.SourcesMiner;
import fr.quentin.v2.sources.Sources.Specifier;

public class JgitMiner implements SourcesMiner {

    private Specifier spec;

    public JgitMiner(Sources.Specifier spec) {
        this.spec = spec;
    }

    @Override
    public Sources compute() throws Exception {
        return new Sources(spec) {

            @Override
            public SourcesHelper open() throws Exception {
                return new SourcesHelper(spec.repository);
            }

        };
    }

}