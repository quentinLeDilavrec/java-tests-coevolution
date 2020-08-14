package fr.quentin;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import gr.uom.java.xmi.diff.CodeRange;

import org.junit.jupiter.api.Test;
import org.refactoringminer.api.GitHistoryRefactoringMiner;
import org.refactoringminer.api.Refactoring;
import org.refactoringminer.api.RefactoringHandler;
import org.refactoringminer.api.RefactoringType;
import org.refactoringminer.rm1.GitHistoryRefactoringMinerImpl;

import fr.quentin.coevolutionMiner.utils.FilePathFilter;
import fr.quentin.coevolutionMiner.utils.SourcesHelper;
import fr.quentin.impactMiner.AugmentedAST;
import fr.quentin.impactMiner.Evolution;
import fr.quentin.impactMiner.Explorer;
import fr.quentin.impactMiner.ImpactAnalysis;
import fr.quentin.impactMiner.ImpactChain;
import fr.quentin.impactMiner.Impacts;
import fr.quentin.impactMiner.Position;
import spoon.MavenLauncher;

/**
 * Unit test for simple App.
 */
class AppTest {

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
    public void case1Test() throws Exception {
        // String gitURL = "https://github.com/antlr/antlr4.git";
        // String commitId = "b395127e733b33c27f344695ebf155ecf5edeeab";
        String gitURL = "https://github.com/INRIA/spoon.git";
        String commitId = "904fb1e7001a8b686c6956e32c4cc0cdb6e2f80b";

        List<Refactoring> detectedRefactorings = new ArrayList<Refactoring>();
        List<Evolution<Refactoring>> evolutions = new ArrayList<>();
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

            assertNotEquals(launcher.getFactory().Type().getAll().size(), 0,
                    "At least one top-level type should exist.");

            try {
                miner.detectBetweenCommits(helper.getRepo(), helper.getBeforeCommit(commitId), commitId,
                        new RefactoringHandler() {
                            @Override
                            public void handle(String commitId, List<Refactoring> refactorings) {
                                detectedRefactorings.addAll(refactorings);
                                String before;
                                try {
                                    before = helper.getBeforeCommit(commitId);
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                                for (Refactoring op : refactorings) {
                                    if (op.getRefactoringType().equals(RefactoringType.MOVE_OPERATION)) {
                                        MoveMethodEvolution tmp = new MoveMethodEvolution(
                                                path.toAbsolutePath().toString(), op, before, commitId);
                                        evolutions.add(tmp);
                                    }
                                }
                            }
                        });
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            System.out.println("refactorings");
            System.out.println(detectedRefactorings);
            AugmentedAST<MavenLauncher> aug = new AugmentedAST<>(launcher);
            ImpactAnalysis l = new ImpactAnalysis(aug);
            System.out.println("evolutions");
            System.out.println(evolutions);
            int MAX_EVO = 1000;
            Explorer imptst1 = l.getImpactedTests(evolutions.subList(0, Math.min(evolutions.size(), MAX_EVO)));
            System.out.println("chains");
            System.out.println(imptst1);
            Impacts x = new Impacts(imptst1.getFinishedChains(), imptst1.getRedundantChains());
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
        List<Evolution<Refactoring>> evolutions = new ArrayList<>();
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

            assertNotEquals(launcher.getFactory().Type().getAll().size(), 0,
                    "At least one top-level type should exist.");

            try {
                miner.detectBetweenCommits(helper.getRepo(), commitIdBefore, commitId, new RefactoringHandler() {
                    @Override
                    public void handle(String commitId, List<Refactoring> refactorings) {
                        detectedRefactorings.addAll(refactorings);
                        String before;
                        try {
                            before = helper.getBeforeCommit(commitId);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                        for (Refactoring op : refactorings) {
                            if (op.getRefactoringType().equals(RefactoringType.MOVE_OPERATION)) {
                                MoveMethodEvolution tmp = new MoveMethodEvolution(path.toAbsolutePath().toString(), op,
                                        before, commitId);
                                evolutions.add(tmp);
                            }
                        }
                    }
                });
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            System.out.println("refactorings");
            System.out.println(detectedRefactorings);

            AugmentedAST<MavenLauncher> aug = new AugmentedAST<>(launcher);
            ImpactAnalysis l = new ImpactAnalysis(aug);
            System.out.println("evolutions");
            System.out.println(evolutions);
            int MAX_EVO = 1000;
            Explorer imptst1 = l.getImpactedTests(evolutions.subList(0, Math.min(evolutions.size(), MAX_EVO)));
            System.out.println("chains");
            System.out.println(imptst1);
            Impacts x = new Impacts(imptst1.getFinishedChains(), imptst1.getRedundantChains());
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
        List<Evolution<Refactoring>> evolutions = new ArrayList<>();
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

            assertNotEquals(launcher.getFactory().Type().getAll().size(), 0,
                    "At least one top-level type should exist.");

            try {
                miner.detectBetweenCommits(helper.getRepo(), commitIdBefore, commitId, new RefactoringHandler() {
                    @Override
                    public void handle(String commitId, List<Refactoring> refactorings) {
                        detectedRefactorings.addAll(refactorings);
                        String before;
                        try {
                            before = helper.getBeforeCommit(commitId);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                        for (Refactoring op : refactorings) {
                            if (op.getRefactoringType().equals(RefactoringType.MOVE_OPERATION)) {
                                MoveMethodEvolution tmp = new MoveMethodEvolution(path.toAbsolutePath().toString(), op,
                                        before, commitId);
                                evolutions.add(tmp);
                            }
                        }
                    }
                });
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            System.out.println("refactorings");
            System.out.println(detectedRefactorings);
            AugmentedAST<MavenLauncher> aug = new AugmentedAST<>(launcher);
            ImpactAnalysis l = new ImpactAnalysis(aug);
            System.out.println("evolutions");
            System.out.println(evolutions);
            int MAX_EVO = 1000;
            Explorer imptst1 = l.getImpactedTests(evolutions.subList(0, Math.min(evolutions.size(), MAX_EVO)));
            System.out.println("chains");
            System.out.println(imptst1);
            Impacts x = new Impacts(imptst1.getFinishedChains(), imptst1.getRedundantChains());
            System.out.println(x);
        }
    }

    static class MoveMethodEvolution implements Evolution<Refactoring> {
        Set<Position> impacts = new HashSet<>();
        Set<Position> post = new HashSet<>();
        private Refactoring op;
        private String commitIdBefore;
        private String commitIdAfter;

        MoveMethodEvolution(String root, Refactoring op, String commitIdBefore, String commitIdAfter) {
            this.op = op;
            this.commitIdBefore = commitIdBefore;
            this.commitIdAfter = commitIdAfter;
            for (CodeRange range : op.leftSide()) {
                this.impacts.add(new Position(Paths.get(root, range.getFilePath()).toString(), range.getStartOffset(),
                        range.getEndOffset()));
            }
            for (CodeRange range : op.rightSide()) {
                this.post.add(new Position(Paths.get(root, range.getFilePath()).toString(), range.getStartOffset(),
                        range.getEndOffset()));
            }
        }

        @Override
        public Set<Position> getPreEvolutionPositions() {
            return impacts;
        }

        @Override
        public Set<Position> getPostEvolutionPositions() {
            return post;
        }

        @Override
        public Refactoring getOriginal() {
            return op;
        }

        @Override
        public String getCommitIdBefore() {
            return commitIdBefore;
        }

        @Override
        public String getCommitIdAfter() {
            return commitIdAfter;
        }
    }

    static class OtherEvolution implements Evolution<Refactoring> {
        Set<Position> impacts = new HashSet<>();
        Set<Position> post = new HashSet<>();
        private Refactoring op;
        private String commitIdBefore;
        private String commitIdAfter;

        OtherEvolution(String root, Refactoring op, String commitIdBefore, String commitIdAfter) {
            this.op = op;
            this.commitIdBefore = commitIdBefore;
            this.commitIdAfter = commitIdAfter;
            for (CodeRange range : op.leftSide()) {
                this.impacts.add(new Position(Paths.get(root, range.getFilePath()).toString(), range.getStartOffset(),
                        range.getEndOffset()));
            }
            for (CodeRange range : op.rightSide()) {
                this.post.add(new Position(Paths.get(root, range.getFilePath()).toString(), range.getStartOffset(),
                        range.getEndOffset()));
            }
        }

        @Override
        public Set<Position> getPreEvolutionPositions() {
            return impacts;
        }

        @Override
        public Set<Position> getPostEvolutionPositions() {
            return post;
        }

        @Override
        public Refactoring getOriginal() {
            return op;
        }

        @Override
        public String getCommitIdBefore() {
            return commitIdBefore;
        }

        @Override
        public String getCommitIdAfter() {
            return commitIdAfter;
        }
    }

}
