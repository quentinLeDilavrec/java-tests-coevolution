package fr.quentin.coevolutionMiner.v2.coevolution.miners;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import com.github.gumtreediff.tree.Version;

import org.apache.commons.lang3.math.Fraction;
import org.apache.commons.lang3.tuple.ImmutableTriple;

import fr.quentin.coevolutionMiner.v2.evolution.Evolutions.Evolution;
import gumtree.spoon.apply.EvoStateMaintainer;
import spoon.MavenLauncher;
import spoon.MavenLauncher.SOURCE_TYPE;
import spoon.reflect.cu.CompilationUnit;
import spoon.reflect.cu.SourcePosition;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.reference.CtReference;

public class EvoStateMaintainerImpl extends EvoStateMaintainer<Evolution> {

    private FunctionalImpactRunner validityLauncher;

    protected EvoStateMaintainerImpl(Version initialState, Map<Evolution, Set<Evolution>> evoToEvo) {
        super(initialState, evoToEvo);
    }

    public Set<Evolution> getValidable() {
        return this.validable;
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

    static final Section APP_SECTION = new Section() {
    };
    static final Section TEST_SECTION = new Section() {
    };
    private static final float minReqConst = 1.0f;

    @Override
    protected Set<Section> sectionSpanning(CtElement element) {
        ImmutableTriple<?, ?, MavenLauncher.SOURCE_TYPE> tmp = getSourceTypeNRootDirectory(element);

        if (tmp == null) {
            return null;
        }

        switch (tmp.getRight()) {
            case APP_SOURCE:
                return Collections.singleton(APP_SECTION);
            case TEST_SOURCE:
                return Collections.singleton(TEST_SECTION);
            default:
                return Collections.emptySet();
        }
    }

    @Override
    protected Consumer<Set<Evolution>> getValidityLauncher() {
        return this.validityLauncher;
    }

    public void setValidityLauncher(FunctionalImpactRunner validityLauncher) {
        this.validityLauncher = validityLauncher;
    }

    @Override
    protected boolean isLaunchable(Evolution e) {
        return ratio(e).floatValue() >= minReqConst;
    }

    public Fraction ratio(Evolution e) {
        return Fraction.getFraction(evoState.get(e), evoReqSize.get(e));
    }

    @Override
    public Object getOriginal(Evolution evolution) {
        return evolution.getOriginal();
    }
}