package fr.quentin.coevolutionMiner.v2.sources;

public interface SourcesStorage extends AutoCloseable {

    public void put(Sources value);

    public Sources get(Sources.Specifier impacts_spec);

}