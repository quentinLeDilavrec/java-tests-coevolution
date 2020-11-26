package fr.quentin.coevolutionMiner.v2.coevolution.miners;

import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.math.Fraction;

import fr.quentin.coevolutionMiner.v2.evolution.Evolutions.Evolution;
import fr.quentin.coevolutionMiner.v2.evolution.miners.GumTreeSpoonMiner.EvolutionsAtCommit.EvolutionsAtProj;
import gumtree.spoon.apply.ApplierHelper;

public class ApplierHelperImpl extends ApplierHelper<Evolution> {

    @Override
    protected Object getOriginal(Evolution evolution) {
        return evolution.getOriginal();
    }

    public ApplierHelperImpl(EvolutionsAtProj eap, Map<Evolution, Set<Evolution>> atomizedRefactorings) {
        super(eap.getScanner(), eap.getMdiff().getMiddle(), eap.getDiff(), atomizedRefactorings);
        this.mdiff = eap.getMdiff();
    }

    public Fraction ratio(Evolution e) {
        return this.evoState.ratio(e);
    }
}
