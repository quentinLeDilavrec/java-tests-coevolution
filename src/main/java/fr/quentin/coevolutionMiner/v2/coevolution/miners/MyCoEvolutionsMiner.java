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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import com.github.gumtreediff.actions.MyAction.MyMove;
import com.github.gumtreediff.actions.model.Move;
import com.github.gumtreediff.tree.AbstractVersionedTree;
import com.github.gumtreediff.tree.ITree;
import com.github.gumtreediff.tree.VersionedTree;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.math.Fraction;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.maven.shared.invoker.InvocationResult;
import org.apache.maven.shared.utils.cli.CommandLineException;
import org.eclipse.jetty.util.MultiMap;
import org.refactoringminer.api.Refactoring;

import fr.quentin.coevolutionMiner.v2.evolution.EvolutionHandler;
import fr.quentin.coevolutionMiner.v2.evolution.Evolutions;
import fr.quentin.coevolutionMiner.v2.evolution.EvolutionsMiner;
import fr.quentin.coevolutionMiner.v2.evolution.Evolutions.Evolution;
import fr.quentin.coevolutionMiner.v2.evolution.Evolutions.Evolution.DescRange;
import fr.quentin.coevolutionMiner.v2.evolution.miners.GumTreeSpoonMiner;
import fr.quentin.coevolutionMiner.v2.evolution.miners.MixedMiner;
import fr.quentin.coevolutionMiner.v2.evolution.miners.RefactoringMiner;
import fr.quentin.coevolutionMiner.v2.evolution.miners.GumTreeSpoonMiner.EvolutionsAtCommit;
import fr.quentin.coevolutionMiner.v2.evolution.miners.GumTreeSpoonMiner.EvolutionsAtCommit.EvolutionsAtProj;
import fr.quentin.coevolutionMiner.v2.evolution.miners.GumTreeSpoonMiner.EvolutionsMany;
import fr.quentin.coevolutionMiner.v2.impact.ImpactHandler;
import fr.quentin.coevolutionMiner.v2.impact.Impacts;
import fr.quentin.coevolutionMiner.v2.impact.Impacts.Impact;
import fr.quentin.coevolutionMiner.v2.sources.Sources;
import fr.quentin.coevolutionMiner.v2.sources.SourcesHandler;
import fr.quentin.coevolutionMiner.v2.sources.Sources.Commit;
import fr.quentin.coevolutionMiner.v2.utils.Iterators2;
import fr.quentin.coevolutionMiner.v2.coevolution.CoEvolutionHandler;
import fr.quentin.coevolutionMiner.v2.coevolution.CoEvolutions;
import fr.quentin.coevolutionMiner.v2.coevolution.CoEvolutionsMiner;
import fr.quentin.coevolutionMiner.v2.coevolution.CoEvolutionsStorage;
import fr.quentin.coevolutionMiner.v2.coevolution.CoEvolutions.CoEvolution;
import fr.quentin.coevolutionMiner.v2.coevolution.miners.EImpact.FailureReport;
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
import fr.quentin.coevolutionMiner.v2.ast.Project.AST.FileSnapshot;
import fr.quentin.coevolutionMiner.v2.ast.Project.AST.FileSnapshot.Range;
import fr.quentin.coevolutionMiner.v2.ast.miners.SpoonMiner;
import fr.quentin.coevolutionMiner.v2.ast.miners.SpoonMiner.ProjectSpoon.SpoonAST;

// CAUTION same limitation as MyImpactsMiner
public class MyCoEvolutionsMiner implements CoEvolutionsMiner {

    static Logger logger = Logger.getLogger(MyCoEvolutionsMiner.class.getName());

    private final class CoEvolutionsManyCommit extends CoEvolutions {
        private final Set<CoEvolution> coevolutions = new HashSet<>();
        private final Set<EImpact> eImpacts = new HashSet<>();
        private final Set<ImmutablePair<Range, EImpact.FailureReport>> initialTests = new HashSet<>();

        CoEvolutionsManyCommit(Specifier spec) {
            super(spec);
        }

        @Override
        public JsonElement toJson() {
            return new JsonObject();
        }

        @Override
        public Set<CoEvolution> getCoEvolutions() {
            return Collections.unmodifiableSet(coevolutions);
        }

        public void add(CoEvolutionsExtension currCoevolutions) {
            coevolutions.addAll(currCoevolutions.getCoEvolutions());
            eImpacts.addAll(currCoevolutions.getEImpacts());
            initialTests.addAll(currCoevolutions.getInitialTests());
        }

        @Override
        public Set<EImpact> getEImpacts() {
            return eImpacts;
        }

        @Override
        public Set<ImmutablePair<Range, EImpact.FailureReport>> getInitialTests() {
            return initialTests;
        }
    }

    private ProjectHandler astHandler;
    private EvolutionHandler evoHandler;
    private CoEvolutions.Specifier spec;

    private SourcesHandler srcHandler;

    private CoEvolutionsStorage store;

    private ImpactHandler impactHandler;

    public MyCoEvolutionsMiner(CoEvolutions.Specifier spec, SourcesHandler srcHandler, ProjectHandler astHandler,
            EvolutionHandler evoHandler, ImpactHandler impactHandler, CoEvolutionsStorage store) {
        this.spec = spec;
        this.srcHandler = srcHandler;
        this.astHandler = astHandler;
        this.evoHandler = evoHandler;
        this.impactHandler = impactHandler;
        this.store = store;
    }

    @Override
    public CoEvolutions compute() {
        assert spec.evoSpec != null : spec;
        try {

            CoEvolutionsManyCommit globalResult = new CoEvolutionsManyCommit(spec);
            Sources sourcesProvider = srcHandler.handle(spec.evoSpec.sources, "JGit");
            String initialCommitId = spec.evoSpec.commitIdBefore;
            List<Sources.Commit> commits;
            try {
                commits = sourcesProvider.getCommitsBetween(initialCommitId, spec.evoSpec.commitIdAfter);
                System.out.println(
                        commits.size() > 2 ? "caution computation of coevolutions only between consecutive commits"
                                : commits.size());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            // final Evolutions global_evolutions = evoHandler.handle(spec.evoSpec);
            // Set<Evolution> global_evolutions_set = global_evolutions.toSet();

            // GET COMMIT FROM ID
            // // // NOTE GOING FORWARD in time
            // for (Evolution evolution : evolutions) {
            // String commitIdBefore = evolution.getCommitBefore().getId();
            // perBeforeCommit.get(sourcesProvider.temporaryCreateCommit(commitIdBefore)).add(evolution);
            // // String commitIdAfter = evolution.getCommitIdAfter();
            // }
            SmallMiningException smallSuppressedExc = new SmallMiningException(
                    "Small errors occured during consecutive commits analysis");
            Commit currentCommit = null;
            for (Commit nextCommit : commits) {
                if (currentCommit == null) {
                    currentCommit = nextCommit;
                    continue;
                }
                try {
                    CoEvolutionsExtension currCoevolutions = computeDirectCoevolutions(sourcesProvider, currentCommit,
                            nextCommit);
                    globalResult.add(currCoevolutions);
                } catch (SmallMiningException e) {
                    smallSuppressedExc.addSuppressed(e);
                }
            }
            if (smallSuppressedExc.getSuppressed().length > 0) {
                logger.log(Level.INFO, "Small exceptions", smallSuppressedExc);
            }
            // depr(global_evolutions_set);
            return globalResult;
        } catch (SeverMiningException e) {
            throw new RuntimeException(e);
        }
    }

    // fact with Stirling's approximation
    double factApprox(double n) {
        return n <= 1 ? 1 : Math.sqrt(2 * Math.PI * n) * Math.pow(n / Math.E, n);
    }

    // TODO go through projects modules recusively
    private CoEvolutionsExtension computeDirectCoevolutions(Sources sourcesProvider, Commit currentCommit,
            Commit nextCommit) throws SmallMiningException, SeverMiningException {

        Project.Specifier<SpoonMiner> before_ast_id = astHandler.buildSpec(spec.evoSpec.sources, currentCommit.getId());
        Project.Specifier<SpoonMiner> after_ast_id = astHandler.buildSpec(spec.evoSpec.sources, nextCommit.getId());

        Evolutions.Specifier currEvoSpecMixed = EvolutionHandler.buildSpec(sourcesProvider.spec, currentCommit.getId(),
                nextCommit.getId(), MixedMiner.class);

        Evolutions.Specifier currEvoSpecGTS = EvolutionHandler.buildSpec(sourcesProvider.spec, currentCommit.getId(),
                nextCommit.getId(), GumTreeSpoonMiner.class);
        Evolutions currentDiff = evoHandler.handle(currEvoSpecGTS);

        Evolutions.Specifier currEvoSpecRM = EvolutionHandler.buildSpec(sourcesProvider.spec, currentCommit.getId(),
                nextCommit.getId(), RefactoringMiner.class);
        Evolutions currentEvolutions = evoHandler.handle(currEvoSpecRM);
        logNaiveCostCoEvoValidation(currentEvolutions);

        Project<CtElement> before_proj = astHandler.handle(before_ast_id);
        if (before_proj.getAst().compilerException != null || !before_proj.getAst().isUsable()) {
            throw new SmallMiningException("Before Code Don't Build", before_proj.getAst().compilerException);
        }
        SpoonAST ast_before = (SpoonAST) before_proj.getAst();

        Project<?> after_proj = astHandler.handle(after_ast_id);
        if (after_proj.getAst().compilerException != null || !after_proj.getAst().isUsable()) {
            throw new SmallMiningException("Code after evolutions Don't Build", after_proj.getAst().compilerException);
        }
        SpoonAST ast_after = (SpoonAST) after_proj.getAst();

        // match each refactoring against evolutions mined by gumtree,
        // it allows to easily apply refactrings
        Map<Evolution, Set<Evolution>> atomizedRefactorings = decompose(currentEvolutions, currentDiff);
        // TODO materialize the compostion relation

        for (Entry<Evolution, Set<Evolution>> entry : atomizedRefactorings.entrySet()) {
            Evolution evo = entry.getKey();
            System.out.println("---------------" + evo.getContainer().spec.miner.getSimpleName());
            System.out.println(evo.getType());
            for (Evolution atom : entry.getValue()) {
                if (atom != evo) {
                    System.out.println(atom.getType());
                }
            }
        }

        Impacts currentImpacts = impactHandler.handle(impactHandler.buildSpec(before_ast_id, currEvoSpecMixed));
        Impacts afterImpacts = impactHandler.handle(impactHandler.buildSpec(after_ast_id, currEvoSpecMixed));

        // Map<FileSnapshot, Set<Evolution>> byFileBefore = new HashMap<>();
        // Map<FileSnapshot, Set<Evolution>> byFileAfter = new HashMap<>();
        // Map<Evolution, Set<Evolution>> byEvo = new HashMap<>();
        // Set<Set<Evolution>> smallestGroups = groupEvoByCommonFiles(currentEvolutions,
        // byFileBefore, byFileAfter, byEvo);

        // System.out.println(smallestGroups);

        GumTreeSpoonMiner.EvolutionsMany efz = (GumTreeSpoonMiner.EvolutionsMany) currentDiff;
        EvolutionsAtCommit currEvoAtCommit = efz.getPerCommit(currentCommit.getId(), nextCommit.getId());
        // efz.getDiff(before_ast_id, after_ast_id, "").getOperationChildren(, arg1); //
        // TODO
        Map<Project, Set<Entry<Range, Set<Object>>>> impactedTestsByProject = new HashMap<>();
        for (Entry<Range, Set<Object>> impactedTests : currentImpacts.getImpactedTests().entrySet()) {
            impactedTestsByProject.putIfAbsent(impactedTests.getKey().getFile().getAST().getProject(), new HashSet<>());
            impactedTestsByProject.get(impactedTests.getKey().getFile().getAST().getProject()).add(impactedTests);
        }
        for (Entry<Range, Set<Object>> impactedTests : afterImpacts.getImpactedTests().entrySet()) {
            impactedTestsByProject.putIfAbsent(impactedTests.getKey().getFile().getAST().getProject(), new HashSet<>());
            impactedTestsByProject.get(impactedTests.getKey().getFile().getAST().getProject()).add(impactedTests);
        }
        class InterestingCase {
            public EvolutionsAtProj evolutionsAtProj;
            public Set<Evolution> evosForThisTest;
            public Range testBefore;
            public Range testAfter;
        }
        Map<EvolutionsAtProj, Set<InterestingCase>> interestingCases = new HashMap<>();
        Map<EvolutionsAtProj, Set<Evolution>> evoPerProj = new HashMap<>();
        Map<EvolutionsAtProj, Set<Range>> impactedTestsPerProj = new HashMap<>();
        for (Entry<Project, Set<Entry<Range, Set<Object>>>> entry : impactedTestsByProject.entrySet()) {
            boolean isBefore = entry.getKey().spec.commitId.equals(spec.evoSpec.commitIdBefore);
            Project projectCurr = entry.getKey();
            Project.Specifier projectSpecBefore = isBefore ? projectCurr.spec
                    : currEvoAtCommit.getProjectSpec(projectCurr.spec);
            Project.Specifier projectSpecAfter = isBefore ? currEvoAtCommit.getProjectSpec(projectCurr.spec)
                    : projectCurr.spec;
            EvolutionsAtProj evolutionsAtProj = currEvoAtCommit.getModule(projectSpecBefore, projectSpecAfter);
            Project projectBefore = evolutionsAtProj.getBeforeProj();
            Project projectAfter = evolutionsAtProj.getAfterProj();

            Map<Range, InterestingCase> intInterestingCases = new HashMap<>();
            for (Entry<Range, Set<Object>> impactedTest : entry.getValue()) {
                Range testAfter = isBefore ? null : impactedTest.getKey();
                Range testBefore = isBefore ? impactedTest.getKey() : null;
                // TODO extract functionality
                if (isBefore) {
                    AbstractVersionedTree treeTestBefore = (AbstractVersionedTree) ((CtElement) testBefore
                            .getOriginal()).getMetadata(VersionedTree.MIDDLE_GUMTREE_NODE);
                    AbstractVersionedTree treeTestAfter = treeTestBefore;
                    MyMove mov = (MyMove) treeTestBefore.getMetadata(MyScriptGenerator.MOVE_SRC_ACTION);
                    if (mov != null) {
                        treeTestAfter = mov.getInsert().getTarget();
                    }
                    testAfter = GumTreeSpoonMiner.toRange(projectAfter, treeTestAfter, currEvoAtCommit.afterVersion);
                } else {
                    AbstractVersionedTree treeTestAfter = (AbstractVersionedTree) ((CtElement) testAfter.getOriginal())
                            .getMetadata(VersionedTree.MIDDLE_GUMTREE_NODE);
                    AbstractVersionedTree treeTestBefore = treeTestAfter;
                    MyMove mov = (MyMove) treeTestAfter.getMetadata(MyScriptGenerator.MOVE_DST_ACTION);
                    if (mov != null) {
                        treeTestAfter = mov.getInsert().getTarget();
                    }
                    testBefore = GumTreeSpoonMiner.toRange(projectBefore, treeTestBefore,
                            currEvoAtCommit.beforeVersion);
                }
                Set<Evolutions.Evolution.DescRange> evosInGame = (Set) impactedTest.getValue();

                Set<Evolution> evosForThisTest;
                InterestingCase curr = intInterestingCases.get(testBefore);
                if (curr == null) {
                    curr = new InterestingCase();
                    intInterestingCases.put(testBefore, curr);
                    curr.evosForThisTest = new HashSet<>();
                    curr.testBefore = testBefore;
                    curr.testAfter = testAfter;
                    curr.evolutionsAtProj = evolutionsAtProj;
                } else {
                    if (!testBefore.equals(curr.testBefore)) {
                        throw new RuntimeException("not same test before");
                    }
                }
                evosForThisTest = curr.evosForThisTest;

                for (Evolutions.Evolution.DescRange obj : evosInGame) {
                    Evolution src = ((Evolutions.Evolution.DescRange) obj).getSource();
                    evosForThisTest.add(src);
                    evosForThisTest.addAll(atomizedRefactorings.get(src));
                }

                evoPerProj.putIfAbsent(evolutionsAtProj, new HashSet<>());
                evoPerProj.get(evolutionsAtProj).addAll(evosForThisTest);

                impactedTestsPerProj.putIfAbsent(evolutionsAtProj, new HashSet<>());
                impactedTestsPerProj.get(evolutionsAtProj).add(testBefore);
            }
            if (intInterestingCases.size() > 0) {
                interestingCases.putIfAbsent(evolutionsAtProj, new HashSet<>());
                interestingCases.get(evolutionsAtProj).addAll(intInterestingCases.values());
            }
        }
        // TODO also put the case without impact optimisation (to avoid missing co-evolutions, for now because we ignore to much evo from the code)
        for (EvolutionsAtProj k : interestingCases.keySet()) {
            Set<InterestingCase> tmpIntereSet = new HashSet<>();
            for (InterestingCase c : interestingCases.get(k)) {
                InterestingCase curr = new InterestingCase();
                curr.evosForThisTest = new HashSet<>(k.toSet());
                curr.testBefore = c.testBefore;
                curr.testAfter = c.testAfter;
                curr.evolutionsAtProj = c.evolutionsAtProj;
                tmpIntereSet.add(curr);
            }
            interestingCases.get(k).addAll(tmpIntereSet);
        }

        // Validation phase, by compiling and running tests
        Path path = Paths.get("/tmp/applyResults", getRepoRawPath(), nextCommit.getId(),
                currentCommit.getId(), ""+new Date().getTime());
        Path oriPath = ((SpoonAST) currEvoAtCommit.getRootModule().getBeforeProj().getAst()).rootDir;
        Path afterOriPath = ((SpoonAST) currEvoAtCommit.getRootModule().getAfterProj().getAst()).rootDir;
        FileUtils.deleteQuietly(path.toFile());
        try {
            FileUtils.copyDirectory(oriPath.toFile(), path.toFile());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        CoEvolutions.Specifier coevoSpec = CoEvolutionHandler.buildSpec(sourcesProvider.spec, currEvoSpecMixed);
        CoEvolutionsExtension currCoevolutions = new CoEvolutionsExtension(coevoSpec, currentEvolutions, before_proj,
                after_proj);

        Map<Set<Evolution>, Set<EImpact>> functionalImpacts = new HashMap<>();
        Map<Range, EImpact> initialTestsStatus = new HashMap<>();
        JavaOutputProcessor outputProcessor = new JavaOutputProcessor();

        OutputDestinationHandler outDestHandler = new OutputDestinationHandler() {
            @Override
            public Path getOutputPath(CtModule module, CtPackage pack, CtType type) {
                SourcePosition position = type.getPosition();
                Path path = position.getFile().toPath();
                ImmutableTriple<Path, Path, MavenLauncher.SOURCE_TYPE> srcTriple = (ImmutableTriple<Path, Path, MavenLauncher.SOURCE_TYPE>) position
                        .getCompilationUnit().getMetadata("SourceTypeNRootDirectory");
                return getDefaultOutputDirectory().toPath().resolve(srcTriple.left.relativize(path));
            }

            @Override
            public File getDefaultOutputDirectory() {
                return path.toFile();
            }
        };
        for (EvolutionsAtProj k : interestingCases.keySet()) {

            for (Range initialTest : impactedTestsPerProj.get(k)) {
                EImpact.FailureReport report = null;
                FailureReport resInitial = runValidationCheckers(sourcesProvider, path,
                        ((CtMethod) initialTest.getOriginal()).getDeclaringType().getQualifiedName(),
                        ((CtMethod) initialTest.getOriginal()).getSimpleName(), report);
                EImpact eimpact = new EImpact();
                eimpact.tests.put(initialTest, new ImmutablePair<>(initialTest, report));
                initialTestsStatus.put(initialTest, eimpact);

                // EImpact eimpact = new EImpact();
                // for (Evolution e : t) {
                //     eimpact.evolutions.put(e, ah.ratio(e));
                // }
                // eimpact.tests.put(testBefore, new ImmutablePair<>(testToExec, report));
                // functionalImpacts.putIfAbsent(t, new HashSet<>());
                // functionalImpacts.get(t).add(eimpact);
            }

            for (InterestingCase c : interestingCases.get(k)) {
                // if (c.evosForThisTest.size() > 40) {
                //     logger.info(c.evosForThisTest.size() + "elements for combination is too much");
                //     continue;
                // }
                try (ApplierHelperImpl ah = new ApplierHelperImpl(k, atomizedRefactorings);) { // evoPerProj.get(k)
                    // ah.setTestDirectories(
                    // ((SpoonAST)
                    // k.getBeforeProj().getAst()).launcher.getPomFile().getSourceDirectories().stream()
                    // .map(x -> k.getBeforeProj().getAst().rootDir.relativize(x.toPath()))
                    // .collect(Collectors.toList()));
                    // ah.setSourceDirectories(
                    // ((SpoonAST)
                    // k.getBeforeProj().getAst()).launcher.getPomFile().getTestDirectories().stream()
                    // .map(x -> k.getBeforeProj().getAst().rootDir.relativize(x.toPath()))
                    // .collect(Collectors.toList()));
                    abstract class FunctionalImpactHelper implements Consumer<Set<Evolution>> {

                        public Range testBefore;
                        public Set<Evolution> evosForThisTest;
                        public CtMethod elementTestBefore;
                        public String sigTestBefore;

                    }

                    Factory mFacto = (Factory) k.getMdiff().getMiddle().getMetadata("Factory");
                    mFacto.getEnvironment().setOutputDestinationHandler(outDestHandler);
                    outputProcessor.setFactory(mFacto);
                    Set<Path> initiallyPresent = new HashSet<>();
                    for (CtType type : mFacto.getModel().getAllTypes()) {
                        if (!type.isShadow()) {
                            Path exactPath = outDestHandler.getOutputPath(type.getPackage().getDeclaringModule(), type.getPackage(), type);
                            initiallyPresent.add(exactPath);
                        }
                    }
                    
                    FunctionalImpactHelper consumer = new FunctionalImpactHelper() {
                        @Override
                        public void accept(Set<Evolution> t) {
                            for (Evolution ddd : t) {
                                System.err.println(ddd.getOriginal());
                            }
                            AbstractVersionedTree treeTestBefore = (AbstractVersionedTree) ((CtElement) this.testBefore
                                    .getOriginal()).getMetadata(VersionedTree.MIDDLE_GUMTREE_NODE);
                            CtMethod elementTestAfter = ah.getUpdatedMethod(currEvoAtCommit.afterVersion,
                                    treeTestBefore);
                            String testSig = elementTestAfter.getDeclaringType().getQualifiedName() + "#"
                                    + elementTestAfter.getSimpleName();
                            // String resInitial = initialTestsStatus.get(this.testBefore);
                            String testClassQualName = elementTestAfter.getDeclaringType().getQualifiedName();
                            String testSimpName = elementTestAfter.getSimpleName();
                            // String testSignature = elementTestAfter.getSignature();
                            // boolean isSameTestNameAndPlace = testClassQualName
                            // .equals(elementTestBefore.getDeclaringType().getQualifiedName())
                            // && testSimpName.equals(elementTestBefore.getSimpleName());
                            boolean isSameTestSig = testSig.equals(sigTestBefore);
                            SourcePosition position = elementTestAfter.getPosition();
                            if (position == null || !position.isValidPosition()) {
                                throw new RuntimeException(); // TODO make it less hard, but it shouldn't append anyway
                            }
                            // need to get the new Range corresponding the the test
                            Range testToExec;
                            if (isSameTestSig) {
                                // can point to the original test ?
                                testToExec = testBefore;
                            } else {
                                String elePath = k.getAfterProj().getAst().rootDir
                                        .relativize(position.getFile().toPath()).toString();
                                if (elePath.startsWith("..")) {
                                    elePath = k.getBeforeProj().getAst().rootDir.relativize(position.getFile().toPath())
                                            .toString();
                                }
                                Range testAfter = k.getAfterProj().getRange(elePath, position.getSourceStart(),
                                        position.getSourceEnd());
                                if (testAfter == null)
                                    throw new RuntimeException();
                                testToExec = testAfter;
                                // TODO save that testBefore --> testAfter
                            }
                            // DescRange descRange = (Evolution.DescRange) elementTestAfter
                            // .getMetadata(EvolutionsMiner.METADATA_KEY_EVO);
                            // if (descRange.getTarget().equals(testBefore)) {
                            // }
                            EImpact.FailureReport report = null;

                            report = serializeChangedCode(sourcesProvider, path, outputProcessor, initiallyPresent, mFacto, report);

                            report = runValidationCheckers(sourcesProvider, path, testClassQualName, testSimpName,
                                    report);

                            saveReport(functionalImpacts, ah, t, testToExec, report, testBefore);
                        }

                    };
                    ah.setValidityLauncher(consumer);
                    Range testBefore = c.testBefore;
                    consumer.testBefore = testBefore;
                    consumer.evosForThisTest = c.evosForThisTest;
                    AbstractVersionedTree treeTestBefore = (AbstractVersionedTree) ((CtElement) testBefore
                            .getOriginal()).getMetadata(VersionedTree.MIDDLE_GUMTREE_NODE);
                    CtElement[] ttt = ah.watchApply(currEvoAtCommit.beforeVersion, treeTestBefore,
                            treeTestBefore.getChildren(currEvoAtCommit.beforeVersion).get(0));
                    if (ttt[0] != ttt[1].getParent()) {
                        throw new RuntimeException();
                    }
                    consumer.elementTestBefore = (CtMethod) ttt[0];
                    consumer.sigTestBefore = consumer.elementTestBefore.getDeclaringType().getQualifiedName() + "#"
                            + consumer.elementTestBefore.getSimpleName();
                    // ITree treeTestBefore = (ITree) ((CtElement) testBefore.getOriginal())
                    // .getMetadata(VersionedTree.MIDDLE_GUMTREE_NODE);
                    // CtElement[] testsBefore = ah.watchApply(currEvoAtCommit.beforeVersion,
                    // treeTestBefore);
                    // String resInitial = initialTestsStatus.get(testsBefore[0]);
                    // ah.serialize(path.toFile());
                    // String resBefore = executeTest(sourcesProvider, path,
                    // ((CtMethod) testsBefore[0]).getDeclaringType().getQualifiedName(),
                    // ((CtMethod) testsBefore[0]).getSimpleName());
                    ah.setLeafsActionsLimit(6);
                    ah.applyEvolutions(c.evosForThisTest);

                    // CtElement[] testsAfter = ah.getUpdatedElement(currEvoAtCommit.beforeVersion,
                    // treeTestBefore);
                    // ah.serialize(path.toFile());
                    // String resAfter = executeTest(sourcesProvider, path,
                    // ((CtMethod) testsAfter[0]).getDeclaringType().getQualifiedName(),
                    // ((CtMethod) testsAfter[0]).getSimpleName());

                    // if (resInitial == null && resBefore != null && resAfter == null) {
                    // // this is a coevolution
                    // } else if (resInitial == null && resBefore != null && resAfter != null) {
                    // // there are at least missing resolutions
                    // } else if (resInitial != null && resBefore != null && resAfter == null) {
                    // // repaired the test, but we don't know what made it fail in the first place
                    // } else { // evolutions had no effects on success of considered tests

                    // }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
            currCoevolutions.setInitialResults(initialTestsStatus);
            currCoevolutions.addEImpacts(functionalImpacts);
        }

        // for (Entry<Range, Set<Object>> impactedTest :
        // currentImpacts.getImpactedTests().entrySet()) {
        // Range testBefore = impactedTest.getKey();
        // Set<Evolutions.Evolution.DescRange> evosInGame = (Set)
        // impactedTest.getValue();
        // Set<Project<?>.AST.FileSnapshot.Range> testsAfter = new HashSet<>();
        // Project<?>.AST.FileSnapshot.Range qqqqqq = currentDiff.map(testBefore,
        // after_proj);
        // if (qqqqqq != null) {
        // testsAfter.add(qqqqqq);
        // }
        // for (Evolutions.Evolution.DescRange descR : evosInGame) { // TODO there could
        // be multiple testAfter
        // if (descR.getTarget() == testBefore) {
        // if (descR.getSource().getType().equals("Move Method")) {
        // testsAfter.add(descR.getSource().getAfter().get(0).getTarget());
        // }
        // }
        // }
        // // if (PartiallyInstanciateState) {
        // // Set<String> javaFiles = new HashSet<>();
        // // // TODO add original if not here ?
        // // Set<CtType> reqBefore = ast_before.augmented.needs((CtElement)
        // testBefore.getOriginal());
        // // Set<String> reqBeforeS = typesToRelPaths(reqBefore,
        // ast_before.getProject().spec.relPath.toString());
        // // System.out.println(reqBeforeS);
        // // System.err.println(ast_after);
        // // System.err.println(testsAfter);
        // // System.err.println(ast_after.augmented);
        // // Set<CtType> reqAfter = ast_after.augmented.needs((CtElement)
        // testsAfter.getOriginal());
        // // Set<String> reqAfterS = typesToRelPaths(reqAfter,
        // ast_after.getProject().spec.relPath.toString());

        // // Set<String> reqAdded = new HashSet<>(reqAfterS);
        // // reqAdded.removeAll(reqBeforeS);
        // // addJavaFiles(ast_before, javaFiles);
        // // addJavaFiles(ast_after, javaFiles);
        // // }
        // // set TMP DIR for test
        // // put all non .java from currentCommit and all from reqBefore+current file
        // of
        // // test
        // // compile code ? compile tests ? execute test ? good : half : bad ;
        // // apply all non .java from nextCommit
        // // compile code ? compile tests ? execute test ? good : half : bad ;
        // // apply all from subsets of reqAfter +testAfter file of test
        // Set<Evolution> evosForThisTest = new HashSet<>();
        // for (Evolutions.Evolution.DescRange obj : evosInGame) {
        // Evolution src = ((Evolutions.Evolution.DescRange) obj).getSource();
        // evosForThisTest.addAll(atomizedRefactorings.get(src));
        // }
        // // applyEvolutions(
        // // (GumTreeSpoonMiner.EvolutionsAtCommit)
        // efz.getPerCommit(currentCommit.getId(), nextCommit.getId()),
        // // null, null);
        // for (Project<?>.AST.FileSnapshot.Range testAfter : testsAfter) {
        // // TODO making sure that GTS ⊂ RM
        // // execute testBefore and testAfter
        // }
        // // unapplyEvolutions
        // }
        // oldEnd();
        return currCoevolutions;
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

    // More controlled state, isolate compilation failures, but costly to
    // instanciate
    boolean PartiallyInstanciateState = false;

    private CtType<?> computeTopLevel(CtElement srcNode) {
        CtType<?> topParent = srcNode.getParent(CtType.class);
        if (topParent != null) {
            topParent = topParent.getTopLevelType();
        } else if (srcNode instanceof CtType && ((CtType<?>) srcNode).isTopLevel()) {
        } else {
            logger.warning("did not find a top level on " + srcNode);
        }
        return topParent;
    }

    private void addJavaFiles(SpoonAST ast_before, Set<String> javaFiles) {
        for (java.io.File jf : ast_before.augmented.launcher.getModelBuilder().getInputSources()) {
            if (jf.isFile()) {
                if (jf.isAbsolute()) {
                    javaFiles.add(ast_before.rootDir.relativize(jf.toPath()).toString());
                } else {
                    assert false : jf;
                }
            } else {
                assert false : jf;
            }
        }
    }

    private Set<String> typesToRelPaths(Set<CtType> reqBefore, String root) {
        Set<String> reqBeforeS = new HashSet<>();
        for (CtType t : reqBefore) {
            SourcePosition position = t.getPosition();
            if (position != null && position.isValidPosition()) {
                reqBeforeS.add(Paths.get(root).relativize(position.getFile().toPath()).toString());
            }
        }
        return reqBeforeS;
    }

    private Set<String> getNeededFiles(Impacts currentImpacts, Range testBefore) {
        return null;
    }

    private Map<Evolution, Set<Evolution>> decompose(Evolutions from, Evolutions by) {
        assert from != null;
        assert by != null;
        HashSet<Evolutions.Evolution> notUsed = new HashSet<>(by.toSet());
        Map<Evolution, Set<Evolution>> result = new HashMap<>();

        for (Evolution evolution : Iterators2.createChainIterable(Arrays.asList(from, by))) {
            Set<Evolution> acc = new HashSet<>();
            for (DescRange descRange : evolution.getBefore()) {
                CtElement ele = (CtElement) descRange.getTarget().getOriginal();
                if (ele == null) {
                    logger.warning("no original for" + descRange.getTarget());
                } else {
                    aux(acc, ele);
                }
            }
            for (DescRange descRange : evolution.getAfter()) {
                CtElement ele = (CtElement) descRange.getTarget().getOriginal();
                if (ele == null) {
                    logger.warning("no original for" + descRange.getTarget());
                } else {
                    aux(acc, ele);
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

    private void aux(Set<Evolution> acc, CtElement ele) {
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
            aux(acc, child);
        }
    }

    private Map<Evolution, Set<Evolution>> decomposeOld(Evolutions from, Evolutions by) {
        assert from != null;
        assert by != null;
        Map<Evolution, Set<Evolution>> result = new HashMap<>();

        Map<FileSnapshot, Map<Range, Set<Evolution>>> fromLByFile = new HashMap<>();
        for (Evolution evolution : from) {
            for (DescRange descRange : evolution.getBefore()) {
                fromLByFile.putIfAbsent(descRange.getTarget().getFile(), new HashMap<>());
                fromLByFile.get(descRange.getTarget().getFile()).putIfAbsent(descRange.getTarget(), new HashSet<>());
                fromLByFile.get(descRange.getTarget().getFile()).get(descRange.getTarget()).add(evolution);
            }
            for (DescRange descRange : evolution.getAfter()) {
                fromLByFile.putIfAbsent(descRange.getTarget().getFile(), new HashMap<>());
                fromLByFile.get(descRange.getTarget().getFile()).putIfAbsent(descRange.getTarget(), new HashSet<>());
                fromLByFile.get(descRange.getTarget().getFile()).get(descRange.getTarget()).add(evolution);
            }
        }
        Map<FileSnapshot, Map<Range, Set<Evolution>>> nonUsedByLByFile = new HashMap<>();
        for (Evolution evolution : by) {
            for (DescRange descRange : evolution.getBefore()) {
                nonUsedByLByFile.putIfAbsent(descRange.getTarget().getFile(), new HashMap<>());
                nonUsedByLByFile.get(descRange.getTarget().getFile()).putIfAbsent(descRange.getTarget(),
                        new HashSet<>());
                nonUsedByLByFile.get(descRange.getTarget().getFile()).get(descRange.getTarget()).add(evolution);
            }
            for (DescRange descRange : evolution.getAfter()) {
                nonUsedByLByFile.putIfAbsent(descRange.getTarget().getFile(), new HashMap<>());
                nonUsedByLByFile.get(descRange.getTarget().getFile()).putIfAbsent(descRange.getTarget(),
                        new HashSet<>());
                nonUsedByLByFile.get(descRange.getTarget().getFile()).get(descRange.getTarget()).add(evolution);
            }
        }

        return null;
    }

    private Set<Set<Evolution>> groupEvoByCommonFiles(Evolutions currentEvolutions,
            Map<FileSnapshot, Set<Evolution>> byFileBefore, Map<FileSnapshot, Set<Evolution>> byFileAfter,
            Map<Evolution, Set<Evolution>> byEvo) {
        for (Evolution evo : currentEvolutions) {
            for (DescRange descRange : evo.getBefore()) {
                FileSnapshot f = descRange.getTarget().getFile();
                Set<Evolution> s = byEvo.get(evo);
                s = s == null ? byFileBefore.get(f) : new HashSet<>();
                byEvo.putIfAbsent(evo, s);
                byFileBefore.putIfAbsent(f, s);
                s.add(evo);
            }
            for (DescRange descRange : evo.getAfter()) {
                FileSnapshot f = descRange.getTarget().getFile();
                Set<Evolution> s = byEvo.get(evo);
                s = s == null ? byFileAfter.get(f) : new HashSet<>();
                byEvo.putIfAbsent(evo, s);
                byFileAfter.putIfAbsent(f, s);
                s.add(evo);
            }
        }
        Set<Set<Evolution>> smallestGroups = new HashSet<>();
        smallestGroups.addAll(byEvo.values());
        return smallestGroups;
    }

    private void logNaiveCostCoEvoValidation(Evolutions currentEvolutions) {
        int size = currentEvolutions.toSet().size();
        logger.info("naive: " + size + " evolution ->" + factApprox(size));
    }

    public static class CoEvolutionsExtension extends CoEvolutions {
        private final Set<CoEvolution> coevolutions = new HashSet<>();
        public final Evolutions evolutions;
        public final Project<?> astBefore;
        public final Project<?> astAfter;
        private Map<Range, EImpact> initialTestsStatus; // TODO put it in the db
        private final Set<EImpact> probableCoevoCauses = new HashSet<>(); // merges with prev commits would allow to
                                                                          // find even farther root causes
        private final Set<EImpact> probableCoEvoResolutions = new HashSet<>(); // merge won't help much but need to
                                                                               // confirm
        private final Set<EImpact> partialResolutions = new HashSet<>(); // merge with other commits
        private final Set<EImpact> failfail = new HashSet<>(); // X X
        private final Map<Evolution, Map<Range, EImpact>> probableResolutionsIndex = new HashMap<>();
        private final Map<Evolution, Map<Range, EImpact>> probablyNothing = new HashMap<>();

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
        public Set<ImmutablePair<Range,EImpact.FailureReport>> getInitialTests() {
            Set<ImmutablePair<Range,EImpact.FailureReport>> r = new HashSet<>();
            for (Entry<Range, EImpact> p : initialTestsStatus.entrySet()) {
                r.add(p.getValue().tests.get(p.getKey()));
            }
            return r;
        }

        public void addEImpacts(Map<Set<Evolution>, Set<EImpact>> eImpacts) {
            for (Entry<Set<Evolution>, Set<EImpact>> aaa : eImpacts.entrySet()) {
                for (EImpact ei : aaa.getValue()) {
                    for (Entry<Range, ImmutablePair<Range, EImpact.FailureReport>> bbb : ei.tests.entrySet()) {
                        EImpact.FailureReport resInitial = initialTestsStatus.get(bbb.getKey()).tests
                                .get(bbb.getKey()).right;
                        ImmutablePair<Range, EImpact.FailureReport> ccc = bbb.getValue();
                        EImpact.FailureReport resAfter = ccc.getValue();
                        if (resInitial == null && resAfter == null) { // V V
                            // probable resolution
                            for (Evolution ddd : aaa.getKey()) {
                                probableResolutionsIndex.putIfAbsent(ddd, new HashMap<>());
                                probableResolutionsIndex.get(ddd).put(ccc.getKey(), ei);
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
                                probablyNothing.putIfAbsent(ddd, new HashMap<>());
                                probablyNothing.get(ddd).put(ccc.getKey(), ei);
                                // probableResolutionsIndex.putIfAbsent(ddd, new HashMap<>());
                                // probableResolutionsIndex.get(ddd).put(ccc.getKey(), ei);
                            }
                        } else { // X X
                            failfail.add(ei);
                            // here nothing, but could be part of a any, if considering multiple commits
                            // TODO useless to put in the graph? would need to be assembled with evo from
                            // prev commits until test pass
                            for (Evolution ddd : aaa.getKey()) {
                                probablyNothing.putIfAbsent(ddd, new HashMap<>());
                                probablyNothing.get(ddd).put(ccc.getKey(), ei);
                            }
                        }
                    }
                }

            }
            // compute inclusion of causes in reso then make the coevo
            for (EImpact eimpCauses : probableCoevoCauses) {
                Set<ImmutablePair<Range, EImpact>> possibleReso = null;
                for (Entry<Evolution, Fraction> causeEvos : eimpCauses.evolutions.entrySet()) {
                    Map<Range, EImpact> resos = probableResolutionsIndex.get(causeEvos.getKey());
                    if (resos == null) {
                        break;
                    }
                    if (possibleReso == null) {
                        possibleReso = new HashSet<>();
                        for (Entry<Range, ImmutablePair<Range, EImpact.FailureReport>> testEntry : eimpCauses.tests
                                .entrySet()) {
                            EImpact aaa = resos.get(testEntry.getKey());
                            if (aaa == null) {
                                continue;
                            }
                            possibleReso.add(new ImmutablePair<Range, EImpact>(testEntry.getKey(), aaa));
                        }
                    } else {
                        Set<ImmutablePair<Range, EImpact>> tmp = new HashSet<>();
                        for (Entry<Range, ImmutablePair<Range, EImpact.FailureReport>> testEntry : eimpCauses.tests
                                .entrySet()) {
                            tmp.add(new ImmutablePair<Range, EImpact>(testEntry.getKey(),
                                    resos.get(testEntry.getKey())));
                        }
                        possibleReso.retainAll(tmp);
                    }
                    if (possibleReso == null || possibleReso.isEmpty()) {
                        break;
                    }
                }
                if (possibleReso == null || possibleReso.isEmpty()) {
                    continue;
                }
                // remaining possibleReso are real reso
                for (ImmutablePair<Range, EImpact> eimpResoEntry : possibleReso) {
                    EImpact eimpReso = eimpResoEntry.getValue();
                    Set<Evolution> resolutions = new HashSet<>(eimpReso.evolutions.keySet());
                    resolutions.removeAll(eimpCauses.evolutions.keySet());
                    if (resolutions.size() == 0)
                        continue;
                    CoEvolutionExtension res = new CoEvolutionExtension(new HashSet<>(eimpCauses.evolutions.keySet()),
                            resolutions, Collections.singleton(eimpResoEntry.getKey()),
                            Collections.singleton(eimpResoEntry.getValue().tests.get(eimpResoEntry.getKey()).getKey()));
                    coevolutions.add(res);
                }
            }
        }

        public void setInitialResults(Map<Range, EImpact> initialTestsStatus) {
            this.initialTestsStatus = initialTestsStatus;
        }

        class CoEvolutionExtension extends CoEvolution {
            public CoEvolutionExtension(Set<Evolution> causes, Set<Evolution> resolutions, Set<Range> testsBefore,
                    Set<Range> testsAfter) {
                super(causes, resolutions, testsBefore, testsAfter);
            }
        }

        private Set<Project.AST.FileSnapshot.Range> adjustToTests(Impacts impacts, Evolution.DescRange desc) {
            Set<Impact> tmp = impacts.getPerRootCause().get(desc);
            Set<Project.AST.FileSnapshot.Range> r = new HashSet<>();
            if (tmp != null) {
                for (Impact impact : tmp) {
                    for (Impacts.Impact.DescRange eff : impact.getEffects()) {
                        if (impacts.getAst().getAst().isTest(eff.getTarget())) {
                            r.add(eff.getTarget());
                        }
                    }
                    for (Impacts.Impact.DescRange cau : impact.getCauses()) {
                        if (impacts.getAst().getAst().isTest(cau.getTarget())) {
                            r.add(cau.getTarget());
                        }
                    }
                }
            }
            return r;
        }

        @Override
        public Set<CoEvolution> getCoEvolutions() {
            return Collections.unmodifiableSet(coevolutions);
        }
    }

    private static String executeTest(Sources sourcesProvider, Path path, String declaringClass, String name) {
        try {
            StringBuilder r = new StringBuilder();
            logger.info("Launching test: " + declaringClass + "#" + name);
            InvocationResult res = SourcesHelper.executeTests(path, declaringClass + "#" + name, x -> {
                System.out.println(x);
                r.append(x + "\n");
            });
            CommandLineException executionException = res.getExecutionException();
            if (executionException != null) {
                throw executionException;
            }
            if (res.getExitCode() == 0) {
                return null;
            } else {
                return r.toString();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String compileAllTests(Sources sourcesProvider, Path path) {
        try {
            StringBuilder r = new StringBuilder();
            logger.info("Compiling all tests ");
            InvocationResult res = SourcesHelper.compileAllTests(path, x -> {
                System.out.println(x);
                r.append(x + "\n");
            });
            CommandLineException executionException = res.getExecutionException();
            if (executionException != null) {
                throw executionException;
            }
            if (res.getExitCode() == 0) {
                return null;
            } else {
                return r.toString();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String compileApp(Sources sourcesProvider, Path path) {
        try {
            StringBuilder r = new StringBuilder();
            logger.info("Compiling App");
            InvocationResult res = SourcesHelper.compileApp(path, x -> {
                System.out.println(x);
                r.append(x + "\n");
            });
            CommandLineException executionException = res.getExecutionException();
            if (executionException != null) {
                throw executionException;
            }
            if (res.getExitCode() == 0) {
                return null;
            } else {
                return r.toString();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static EImpact.FailureReport serializeChangedCode(Sources sourcesProvider, Path path,
            JavaOutputProcessor outputProcessor, Set<Path> initialPaths, Factory mFacto, EImpact.FailureReport report) {
        Set<Path> diff = new HashSet<>(initialPaths);
        String res;
        OutputDestinationHandler odh = mFacto.getEnvironment().getOutputDestinationHandler();
        try {
            for (CtType type : mFacto.getModel().getAllTypes()) {
                if (!type.isShadow()) {
                    outputProcessor.createJavaFile(type);
                    Path exactPath = odh.getOutputPath(type.getPackage().getDeclaringModule(), type.getPackage(), type);
                    diff.remove(exactPath);
                }
            }
            for (Path toRemove : diff) {
                FileUtils.deleteQuietly(toRemove.toFile());
            }
        } catch (Exception e) {
            if (report == null) {
                res = compileApp(sourcesProvider, path);
                if (res != null) {
                    report = new EImpact.FailureReport(e.toString(), null,"App compiling");
                }
            }
        }
        return report;
    }

    private static FailureReport runValidationCheckers(Sources sourcesProvider, Path path, String testClassQualName,
            String testSimpName, EImpact.FailureReport report) {
        String res;
        if (report == null) {
            res = compileApp(sourcesProvider, path);
            if (res != null) {
                report = new EImpact.FailureReport(res,null,"App compiling");
            }
        }
        if (report == null) {
            res = compileAllTests(sourcesProvider, path);
            if (res != null) {
                report = new EImpact.FailureReport(res,null,"Tests compiling");
            }
        }
        if (report == null) {
            res = executeTest(sourcesProvider, path, testClassQualName, testSimpName);
            if (res != null) {
                report = new EImpact.FailureReport(res,null,"Tests execution");
            }
        }
        return report;
    }

    private static void saveReport(Map<Set<Evolution>, Set<EImpact>> functionalImpacts, ApplierHelperImpl ah,
            Set<Evolution> t, Range testToExec, EImpact.FailureReport report, Range testBefore) {
        EImpact eimpact = new EImpact();
        for (Evolution e : t) {
            eimpact.evolutions.put(e, ah.ratio(e));
        }
        eimpact.tests.put(testBefore, new ImmutablePair<>(testToExec, report));
        functionalImpacts.putIfAbsent(t, new HashSet<>());
        functionalImpacts.get(t).add(eimpact);
    }

    public class SmallMiningException extends Exception {

        public SmallMiningException(String string, Exception compilerException) {
            super(string, compilerException);
        }

        public SmallMiningException(String string) {
            super(string);
        }

        private static final long serialVersionUID = 6192596956456010689L;

    }

    public class SeverMiningException extends Exception {

        public SeverMiningException(String string) {
            super(string);
        }

        private static final long serialVersionUID = 6192596956456010689L;

    }
}