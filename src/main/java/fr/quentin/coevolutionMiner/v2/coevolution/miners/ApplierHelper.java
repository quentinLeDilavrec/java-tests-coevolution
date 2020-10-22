package fr.quentin.coevolutionMiner.v2.coevolution.miners;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.function.Consumer;
import java.util.function.Function;

import com.github.gumtreediff.actions.model.Delete;
import com.github.gumtreediff.actions.model.Insert;
import com.github.gumtreediff.actions.model.Move;
import com.github.gumtreediff.actions.model.Update;
import com.github.gumtreediff.tree.AbstractVersionedTree;
import com.github.gumtreediff.tree.ITree;

import org.apache.commons.compress.utils.Iterators;

import fr.quentin.coevolutionMiner.v2.evolution.Evolutions.Evolution;
import fr.quentin.coevolutionMiner.v2.evolution.miners.GumTreeSpoonMiner.EvolutionsAtCommit.EvolutionsAtProj;
import gumtree.spoon.apply.AAction;
import gumtree.spoon.apply.ActionApplier;
import gumtree.spoon.apply.WrongAstContextException;
import gumtree.spoon.builder.SpoonGumTreeBuilder;
import gumtree.spoon.diff.Diff;
import gumtree.spoon.diff.DiffImpl;
import gumtree.spoon.diff.MultiDiffImpl;
import spoon.compiler.Environment;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtPackage;
import spoon.reflect.declaration.CtType;
import spoon.reflect.factory.Factory;
import spoon.support.StandardEnvironment;

public class ApplierHelper implements AutoCloseable {

    private SpoonGumTreeBuilder scanner;
    private ITree middle;
    private Diff diff;
    private List<AAction> actions;
    private EvoStateMaintainer evoState;
    private Consumer<Set<Evolution>> validityLauncher;
    private float meanSimConst = .7f;
    private float minReqConst = .1f;
    public final Map<Evolution, Set<Evolution>> evoToEvo;

    class EvoStateMaintainer {
        private Map<Evolution, Integer> evoState = new HashMap<>();
        private Map<Evolution, Integer> evoReqSize = new HashMap<>();
        private Map<Evolution, Set<AbstractVersionedTree>> evoToTree = new HashMap<>();
        private final Map<Evolution, Set<Evolution>> evoToEvo = ApplierHelper.this.evoToEvo;
        private Map<Object, Set<Evolution>> presentMap = new HashMap<>();
        private Map<Object, Set<Evolution>> absentMap = new HashMap<>();
        private Map<AbstractVersionedTree, Boolean> reqState = new HashMap<>();
        private Set<Evolution> validable = new HashSet<>();

        EvoStateMaintainer(Collection<Evolution> evos) {
            for (Evolution e : evos) {
                markRequirements(new Chain<>(this, e));
            }
        }

        void set(AbstractVersionedTree a, boolean present) {
            Boolean prev = reqState.put(a, present);
            if (prev != null && prev == present) {
                // nothing to do
            } else {
                for (Evolution e : (present ? presentMap : absentMap).get(a)) {
                    Integer v = evoState.getOrDefault(e, 0);
                    evoState.put(e, ++v);
                    if (isLaunchable(e)) {
                        validable.add(e);
                    } else {
                        validable.remove(e);
                    }
                }
                validityLauncher.accept(validable);
            }
        }

        private boolean isLaunchable(Evolution e) {
            return evoState.get(e) / evoReqSize.get(e) > minReqConst;
        }

        public Set<AbstractVersionedTree> getTrees(Evolution e) {
            return Collections.unmodifiableSet(evoToTree.get(e));
        }

        public Set<Evolution> getComponents(Evolution e) {
            return Collections.unmodifiableSet(evoToEvo.get(e));
        }

        private void markRequirements(Chain<Evolution> evos) {
            for (Evolution e : evoToEvo.get(evos.curr)) {
                markRequirements((AAction) e.getOriginal(), evos);
                markRequirements(new Chain<>(this, e, evos));
            }
        }

        private void markRequirements(AAction a, Collection<Evolution> e) {
            if (a instanceof Delete) {
                markDeleteRequirements(a, e);
            } else {
                markInsertRequirements(a, e);
                markSourceRequirements(a, e);
                markContextRequirements(a, e);
            }
        }

        private void markInsertRequirements(AAction a, Collection<Evolution> e) {
            AbstractVersionedTree t = a.getTarget();
            assert (t != null);
            AbstractVersionedTree p = t.getParent();
            presentMap.putIfAbsent(p, new HashSet<>());
            presentMap.get(p).addAll(e);
        }

        private void markDeleteRequirements(AAction<Delete> a, Collection<Evolution> e) {
            AbstractVersionedTree t = a.getTarget();
            assert (t != null);
            for (AbstractVersionedTree c : t.getAllChildren()) {
                presentMap.putIfAbsent(c, new HashSet<>());
                presentMap.get(c).addAll(e);
            }
        }

        private void markSourceRequirements(AAction<Move> a, Collection<Evolution> e) {
            // TODO
        }

        private void markContextRequirements(AAction<?> a, Collection<Evolution> e) {
            // TODO
        }
    }

    public ApplierHelper(SpoonGumTreeBuilder scanner, ITree middle, Diff diff,
            Collection<Evolution> allPossiblyConsideredEvos, Map<Evolution, Set<Evolution>> atomizedRefactorings) {
        this.scanner = scanner;
        this.middle = middle;
        this.diff = diff;
        this.actions = (List) ((DiffImpl) diff).getActionsList();
        this.evoToEvo = atomizedRefactorings;
        this.evoState = new EvoStateMaintainer(allPossiblyConsideredEvos);
    }

    public ApplierHelper(EvolutionsAtProj eap, Collection<Evolution> allPossiblyConsideredEvos,
            Map<Evolution, Set<Evolution>> atomizedRefactorings) {
        this(eap.getScanner(), eap.getMdiff().getMiddle(), (eap.getDiff()), allPossiblyConsideredEvos,
                atomizedRefactorings);
    }

    public CtElement applyEvolutions(Set<Evolution> wantedEvos) {
        for (Evolution evolution : wantedEvos) {
            evolution.getOriginal();
        }
        return null; // TODO
    }

    public CtElement applyAllActions() {
        return applyActions(actions);
    }

    private CtElement applyActions(List<AAction> wantedActions) {
        List<AAction> retryList = new ArrayList<>();
        for (AAction action : wantedActions) {
            try {
                auxApply(scanner, middle, action);

            } catch (gumtree.spoon.apply.WrongAstContextException e) {
                retryList.add(action);
            }
        }
        for (AAction action : retryList) {
            try {
                auxApply(scanner, middle, action);
            } catch (gumtree.spoon.apply.WrongAstContextException e) {
                throw new RuntimeException(e);
            }
        }

        CtElement middleE = null;
        Queue<ITree> tmp = new LinkedBlockingDeque<>();
        tmp.add(middle);

        while (middleE == null) {
            ITree curr = tmp.poll();
            middleE = (CtElement) curr.getMetadata(SpoonGumTreeBuilder.SPOON_OBJECT);
            List<ITree> children = curr.getChildren();
            tmp.addAll(children);
        }
        return middleE;
    }

    public static void auxApply(final SpoonGumTreeBuilder scanner, ITree middle, AAction action)
            throws WrongAstContextException {
        if (action instanceof Insert) {
            ActionApplier.applyAInsert((Factory) middle.getMetadata("Factory"), scanner.getTreeContext(),
                    (Insert & AAction<Insert>) action);
        } else if (action instanceof Delete) {
            ActionApplier.applyADelete((Factory) middle.getMetadata("Factory"), scanner.getTreeContext(),
                    (Delete & AAction<Delete>) action);
        } else if (action instanceof Update) {
            ActionApplier.applyAUpdate((Factory) middle.getMetadata("Factory"), scanner.getTreeContext(),
                    (Update & AAction<Update>) action);
        } else if (action instanceof Move) {
            ActionApplier.applyAMove((Factory) middle.getMetadata("Factory"), scanner.getTreeContext(),
                    (Move & AAction<Move>) action);
        } else {
            throw null;
        }
    }

    @Override
    public void close() throws Exception {
        // TODO Auto-generated method stub
        // TODO undo all changes on middle repr as spoon ast
    }

}
