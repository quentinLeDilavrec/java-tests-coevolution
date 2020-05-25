package fr.quentin.coevolutionMiner.v2.impact;

import fr.quentin.coevolutionMiner.v2.evolution.Evolutions;
import fr.quentin.coevolutionMiner.v2.ast.AST;

public interface ImpactsStorage extends AutoCloseable {

    public void put(Impacts value);

    public Impacts get(Impacts.Specifier impacts_spec);

}