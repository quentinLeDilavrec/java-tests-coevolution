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
import fr.quentin.coevolutionMiner.v2.coevolution.miners.MyCoEvolutionsMiner.CoEvolutionsExtension.Builder;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.visitor.Filter;
import fr.quentin.impactMiner.Position;
import fr.quentin.coevolutionMiner.utils.SourcesHelper;
import fr.quentin.coevolutionMiner.v2.ast.Project;
import fr.quentin.coevolutionMiner.v2.ast.ASTHandler;
import fr.quentin.coevolutionMiner.v2.ast.Project.AST.FileSnapshot.Range;

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
    }

    private ASTHandler astHandler;
    private EvolutionHandler evoHandler;
    private CoEvolutions.Specifier spec;

    private SourcesHandler srcHandler;

    private CoEvolutionsStorage store;

    private ImpactHandler impactHandler;

    public MyCoEvolutionsMiner(CoEvolutions.Specifier spec, SourcesHandler srcHandler, ASTHandler astHandler,
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
        Sources sourcesProvider = srcHandler.handle(spec.evoSpec.sources, "JGit");
        Set<Sources.Commit> commits;
        String commitIdInitial = spec.evoSpec.commitIdBefore;
        try {
            commits = sourcesProvider.getCommitsBetween(commitIdInitial, spec.evoSpec.commitIdAfter);
            System.out.println(commits.size() > 2
                    ? "caution computation of coevolutions only between consecutive commits"
                    : commits.size());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        if (spec.evoSpec == null)
            return null;
        final Evolutions evo = evoHandler.handle(spec.evoSpec);

        Set<Evolution> evolutions = evo.toSet();
        CoEvolutionsManyCommit res = new CoEvolutionsManyCommit(spec);

        // AST.Specifier ast_id = astHandler.buildSpec(spec.evoSpec.sources,
        // commitIdInitial);
        // AST astBefore = astHandler.handle(ast_id, "Spoon");
        // Impacts impactsInitial = impactHandler.handle(impactHandler.buildSpec(ast_id,
        // spec.evoSpec));
        // Map<Sources.Commit, List<Evolution>> perBeforeCommit = new HashMap<>();
        // // // NOTE GOING FORWARD in time
        Commit currentCommit = null;
        System.out.println(commitIdInitial);
        System.out.println(spec.evoSpec.commitIdAfter);
        for (Commit commit : commits) {
            System.out.println(commit.getId());
            if (commit.getId().equals(commitIdInitial)){
                currentCommit = commit;
                break;
            }
            // perBeforeCommit.put(commit, new ArrayList<>());
        }
        if (currentCommit == null) {
            throw new RuntimeException("do not find the initial commit");
        }
        // for (Evolution evolution : evolutions) {
        // String commitIdBefore = evolution.getCommitBefore().getId();
        // perBeforeCommit.get(sourcesProvider.temporaryCreateCommit(commitIdBefore)).add(evolution);
        // // String commitIdAfter = evolution.getCommitIdAfter();
        // }
        while (currentCommit.getChildrens().size() > 0) {
            // List<Evolution> currEvolutions = perBeforeCommit.get(currentCommit);
            Commit nextCommit = null;
            for (Commit commit : currentCommit.getChildrens()) {
                if (commits.contains(commit)) {
                    nextCommit = commit;
                    break;
                }
            }
            if (nextCommit == null) {
                throw new RuntimeException("won't find the next commit");
            }
            Evolutions.Specifier currEvoSpec = EvolutionHandler.buildSpec(sourcesProvider.spec, currentCommit.getId(),
                    nextCommit.getId());
            Project.Specifier before_ast_id = astHandler.buildSpec(spec.evoSpec.sources, currentCommit.getId());
            Project before_ast = astHandler.handle(before_ast_id, "Spoon");
            if (before_ast.getAst().compilerException != null) {
                logger.info("Before Code Don't Build");
                continue;
            }
            Exception beforeTestsCompileException = compileAllTests(sourcesProvider, before_ast.getAst().rootDir);
            if (beforeTestsCompileException != null) {
                logger.info("Before Tests Don't Build");
                continue;
            }
            Project.Specifier after_ast_id = astHandler.buildSpec(spec.evoSpec.sources, nextCommit.getId());
            Project after_ast = astHandler.handle(after_ast_id, "Spoon");
            if (after_ast.getAst().compilerException != null) {
                logger.info("Code after evolutions Don't Build");
                continue;
            }
            Evolutions currentEvolutions = evoHandler.handle(currEvoSpec);
            Impacts currentImpacts = impactHandler.handle(impactHandler.buildSpec(before_ast_id, currEvoSpec));
            // NOTE it compute the position of tests after the evolutions
            Impacts afterImpacts = impactHandler.handle(impactHandler.buildSpec(after_ast_id, currEvoSpec));
            CoEvolutions.Specifier coevoSpec = CoEvolutionHandler.buildSpec(sourcesProvider.spec, currEvoSpec);
            CoEvolutionsExtension currCoevolutions = new CoEvolutionsExtension(coevoSpec, currentEvolutions, before_ast,
                    after_ast);
            Builder coevoBuilder = currCoevolutions.createBuilder();
            coevoBuilder.setImpactsAfter(afterImpacts);
            store.construct(coevoBuilder, currentImpacts.getImpactedTests());

            // TODO compile tests separately
            Exception afterTestsCompileException = compileAllTests(sourcesProvider, after_ast.getAst().rootDir);
            if (afterTestsCompileException != null) {
                logger.info("Tests after evolutions Don't Build");
                continue;
            }
            Set<CoEvolution> toValidate = new HashSet<>();
            for (CoEvolution entry : currCoevolutions.getUnvalidated()) {
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
                currCoevolutions.validate(entry);
            }
            System.out.println("unvalidated found");
            System.out.println(currCoevolutions.getUnvalidated().size());
            res.addValidated(currCoevolutions.getValidated());
            res.addUnvalidated(currCoevolutions.getUnvalidated());
            currentCommit = nextCommit;
        }
        for (Evolution evolution : evolutions) {
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

        return res;

    }

    public class CoEvolutionsExtension extends CoEvolutions {
        private final Set<CoEvolution> validatedcoevolutions = new HashSet<>();
        private final Set<CoEvolution> unvalidatedCoevolutions = new HashSet<>();
        public final Evolutions evolutions;
        public final Project astBefore;
        public final Project astAfter;

        private CoEvolutionsExtension(Specifier spec, Evolutions evolutions, Project astBefore, Project astAfter) {
            super(spec);
            this.evolutions = evolutions;
            this.astBefore = astBefore;
            this.astAfter = astAfter;
        }

        public void validate(CoEvolution entry) {
            if (unvalidatedCoevolutions.contains(entry)){
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

            public Project getAstBefore() {
                return CoEvolutionsExtension.this.astBefore;
            }

            public Project getAstAfter() {
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
                    Set<Evolution> directShort, Set<Evolution> directShortAdjusted, Project.AST.FileSnapshot.Range testBefore) {
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

}