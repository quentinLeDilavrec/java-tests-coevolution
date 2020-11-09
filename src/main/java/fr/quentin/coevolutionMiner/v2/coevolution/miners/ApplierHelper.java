package fr.quentin.coevolutionMiner.v2.coevolution.miners;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.function.Consumer;
import java.util.function.Function;

import com.github.gumtreediff.actions.model.Action;
import com.github.gumtreediff.actions.model.Delete;
import com.github.gumtreediff.actions.model.Insert;
import com.github.gumtreediff.actions.model.Move;
import com.github.gumtreediff.actions.model.Update;
import com.github.gumtreediff.tree.AbstractVersionedTree;
import com.github.gumtreediff.tree.ITree;
import com.github.gumtreediff.tree.VersionedTree;

import org.apache.commons.compress.utils.Iterators;
import org.apache.commons.lang3.math.Fraction;
import org.eclipse.jetty.util.MultiMap;

import fr.quentin.coevolutionMiner.v2.ast.miners.SpoonMiner.ProjectSpoon.SpoonAST;
import fr.quentin.coevolutionMiner.v2.evolution.Evolutions.Evolution;
import fr.quentin.coevolutionMiner.v2.evolution.miners.GumTreeSpoonMiner;
import fr.quentin.coevolutionMiner.v2.evolution.miners.GumTreeSpoonMiner.VersionCommit;
import fr.quentin.coevolutionMiner.v2.evolution.miners.GumTreeSpoonMiner.EvolutionsAtCommit.EvolutionsAtProj;
import gumtree.spoon.apply.AAction;
import gumtree.spoon.apply.ActionApplier;
import gumtree.spoon.apply.Combination;
import gumtree.spoon.apply.WrongAstContextException;
import gumtree.spoon.apply.operations.MyScriptGenerator;
import gumtree.spoon.builder.SpoonGumTreeBuilder;
import gumtree.spoon.diff.Diff;
import gumtree.spoon.diff.DiffImpl;
import gumtree.spoon.diff.MultiDiffImpl;
import gumtree.spoon.diff.operations.Operation;
import spoon.Launcher;
import spoon.MavenLauncher;
import spoon.compiler.Environment;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtPackage;
import spoon.reflect.declaration.CtType;
import spoon.reflect.factory.Factory;
import spoon.support.JavaOutputProcessor;
import spoon.support.StandardEnvironment;

public class ApplierHelper implements AutoCloseable {

    private SpoonGumTreeBuilder scanner;
    private AbstractVersionedTree middle;
    private Diff diff;
    private List<AAction> actions;
    public final EvoStateMaintainer evoState;
    private Consumer<Set<Evolution>> validityLauncher;

    public void setValidityLauncher(Consumer<Set<Evolution>> validityLauncher) {
        this.validityLauncher = validityLauncher;
    }

    private float meanSimConst = .7f;
    private float minReqConst = .1f;
    public final Map<Evolution, Set<Evolution>> evoToEvo;
    private Factory factory;
    private Launcher launcher;
    private MultiDiffImpl mdiff;

    class EvoStateMaintainer {
        private Map<Evolution, Integer> evoState = new HashMap<>();
        private Map<Evolution, Integer> evoReqSize = new HashMap<>();
        // private Map<Evolution, Set<AAction>> evoToTree = new HashMap<>();
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

        boolean set(AbstractVersionedTree a, boolean present, boolean silent) {
            Boolean prev = reqState.put(a, present);
            if (prev != null && prev == present) {
                // nothing to do
                return false;
            } else {
                for (Evolution e : (present ? presentMap : absentMap).getOrDefault(a, Collections.emptySet())) {
                    Integer v = evoState.getOrDefault(e, 0);
                    evoState.put(e, ++v);
                    if (isLaunchable(e)) {
                        validable.add(e);
                    } else {
                        validable.remove(e);
                    }
                }
                if (!silent) {
                    triggerCallback();
                }
                return true;
            }
        }

        void triggerCallback() {
            validityLauncher.accept(validable);
        }

        private boolean isLaunchable(Evolution e) {
            return ratio(e).floatValue() > minReqConst;
        }

        public Fraction ratio(Evolution e) {
            return Fraction.getFraction(evoState.get(e), evoReqSize.get(e));
        }

        public Set<Evolution> getEvolutions(Set<Evolution> e) {
            Set<Evolution> r = new HashSet<>();
            getEvolutions(e, r);
            return r;
        }

        private void getEvolutions(Set<Evolution> evos, Set<Evolution> r) {
            for (Evolution e : evos) {
                r.add(e);
                if (evoToEvo.containsKey(e)) {
                    getEvolutions(evoToEvo.get(e), r);
                }
            }
        }

        public Set<AAction> getActions(Set<Evolution> e) {
            Set<AAction> r = new HashSet<>();
            for (Evolution e1 : e) {
                Object original = e1.getOriginal();
                if (original instanceof Operation) {
                    AAction action = (AAction) ((Operation) original).getAction();
                    r.add(action);
                }
            }
            return r;
        }

        public boolean isCurrentlyApplied(AAction a) {
            if (a instanceof Move) {
                Boolean s = reqState.get(a.getSource());
                Boolean t = reqState.get(a.getTarget());
                return s && t;
            } else {
                return reqState.get(a.getTarget());
            }
        }

        public Set<Evolution> getComponents(Evolution e) {
            return Collections.unmodifiableSet(evoToEvo.get(e));
        }

        private void markRequirements(Chain<Evolution> evos) {
            Object original = evos.curr.getOriginal();
            if (original instanceof Action) {
                Action action = (Action) original;// ((Operation) original).getAction();
                // evoToTree.putIfAbsent(evos.curr, new HashSet<>());
                // evoToTree.get(evos.curr).add((AAction) action);
                markRequirements((AAction) action, evos);
            }
            Set<Evolution> compo = evoToEvo.get(evos.curr);
            if (compo != null) {
                for (Evolution e : compo) {
                    Chain<Evolution> newe = new Chain<>(this, e, evos);
                    markRequirements(newe);
                }
            }
        }

        private void markRequirements(AAction a, Collection<Evolution> e) {
            if (a instanceof Delete) {
                markDeleteRequirements(a, e);
                // } else if (a instanceof Move) {
                // markSourceRequirements(a, e);
                // markInsertRequirements(a, e);
            } else {
                markInsertRequirements(a, e);
                // markContextRequirements(a, e);
            }
        }

        private void markInsertRequirements(AAction a, Collection<Evolution> e) {
            AbstractVersionedTree t = a.getTarget();
            assert (t != null);
            // AbstractVersionedTree p = t.getParent();
            presentMap.putIfAbsent(t, new HashSet<>());
            presentMap.get(t).addAll(e);
        }

        private void markDeleteRequirements(AAction<Delete> a, Collection<Evolution> e) {
            AbstractVersionedTree t = a.getTarget();
            assert (t != null);
            absentMap.putIfAbsent(t, new HashSet<>());
            absentMap.get(t).addAll(e);
            // for (AbstractVersionedTree c : t.getAllChildren()) {
            // absentMap.putIfAbsent(c, new HashSet<>());
            // absentMap.get(c).addAll(e);
            // }
        }

        // /**
        // * For a move, the insertion must be done before the deletion.
        // * Thus the inserted node must be populated before the removed node is
        // unpopulated.
        // * @param a
        // * @param e
        // */
        // private void markSourceRequirements(AAction<Move> a, Collection<Evolution> e)
        // {
        // AbstractVersionedTree s = (AbstractVersionedTree)a.getSource();
        // AbstractVersionedTree t = a.getTarget();
        // assert (t != null);
        // assert (s != null);
        // presentMap.putIfAbsent(s, new HashSet<>());
        // presentMap.get(s).addAll(e);
        // }

        // private void markContextRequirements(AAction<?> a, Collection<Evolution> e) {
        // // TODO
        // }
    }

    private ApplierHelper(SpoonGumTreeBuilder scanner, AbstractVersionedTree middle, Diff diff,
            Collection<Evolution> allPossiblyConsideredEvos, Map<Evolution, Set<Evolution>> atomizedRefactorings) {
        this.scanner = scanner;
        this.middle = middle;
        this.factory = (Factory) middle.getMetadata("Factory");
        this.launcher = (Launcher) middle.getMetadata("Launcher");
        this.diff = diff;
        this.actions = (List) ((DiffImpl) diff).getAtomicActions();
        this.evoToEvo = atomizedRefactorings;
        this.evoState = new EvoStateMaintainer(allPossiblyConsideredEvos);
    }

    public ApplierHelper(EvolutionsAtProj eap, Collection<Evolution> allPossiblyConsideredEvos,
            Map<Evolution, Set<Evolution>> atomizedRefactorings) {
        this(eap.getScanner(), eap.getMdiff().getMiddle(), eap.getDiff(), allPossiblyConsideredEvos,
                atomizedRefactorings);
        this.mdiff = eap.getMdiff();
    }

    public Launcher applyEvolutions(Set<Evolution> wantedEvos) {
        Set<AAction> acts = new HashSet<>();
        extractActions(wantedEvos, acts);
        return applyCombActions(acts);
    }

    private void extractActions(Set<Evolution> wantedEvos, Set<AAction> acts) {
        for (Evolution evolution : wantedEvos) {
            Object original = evolution.getOriginal();
            if (original instanceof Action) {
                acts.add((AAction) /* ((Operation) */ original/* ).getAction() */);
            }
            Set<Evolution> others = evoState.evoToEvo.get(evolution);
            if (others != null) {
                extractActions(others, acts);
            }
        }
    }

    public Launcher applyAllActions() {
        return applyCombActions(actions);
    }

    private Launcher applyActions(List<AAction> wantedActions) {
        List<AAction> retryList = new ArrayList<>();
        for (AAction action : wantedActions) {
            try {
                auxApply(scanner, this.factory, action);
            } catch (gumtree.spoon.apply.WrongAstContextException e) {
                retryList.add(action);
            }
        }
        for (AAction action : retryList) {
            try {
                auxApply(scanner, this.factory, action);
            } catch (gumtree.spoon.apply.WrongAstContextException e) {
                throw new RuntimeException(e);
            }
        }
        return launcher;
    }

    private AAction getAction(AbstractVersionedTree tree, boolean way) {
        AAction action;
        if (way) {
            action = (AAction) tree.getMetadata(MyScriptGenerator.INSERT_ACTION);
            if (action == null) {
                action = (AAction) tree.getMetadata(MyScriptGenerator.DELETE_ACTION);
                action = invertAction(action);
            }
        } else {
            action = (AAction) tree.getMetadata(MyScriptGenerator.DELETE_ACTION);
            if (action == null) {
                action = (AAction) tree.getMetadata(MyScriptGenerator.INSERT_ACTION);
                action = invertAction(action);
            }
        }
        return action;
    }

    private AAction invertAction(AAction action) {
        AAction r;
        if (action instanceof Insert) {
            r = AAction.build(Delete.class, null, action.getTarget());
        } else if (action instanceof Delete) {
            r = AAction.build(Insert.class, action.getTarget(), action.getTarget());
        } else if (action instanceof Update) {
            r = AAction.build(Update.class, action.getTarget(), (AbstractVersionedTree) action.getSource());
        } else {
            throw new RuntimeException(action.getClass().getCanonicalName());
        }
        return r;
    }

    private Launcher applyCombActions(Collection<AAction> wantedActions) {
        Map<AbstractVersionedTree, Boolean> waitingToBeApplied = new HashMap<>();
        Combination.CombinationHelper<AbstractVersionedTree> combs = Combination.build(middle, wantedActions);
        do {
            Combination.CHANGE<AbstractVersionedTree> change = combs.next();
            try {
                boolean b = auxApply(scanner, this.factory, getAction(change.content, change.way));
                for (AbstractVersionedTree node : waitingToBeApplied.keySet()) {
                    try {
                        auxApply(scanner, this.factory, getAction(node, waitingToBeApplied.get(node)));
                        waitingToBeApplied.remove(node);
                    } catch (gumtree.spoon.apply.WrongAstContextException e) {
                    }
                }
                if(b)
                    evoState.triggerCallback();
            } catch (gumtree.spoon.apply.WrongAstContextException e) {
                waitingToBeApplied.put(change.content, change.way);
            }
        } while (!combs.isInit());
        return launcher;
    }

    public boolean auxApply(final SpoonGumTreeBuilder scanner, Factory facto, AAction action)
            throws WrongAstContextException {
        if (action instanceof Insert) {
            ActionApplier.applyAInsert(facto, scanner.getTreeContext(), (Insert & AAction<Insert>) action);
            // Object dst =
            // action.getTarget().getMetadata(MyScriptGenerator.MOVE_DST_ACTION);
            AbstractVersionedTree dst = action.getTarget();
            AAction<Move> mact = (AAction<Move>) dst.getMetadata(MyScriptGenerator.MOVE_SRC_ACTION);
            if (mact != null) {
                ITree src = mact.getSource();
                if (watching.containsKey(src)) {
                    watching.put(src, dst);
                }
            }
            return evoState.set(dst, true, true);
        } else if (action instanceof Delete) {
            ActionApplier.applyADelete(facto, scanner.getTreeContext(), (Delete & AAction<Delete>) action);
            return evoState.set(action.getTarget(), false, true);
        } else if (action instanceof Update) {
            ActionApplier.applyAUpdate(facto, scanner.getTreeContext(), (Update & AAction<Update>) action);
            return evoState.set((AbstractVersionedTree) action.getSource(), false, true) & evoState.set(action.getTarget(), true, true);
            // } else if (action instanceof Move){
            // ActionApplier.applyAMove(facto, scanner.getTreeContext(), (Move &
            // AAction<Move>) action);
            // evoState.set((AbstractVersionedTree)action.getSource(), false);
            // evoState.set(action.getTarget(), true);
        } else {
            throw new RuntimeException(action.getClass().getCanonicalName());
        }
    }

    public void auxUnApply(final SpoonGumTreeBuilder scanner, Factory facto, AAction action)
            throws WrongAstContextException {
        if (action instanceof Insert) {
            ActionApplier.applyADelete(facto, scanner.getTreeContext(),
                    AAction.build(Delete.class, null, action.getTarget()));
            // Object dst =
            // action.getTarget().getMetadata(MyScriptGenerator.MOVE_DST_ACTION);
            AbstractVersionedTree dst = action.getTarget();
            AAction<Move> mact = (AAction<Move>) dst.getMetadata(MyScriptGenerator.MOVE_SRC_ACTION);
            if (mact != null) {
                ITree src = mact.getSource();
                if (watching.containsKey(src)) {
                    watching.put(src, dst);
                }
            }
            evoState.set(dst, false, true);
        } else if (action instanceof Delete) {
            ActionApplier.applyAInsert(facto, scanner.getTreeContext(),
                    AAction.build(Insert.class, action.getTarget(), action.getTarget())); // hope it gest the original
                                                                                          // CtElement
            evoState.set(action.getTarget(), true, true);
        } else if (action instanceof Update) {
            ActionApplier.applyAUpdate(facto, scanner.getTreeContext(),
                    AAction.build(Update.class, action.getTarget(), (AbstractVersionedTree) action.getSource()));
            evoState.set((AbstractVersionedTree) action.getTarget(), false, true);
            evoState.set((AbstractVersionedTree) action.getSource(), true, true);
            // } else if (action instanceof Move){
            // ActionApplier.applyAMove(facto, scanner.getTreeContext(), (Move &
            // AAction<Move>) action);
            // evoState.set((AbstractVersionedTree)action.getSource(), false);
            // evoState.set(action.getTarget(), true);
        } else {
            throw new RuntimeException(action.getClass().getCanonicalName());
        }
    }

    @Override
    public void close() throws Exception {
        // TODO Auto-generated method stub
        // TODO undo all changes on middle repr as spoon ast
    }

    static void applyEvolutions(EvolutionsAtProj eap, Set<Evolution> wantedEvos,
            Map<Evolution, Set<Evolution>> atomizedRefactorings) {
        // bug spoon while parsing comments
        // make system tests that analyze spoon itself
        // maven modules can be handled by the used, just need to open some api calls
        // (access to SpoonPom childrens, MavenLauncher that takes an SpoonPom Instance)
        // make spoon being able to be more in incremental, in the future use an
        // incremental parser, would allow to scale to mining of whole git repo ?!?!
        // interested?
        // that is something like versioned visitors and accessors OR keep them as is
        // but make a processor that can change the version of the ast
        // make things more immutable in the future? solve part of the complexity of
        // ChangeListener
        // reversible / bijective
        // avoid cloning whole ast
        // keep original asts and found operations valid
        // able to apply each atomic op
        // what about move class / move package ?
        // transformation into coming patch ?
        // maintain an intermediary ast ? where we apply ops
        // would it be easier to create an Itree instead of a spoon ast ?
        // ability to apply and unapply op in any order
        // able to serialize/prettyprint intermediary ast
        // in general being able to manipulate the intermediary ast like a standard
        // spoon ast
        // avoid cloning the whole ast to create the intermediary tree
        // is it possible to avoid using CtPath to go between left or right and
        // intermediary
        // ChangeListener can be used realistically? extends it to revert operations?
        // as martin pointed out, it can be looked at like a merge for the creating of
        // the intermediary,
        // considering a left, middle and right ast, here left = middle, but we need to
        // filter some evolutions coming from right.
        try (ApplierHelper ah = new ApplierHelper(eap, wantedEvos, atomizedRefactorings);) {
            Launcher launcher = ah.applyEvolutions(wantedEvos);
            serialize(launcher, Paths.get("/tmp/applyResults/").toFile());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    void serialize(File out) {
        serialize(this.launcher, out);
    }

    public static void serialize(Launcher launcher, File out) {
        JavaOutputProcessor outWriter = launcher.createOutputWriter();
        outWriter.getEnvironment().setSourceOutputDirectory(out); // TODO go further!!!!
        for (CtType p : launcher.getModel().getAllTypes()) {
            if (!p.isShadow()) {
                outWriter.createJavaFile(p);
            }
        }
    }

    private Map<ITree, ITree> watching = new HashMap<>();

    public CtElement[] watchApply(VersionCommit beforeVersion, ITree... treeTest) {
        CtElement[] r = new CtElement[treeTest.length];
        for (int i = 0; i < treeTest.length; i++) {
            ITree x = treeTest[i];
            watching.put(x, x);
            CtElement ele = (CtElement) x.getMetadata(SpoonGumTreeBuilder.SPOON_OBJECT);
            if (ele == null) {
                ele = (CtElement) x.getMetadata(VersionedTree.ORIGINAL_SPOON_OBJECT);
            }
            r[i] = ele;
        }
        return r;
    }

    public CtElement[] getUpdatedElement(VersionCommit afterVersion, ITree... treeTest) {
        CtElement[] r = new CtElement[treeTest.length];
        for (int i = 0; i < treeTest.length; i++) {
            ITree xx = treeTest[i];
            ITree x = watching.getOrDefault(xx, xx);
            // Map<VersionCommit, CtElement> map = (Map<VersionCommit, CtElement>) x
            //         .getMetadata(MyScriptGenerator.ORIGINAL_SPOON_OBJECT_PER_VERSION);
            CtElement ele = null;
            // if (map != null) {
            //     ele = map.get(afterVersion);
            // }
            if (ele == null) {
                ele = (CtElement) x.getMetadata(SpoonGumTreeBuilder.SPOON_OBJECT);
            }
            r[i] = ele;
        }
        return r;
    }

}
