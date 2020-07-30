package fr.quentin.coevolutionMiner.v2.coevolution.miners;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import org.refactoringminer.api.Refactoring;

import fr.quentin.coevolutionMiner.v2.evolution.EvolutionHandler;
import fr.quentin.coevolutionMiner.v2.evolution.Evolutions;
import fr.quentin.coevolutionMiner.v2.evolution.Evolutions.Evolution;
import fr.quentin.coevolutionMiner.v2.evolution.Evolutions.Evolution.DescRange;
import fr.quentin.coevolutionMiner.v2.evolution.miners.GumTreeSpoonMiner;
import fr.quentin.coevolutionMiner.v2.evolution.miners.RefactoringMiner;
import fr.quentin.coevolutionMiner.v2.impact.ImpactHandler;
import fr.quentin.coevolutionMiner.v2.impact.Impacts;
import fr.quentin.coevolutionMiner.v2.impact.Impacts.Impact;
import fr.quentin.coevolutionMiner.v2.sources.Sources;
import fr.quentin.coevolutionMiner.v2.sources.SourcesHandler;
import fr.quentin.coevolutionMiner.v2.sources.Sources.Commit;
import fr.quentin.coevolutionMiner.v2.coevolution.CoEvolutionHandler;
import fr.quentin.coevolutionMiner.v2.coevolution.CoEvolutions;
import fr.quentin.coevolutionMiner.v2.coevolution.CoEvolutionsMiner;
import fr.quentin.coevolutionMiner.v2.coevolution.CoEvolutionsStorage;
import fr.quentin.coevolutionMiner.v2.coevolution.CoEvolutions.CoEvolution;
import fr.quentin.coevolutionMiner.v2.coevolution.miners.MyCoEvolutionsMiner.CoEvolutionsExtension;
import fr.quentin.coevolutionMiner.v2.coevolution.miners.MyCoEvolutionsMiner.CoEvolutionsExtension.Builder;
import spoon.reflect.cu.SourcePosition;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.visitor.Filter;
import fr.quentin.impactMiner.Position;
import fr.quentin.coevolutionMiner.utils.SourcesHelper;
import fr.quentin.coevolutionMiner.v2.ast.Project;
import fr.quentin.coevolutionMiner.v2.ast.ProjectHandler;
import fr.quentin.coevolutionMiner.v2.ast.Project.AST.FileSnapshot.Range;
import fr.quentin.coevolutionMiner.v2.ast.miners.SpoonMiner;

// CAUTION same limitation as MyImpactsMiner
public class MyCoEvolutionsMiner implements CoEvolutionsMiner {

    Logger logger = Logger.getLogger(MyCoEvolutionsMiner.class.getName());

    private final class CoEvolutionsManyCommit extends CoEvolutions {
        private final Set<CoEvolution> validatedcoevolutions = new HashSet<>();
        private final Set<CoEvolution> unvalidatedCoevolutions = new HashSet<>();

        CoEvolutionsManyCommit(Specifier spec) {
            super(spec);
        }

        void addValidated(Set<CoEvolution> set) {
            validatedcoevolutions.addAll(set);
        }

        void addUnvalidated(Set<CoEvolution> set) {
            unvalidatedCoevolutions.addAll(set);
        }

        @Override
        public Set<CoEvolution> getValidated() {
            return validatedcoevolutions;
        }

        @Override
        public Set<CoEvolution> getUnvalidated() {
            return unvalidatedCoevolutions;
        }

        @Override
        public JsonElement toJson() {
            return new JsonObject();
        }

        public void add(CoEvolutionsExtension currCoevolutions) {
            addValidated(currCoevolutions.getValidated());
            addUnvalidated(currCoevolutions.getUnvalidated());
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

    class CoEvoManyCommitBuilder {
        CoEvoManyCommitBuilder() {

        }
    }

    @Override
    public CoEvolutions compute() {
        assert spec.evoSpec != null : spec;
        try {

            CoEvolutionsManyCommit globalResult = new CoEvolutionsManyCommit(spec);
            Sources sourcesProvider = srcHandler.handle(spec.evoSpec.sources, "JGit");
            String initialCommitId = spec.evoSpec.commitIdBefore;
            Set<Sources.Commit> commits;
            try {
                commits = sourcesProvider.getCommitsBetween(initialCommitId, spec.evoSpec.commitIdAfter);
                System.out.println(
                        commits.size() > 2 ? "caution computation of coevolutions only between consecutive commits"
                                : commits.size());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            final Evolutions global_evolutions = evoHandler.handle(spec.evoSpec);
            Set<Evolution> global_evolutions_set = global_evolutions.toSet();

            // GET COMMIT FROM ID
            // // // NOTE GOING FORWARD in time
            Commit currentCommit = findCorrespondingCommit(commits, initialCommitId);
            // for (Evolution evolution : evolutions) {
            // String commitIdBefore = evolution.getCommitBefore().getId();
            // perBeforeCommit.get(sourcesProvider.temporaryCreateCommit(commitIdBefore)).add(evolution);
            // // String commitIdAfter = evolution.getCommitIdAfter();
            // }
            SmallMiningException smallSuppressedExc = new SmallMiningException(
                    "Small errors occured during consecutive commits analysis");

            while (currentCommit.getChildrens().size() > 0) {
                Commit nextCommit = findNextCommit(commits, currentCommit);
                try {
                    CoEvolutionsExtension currCoevolutions = computeDirectCoevolutions(sourcesProvider, currentCommit,
                            nextCommit);
                    globalResult.add(currCoevolutions);
                } catch (SmallMiningException e) {
                    smallSuppressedExc.addSuppressed(e);
                }
                currentCommit = nextCommit;
            }
            if (smallSuppressedExc.getSuppressed().length > 0) {
                logger.log(Level.INFO, "Small exceptions", smallSuppressedExc);
            }
            depr(global_evolutions_set);
            return globalResult;
        } catch (SeverMiningException e) {
            throw new RuntimeException(e);
        }
    }

    private CoEvolutionsExtension computeDirectCoevolutions(Sources sourcesProvider, Commit currentCommit,
            Commit nextCommit) throws SmallMiningException, SeverMiningException {
        // List<Evolution> currEvolutions = perBeforeCommit.get(currentCommit);
        // GET THE RIGHT NEXT COMMIT (FORWARD IN TIME)
        Evolutions.Specifier currEvoSpecGTS = EvolutionHandler.buildSpec(sourcesProvider.spec, currentCommit.getId(),
                nextCommit.getId(), GumTreeSpoonMiner.class);
        Evolutions currentDiff = evoHandler.handle(currEvoSpecGTS);

        Evolutions.Specifier currEvoSpecRM = EvolutionHandler.buildSpec(sourcesProvider.spec, currentCommit.getId(),
                nextCommit.getId(), RefactoringMiner.class);
        Evolutions currentEvolutions = evoHandler.handle(currEvoSpecRM);

        Project.Specifier<SpoonMiner> before_ast_id = astHandler.buildSpec(spec.evoSpec.sources, currentCommit.getId());
        Project<CtElement> before_ast = astHandler.handle(before_ast_id);
        if (before_ast.getAst().compilerException != null) {
            throw new SmallMiningException("Before Code Don't Build");
        }

        Impacts currentImpacts = impactHandler.handle(impactHandler.buildSpec(before_ast_id, currEvoSpecRM));

        Project.Specifier after_ast_id = astHandler.buildSpec(spec.evoSpec.sources, nextCommit.getId());
        Project<?> after_ast = astHandler.handle(after_ast_id);

        for (Project.AST.FileSnapshot.Range testBefore : currentImpacts.getImpactedTests()) {
            CtElement originalImpactedTest = before_ast.getAst().getOriginal(testBefore);
            CtElement nextStateImpactedTest = currentDiff.map(currentCommit.getId(), nextCommit.getId(),
                    originalImpactedTest, true);
            SourcePosition position = nextStateImpactedTest.getPosition();
            Range testAfter = after_ast.getRange(position.getFile().getPath(),
                    position.getSourceStart(), position.getSourceEnd()); // TODO add original is not here
            Set<File> reqBefore = testBefore.getNeededFiles();
            Set<File> reqAfter = testAfter.getNeededFiles();
            // set TMP DIR for test
            // put all non .java from currentCommit and all from reqBefore+current file of test
            // compile code -> compile tests -> execute test
            //  put all non .java from nextCommit
        }

        // Compile needed code and tests for each test potentially containing coevos
        Exception beforeTestsCompileException = compileAllTests(sourcesProvider, before_ast.getAst().rootDir); 
        if (beforeTestsCompileException != null) {
            throw new SmallMiningException("Before Tests Don't Build");
        }

        if (after_ast.getAst().compilerException != null) {
            throw new SmallMiningException("Code after evolutions Don't Build");
        }
        Impacts afterImpacts = impactHandler.handle(impactHandler.buildSpec(after_ast_id, currEvoSpecRM));
        CoEvolutions.Specifier coevoSpec = CoEvolutionHandler.buildSpec(sourcesProvider.spec, currEvoSpecRM);
        CoEvolutionsExtension currCoevolutions1 = new CoEvolutionsExtension(coevoSpec, currentEvolutions, before_ast,
                after_ast);
        Builder coevoBuilder = currCoevolutions1.createBuilder();
        coevoBuilder.setImpactsAfter(afterImpacts);
        store.construct(coevoBuilder, currentImpacts.getImpactedTests());
        Set<CoEvolution> toValidate = new HashSet<>();
        for (CoEvolution entry : currCoevolutions1.getUnvalidated()) {
            // TODO loop on tests before to make checks with multiple set of properties
            Project.AST.FileSnapshot.Range posBefore = null;
            for (Project.AST.FileSnapshot.Range aefgzf : entry.getTestsBefore()) {
                posBefore = aefgzf;
                break;
            }

            CtMethod<?> testsBefore = (CtMethod<?>) before_ast.getAst().getOriginal(posBefore);
            Exception resultTestBefore = executeTest(sourcesProvider, before_ast.getAst().rootDir,
                    testsBefore.getDeclaringType().getQualifiedName(), testsBefore.getSimpleName());

            // TODO idem
            Project.AST.FileSnapshot.Range posAfter = null;
            for (Project.AST.FileSnapshot.Range aefgzf : entry.getTestsAfter()) {
                posAfter = aefgzf;
                break;
            }
            if (posAfter == null) {
                if (resultTestBefore != null) {
                    logger.info("Test before evo failed");
                } else {
                    logger.info("Test before evo success but was not able to get test in after version");
                }
                continue;
            }
            CtMethod<?> testsAfter = (CtMethod<?>) after_ast.getAst().getOriginal(posAfter);
            if (resultTestBefore != null) {
                // TODO "mvn test "+ test.get(0).getDeclaringType() + "$" +
                // test.get(0).getSimpleName();
                Exception resultTestAfter = executeTest(sourcesProvider, after_ast.getAst().rootDir,
                        testsAfter.getDeclaringType().getQualifiedName(), testsAfter.getSimpleName());
                if (resultTestAfter != null) {
                    logger.info("TestStayedFailed");
                } else {
                    logger.info("TestNowSuccessful");
                    toValidate.add(entry);
                }
            } else {
                // TODO execute a test without its co-evolution by modifying code
                // Exception resultTestAfterWithoutResolutions =
                // executeTestWithoutCoevo(sourcesProvider,nextCommit.id,entry.getValue(),resolutions);
                if (testsAfter != null) {
                    Exception resultTestAfter = executeTest(sourcesProvider, after_ast.getAst().rootDir,
                            testsAfter.getDeclaringType().getQualifiedName(), testsAfter.getSimpleName());
                    if (resultTestAfter != null) {
                        logger.info("TestNowFail");
                    } else {
                        logger.info("TestStayedSuccessful");
                        toValidate.add(entry); // TODO implement the deactivation of evolutions
                        // for now here it does not garantie that this coevolution solves anythis (at
                        // least it does not make it invalid)
                    }
                } else {
                    logger.info("Test after not found");
                    System.out.println(testsAfter);
                    System.out.println(posAfter);
                }
                // logger.info(resultTestAfterWithoutResolutions!=null?resultTestAfter!=null?"TestNotResolved":"TestNowSuccessful":resultTestAfter!=null?"ResolutionMakeTestFail":"GoodResolution");
            }
        }
        for (CoEvolution entry : toValidate) {
            currCoevolutions1.validate(entry);
        }
        System.out.println("unvalidated found");
        System.out.println(currCoevolutions1.getUnvalidated().size());

        CoEvolutionsExtension currCoevolutions = currCoevolutions1;
        return currCoevolutions;
    }

    public class SmallMiningException extends Exception {

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

    private Commit findNextCommit(Set<Sources.Commit> commits, Commit currentCommit) {
        Commit nextCommit = null;
        for (Commit commit : currentCommit.getChildrens()) {
            if (commits.contains(commit)) {
                nextCommit = commit;
                break;
            }
        }
        if (nextCommit == null) {
            throw new RuntimeException("can't find the next commit");
        }
        return nextCommit;
    }

    private Commit findCorrespondingCommit(Set<Sources.Commit> commits, String commitIdInitial) {
        Commit currentCommit = null;
        System.out.println(commitIdInitial);
        System.out.println(spec.evoSpec.commitIdAfter);
        for (Commit commit : commits) {
            System.out.println(commit.getId());
            if (commit.getId().equals(commitIdInitial)) {
                currentCommit = commit;
                break;
            }
            // perBeforeCommit.put(commit, new ArrayList<>());
        }
        if (currentCommit == null) {
            throw new RuntimeException("do not find the initial commit");
        }
        return currentCommit;
    }

    public class CoEvolutionsExtension extends CoEvolutions {
        private final Set<CoEvolution> validatedcoevolutions = new HashSet<>();
        private final Set<CoEvolution> unvalidatedCoevolutions = new HashSet<>();
        public final Evolutions evolutions;
        public final Project<?> astBefore;
        public final Project<?> astAfter;

        private CoEvolutionsExtension(Specifier spec, Evolutions evolutions, Project<?> astBefore,
                Project<?> astAfter) {
            super(spec);
            this.evolutions = evolutions;
            this.astBefore = astBefore;
            this.astAfter = astAfter;
        }

        public void validate(CoEvolution entry) {
            if (unvalidatedCoevolutions.contains(entry)) {
                validatedcoevolutions.add(entry);
                unvalidatedCoevolutions.remove(entry);
            }
        }

        class CoEvolutionExtension extends CoEvolution {

            private Set<Project.AST.FileSnapshot.Range> testsBefore;
            private Set<Project.AST.FileSnapshot.Range> testsAfter;

            public CoEvolutionExtension(Set<Evolution> causes, Set<Evolution> resolutions,
                    Set<Project.AST.FileSnapshot.Range> testsBefore, Set<Range> testsAfter) {
                super();
                this.causes = causes;
                this.resolutions = resolutions;
                this.testsBefore = testsBefore;
                this.testsAfter = testsAfter;
            }

            @Override
            public Set<Project.AST.FileSnapshot.Range> getTestsBefore() {
                return Collections.unmodifiableSet(testsBefore);
            }

            @Override
            public Set<Project.AST.FileSnapshot.Range> getTestsAfter() {
                return Collections.unmodifiableSet(testsAfter);
            }
        }

        @Override
        public Set<CoEvolution> getValidated() {
            return Collections.unmodifiableSet(validatedcoevolutions);
        }

        @Override
        public Set<CoEvolution> getUnvalidated() {
            return Collections.unmodifiableSet(unvalidatedCoevolutions);
        }

        Builder createBuilder() {
            return new Builder();
        }

        public class Builder {

            private Impacts impactsAfter;

            Builder() {
            }

            public void setImpactsAfter(Impacts afterImpacts) {
                this.impactsAfter = afterImpacts;
            }

            public CoEvolutions.Specifier getSpec() {
                return CoEvolutionsExtension.this.spec;
            }

            public Evolutions getEvolutions() {
                return CoEvolutionsExtension.this.evolutions;
            }

            public Project<?> getAstBefore() {
                return CoEvolutionsExtension.this.astBefore;
            }

            public Project<?> getAstAfter() {
                return CoEvolutionsExtension.this.astAfter;
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

            public void addCoevolution(Set<Evolution> throughCall, Set<Evolution> directLong,
                    Set<Evolution> directShort, Set<Evolution> directShortAdjusted,
                    Project.AST.FileSnapshot.Range testBefore) {
                Set<Evolution> direct = new HashSet<>();
                Set<Project.AST.FileSnapshot.Range> testsAfter = new HashSet<>();
                direct.addAll(directLong);
                for (Evolution evo : directLong) {
                    for (Evolution.DescRange desc : evo.getAfter()) {
                        testsAfter.addAll(adjustToTests(impactsAfter, desc));
                    }
                }
                direct.addAll(directShort);
                for (Evolution evo : directShort) {
                    for (Evolution.DescRange desc : evo.getAfter()) {
                        testsAfter.addAll(adjustToTests(impactsAfter, desc));
                    }
                }
                direct.addAll(directShortAdjusted);
                for (Evolution evo : directShortAdjusted) {
                    if (evo.getType().equals("Move Method") || evo.getType().equals("Change Variable Type")
                            || evo.getType().equals("Rename Variable")) {
                        for (Evolution.DescRange desc : evo.getAfter()) {
                            testsAfter.addAll(adjustToTests(impactsAfter, desc));
                        }
                    }
                }
                // TODO coevolutions should be assembled at some point (maybe here) by subset of
                // their evolutions
                CoEvolution tmp = new CoEvolutionExtension(throughCall, direct, Collections.singleton(testBefore),
                        testsAfter);
                unvalidatedCoevolutions.add(tmp);
            }

        }
    }

    // private final class FilterMethod implements Filter<CtMethod<?>> {
    // private final AST.FileSnapshot.Range range;

    // private FilterMethod(AST.FileSnapshot.Range range) {
    // this.range = range;
    // }

    // @Override
    // public boolean matches(CtMethod<?> element) {
    // return
    // range.getFile().getPath().equals(element.getPosition().getFile().toPath().toString())
    // && range.getStart() == element.getPosition().getSourceStart()
    // && range.getEnd() == element.getPosition().getSourceEnd();
    // }
    // }

    private Exception executeTest(Sources sourcesProvider, Path path, String declaringClass, String name) {
        try {
            return SourcesHelper.executeTests(path, declaringClass + "#" + name).getExecutionException();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Exception compileAllTests(Sources sourcesProvider, Path path) {
        try {
            return SourcesHelper.compileAllTests(path).getExecutionException();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void depr(Set<Evolution> global_evolutions_set) {
        for (Evolution evolution : global_evolutions_set) {
            // Path rootBefore = astBefore.rootDir;
            // ImpactAnalysis impactAnaBefore = new ImpactAnalysis(astBefore.launcher);//
            // TODO clone model and/or launcher
            // List<ImpactChain> impactedTests;
            // try {
            // impactedTests = impactAnaBefore.getImpactedTests(evolutions);
            // } catch (IOException e) {
            // throw new RuntimeException(e);
            // }
            // fr.quentin.Impacts impacts = new fr.quentin.Impacts(impactedTests);

            // // TODO more than ana the commit just after the evolution
            // AST astAfter = astHandler.handle(astHandler.buildSpec(spec.evoSpec.sources,
            // commitIdAfter), "Spoon");
            // Path rootAfter = astAfter.rootDir;
            // ImpactAnalysis impactAnaAfter = new ImpactAnalysis(astAfter.launcher);// TODO
            // clone model and/or launcher
            // List<ImpactChain> modifiedTests;
            // try {
            // modifiedTests = impactAnaAfter.getImpactedTestsPostEvolution(evolutions);
            // } catch (IOException e) {
            // throw new RuntimeException(e);
            // }
            // List<ImpactChain> involedEvolutions;
            // // try {
            // // // involedEvolutions =
            // impactAnaAfter.getInvolvedEvolutions(impacts,evolutions); // TODO interpolate
            // results (do it with neo4j?)
            // // } catch (IOException e) {
            // // throw new RuntimeException(e);
            // // }
            // fr.quentin.Impacts impactsAfter = new fr.quentin.Impacts(impactedTests);

            // res.add();
        }
    }

}