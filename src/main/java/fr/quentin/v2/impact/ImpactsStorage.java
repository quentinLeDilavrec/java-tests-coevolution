package fr.quentin.v2.impact;

import fr.quentin.v2.evolution.Evolutions;
import fr.quentin.v2.ast.AST;

public interface ImpactsStorage {

    public void put(Impacts.Specifier impacts_spec, Impacts value);

    public Impacts get(Impacts.Specifier impacts_spec);

}