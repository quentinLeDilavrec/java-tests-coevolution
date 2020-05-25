package fr.quentin.coevolutionMiner.v2.evolution;

public interface EvolutionsStorage extends AutoCloseable {

    public void put(Evolutions.Specifier impacts_spec, Evolutions value);

    public Evolutions get(Evolutions.Specifier impacts_spec);

}