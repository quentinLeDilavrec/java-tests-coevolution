package fr.quentin.v2.evolution;

public interface EvolutionsStorage {

    public void put(Evolutions.Specifier impacts_spec, Evolutions value);

    public Evolutions get(Evolutions.Specifier impacts_spec);

}