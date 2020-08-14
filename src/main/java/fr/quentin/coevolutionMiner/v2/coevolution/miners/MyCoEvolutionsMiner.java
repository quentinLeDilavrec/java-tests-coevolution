package fr.quentin.coevolutionMiner.v2.coevolution.miners;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.refactoringminer.api.Refactoring;

import fr.quentin.coevolutionMiner.v2.evolution.EvolutionHandler;
import fr.quentin.coevolutionMiner.v2.evolution.Evolutions;
import fr.quentin.coevolutionMiner.v2.evolution.EvolutionsMiner;
import fr.quentin.coevolutionMiner.v2.evolution.Evolutions.Evolution;
import fr.quentin.coevolutionMiner.v2.evolution.Evolutions.Evolution.DescRange;
import fr.quentin.coevolutionMiner.v2.evolution.miners.GumTreeSpoonMiner;
import fr.quentin.coevolutionMiner.v2.evolution.miners.RefactoringMiner;
import fr.quentin.coevolutionMiner.v2.evolution.miners.GumTreeSpoonMiner.EvolutionsMany;
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
import spoon.MavenLauncher;
import spoon.compiler.Environment.PRETTY_PRINTING_MODE;
import spoon.reflect.cu.SourcePosition;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.path.CtPath;
import spoon.reflect.visitor.Filter;
import spoon.support.JavaOutputProcessor;
import fr.quentin.impactMiner.Position;
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

        for (Entry<Evolution, Set<Evolution>> entry : atomizedRefactorings.entrySet()) {
            Evolution evo = entry.getKey();
            System.out.println("---------------"+ evo.getContainer().spec.miner.getSimpleName());
            System.out.println(evo.getType());
            for (Evolution atom : entry.getValue()) {
                if (atom != evo) {
                    System.out.println(atom.getType());
                }
            }
        }

        Impacts currentImpacts = impactHandler.handle(impactHandler.buildSpec(before_ast_id, currEvoSpecRM));

        // Map<FileSnapshot, Set<Evolution>> byFileBefore = new HashMap<>();
        // Map<FileSnapshot, Set<Evolution>> byFileAfter = new HashMap<>();
        // Map<Evolution, Set<Evolution>> byEvo = new HashMap<>();
        // Set<Set<Evolution>> smallestGroups = groupEvoByCommonFiles(currentEvolutions,
        // byFileBefore, byFileAfter, byEvo);

        // System.out.println(smallestGroups);


        EvolutionsMany efz = ((GumTreeSpoonMiner.EvolutionsMany)currentDiff);
        // efz.getDiff(before_ast_id, after_ast_id, "").getOperationChildren(, arg1); // TODO
        for (Entry<Range, Set<Object>> entry : currentImpacts.getImpactedTests().entrySet()) {
            Range testBefore = entry.getKey();
            Project<?>.AST.FileSnapshot.Range testAfter = currentDiff.map(testBefore, after_proj);
            if (PartiallyInstanciateState) {
                Set<String> javaFiles = new HashSet<>();
                // TODO add original if not here ?
                Set<CtType> reqBefore = ast_before.augmented.needs((CtElement) testBefore.getOriginal());
                Set<String> reqBeforeS = typesToRelPaths(reqBefore, ast_before.getProject().spec.relPath.toString());
                System.out.println(reqBeforeS);
                System.err.println(ast_after);
                System.err.println(testAfter);
                System.err.println(ast_after.augmented);
                Set<CtType> reqAfter = ast_after.augmented.needs((CtElement) testAfter.getOriginal());
                Set<String> reqAfterS = typesToRelPaths(reqAfter, ast_after.getProject().spec.relPath.toString());

                Set<String> reqAdded = new HashSet<>(reqAfterS);
                reqAdded.removeAll(reqBeforeS);
                addJavaFiles(ast_before, javaFiles);
                addJavaFiles(ast_after, javaFiles);
            }
            // set TMP DIR for test
            // put all non .java from currentCommit and all from reqBefore+current file of
            // test
            // compile code ? compile tests ? execute test ? good : half : bad ;
            // apply all non .java from nextCommit
            // compile code ? compile tests ? execute test ? good : half : bad ;
            // apply all from subsets of reqAfter +testAfter file of test
            Set<Object> evoInGame = entry.getValue();
            Set<Evolution> evosForThisTest = new HashSet<>();
            for (Entry<Evolution, Set<Evolution>> aaa : atomizedRefactorings.entrySet()) {
                if (evoInGame.contains(aaa.getKey())) {
                    evosForThisTest.addAll(aaa.getValue());
                }
            }
            // TODO making sure that GTS ⊂ RM
            applyEvolutions(ast_before, ast_after, evosForThisTest);
        }
        // TODO review + remove rest

        // Compile needed code and tests for each test potentially containing coevos
        Exception beforeTestsCompileException = compileAllTests(sourcesProvider, before_proj.getAst().rootDir);
        if (beforeTestsCompileException != null) {
            throw new SmallMiningException("Before Tests Don't Build", beforeTestsCompileException);
        }

        Impacts afterImpacts = impactHandler.handle(impactHandler.buildSpec(after_ast_id, currEvoSpecRM));
        CoEvolutions.Specifier coevoSpec = CoEvolutionHandler.buildSpec(sourcesProvider.spec, currEvoSpecRM);
        CoEvolutionsExtension currCoevolutions1 = new CoEvolutionsExtension(coevoSpec, currentEvolutions, before_proj,
                after_proj);
        MyCoEvolutionsMiner.CoEvolutionsExtension.Builder coevoBuilder = currCoevolutions1.createBuilder();
        coevoBuilder.setImpactsAfter(afterImpacts);
        // store.construct(coevoBuilder, currentImpacts.getImpactedTests());
        Set<CoEvolution> toValidate = new HashSet<>();
        for (CoEvolution entry : currCoevolutions1.getUnvalidated()) {
            // TODO loop on tests before to make checks with multiple set of properties
            Project.AST.FileSnapshot.Range posBefore = null;
            for (Project.AST.FileSnapshot.Range aefgzf : entry.getTestsBefore()) {
                posBefore = aefgzf;
                break;
            }

            CtMethod<?> testsBefore = (CtMethod<?>) before_proj.getAst().getOriginal(posBefore);
            Exception resultTestBefore = executeTest(sourcesProvider, before_proj.getAst().rootDir,
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
            CtMethod<?> testsAfter = (CtMethod<?>) after_proj.getAst().getOriginal(posAfter);
            if (resultTestBefore != null) {
                // TODO "mvn test "+ test.get(0).getDeclaringType() + "$" +
                // test.get(0).getSimpleName();
                Exception resultTestAfter = executeTest(sourcesProvider, after_proj.getAst().rootDir,
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
                    Exception resultTestAfter = executeTest(sourcesProvider, after_proj.getAst().rootDir,
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

    // More controlled state, isolate compilation failures, but costly to
    // instanciate
    boolean PartiallyInstanciateState = false;

    private void applyEvolutions(SpoonAST ast_before, SpoonAST ast_after, Set<Evolution> set) {
        MavenLauncher launcher = ast_before.augmented.launcher;
        JavaOutputProcessor outWriter = launcher.createOutputWriter();
        outWriter.getEnvironment().setSourceOutputDirectory(Paths.get("/tmp/").toFile()); // TODO !!!!
        outWriter.getEnvironment().setPrettyPrintingMode(PRETTY_PRINTING_MODE.AUTOIMPORT);
        Map<String, CtType<?>> cloned = new HashMap<>();
        for (Entry<String, CtType<?>> entry : ast_before.augmented.getTypesIndexByFileName().entrySet()) {
            cloned.put(entry.getKey(), entry.getValue().clone());
        }
        applyEvolutions(set, cloned);
        for (CtType<?> type : cloned.values()) {
            type.updateAllParentsBelow();
            outWriter.createJavaFile(type);
        }
    }

    private void applyEvolutions(Set<Evolution> set, Map<String, CtType<?>> cloned) {
        for (Evolution evo : set) {
            Object _ori = evo.getOriginal();
            if (_ori == null) {
                logger.warning("no origin for" + evo);
            } else if (_ori instanceof Operation) {
                Operation<?> ori = (Operation<?>) _ori;
                if (_ori instanceof InsertOperation) {
                    CtElement srcNode = ori.getSrcNode();
                    CtElement p = ((InsertOperation) ori).getParent();
                    CtType<?> topParent = computeTopLevel(p);
                    CtPath relPath = p.getPath().relativePath(topParent);
                    String filePath = topParent.getPosition().getFile().getPath();
                    List<CtElement> clonedTargs = relPath.evaluateOn(cloned.get(filePath));
                    for (CtElement targ : clonedTargs) {
                        // targ;
                    }
                } else if (_ori instanceof DeleteOperation) {
                    CtElement srcNode = ori.getSrcNode();
                    CtType<?> topParent = computeTopLevel(srcNode);
                    CtPath relPath = srcNode.getPath().relativePath(topParent);
                    String filePath = topParent.getPosition().getFile().getPath();
                    List<CtElement> clonedTargs = relPath.evaluateOn(cloned.get(filePath));
                    for (CtElement targ : clonedTargs) {
                        targ.delete();
                    }
                } else if (_ori instanceof MoveOperation) {
                    CtElement p = ((MoveOperation)ori).getParent();
                    CtElement srcNode = ori.getSrcNode();
                    CtType<?> topParent = computeTopLevel(srcNode);
                    CtPath relPath = p.getPath().relativePath(topParent);
                    String filePath = topParent.getPosition().getFile().getPath();
                    List<CtElement> clonedTargs = relPath.evaluateOn(cloned.get(filePath));
                    for (CtElement targ : clonedTargs) {
                    }
                } else if (_ori instanceof UpdateOperation) {
                }
            } else if (_ori instanceof Refactoring) {

            }
        }
    }

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

        for (Evolution evolution : from) {
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
            notUsed.removeAll(acc);
            result.put(evolution, acc);
        }
        // promote
        for (Evolution evolution : notUsed) {
            result.put(evolution, Collections.singleton(evolution));
        }
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