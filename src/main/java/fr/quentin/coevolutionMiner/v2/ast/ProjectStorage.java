package fr.quentin.coevolutionMiner.v2.ast;

public interface ProjectStorage {

    public <T> void put(Project.Specifier impacts_spec, Project<T> value);

    public Project get(Project.Specifier project_spec);

}