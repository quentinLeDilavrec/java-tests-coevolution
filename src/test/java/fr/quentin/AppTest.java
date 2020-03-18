package fr.quentin;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Test;
import gr.uom.java.xmi.diff.CodeRange;
import org.refactoringminer.api.GitHistoryRefactoringMiner;
import org.refactoringminer.api.Refactoring;
import org.refactoringminer.api.RefactoringHandler;
import org.refactoringminer.api.RefactoringType;
import org.refactoringminer.rm1.GitHistoryRefactoringMinerImpl;

import fr.quentin.utils.FilePathFilter;
import fr.quentin.utils.SourcesHelper;
import spoon.MavenLauncher;

/**
 * Unit test for simple App.
 */
public class AppTest {

    /*
     * TODO check if Impacts graph is correctly constructed in the following setup {
     * repo: "https://github.com/INRIA/spoon.git", commitIdBefore:
     * "4b42324566bdd0da145a647d136a2f555c533978", commitIdAfter:
     * "904fb1e7001a8b686c6956e32c4cc0cdb6e2f80b" } in particular for root with
     * hashCode 2122176080 and for chains of length 7.
     */

    /**
     * 
     * @throws Exception
     */
    @Test
    public void case1() throws Exception {
        // String gitURL = "https://github.com/antlr/antlr4.git";
        // String commitId = "b395127e733b33c27f344695ebf155ecf5edeeab";
        String gitURL = "https://github.com/INRIA/spoon.git";
        String commitId = "904fb1e7001a8b686c6956e32c4cc0cdb6e2f80b";

        List<Refactoring> detectedRefactorings = new ArrayList<Refactoring>();
        GitHistoryRefactoringMiner miner = new GitHistoryRefactoringMinerImpl();

        try (SourcesHelper helper = new SourcesHelper(gitURL);) {
            Path path = helper.materializePrev(commitId);
            MavenLauncher launcher = new MavenLauncher(path.toString(), MavenLauncher.SOURCE_TYPE.ALL_SOURCE);
            launcher.getEnvironment().setLevel("INFO");
            launcher.getFactory().getEnvironment().setLevel("INFO");

            // Compile with maven to get deps
            SourcesHelper.prepare(path);

            // Build Spoon model
            launcher.buildModel();

            assertNotEquals("At least one top-level type should exist.", launcher.getFactory().Type().getAll().size(),
                    0);

            try {
                miner.detectBetweenCommits(helper.getRepo(), helper.getBeforeCommit(commitId), commitId,
                        new RefactoringHandler() {
                            @Override
                            public void handle(String commitId, List<Refactoring> refactorings) {
                                detectedRefactorings.addAll(refactorings);
                            }
                        });
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            System.out.println("refactorings");
            System.out.println(detectedRefactorings);

            List<Evolution> processedEvolutions = new ArrayList<>();

            for (Refactoring op : detectedRefactorings) {
                System.out.println(op.getRefactoringType());
                if (op.getRefactoringType().equals(RefactoringType.MOVE_OPERATION)) {
                    List<CodeRange> src = op.leftSide();
                    MoveMethodEvolution tmp = new MoveMethodEvolution(path.toAbsolutePath().toString(), src, op);
                    processedEvolutions.add(tmp);
                }
            }
            ImpactAnalysis l = new ImpactAnalysis(launcher);
            System.out.println("evolutions");
            System.out.println(processedEvolutions);
            int MAX_EVO = 1000;
            List<ImpactChain> imptst1 = l
                    .getImpactedTests(processedEvolutions.subList(0, Math.min(processedEvolutions.size(), MAX_EVO)));
            System.out.println("chains");
            System.out.println(imptst1);
            Impacts x = new Impacts(imptst1);
            System.out.println(x);
        }
    }

    public static void main(String[] args) throws Exception {
        new AppTest().case2();
    }

    /**
     * 
     * @throws Exception
     */
    @Test
    public void case3() throws Exception {
        String gitURL = "https://github.com/quentinLeDilavrec/interacto-java-api.git";
        String commitId = "d022a91c49378cd182d6b1398dad3939164443b4";
        String commitIdBefore = "3bf9a6d0876fc5c99221934a5ecd161ea51204f0";

        List<Refactoring> detectedRefactorings = new ArrayList<Refactoring>();
        GitHistoryRefactoringMiner miner = new GitHistoryRefactoringMinerImpl();

        try (SourcesHelper helper = new SourcesHelper(gitURL);) {
            Path path = helper.materialize(commitIdBefore, new FilePathFilter() {

                @Override
                public boolean isAllowed(String filePath) {
                    if (filePath.equals("src/main/java/module-info.java")) {
                        return false;
                    } else {
                        return true;
                    }
                }
                
            });
            MavenLauncher launcher = new MavenLauncher(path.toString(), MavenLauncher.SOURCE_TYPE.ALL_SOURCE);
            launcher.getEnvironment().setLevel("INFO");
            launcher.getFactory().getEnvironment().setLevel("INFO");

            // Compile with maven to get deps
            SourcesHelper.prepare(path);

            // Build Spoon model
            launcher.buildModel();

            assertNotEquals("At least one top-level type should exist.", launcher.getFactory().Type().getAll().size(),
                    0);

            try {
                miner.detectBetweenCommits(helper.getRepo(), commitIdBefore, commitId,
                        new RefactoringHandler() {
                            @Override
                            public void handle(String commitId, List<Refactoring> refactorings) {
                                detectedRefactorings.addAll(refactorings);
                            }
                        });
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            System.out.println("refactorings");
            System.out.println(detectedRefactorings);

            List<Evolution> processedEvolutions = new ArrayList<>();

            for (Refactoring op : detectedRefactorings) {
                System.out.println(op.getRefactoringType());
                if (op.getRefactoringType().equals(RefactoringType.MOVE_OPERATION)) {
                    List<CodeRange> src = op.leftSide();
                    MoveMethodEvolution tmp = new MoveMethodEvolution(path.toAbsolutePath().toString(), src, op);
                    processedEvolutions.add(tmp);
                }
            }
            for (Refactoring op : detectedRefactorings) {
                System.out.println(op.getRefactoringType());
                    List<CodeRange> src = op.leftSide();
                    MoveMethodEvolution tmp = new MoveMethodEvolution(path.toAbsolutePath().toString(), src, op);
                    processedEvolutions.add(tmp);
            }
            ImpactAnalysis l = new ImpactAnalysis(launcher);
            System.out.println("evolutions");
            System.out.println(processedEvolutions);
            int MAX_EVO = 1000;
            List<ImpactChain> imptst1 = l
                    .getImpactedTests(processedEvolutions.subList(0, Math.min(processedEvolutions.size(), MAX_EVO)));
            System.out.println("chains");
            System.out.println(imptst1);
            Impacts x = new Impacts(imptst1);
            System.out.println(x);
        }
    }
    
    /**
     * 
     * @throws Exception
     */
    @Test
    public void case2() throws Exception {
        // String gitURL = "https://github.com/antlr/antlr4.git";
        // String commitId = "b395127e733b33c27f344695ebf155ecf5edeeab";
        String gitURL = "https://github.com/INRIA/spoon.git";
        String commitId = "904fb1e7001a8b686c6956e32c4cc0cdb6e2f80b";
        String commitIdBefore = "4b42324566bdd0da145a647d136a2f555c533978";


        List<Refactoring> detectedRefactorings = new ArrayList<Refactoring>();
        GitHistoryRefactoringMiner miner = new GitHistoryRefactoringMinerImpl();

        try (SourcesHelper helper = new SourcesHelper(gitURL);) {
            Path path = helper.materialize(commitIdBefore);
            MavenLauncher launcher = new MavenLauncher(path.toString(), MavenLauncher.SOURCE_TYPE.ALL_SOURCE);
            launcher.getEnvironment().setLevel("INFO");
            launcher.getFactory().getEnvironment().setLevel("INFO");

            // Compile with maven to get deps
            SourcesHelper.prepare(path);

            // Build Spoon model
            launcher.buildModel();

            assertNotEquals("At least one top-level type should exist.", launcher.getFactory().Type().getAll().size(),
                    0);

            try {
                miner.detectBetweenCommits(helper.getRepo(), commitIdBefore, commitId,
                        new RefactoringHandler() {
                            @Override
                            public void handle(String commitId, List<Refactoring> refactorings) {
                                detectedRefactorings.addAll(refactorings);
                            }
                        });
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            System.out.println("refactorings");
            System.out.println(detectedRefactorings);

            List<Evolution> processedEvolutions = new ArrayList<>();

            for (Refactoring op : detectedRefactorings) {
                System.out.println(op.getRefactoringType());
                if (op.getRefactoringType().equals(RefactoringType.MOVE_OPERATION)) {
                    List<CodeRange> src = op.leftSide();
                    MoveMethodEvolution tmp = new MoveMethodEvolution(path.toAbsolutePath().toString(), src, op);
                    processedEvolutions.add(tmp);
                }
            }
            for (Refactoring op : detectedRefactorings) {
                System.out.println(op.getRefactoringType());
                    List<CodeRange> src = op.leftSide();
                    OtherEvolution tmp = new OtherEvolution(path.toAbsolutePath().toString(), src, op);
                    processedEvolutions.add(tmp);
            }
            ImpactAnalysis l = new ImpactAnalysis(launcher);
            System.out.println("evolutions");
            System.out.println(processedEvolutions);
            int MAX_EVO = 1000;
            List<ImpactChain> imptst1 = l
                    .getImpactedTests(processedEvolutions.subList(0, Math.min(processedEvolutions.size(), MAX_EVO)));
            System.out.println("chains");
            System.out.println(imptst1);
            Impacts x = new Impacts(imptst1);
            System.out.println(x);
        }
    }
    
    
    static class MoveMethodEvolution implements Evolution {
        Set<Position> impacts = new HashSet<>();
        private Refactoring op;

        MoveMethodEvolution(String root, List<CodeRange> holders, Refactoring op) {
            this.op = op;
            for (CodeRange range : holders) {
                System.out.println("postion");
                System.out.println(Paths.get(root, range.getFilePath()).toString());
                System.out.println(range.getStartOffset());
                System.out.println(range.getEndOffset());
                this.impacts.add(new Position(Paths.get(root, range.getFilePath()).toString(), range.getStartOffset(),
                        range.getEndOffset()));
            }
        }

        @Override
        public Set<Position> getImpactingPositions() {
            return impacts;
        }

    }
    
    static class OtherEvolution implements Evolution {
        Set<Position> impacts = new HashSet<>();
        private Refactoring op;

        OtherEvolution(String root, List<CodeRange> holders, Refactoring op) {
            this.op = op;
            for (CodeRange range : holders) {
                System.out.println("postion");
                System.out.println(Paths.get(root, range.getFilePath()).toString());
                System.out.println(range.getStartOffset());
                System.out.println(range.getEndOffset());
                this.impacts.add(new Position(Paths.get(root, range.getFilePath()).toString(), range.getStartOffset(),
                        range.getEndOffset()));
            }
        }

        @Override
        public Set<Position> getImpactingPositions() {
            return impacts;
        }

    }

}
