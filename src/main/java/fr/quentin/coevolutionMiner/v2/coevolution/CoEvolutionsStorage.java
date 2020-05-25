package fr.quentin.coevolutionMiner.v2.coevolution;

import fr.quentin.coevolutionMiner.v2.evolution.Evolutions;

import java.util.Set;

import fr.quentin.coevolutionMiner.v2.ast.AST;
import fr.quentin.coevolutionMiner.v2.ast.AST.FileSnapshot.Range;
import fr.quentin.coevolutionMiner.v2.coevolution.CoEvolutions.Specifier;
import fr.quentin.coevolutionMiner.v2.coevolution.miners.MyCoEvolutionsMiner.CoEvolutionsExtension;

public interface CoEvolutionsStorage extends AutoCloseable {

    public void put(CoEvolutions value);

    public CoEvolutions get(CoEvolutions.Specifier impacts_spec);

	public void construct(CoEvolutionsExtension.Builder coevoBuilder, Set<Range> startingTests);

}