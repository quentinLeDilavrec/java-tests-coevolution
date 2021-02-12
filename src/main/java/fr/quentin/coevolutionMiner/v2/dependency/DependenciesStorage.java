package fr.quentin.coevolutionMiner.v2.dependency;

import fr.quentin.coevolutionMiner.v2.evolution.EvolutionHandler;
import fr.quentin.coevolutionMiner.v2.evolution.Evolutions;
import fr.quentin.coevolutionMiner.v2.ast.Project;
import fr.quentin.coevolutionMiner.v2.ast.ProjectHandler;

public interface DependenciesStorage extends AutoCloseable {

    public void put(Dependencies value);

    public Dependencies get(Dependencies.Specifier impacts_spec, ProjectHandler astHandler,
            EvolutionHandler evoHandler);

}