package fr.quentin.coevolutionMiner.v2.coevolution.miners;

import java.io.File;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Logger;

import com.github.gumtreediff.tree.AbstractVersionedTree;
import com.github.gumtreediff.tree.VersionedTree;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.math.Fraction;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.maven.shared.invoker.InvocationResult;
import org.apache.maven.shared.utils.cli.CommandLineException;

import fr.quentin.coevolutionMiner.utils.SourcesHelper;
import fr.quentin.coevolutionMiner.v2.ast.Project.AST.FileSnapshot.Range;
import fr.quentin.coevolutionMiner.v2.coevolution.miners.EImpact.FailureReport;
import fr.quentin.coevolutionMiner.v2.evolution.Evolutions.Evolution;
import fr.quentin.coevolutionMiner.v2.evolution.miners.GumTreeSpoonMiner.EvolutionsAtCommit.EvolutionsAtProj;
import gumtree.spoon.apply.ApplierHelper;
import spoon.reflect.cu.SourcePosition;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtModule;
import spoon.reflect.declaration.CtPackage;
import spoon.reflect.declaration.CtType;
import spoon.reflect.factory.Factory;
import spoon.support.JavaOutputProcessor;
import spoon.support.OutputDestinationHandler;
import spoon.MavenLauncher;

class FunctionalImpactRunner implements Consumer<Set<Evolution>> {
    private final class OutputDestinationHandlerImpl implements OutputDestinationHandler {
        private final File expDir;

        private OutputDestinationHandlerImpl(File expDir) {
            this.expDir = expDir;
        }

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
            return expDir;
        }
    }

    static Logger logger = Logger.getLogger(FunctionalImpactRunner.class.getName());

    public JavaOutputProcessor outputProcessor;
    public Range testBefore;
    // private ApplierHelper<Evolution> applierHelper;
    private EvolutionsAtProj evolutionsAtProj;
    public Set<Path> initiallyPresent;
    private Factory middleFactory;
    public Set<EImpact> resultingImpacts = new HashSet<>();

    private OutputDestinationHandlerImpl outDestHandler;

    private Range testToExec;

    private String testClassQualName;

    private String testSimpName;

    private String sigTestBefore;

    private ApplierHelper<Evolution> applierHelper;

    private Range testAfter;

    public Factory getMiddleFactory() {
        return middleFactory;
    }

    public EvolutionsAtProj getEvolutionsAtProj() {
        return evolutionsAtProj;
    }

    public void setEvolutionsAtProj(EvolutionsAtProj evolutionsAtProj) {
        this.evolutionsAtProj = evolutionsAtProj;
        middleFactory = (Factory) evolutionsAtProj.getMdiff().getMiddle().getMetadata("Factory");

        Factory mFacto = getMiddleFactory();
        mFacto.getEnvironment().setOutputDestinationHandler(outDestHandler);
        outputProcessor.setFactory(mFacto);

        Set<Path> initiallyPresent = new HashSet<>();
        for (CtType type : mFacto.getModel().getAllTypes()) {
            if (!type.isShadow()) {
                Path exactPath = outDestHandler.getOutputPath(type.getPackage().getDeclaringModule(), type.getPackage(),
                        type);
                initiallyPresent.add(exactPath);
            }
        }
        this.initiallyPresent = initiallyPresent;
    }

    @Override
    public void accept(Set<Evolution> t) {
        EImpact.FailureReport report = serializeChangedCode(outputProcessor, initiallyPresent, middleFactory);

        CtMethod elementTestAfter = applierHelper.getUpdatedMethod(evolutionsAtProj.getEnclosingInstance().afterVersion,
                (AbstractVersionedTree) ((CtElement) this.testBefore.getOriginal())
                        .getMetadata(VersionedTree.MIDDLE_GUMTREE_NODE));
        if (elementTestAfter != null) {
            prepareCheck(elementTestAfter, testBefore, testAfter);

            try {
                report = runValidationCheckers(outputProcessor.getOutputDirectory(), testClassQualName, testSimpName,
                        report);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            this.resultingImpacts.add(saveReport(t, report));
        }
    }

    private static String executeTest(File baseDir, String declaringClass, String name) throws Exception {
        StringBuilder r = new StringBuilder();
        logger.info("Launching test: " + declaringClass + "#" + name);
        InvocationResult res = SourcesHelper.executeTests(baseDir, declaringClass + "#" + name, x -> {
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
    }

    private static String compileAllTests(File baseDir) throws Exception {
        StringBuilder r = new StringBuilder();
        logger.info("Compiling all tests ");
        InvocationResult res = SourcesHelper.compileAllTests(baseDir, x -> {
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
    }

    private static String compileApp(File baseDir) throws Exception {
        StringBuilder r = new StringBuilder();
        logger.info("Compiling App");
        InvocationResult res = SourcesHelper.compileApp(baseDir, x -> {
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
    }

    private static EImpact.FailureReport serializeChangedCode(JavaOutputProcessor outputProcessor,
            Set<Path> initialPaths, Factory mFacto) {
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
            return new EImpact.FailureReport(e.toString(), null, "App compiling");
        }
        return null;
    }

    public static FailureReport runValidationCheckers(File baseDir, String testClassQualName, String testSimpName,
            EImpact.FailureReport report) throws Exception {
        String res;
        if (report == null) {
            res = compileApp(baseDir);
            if (res != null) {
                report = new EImpact.FailureReport(res, null, "App compiling");
            }
        }
        if (report == null) {
            res = compileAllTests(baseDir);
            if (res != null) {
                report = new EImpact.FailureReport(res, null, "Tests compiling");
            }
        }
        if (report == null) {
            res = executeTest(baseDir, testClassQualName, testSimpName);
            if (res != null) {
                report = new EImpact.FailureReport(res, null, "Tests execution");
            }
        }
        return report;
    }

    private EImpact saveReport(Set<Evolution> t, EImpact.FailureReport report) {
        EImpact eimpact = new EImpact();
        for (Evolution e : t) {
            eimpact.evolutions.put(e, applierHelper.evoState.ratio(e));
        }
        eimpact.tests.put(testBefore, new ImmutablePair<>(testToExec, report));
        return eimpact;
    }

    public FunctionalImpactRunner(File expDir) {
        this.outDestHandler = new OutputDestinationHandlerImpl(expDir);
    }

    public void prepareApply(ApplierHelper<Evolution> applierHelper, Range testBefore, Range testAfter) {
        this.applierHelper = applierHelper;
        this.testBefore = testBefore;
        this.testAfter = testAfter;
        AbstractVersionedTree treeTestBefore = (AbstractVersionedTree) ((CtElement) this.testBefore.getOriginal())
                .getMetadata(VersionedTree.MIDDLE_GUMTREE_NODE);
        CtElement[] ttt = applierHelper.watchApply(evolutionsAtProj.getEnclosingInstance().beforeVersion,
                treeTestBefore,
                treeTestBefore.getChildren(evolutionsAtProj.getEnclosingInstance().beforeVersion).get(0));
        if (ttt[0] != ttt[1].getParent()) {
            throw new RuntimeException();
        }
        CtMethod elementTestBefore = (CtMethod) ttt[0];
        this.sigTestBefore = elementTestBefore.getDeclaringType().getQualifiedName() + "#"
                + elementTestBefore.getSimpleName();
    }

    public void prepareCheck(CtMethod elementTestAfter, Range testBefore, Range testAfter) {
        String testSig = elementTestAfter.getDeclaringType().getQualifiedName() + "#"
                + elementTestAfter.getSimpleName();
        this.testClassQualName = elementTestAfter.getDeclaringType().getQualifiedName();
        this.testSimpName = elementTestAfter.getSimpleName();
        boolean isSameTestSig = testSig.equals(sigTestBefore);
        SourcePosition position = elementTestAfter.getPosition();
        if (position == null || !position.isValidPosition()) {
            throw new RuntimeException(); // TODO make it less hard, but rest of exp shouldn't append anyway
        }
        // get the "new" Range corresponding the test
        if (isSameTestSig) {
            this.testToExec = testBefore;
        } else {
            String elePath = evolutionsAtProj.getAfterProj().getAst().rootDir.relativize(position.getFile().toPath())
                    .toString();
            if (elePath.startsWith("..")) {
                elePath = evolutionsAtProj.getBeforeProj().getAst().rootDir.relativize(position.getFile().toPath())
                        .toString();
            }
            Range testAfterB = evolutionsAtProj.getAfterProj().getRange(elePath, position.getSourceStart(),
                    position.getSourceEnd());
            if (testAfterB == null)
                throw new RuntimeException();
            if (testAfter != null && !testAfterB.equals(testAfter))
                logger.info("not sure about wich method is the test after the evolutions are applied.");
            this.testToExec = testAfter;
            // TODO save that: testBefore --> testAfter
        }
    }
}