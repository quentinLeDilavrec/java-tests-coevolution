package fr.quentin.coevolutionMiner.v2.sources;

import java.util.Set;

public interface SourcesStorage extends AutoCloseable {

    public void put(Sources value);

    public Sources get(Sources.Specifier spec);

    public void putUpdatedCommits(Set<Sources.Commit> commits);

}