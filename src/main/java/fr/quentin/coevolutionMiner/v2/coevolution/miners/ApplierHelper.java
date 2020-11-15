package fr.quentin.coevolutionMiner.v2.coevolution.miners;

import java.io.File;
import java.nio.file.Path;
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
import java.util.Map.Entry;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Logger;

import com.github.gumtreediff.actions.model.Action;
import com.github.gumtreediff.actions.model.Delete;
import com.github.gumtreediff.actions.model.Insert;
import com.github.gumtreediff.actions.model.Move;
import com.github.gumtreediff.actions.model.Update;
import com.github.gumtreediff.tree.AbstractVersionedTree;
import com.github.gumtreediff.tree.ITree;
import com.github.gumtreediff.tree.Version;
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
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtModule;
import spoon.reflect.declaration.CtPackage;
import spoon.reflect.declaration.CtType;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtExecutableReference;
import spoon.support.DefaultOutputDestinationHandler;
import spoon.support.JavaOutputProcessor;
import spoon.support.OutputDestinationHandler;
import spoon.support.StandardEnvironment;

public class ApplierHelper implements AutoCloseable {
    Logger logger = Logger.getLogger(ApplierHelper.class.getName());

    private SpoonGumTreeBuilder scanner;
    private AbstractVersionedTree middle;
    private Diff diff;
    private List<AAction> actions;
    public final EvoStateMaintainer evoState;
    private Consumer<Set<Evolution>> validityLauncher;

    public void setValidityLauncher(Consumer<Set<Evolution>> validityLauncher) {
        this.validityLauncher = validityLauncher;
    }

    private float meanSimConst = .99f;
    private float minReqConst = .9f;
    public final Map<Evolution, Set<Evolution>> evoToEvo;
    private Factory factory;
    private Launcher launcher;
    private MultiDiffImpl mdiff;
    private int leafsActionsLimit;

    class EvoStateMaintainer {
        private Map<Evolution, Integer> evoState = new HashMap<>();
        private Map<Evolution, Integer> evoReqSize = new HashMap<>();
        // private Map<Evolution, Set<AAction>> evoToTree = new HashMap<>();
        private final Map<Evolution, Set<Evolution>> evoToEvo = ApplierHelper.this.evoToEvo;
        private Map<AAction, Set<Evolution>> presentMap = new HashMap<>();
        // private Map<Object, Set<Evolution>> absentMap = new HashMap<>();
        private Map<AAction, Boolean> reqState = new HashMap<>(); // false by default
        private Set<Evolution> validable = new HashSet<>();

        EvoStateMaintainer() {
            for (Evolution e : evoToEvo.keySet()) {
                markRequirements(new Chain<>(this, e));
            }
            for (Set<Evolution> values : presentMap.values()) {
                for (Evolution evolution : values) {
                    evoReqSize.put(evolution, evoReqSize.getOrDefault(evolution, 0) + 1);
                }
            }
        }

        boolean set(AAction a, boolean inverted, boolean silent) {
            Boolean prev = reqState.put(a, inverted);
            if (prev != null && prev == inverted) {
                // nothing to do
                return false;
            } else {
                for (Evolution e : presentMap.getOrDefault(a, Collections.emptySet())) {
                    Integer v = evoState.getOrDefault(e, 0);
                    evoState.put(e, inverted ? v - 1 : v + 1);
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
            validityLauncher.accept(Collections.unmodifiableSet(new HashSet<>(validable)));
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
            // if (a instanceof Move) {
            // Boolean s = reqState.get(a.getSource());
            // Boolean t = reqState.get(a.getTarget());
            // return s && t;
            // } else {
            // return reqState.get(a.getTarget());
            // }
            return reqState.get(a);
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
            presentMap.putIfAbsent(a, new HashSet<>());
            presentMap.get(a).addAll(e);
            // if (a instanceof Delete) {
            // // markDeleteRequirements(a, e);
            // // } else if (a instanceof Move) {
            // // markSourceRequirements(a, e);
            // // markInsertRequirements(a, e);
            // } else {
            // markInsertRequirements(a, e);
            // // markContextRequirements(a, e);
            // }
        }

        private void markInsertRequirements(AAction a, Collection<Evolution> e) {
            // AbstractVersionedTree t = a.getTarget();
            // assert (t != null);
            // // AbstractVersionedTree p = t.getParent();
            // presentMap.putIfAbsent(t, new HashSet<>());
            // presentMap.get(t).addAll(e);
        }

        private void markDeleteRequirements(AAction<Delete> a, Collection<Evolution> e) {
            // AbstractVersionedTree t = a.getTarget();
            // assert (t != null);
            // absentMap.putIfAbsent(t, new HashSet<>());
            // absentMap.get(t).addAll(e);
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
            Map<Evolution, Set<Evolution>> atomizedRefactorings) {
        this.scanner = scanner;
        this.middle = middle;
        this.factory = (Factory) middle.getMetadata("Factory");
        this.launcher = (Launcher) middle.getMetadata("Launcher");
        this.diff = diff;
        this.actions = (List) ((DiffImpl) diff).getAtomicActions();
        this.evoToEvo = atomizedRefactorings;
        this.evoState = new EvoStateMaintainer();
    }

    public ApplierHelper(EvolutionsAtProj eap, Map<Evolution, Set<Evolution>> atomizedRefactorings) {
        this(eap.getScanner(), eap.getMdiff().getMiddle(), eap.getDiff(), atomizedRefactorings);
        this.mdiff = eap.getMdiff();
    }

    void setLeafsActionsLimit(int limit){
        this.leafsActionsLimit = limit;
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
                auxApply(scanner, this.factory, action, false);
            } catch (gumtree.spoon.apply.WrongAstContextException e) {
                retryList.add(action);
            }
        }
        for (AAction action : retryList) {
            try {
                auxApply(scanner, this.factory, action, false);
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
        } else {
            action = (AAction) tree.getMetadata(MyScriptGenerator.DELETE_ACTION);
        }
        return action;
    }

    private AAction invertAction(AAction action) {
        AAction r;
        if (action instanceof Insert) {
            r = AAction.build(Delete.class, action.getTarget(), null);
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
        int exp = combs.minExposant();
        if(exp>leafsActionsLimit){
            logger.warning(exp + " leafs would make too much combinations");
            return null;
        }
        do {
            Combination.CHANGE<AbstractVersionedTree> change = combs.next();
            try {
                AAction action = getAction(change.content, change.way);
                boolean inverted = action == null;
                action = inverted ? getAction(change.content, !change.way) : action;
                boolean b = auxApply(scanner, this.factory, action, inverted);
                for (AbstractVersionedTree node : waitingToBeApplied.keySet()) {
                    try {
                        AAction action2 = getAction(node, waitingToBeApplied.get(node));
                        boolean inverted2 = action2 == null;
                        action2 = inverted2 ? getAction(node, !waitingToBeApplied.get(node)) : action2;
                        auxApply(scanner, this.factory, action2, inverted2);
                        waitingToBeApplied.remove(node);
                    } catch (gumtree.spoon.apply.WrongAstContextException e) {
                    }
                }
                if (b)
                    evoState.triggerCallback();
            } catch (gumtree.spoon.apply.WrongAstContextException e) {
                waitingToBeApplied.put(change.content, change.way);
            }
        } while (!combs.isInit());
        return launcher;
    }

    public boolean auxApply(final SpoonGumTreeBuilder scanner, Factory facto, AAction action, boolean inverted)
            throws WrongAstContextException {
        AAction invertableAction = inverted ? invertAction(action) : action;
        if (invertableAction instanceof Insert) {
            ActionApplier.applyAInsert(facto, scanner.getTreeContext(), (Insert & AAction<Insert>) invertableAction);
            // Object dst =
            // invertableAction.getTarget().getMetadata(MyScriptGenerator.MOVE_DST_ACTION);
            AbstractVersionedTree dst = invertableAction.getTarget();
            AAction<Move> mact = (AAction<Move>) dst.getMetadata(MyScriptGenerator.MOVE_SRC_ACTION);
            if (mact != null) {
                AbstractVersionedTree src = (AbstractVersionedTree) mact.getSource();
                if (watching.containsKey(src)) {
                    watching.put(src, dst);
                }
            }
            // return evoState.set(dst, true, true);
        } else if (invertableAction instanceof Delete) {
            ActionApplier.applyADelete(facto, scanner.getTreeContext(), (Delete & AAction<Delete>) invertableAction);
            // return evoState.set(invertableAction.getTarget(), false, true);
        } else if (invertableAction instanceof Update) {
            ActionApplier.applyAUpdate(facto, scanner.getTreeContext(), (Update & AAction<Update>) invertableAction);
            AbstractVersionedTree target = invertableAction.getTarget();
            if (inverted) {
                if (watching.containsKey(target)) {
                    watching.put(target, target);
                }
            } else {
                AbstractVersionedTree src = (AbstractVersionedTree) invertableAction.getSource();
                if (watching.containsKey(src)) {
                    watching.put(src, target);
                }
            }
            // return evoState.set((AbstractVersionedTree) invertableAction.getSource(),
            // false, true)
            // & evoState.set(invertableAction.getTarget(), true, true);
            // } else if (invertableAction instanceof Move){
            // ActionApplier.applyAMove(facto, scanner.getTreeContext(), (Move &
            // AAction<Move>) invertableAction);
            // evoState.set((AbstractVersionedTree)invertableAction.getSource(), false);
            // evoState.set(invertableAction.getTarget(), true);
        } else {
            throw new RuntimeException(action.getClass().getCanonicalName());
        }
        return evoState.set(action, inverted, true);
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
                AbstractVersionedTree src = (AbstractVersionedTree) mact.getSource();
                if (watching.containsKey(src)) {
                    watching.put(src, dst);
                }
            }
            // evoState.set(dst, false, true);
        } else if (action instanceof Delete) {
            ActionApplier.applyAInsert(facto, scanner.getTreeContext(),
                    AAction.build(Insert.class, action.getTarget(), action.getTarget())); // hope it gets the original
                                                                                          // CtElement
            // evoState.set(action.getTarget(), true, true);
        } else if (action instanceof Update) {
            ActionApplier.applyAUpdate(facto, scanner.getTreeContext(),
                    AAction.build(Update.class, action.getTarget(), (AbstractVersionedTree) action.getSource()));
            if (watching.containsKey(action.getTarget())) {
                watching.put(action.getTarget(), (AbstractVersionedTree) action.getSource());
            }
            // evoState.set((AbstractVersionedTree) action.getTarget(), false, true);
            // evoState.set((AbstractVersionedTree) action.getSource(), true, true);
            // } else if (action instanceof Move){
            // ActionApplier.applyAMove(facto, scanner.getTreeContext(), (Move &
            // AAction<Move>) action);
            // evoState.set((AbstractVersionedTree)action.getSource(), false);
            // evoState.set(action.getTarget(), true);
        } else {
            throw new RuntimeException(action.getClass().getCanonicalName());
        }
        evoState.set(action, true, true);
    }

    @Override
    public void close() throws Exception {
        // TODO Auto-generated method stub
        // TODO undo all changes on middle repr as spoon ast
    }

    // static void applyEvolutions(EvolutionsAtProj eap, Set<Evolution> wantedEvos,
    // Map<Evolution, Set<Evolution>> atomizedRefactorings) {
    // // bug spoon while parsing comments
    // // make system tests that analyze spoon itself
    // // maven modules can be handled by the used, just need to open some api calls
    // // (access to SpoonPom childrens, MavenLauncher that takes an SpoonPom
    // Instance)
    // // make spoon being able to be more in incremental, in the future use an
    // // incremental parser, would allow to scale to mining of whole git repo ?!?!
    // // interested?
    // // that is something like versioned visitors and accessors OR keep them as is
    // // but make a processor that can change the version of the ast
    // // make things more immutable in the future? solve part of the complexity of
    // // ChangeListener
    // // reversible / bijective
    // // avoid cloning whole ast
    // // keep original asts and found operations valid
    // // able to apply each atomic op
    // // what about move class / move package ?
    // // transformation into coming patch ?
    // // maintain an intermediary ast ? where we apply ops
    // // would it be easier to create an Itree instead of a spoon ast ?
    // // ability to apply and unapply op in any order
    // // able to serialize/prettyprint intermediary ast
    // // in general being able to manipulate the intermediary ast like a standard
    // // spoon ast
    // // avoid cloning the whole ast to create the intermediary tree
    // // is it possible to avoid using CtPath to go between left or right and
    // // intermediary
    // // ChangeListener can be used realistically? extends it to revert operations?
    // // as martin pointed out, it can be looked at like a merge for the creating
    // of
    // // the intermediary,
    // // considering a left, middle and right ast, here left = middle, but we need
    // to
    // // filter some evolutions coming from right.
    // try (ApplierHelper ah = new ApplierHelper(eap, wantedEvos,
    // atomizedRefactorings);) {
    // Launcher launcher = ah.applyEvolutions(wantedEvos);
    // serialize(launcher, Paths.get("/tmp/applyResults/").toFile());
    // } catch (Exception e) {
    // throw new RuntimeException(e);
    // }

    // }

    // void serialize(File out) {
    // JavaOutputProcessor outWriter = launcher.createOutputWriter();
    // outWriter.getEnvironment().setOutputDestinationHandler(new
    // MyDefaultOutputDestinationHandler(out,outWriter.getEnvironment()));
    // // outWriter.getEnvironment().setSourceOutputDirectory(out);
    // for (CtType p : launcher.getModel().getAllTypes()) {
    // if (!p.isShadow()) {
    // outWriter.createJavaFile(p);
    // }
    // }
    // }

    // public static void serialize(Launcher launcher, File out) {
    // }

    private Map<AbstractVersionedTree, AbstractVersionedTree> watching = new HashMap<>();

    public CtElement[] watchApply(VersionCommit version, AbstractVersionedTree... treeTest) {
        CtElement[] r = new CtElement[treeTest.length];
        for (int i = 0; i < treeTest.length; i++) {
            AbstractVersionedTree x = treeTest[i];
            watching.put(x, x);
            CtElement ele = computeAt(version, x);
            r[i] = ele;
        }
        return r;
    }

    public CtElement[] getUpdatedElement(VersionCommit version, AbstractVersionedTree... treeTest) {
        CtElement[] r = new CtElement[treeTest.length];
        for (int i = 0; i < treeTest.length; i++) {
            AbstractVersionedTree xx = treeTest[i];
            AbstractVersionedTree x = watching.getOrDefault(xx, xx);
            if (x.getAddedVersion() != version) {
                continue;
            }
            r[i] = computeAt(version, x);
        }
        return r;
    }

    public CtMethod getUpdatedMethod(VersionCommit version, AbstractVersionedTree treeTest) {
        AbstractVersionedTree xx = treeTest;
        AbstractVersionedTree xxName = treeTest.getChildren(treeTest.getAddedVersion()).get(0);
        AbstractVersionedTree x = watching.getOrDefault(xx, xx);
        AbstractVersionedTree xName = watching.getOrDefault(xxName, xxName);
        CtMethod actualTest = (CtMethod) x.getMetadata(SpoonGumTreeBuilder.SPOON_OBJECT);
        // CtExecutableReference actualTestRef = (CtExecutableReference) xName
        //         .getMetadata(SpoonGumTreeBuilder.SPOON_OBJECT);
        // if (actualTest != actualTestRef.getParent()) {
        //     throw new RuntimeException();
        // }
        String simpname = actualTest.getSimpleName();
        String qualDeclClass = actualTest.getDeclaringType().getQualifiedName();

        CtMethod ele = computeAt(version, x);
        if (ele == null) {
            CtExecutableReference ref = computeAt(version, xName);
            if(ref != null){
                ele = (CtMethod) ref.getParent();
            }
        }

        if (ele!=null && ele.getSimpleName().equals(simpname) && ele.getDeclaringType().getQualifiedName().equals(qualDeclClass)) {
        } else {
            ele = (CtMethod) x.getMetadata(VersionedTree.ORIGINAL_SPOON_OBJECT);
        }
        return ele;
    }

    private <T> T computeAt(VersionCommit afterVersion, AbstractVersionedTree x) {
        Object r = null;
        if (x.getAddedVersion() == afterVersion) {
            r = x.getMetadata(VersionedTree.ORIGINAL_SPOON_OBJECT);
        }
        if (r == null) {
            Map<Version, Object> map = (Map<Version, Object>) x
                    .getMetadata(MyScriptGenerator.ORIGINAL_SPOON_OBJECT_PER_VERSION);
            if(map != null){
                r = map.get(afterVersion);
            }
        }
        return (T) r;
    }

}
