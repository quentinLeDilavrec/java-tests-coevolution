package fr.quentin.coevolutionMiner.v2.evolution;

public interface EvolutionsMiner {
    public static String METADATA_KEY_EVO = "evo.reverse";
    public Evolutions compute();
}