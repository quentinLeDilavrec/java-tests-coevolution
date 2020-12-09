package fr.quentin.coevolutionMiner.v2.coevolution.miners;

import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.math.Fraction;
import org.apache.commons.lang3.tuple.ImmutableTriple;

import fr.quentin.coevolutionMiner.v2.evolution.Evolutions.Evolution;
import fr.quentin.coevolutionMiner.v2.evolution.miners.GumTreeSpoonMiner.EvolutionsAtCommit.EvolutionsAtProj;
import gumtree.spoon.apply.ApplierHelper;
import spoon.MavenLauncher;
import spoon.MavenLauncher.SOURCE_TYPE;
import spoon.reflect.cu.CompilationUnit;
import spoon.reflect.cu.SourcePosition;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.reference.CtReference;

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

    private ImmutableTriple<?, ?, SOURCE_TYPE> getSourceTypeNRootDirectory(CtElement element) {
        if (element instanceof CtReference) {
            return null;
        }
        SourcePosition pos = element.getPosition();
        if (pos == null) {
            return null;
        }
        CompilationUnit cu = pos.getCompilationUnit();
        if (cu == null) {
            return null;
        }
        return (ImmutableTriple<?, ?, SOURCE_TYPE>) cu.getMetadata("SourceTypeNRootDirectory");
    }

    @Override
    protected Boolean isInApp(CtElement element) {

        ImmutableTriple<?, ?, MavenLauncher.SOURCE_TYPE> tmp = getSourceTypeNRootDirectory(element);

        if (tmp == null) {
            return null;
        }

        switch (tmp.getRight()) {
            case APP_SOURCE:
                return true;
            case TEST_SOURCE:
                return false;
            default:
                return null;
        }
    }

    @Override
    protected Boolean isInTest(CtElement element) {

        ImmutableTriple<?, ?, MavenLauncher.SOURCE_TYPE> tmp = getSourceTypeNRootDirectory(element);

        if (tmp == null) {
            return null;
        }

        switch (tmp.getRight()) {
            case APP_SOURCE:
                return false;
            case TEST_SOURCE:
                return true;
            default:
                return null;
        }
    }
}
