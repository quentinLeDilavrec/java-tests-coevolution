package fr.quentin.coevolutionMiner.v2.coevolution.miners;

import java.io.File;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.Function;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.util.stream.Collectors;

import com.github.gumtreediff.actions.MyAction.MyMove;
import com.github.gumtreediff.actions.model.Move;
import com.github.gumtreediff.tree.AbstractVersionedTree;
import com.github.gumtreediff.tree.ITree;
import com.github.gumtreediff.tree.Version;
import com.github.gumtreediff.tree.VersionedTree;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.math.Fraction;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.eclipse.jetty.util.MultiMap;
import org.refactoringminer.api.Refactoring;

import fr.quentin.coevolutionMiner.v2.evolution.EvolutionHandler;
import fr.quentin.coevolutionMiner.v2.evolution.Evolutions;
import fr.quentin.coevolutionMiner.v2.evolution.EvolutionsMiner;
import fr.quentin.coevolutionMiner.v2.evolution.Evolutions.Evolution;
import fr.quentin.coevolutionMiner.v2.evolution.Evolutions.Evolution.DescRange;
import fr.quentin.coevolutionMiner.v2.evolution.miners.GumTreeSpoonMiner;
import fr.quentin.coevolutionMiner.v2.evolution.miners.MixedMiner;
import fr.quentin.coevolutionMiner.v2.evolution.miners.MultiGTSMiner;
import fr.quentin.coevolutionMiner.v2.evolution.miners.RefactoringMiner;
import fr.quentin.coevolutionMiner.v2.evolution.miners.GumTreeSpoonMiner.EvolutionsAtCommit;
import fr.quentin.coevolutionMiner.v2.evolution.miners.GumTreeSpoonMiner.EvolutionsAtCommit.EvolutionsAtProj;
import fr.quentin.coevolutionMiner.v2.sources.Sources;
import fr.quentin.coevolutionMiner.v2.sources.SourcesHandler;
import fr.quentin.coevolutionMiner.v2.sources.Sources.Commit;
import fr.quentin.coevolutionMiner.v2.utils.Iterators2;
import fr.quentin.coevolutionMiner.v2.utils.Utils;
import fr.quentin.coevolutionMiner.v2.utils.Utils.Spanning;
import fr.quentin.coevolutionMiner.v2.coevolution.CoEvolutionHandler;
import fr.quentin.coevolutionMiner.v2.coevolution.CoEvolutions;
import fr.quentin.coevolutionMiner.v2.coevolution.CoEvolutionsMiner;
import fr.quentin.coevolutionMiner.v2.coevolution.CoEvolutionsStorage;
import fr.quentin.coevolutionMiner.v2.coevolution.CoEvolutions.CoEvolution;
import fr.quentin.coevolutionMiner.v2.coevolution.miners.EImpact.FailureReport;
import fr.quentin.coevolutionMiner.v2.coevolution.miners.EImpact.ImpactedRange;
import fr.quentin.coevolutionMiner.v2.dependency.DependencyHandler;
import fr.quentin.coevolutionMiner.v2.dependency.Dependencies;
import fr.quentin.coevolutionMiner.v2.dependency.Dependencies.Dependency;
import spoon.Launcher;
import spoon.MavenLauncher;
import spoon.compiler.Environment;
import spoon.compiler.Environment.PRETTY_PRINTING_MODE;
import spoon.reflect.cu.SourcePosition;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtModule;
import spoon.reflect.declaration.CtPackage;
import spoon.reflect.declaration.CtType;
import spoon.reflect.factory.Factory;
import spoon.reflect.path.CtPath;
import spoon.reflect.visitor.Filter;
import spoon.support.DefaultOutputDestinationHandler;
import spoon.support.JavaOutputProcessor;
import spoon.support.OutputDestinationHandler;
import fr.quentin.impactMiner.Position;
import gumtree.spoon.apply.ApplierHelper;
import gumtree.spoon.apply.operations.MyScriptGenerator;
import gumtree.spoon.diff.Diff;
import gumtree.spoon.diff.DiffImpl;
import gumtree.spoon.diff.operations.DeleteOperation;
import gumtree.spoon.diff.operations.InsertOperation;
import gumtree.spoon.diff.operations.MoveOperation;
import gumtree.spoon.diff.operations.Operation;
import gumtree.spoon.diff.operations.UpdateOperation;
import fr.quentin.coevolutionMiner.utils.SourcesHelper;
import fr.quentin.coevolutionMiner.v2.ast.Project;
import fr.quentin.coevolutionMiner.v2.ast.ProjectHandler;
import fr.quentin.coevolutionMiner.v2.ast.RangeMatchingException;
import fr.quentin.coevolutionMiner.v2.ast.Project.AST.FileSnapshot;
import fr.quentin.coevolutionMiner.v2.ast.Project.AST.FileSnapshot.Range;
import fr.quentin.coevolutionMiner.v2.ast.miners.SpoonMiner;
import fr.quentin.coevolutionMiner.v2.ast.miners.SpoonMiner.ProjectSpoon.SpoonAST;

// CAUTION same limitation as MyImpactsMiner
public class MyCoEvolutionsMiner implements CoEvolutionsMiner {

    static Logger logger = LogManager.getLogger();

    private final ProjectHandler astHandler;
    private final EvolutionHandler evoHandler;
    private final CoEvolutions.Specifier spec;

    private final SourcesHandler srcHandler;

    private final DependencyHandler impactHandler;

    public MyCoEvolutionsMiner(CoEvolutions.Specifier spec, SourcesHandler srcHandler, ProjectHandler astHandler,
            EvolutionHandler evoHandler, DependencyHandler impactHandler) {
        this.spec = spec;
        this.srcHandler = srcHandler;
        this.astHandler = astHandler;
        this.evoHandler = evoHandler;
        this.impactHandler = impactHandler;
    }

    @Override
    public CoEvolutions compute() {
        assert spec.evoSpec != null : spec;
        Sources sourcesProvider = srcHandler.handle(spec.evoSpec.sources);
        Commit beforeCom = sourcesProvider.getCommit(spec.evoSpec.commitIdBefore);
        Commit afterCom = sourcesProvider.getCommit(spec.evoSpec.commitIdAfter);
        try {
            return computeDirectCoevolutions(sourcesProvider, beforeCom, afterCom);
        } catch (SmallMiningException e) {
            logger.warn("small mining exception while searching for coevolutions", e);
            return new CoEvolutions.FailedCoEvolutions(spec, e);
        } catch (SeverMiningException e) {
            throw new RuntimeException(e);
        }
    }

    private CoEvolutionsExtension computeDirectCoevolutions(Sources sourcesProvider, Commit beforeCommit,
            Commit afterCommit) throws SmallMiningException, SeverMiningException {

        Project.Specifier<SpoonMiner> before_ast_id = astHandler.buildSpec(spec.evoSpec.sources, beforeCommit.getId());
        Project.Specifier<SpoonMiner> after_ast_id = astHandler.buildSpec(spec.evoSpec.sources, afterCommit.getId());

        Evolutions.Specifier currEvoMixedSpec = EvolutionHandler.buildSpec(sourcesProvider.spec, beforeCommit.getId(),
                afterCommit.getId(), MixedMiner.class);

        Evolutions.Specifier currEvoSpecGTS = EvolutionHandler.buildSpec(sourcesProvider.spec, beforeCommit.getId(),
                afterCommit.getId(), GumTreeSpoonMiner.class);
        Evolutions currentGTSEvolutions = evoHandler.handle(currEvoSpecGTS);

        Evolutions.Specifier currEvoSpecRM = EvolutionHandler.buildSpec(sourcesProvider.spec, beforeCommit.getId(),
                afterCommit.getId(), RefactoringMiner.class);
        Evolutions currentRMEvolutions = evoHandler.handle(currEvoSpecRM);
        Evolutions currEvoMixed = evoHandler.handle(currEvoMixedSpec);
        logNaiveCostCoEvoValidation(currEvoMixed);

        Project<CtElement> before_proj = astHandler.handle(before_ast_id);
        if (before_proj.getAst().compilerException != null || !before_proj.getAst().isUsable()) {
            throw new SmallMiningException("Initial Code cannot be represented as an AST",
                    before_proj.getAst().compilerException);
        }
        SpoonAST ast_before = (SpoonAST) before_proj.getAst();

        Project<?> after_proj = astHandler.handle(after_ast_id);
        if (after_proj.getAst().compilerException != null || !after_proj.getAst().isUsable()) {
            throw new SmallMiningException("Code after evolutions cannot be represented as an AST",
                    after_proj.getAst().compilerException);
        }
        SpoonAST ast_after = (SpoonAST) after_proj.getAst();

        // match each refactoring against evolutions mined by gumtree,
        // it allows to easily apply refactorings
        Map<Evolution, Set<Evolution>> atomizedRefactorings = decompose(currentRMEvolutions, currentGTSEvolutions);
        // TODO materialize more the composition relation ?

        printAtomised(atomizedRefactorings);

        Dependencies currentImpacts = impactHandler.handle(impactHandler.buildSpec(before_ast_id, currEvoMixedSpec));
        Dependencies afterImpacts = impactHandler.handle(impactHandler.buildSpec(after_ast_id, currEvoMixedSpec));

        CoEvolutions.Specifier coevoSpec = CoEvolutionHandler.buildSpec(sourcesProvider.spec, currEvoMixedSpec);
        CoEvolutionsExtension currCoevolutions = new CoEvolutionsExtension(coevoSpec, currEvoMixed, before_proj,
                after_proj);

        // EvolutionsAtCommit currEvoAtCommit = ((GumTreeSpoonMiner.EvolutionsMany) currentGTSEvolutions)
        //         .getPerCommit(currentCommit.getId(), nextCommit.getId());
        // if (currEvoAtCommit == null) {
        //     logger.warn(
        //             "no evolutions here for this pair of commits: " + currentCommit.getId() + ", " + nextCommit.getId());
        //     return currCoevolutions;
        // }

        InterestingCasesExtractor icExtractor = new InterestingCasesExtractor(currentImpacts, beforeCommit,
                afterImpacts, afterCommit, atomizedRefactorings);
        icExtractor.computeRelax();

        // Validation phase, by compiling and running tests
        Path pathToIndividualExperiment = Paths.get("/tmp/applyResults", getRepoRawPath(), afterCommit.getId(),
                beforeCommit.getId(), "" + new Date().getTime());
        // EvolutionsAtProj rootModule = currEvoAtCommit.getRootModule();
        // if (rootModule == null)
        //     logger.warn("rootModule is null");
        // if (rootModule.getBeforeProj() == null)
        //     logger.warn("beforeProj is null");
        // if (rootModule.getAfterProj() == null)
        //     logger.warn("afterProj is null");
        Path oriPath = ast_before.rootDir;// ((SpoonAST) rootModule.getBeforeProj().getAst()).rootDir;
        Path afterOriPath = ast_after.rootDir;// ((SpoonAST) rootModule.getAfterProj().getAst()).rootDir;
        // setup directory where validation will append
        FileUtils.deleteQuietly(pathToIndividualExperiment.toFile());
        try {
            FileUtils.copyDirectory(oriPath.toFile(), pathToIndividualExperiment.toFile());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        JavaOutputProcessor outputProcessor = new JavaOutputProcessor();
        EvoStateMaintainerImpl evoState = new EvoStateMaintainerImpl(beforeCommit, //currEvoAtCommit.beforeCommit,
                atomizedRefactorings);

        logger.info(
                atomizedRefactorings.keySet().stream().map(x -> "" + x.toString() + ":" + x.getOriginal().toString())
                        .collect(Collectors.toList()).toString());

        FunctionalImpactRunner consumer = new FunctionalImpactRunner(pathToIndividualExperiment.toFile());
        consumer.outputProcessor = outputProcessor;
        evoState.setValidityLauncher(consumer);
        Map<Set<Evolution>, Set<EImpact>> functionalImpacts = new LinkedHashMap<>();
        for (EvolutionsAtProj k : icExtractor.interestingCases.keySet()) {
            consumer.setEvolutionsAtProj(k);
            // check the initial state
            for (Range initialTest : icExtractor.impactedTestsPerProj.get(k)) {
                EImpact.FailureReport report = null;
                try {
                    report = FunctionalImpactRunner.runValidationCheckers(outputProcessor.getOutputDirectory(),
                            ((CtMethod) initialTest.getOriginal()).getDeclaringType().getQualifiedName(),
                            ((CtMethod) initialTest.getOriginal()).getSimpleName(), report);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                EImpact eimpact = new EImpact(initialTest, report);
                currCoevolutions.addInitialTestResult(initialTest, eimpact);
            }

            // check interesting cases by applying evolutions
            for (InterestingCase c : icExtractor.interestingCases.get(k)) {
                logger.info(evoState.getValidable().toString());
                // // TRY reset exp dir?
                // FileUtils.deleteQuietly(pathToIndividualExperiment.toFile());
                // try {
                //     FileUtils.copyDirectory(oriPath.toFile(), pathToIndividualExperiment.toFile());
                // } catch (Exception e) {
                //     throw new RuntimeException(e);
                // }
                try (ApplierHelper<Evolution> ah = new ApplierHelper<>(k.getScanner(), k.getMdiff(), k.getDiff(),
                        evoState)) {
                    consumer.prepareApply(ah, c.testBefore, c.testAfter);
                    ah.setLeafsActionsLimit(5);
                    ah.applyEvolutions(c.evosForThisTest);
                    for (EImpact imp : consumer.resultingImpacts) {
                        Set<Evolution> e = imp.getEvolutions();
                        try {
                            FailureReport report = imp.getSharingTest(c.testBefore).report;
                            logger.info(e.toString() + ";" + (report == null ? "P" : report.when));
                        } catch (Exception ee) {
                            logger.warn("cannot log imp ", ee);
                            logger.info(c.testBefore.toString());
                            logger.info(imp.getSharingTest(c.testBefore).toString());
                        }
                        functionalImpacts.putIfAbsent(e, new LinkedHashSet<>());
                        functionalImpacts.get(e).add(imp);
                    }
                } catch (Exception e) {
                    logger.catching(Level.ERROR, e);
                    throw new RuntimeException(e);
                }
                logger.info(evoState.getValidable().toString());
            }
        }
        currCoevolutions.addEImpacts(functionalImpacts);
        return currCoevolutions;
    }

    private void printAtomised(Map<Evolution, Set<Evolution>> atomizedRefactorings) {
        for (Entry<Evolution, Set<Evolution>> entry : atomizedRefactorings.entrySet()) {
            Evolution evo = entry.getKey();
            System.out.println("---------------" + evo.getEnclosingInstance().spec.miner.getSimpleName());
            System.out.println(evo.getType());
            for (Evolution atom : entry.getValue()) {
                if (atom != evo) {
                    System.out.println(atom.getType());
                }
            }
        }
    }

    private static class InterestingCase {
        public Set<EvolutionsAtProj> evolutionsAtProjSet;
        public Set<Evolution> evosForThisTest;
        public Range testBefore;
        public Range testAfter;
    }

    private String getRepoRawPath() {
        String repoRawPath;
        try {
            repoRawPath = SourcesHelper.parseAddress(this.spec.srcSpec.repository);
        } catch (URISyntaxException e1) {
            throw new RuntimeException(e1);
        }
        return repoRawPath;
    }

    // TODO use more controlled state, to isolate compilation failures, but will be costly to instanciate
    boolean PartiallyInstanciateState = false;

    private Map<Evolution, Set<Evolution>> decompose(Evolutions from, Evolutions by) {
        assert from != null;
        assert by != null;
        // HashSet<Evolutions.Evolution> notUsed = new HashSet<>(by.toSet());
        Map<Evolution, Set<Evolution>> result = new LinkedHashMap<>();

        for (Evolution evolution : Iterators2.createChainIterable(Arrays.asList(from, by))) {
            Set<Evolution> acc = new LinkedHashSet<>();
            for (DescRange descRange : evolution.getBefore()) {
                CtElement ele = (CtElement) descRange.getTarget().getOriginal();
                if (ele == null) {
                    logger.warn("no original for" + descRange.getTarget());
                } else {
                    decomposeAux(acc, ele);
                }
            }
            for (DescRange descRange : evolution.getAfter()) {
                CtElement ele = (CtElement) descRange.getTarget().getOriginal();
                if (ele == null) {
                    logger.warn("no original for" + descRange.getTarget());
                } else {
                    decomposeAux(acc, ele);
                }
            }
            // notUsed.removeAll(acc);
            acc.remove(evolution);
            result.put(evolution, acc);
        }
        // // promote
        // for (Evolution evolution : notUsed) {
        // result.put(evolution, Collections.emptySet());
        // }
        return result;
    }

    private void decomposeAux(Set<Evolution> acc, CtElement ele) {
        assert ele != null;
        Set<DescRange> s = (Set<DescRange>) ele.getMetadata(EvolutionsMiner.METADATA_KEY_EVO);
        if (s != null) {
            for (DescRange ds : s) {
                acc.add(ds.getSource());
            }
        }
        if (ele.getDirectChildren() == null) {
            return;
        }
        for (CtElement child : ele.getDirectChildren()) {
            decomposeAux(acc, child);
        }
    }

    private static void logNaiveCostCoEvoValidation(Evolutions currentEvolutions) {
        int size = currentEvolutions.toSet().size();
        logger.info("naive: " + size + " evolution ->" + factApprox(size));
    }

    // fact with Stirling's approximation
    private static double factApprox(double n) {
        return n <= 1 ? 1 : Math.sqrt(2 * Math.PI * n) * Math.pow(n / Math.E, n);
    }

    public static class CoEvolutionsExtension extends CoEvolutions {
        private final Set<CoEvolution> coevolutions = new LinkedHashSet<>();
        public final Evolutions evolutions;
        public final Project<?> astBefore;
        public final Project<?> astAfter;
        private final Map<Range, EImpact> initialTestsStatus = new HashMap<>(); // TODO put it in the db
        private final Set<EImpact> probableCoevoCauses = new LinkedHashSet<>(); // merges with prev commits would allow to
        // find even farther root causes
        private final Set<EImpact> probableCoEvoResolutions = new LinkedHashSet<>(); // merge won't help much but need to
        // confirm
        private final Set<EImpact> partialResolutions = new LinkedHashSet<>(); // merge with other commits
        private final Set<EImpact> failfail = new LinkedHashSet<>(); // X X
        private final Map<ImmutablePair<Evolution, Range>, Set<EImpact>> probableResolutionsIndex = new HashMap<>();
        private final Map<Evolution, Set<EImpact>> probablyNothing = new HashMap<>();

        public CoEvolutionsExtension(Specifier spec, Evolutions evolutions, Project<?> astBefore, Project<?> astAfter) {
            super(spec);
            this.evolutions = evolutions;
            this.astBefore = astBefore;
            this.astAfter = astAfter;
        }

        @Override
        public Set<EImpact> getEImpacts() {
            Set<EImpact> r = new HashSet<>();
            r.addAll(probableCoEvoResolutions);
            r.addAll(probableCoevoCauses);
            r.addAll(partialResolutions);
            r.addAll(failfail);
            return r;
        }

        @Override
        public Set<EImpact.ImpactedRange> getInitialTests() {
            Set<EImpact.ImpactedRange> r = new LinkedHashSet<>();
            for (Entry<Range, EImpact> p : initialTestsStatus.entrySet()) {
                r.add(p.getValue().getSharingTest(p.getKey()));
            }
            return r;
        }

        public void addEImpacts(Map<Set<Evolution>, Set<EImpact>> eImpacts) {
            logger.info("post-processing " + eImpacts.size() + " sets of evolutions");
            logger.info("precisely:  " + eImpacts.keySet());
            for (Entry<Set<Evolution>, Set<EImpact>> aaa : eImpacts.entrySet()) {
                for (EImpact ei : aaa.getValue()) {
                    for (Range bbb : ei.getSharedTests()) {
                        EImpact.FailureReport resInitial = initialTestsStatus.get(bbb).getSharingTest(bbb).report;
                        ImpactedRange ccc = ei.getSharingTest(bbb);
                        EImpact.FailureReport resAfter = ccc.report;
                        if (resInitial == null && resAfter == null) { // V V
                            // probable resolution
                            for (Evolution ddd : aaa.getKey()) {
                                ImmutablePair<Evolution, Range> pair = new ImmutablePair<>(ddd, bbb);
                                probableResolutionsIndex.putIfAbsent(pair, new LinkedHashSet<>());
                                probableResolutionsIndex.get(pair).add(ei);
                            }
                            probableCoEvoResolutions.add(ei);
                        } else if (resInitial == null) { // V X
                            // probable cause
                            probableCoevoCauses.add(ei);
                        } else if (resAfter == null) { // X V
                            // at least the last part of a resolution, if considering multiple commits
                            partialResolutions.add(ei);
                            for (Evolution ddd : aaa.getKey()) {
                                // for now we wont call it a resolution
                                probablyNothing.putIfAbsent(ddd, new HashSet<>());
                                probablyNothing.get(ddd).add(ei);
                                // probableResolutionsIndex.putIfAbsent(ddd, new HashSet<>());
                                // probableResolutionsIndex.get(ddd).put(ccc.getKey(), ei);
                            }
                        } else { // X X
                            failfail.add(ei);
                            // here nothing, but could be part of a any, if considering multiple commits
                            // TODO useless to put in the graph? would need to be assembled with evo from
                            // prev commits until test pass
                            for (Evolution ddd : aaa.getKey()) {
                                probablyNothing.putIfAbsent(ddd, new HashSet<>());
                                probablyNothing.get(ddd).add(ei);
                            }
                        }
                    }
                }
            }
            // compute inclusion of causes in reso then make the coevo
            // more efficient than probableCoEvoResolutions.iterator().next().evolutions.keySet().containsAll(eimpCauses.evolutions.keySet())
            // TODO extract it to unit test it
            for (EImpact eimpCauses : probableCoevoCauses) {
                for (Range causeEntry : eimpCauses.getSharedTests()) {
                    Set<EImpact> possibleReso = null;
                    for (Evolution causeEvo : eimpCauses.getEvolutions()) {
                        Set<EImpact> resos = probableResolutionsIndex
                                .get(new ImmutablePair<>(causeEvo, causeEntry));
                        if (resos == null) {
                            break;
                        }
                        if (possibleReso == null) {
                            possibleReso = new LinkedHashSet<>(resos);
                        } else {
                            possibleReso.retainAll(resos);
                        }
                        if (possibleReso == null || possibleReso.isEmpty()) {
                            break;
                        }
                    }
                    if (possibleReso == null || possibleReso.isEmpty()) {
                        continue;
                    }

                    // Map<Range, Map<Range, FailureReport>> causeRevIndex = new LinkedHashMap<>();
                    // for (Entry<Range, ImmutablePair<Range, FailureReport>> entry : eimpCauses.tests.entrySet()) {
                    //     causeRevIndex.putIfAbsent(entry.getValue().left, new LinkedHashMap<>());
                    //     FailureReport old = causeRevIndex.get(entry.getValue().left).put(entry.getKey(),
                    //             entry.getValue().right);
                    //     if (old!=null) {
                    //         logger.warn("unclear impact status");
                    //     }
                    // }
                    // remaining possibleReso are real reso
                    for (EImpact eimpReso : possibleReso) {
                        Set<Evolution> resolutions = new LinkedHashSet<>(eimpReso.getEvolutions());
                        resolutions.removeAll(eimpCauses.getEvolutions());
                        if (resolutions.size() == 0)
                            continue;
                        Range testBefore = causeEntry;
                        ImpactedRange resoThing = eimpReso.getSharingTest(testBefore);
                        if (resoThing == null)
                            continue;
                        Range testAfterR = resoThing.range;
                        Range testAfterC = eimpCauses.getSharingTest(causeEntry).range;
                        CoEvolutionExtension res; // TODO to discus
                        if (testAfterR == testBefore && testAfterC == testBefore) {
                            res = new CoEvolutionExtension(new LinkedHashSet<>(eimpCauses.getEvolutions()), resolutions,
                                    Collections.singleton(testBefore), Collections.singleton(testBefore));
                        } else if (testAfterR == testBefore) { // TODO can we really undershoot here?
                            res = new CoEvolutionExtension(new LinkedHashSet<>(eimpCauses.getEvolutions()), resolutions,
                                    Collections.singleton(testBefore), Collections.singleton(testAfterC));
                        } else if (testAfterC == testBefore) {
                            res = new CoEvolutionExtension(new LinkedHashSet<>(eimpCauses.getEvolutions()), resolutions,
                                    Collections.singleton(testBefore), Collections.singleton(testAfterR));
                        } else if (testAfterR == testAfterC) {
                            res = new CoEvolutionExtension(new LinkedHashSet<>(eimpCauses.getEvolutions()), resolutions,
                                    Collections.singleton(testBefore), Collections.singleton(testAfterR));
                        } else {
                            continue;
                        }
                        coevolutions.add(res);
                    }
                }
            }
        }

        public void addInitialTestResult(Range test, EImpact eimpact) {
            this.initialTestsStatus.put(test, eimpact);
        }

        class CoEvolutionExtension extends CoEvolution {
            public CoEvolutionExtension(Set<Evolution> causes, Set<Evolution> resolutions, Set<Range> testsBefore,
                    Set<Range> testsAfter) {
                super(causes, resolutions, testsBefore, testsAfter);
            }
        }

        @Override
        public Set<CoEvolution> getCoEvolutions() {
            return Collections.unmodifiableSet(coevolutions);
        }
    }

    private static class InterestingCasesExtractor {
        Map<EvolutionsAtProj, Set<InterestingCase>> interestingCases = new LinkedHashMap<>();
        Map<EvolutionsAtProj, Set<Evolution>> evoPerProj = new HashMap<>();
        Map<EvolutionsAtProj, Set<Range>> impactedTestsPerProj = new HashMap<>();
        private Dependencies beforeImpacts;
        private Dependencies afterImpacts;
        private Map<Evolution, Set<Evolution>> atomizedRefactorings;
        private Version beforeCommit;
        private Version afterCommit;

        public InterestingCasesExtractor(Dependencies beforeImpacts, Version beforeCommit, Dependencies afterImpacts,
                Version afterCommit, Map<Evolution, Set<Evolution>> atomizedRefactorings) {
            this.beforeImpacts = beforeImpacts;
            this.afterImpacts = afterImpacts;
            this.atomizedRefactorings = atomizedRefactorings;
            this.beforeCommit = beforeCommit;
            this.afterCommit = afterCommit;
        }

        class DPair {
            Set<Entry<Range, Set<Evolution.DescRange>>> before = new HashSet<>();
            Set<Entry<Range, Set<Evolution.DescRange>>> after = new HashSet<>();
        }

        private Map<Project, DPair> testNevosPerProjects() {
            Map<Project, DPair> r = new HashMap<>();
            for (Entry<Range, Set<Evolution.DescRange>> impactedTests : beforeImpacts.getDependentTests().entrySet()) {
                r.putIfAbsent(impactedTests.getKey().getFile().getAST().getProject(), new DPair());
                r.get(impactedTests.getKey().getFile().getAST().getProject()).before.add(impactedTests);
            }
            for (Entry<Range, Set<Evolution.DescRange>> impactedTests : afterImpacts.getDependentTests().entrySet()) {
                r.putIfAbsent(impactedTests.getKey().getFile().getAST().getProject(), new DPair());
                r.get(impactedTests.getKey().getFile().getAST().getProject()).after.add(impactedTests);
            }
            return r;
        }

        public void computeExact() {
            for (Entry<Project, DPair> atProject : testNevosPerProjects().entrySet()) {

                Map<Range, Map<Range, InterestingCase>> interestingCasesLocalIndex = new HashMap<>();

                for (Entry<Range, Set<Evolution.DescRange>> impactedTest : atProject.getValue().before) {
                    Set<EvolutionsAtProj> evolutionsAtProjSet = new HashSet<>();
                    Set<Evolutions.Evolution.DescRange> evosInGame = impactedTest.getValue();
                    Set<Evolution> evosForThisTest = new LinkedHashSet<>();
                    computingOnEvos(evolutionsAtProjSet, evosInGame, evosForThisTest);
                    if (evolutionsAtProjSet.size() == 0) {
                        logger.warn(
                                "no applicable evolutions found thus no possibility to validate potential coevolutions");
                        continue;
                    } else if (evolutionsAtProjSet.size() > 1) {
                        logger.warn("not handling the validation of coevolutions spanning over multiple projects");
                        continue;
                    }

                    EvolutionsAtProj evolutionsAtProj = evolutionsAtProjSet.iterator().next();
                    Project projectBefore = evolutionsAtProj.getBeforeProj();
                    Project projectAfter = evolutionsAtProj.getAfterProj();

                    Range testAfter = null;
                    Range testBefore = impactedTest.getKey();

                    // TODO extract functionality
                    AbstractVersionedTree treeTestBefore = (AbstractVersionedTree) ((CtElement) testBefore
                            .getOriginal()).getMetadata(VersionedTree.MIDDLE_GUMTREE_NODE);
                    AbstractVersionedTree treeTestAfter = treeTestBefore;
                    MyMove mov = (MyMove) treeTestBefore.getMetadata(MyScriptGenerator.MOVE_SRC_ACTION);
                    if (mov != null) {
                        treeTestAfter = mov.getInsert().getTarget();
                    }
                    try {
                        testAfter = Utils.toRange(projectAfter, treeTestAfter, afterCommit);
                    } catch (RangeMatchingException e1) {
                        logger.warn("at" + projectAfter + " for " + Objects.toString(afterCommit) + " cannot format "
                                + treeTestBefore, e1);
                    }

                    if (testBefore == null) {
                        // For now we cannnot handle such deleted test
                        try {
                            logger.info("deleted test ignored: " + ((CtMethod) testAfter).getSignature());
                        } catch (Exception e) {
                        }
                        continue;// TODO handle this
                    }

                    aggregating(interestingCasesLocalIndex, evolutionsAtProjSet, evosForThisTest, evolutionsAtProj,
                            testAfter, testBefore);
                }

                for (Entry<Range, Set<Evolution.DescRange>> impactedTest : atProject.getValue().before) {
                    Set<EvolutionsAtProj> evolutionsAtProjSet = new HashSet<>();
                    Set<Evolutions.Evolution.DescRange> evosInGame = impactedTest.getValue();
                    Set<Evolution> evosForThisTest = new LinkedHashSet<>();
                    computingOnEvos(evolutionsAtProjSet, evosInGame, evosForThisTest);
                    if (evolutionsAtProjSet.size() == 0) {
                        logger.warn(
                                "no applicable evolutions found thus no possibility to validate potential coevolutions");
                        continue;
                    } else if (evolutionsAtProjSet.size() > 1) {
                        logger.warn("not handling the validation of coevolutions spanning over multiple projects");
                        continue;
                    }

                    EvolutionsAtProj evolutionsAtProj = evolutionsAtProjSet.iterator().next();
                    Project projectBefore = evolutionsAtProj.getBeforeProj();
                    Project projectAfter = evolutionsAtProj.getAfterProj();

                    Range testAfter = impactedTest.getKey();
                    Range testBefore = null;

                    AbstractVersionedTree treeTestAfter = (AbstractVersionedTree) ((CtElement) testAfter.getOriginal())
                            .getMetadata(VersionedTree.MIDDLE_GUMTREE_NODE);
                    AbstractVersionedTree treeTestBefore = treeTestAfter;
                    MyMove mov = (MyMove) treeTestAfter.getMetadata(MyScriptGenerator.MOVE_DST_ACTION);
                    if (mov != null) {
                        treeTestAfter = mov.getInsert().getTarget();
                    }
                    if (treeTestBefore.getInsertVersion() != afterCommit) {
                        try {
                            testBefore = Utils.toRange(projectBefore, treeTestBefore, beforeCommit);
                        } catch (RangeMatchingException e) {
                            logger.warn("at" + projectBefore + " for " + Objects.toString(beforeCommit)
                                    + " cannot format " + treeTestBefore, e);
                        }
                    }
                    if (testBefore == null) {
                        // For now we cannnot handle such new test
                        try {
                            logger.info("new test ignored: " + ((CtMethod) testAfter).getSignature());
                        } catch (Exception e) {
                        }
                        continue;// TODO handle this
                    }

                    aggregating(interestingCasesLocalIndex, evolutionsAtProjSet, evosForThisTest, evolutionsAtProj,
                            testAfter, testBefore);
                }
                if (interestingCasesLocalIndex.size() > 0) {
                    for (Map<Range, InterestingCase> cazeM : interestingCasesLocalIndex.values()) {
                        for (InterestingCase caze : cazeM.values()) {
                            if (caze.evolutionsAtProjSet.size() == 0) {
                                logger.warn(
                                        "no applicable evolutions found, thus no possibility to validate potential coevolutions");
                            } else if (caze.evolutionsAtProjSet.size() > 1) {
                                logger.warn(
                                        "not handling the validation of coevolutions spanning over multiple projects");
                            } else {
                                EvolutionsAtProj evolutionsAtProj = caze.evolutionsAtProjSet.iterator().next();
                                interestingCases.putIfAbsent(evolutionsAtProj, new LinkedHashSet<>());
                                interestingCases.get(evolutionsAtProj).add(caze);
                            }
                        }
                    }
                }
            }
        }

        private void computingOnEvos(Set<EvolutionsAtProj> evolutionsAtProjSet,
                Set<Evolutions.Evolution.DescRange> evosInGame, Set<Evolution> evosForThisTest) {
            for (Evolutions.Evolution.DescRange dr : evosInGame) {
                Evolution evolution = dr.getSource();
                evosForThisTest.add(evolution);

                Set<Evolution> c = atomizedRefactorings.get(evolution);
                if (c == null) {
                    logger.warn("following evolution not atomized " + evolution);
                } else {
                    evosForThisTest.addAll(c); // TODO eval of imp. on precision
                }
            }
            for (Evolution evolution : evosForThisTest) {
                if (evolution.getEnclosingInstance() instanceof EvolutionsAtProj) {
                    evolutionsAtProjSet.add((EvolutionsAtProj) evolution.getEnclosingInstance());
                }
            }
        }

        private void aggregating(Map<Range, Map<Range, InterestingCase>> interestingCasesLocalIndex,
                Set<EvolutionsAtProj> evolutionsAtProjSet, Set<Evolution> evosForThisTest,
                EvolutionsAtProj evolutionsAtProj, Range testAfter, Range testBefore) {

            if (testBefore != null && !(testBefore.getOriginal() instanceof CtMethod)) {
                logger.warn("original of testBefore should be a CtMethod but was "
                        + testBefore.getOriginal().getClass().toString());
                return;
            }
            if (testAfter != null && !(testAfter.getOriginal() instanceof CtMethod)) {
                logger.warn("original of testAfter should be a CtMethod but was "
                        + testAfter.getOriginal().getClass().toString());
                return;
            }

            Map<Range, InterestingCase> currM = interestingCasesLocalIndex.get(testBefore);
            InterestingCase curr;
            if (currM != null) {
                curr = currM.get(testAfter);
            } else {
                curr = null;
            }
            if (curr == null) {
                curr = new InterestingCase();
                HashMap<Range, InterestingCase> aaa = new HashMap<>();
                aaa.put(testAfter, curr);
                interestingCasesLocalIndex.put(testBefore, aaa);
                curr.evosForThisTest = new HashSet<>();
                curr.testBefore = testBefore;
                curr.testAfter = testAfter;
                curr.evolutionsAtProjSet = evolutionsAtProjSet;
            }

            evoPerProj.putIfAbsent(evolutionsAtProj, new LinkedHashSet<>());
            evoPerProj.get(evolutionsAtProj).addAll(evosForThisTest);

            curr.evosForThisTest.addAll(evosForThisTest);
            evosForThisTest = curr.evosForThisTest;

            impactedTestsPerProj.putIfAbsent(evolutionsAtProj, new HashSet<>());
            impactedTestsPerProj.get(evolutionsAtProj).add(testBefore);
        }

        // also put the cases without using the static analysis of dependencies (to avoid missing co-evolutions, for now because we can ignore to much evo from the code)
        public void computeRelax() {
            computeExact();
            for (EvolutionsAtProj k : this.interestingCases.keySet()) {
                Set<InterestingCase> tmpIntereSet = new HashSet<>();
                for (InterestingCase c : this.interestingCases.get(k)) {
                    InterestingCase curr = new InterestingCase();
                    curr.evosForThisTest = new LinkedHashSet<>(k.toSet()); // k only contains evos from gts
                    curr.evosForThisTest.addAll(evoPerProj.get(k)); //  evoPerProj only contains impacting evos
                    // need RefactoringMiner evos ?
                    curr.testBefore = c.testBefore;
                    curr.testAfter = c.testAfter;
                    curr.evolutionsAtProjSet = c.evolutionsAtProjSet;
                    tmpIntereSet.add(curr);
                }
                this.interestingCases.get(k).addAll(tmpIntereSet);
            }
        }
    }

}