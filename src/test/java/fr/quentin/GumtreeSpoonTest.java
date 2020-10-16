package fr.quentin;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingDeque;

import com.github.gumtreediff.actions.model.Action;
import com.github.gumtreediff.actions.model.Delete;
import com.github.gumtreediff.actions.model.Insert;
import com.github.gumtreediff.actions.model.Move;
import com.github.gumtreediff.actions.model.Update;
import com.github.gumtreediff.tree.ITree;

import org.junit.jupiter.api.Test;

import fr.quentin.coevolutionMiner.utils.SourcesHelper;
import fr.quentin.impactMiner.types.Evolution.Before;
import gumtree.spoon.apply.AAction;
import gumtree.spoon.apply.ActionApplier;
import gumtree.spoon.apply.MyUtils;
import gumtree.spoon.builder.SpoonGumTreeBuilder;
import gumtree.spoon.diff.DiffImpl;
import gumtree.spoon.diff.MultiDiffImpl;
import spoon.MavenLauncher;
import spoon.compiler.Environment;
import spoon.compiler.Environment.PRETTY_PRINTING_MODE;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtPackage;
import spoon.reflect.declaration.CtType;
import spoon.reflect.factory.Factory;
import spoon.support.JavaOutputProcessor;
import spoon.support.StandardEnvironment;

/**
 * Unit test for spoon model builder.
 */
class GumtreeSpoonTest {

        public MavenLauncher build(String gitURL, String commitId) throws Exception {
                try (SourcesHelper helper = new SourcesHelper(gitURL);) {
                        Path path = helper.materializePrev(commitId);
                        MavenLauncher launcher = new MavenLauncher(path.toString(),
                                        MavenLauncher.SOURCE_TYPE.ALL_SOURCE);
                        launcher.getEnvironment().setLevel("INFO");
                        launcher.getEnvironment().setCommentEnabled(false);
                        launcher.getFactory().getEnvironment().setLevel("INFO");

                        // Compile with maven to get deps
                        SourcesHelper.prepare(path);

                        // Build Spoon model
                        launcher.buildModel();
                        return launcher;
                }
        }

        public void change(String gitURL, String commitId, String commitIdAfter) throws Exception {
                System.setProperty("nolabel", "true");
                Environment env = new StandardEnvironment();
                spoon.reflect.visitor.PrettyPrinter pp = new spoon.reflect.visitor.DefaultJavaPrettyPrinter(env);
                MavenLauncher before = build(gitURL, commitId);
                MavenLauncher after = build(gitURL, commitIdAfter);
                CtPackage left = before.getModel().getRootPackage();
                CtPackage right = after.getModel().getRootPackage();
                final SpoonGumTreeBuilder scanner = new SpoonGumTreeBuilder();
                ITree srcTree;
                srcTree = scanner.getTree(left);
                MultiDiffImpl mdiff = new MultiDiffImpl(srcTree);
                ITree dstTree = scanner.getTree(right);
                DiffImpl diff = mdiff.compute(scanner.getTreeContext(), dstTree);

                ITree middle = mdiff.getMiddle();
                // System.out.println(MyUtils.toPrettyTree(scanner.getTreeContext(), srcTree));
                // System.out.println(MyUtils.toPrettyTree(scanner.getTreeContext(), dstTree));
                // System.out.println(MyUtils.toPrettyTree(scanner.getTreeContext(), middle));
                List<Action> retryList = new ArrayList<>();
                for (Action action : diff.getActionsList()) {
                        try {
                                if (action instanceof Insert) {
                                        ActionApplier.applyAInsert((Factory) middle.getMetadata("Factory"),
                                                        scanner.getTreeContext(), (Insert & AAction<Insert>) action);
                                } else if (action instanceof Delete) {
                                        ActionApplier.applyADelete((Factory) middle.getMetadata("Factory"),
                                                        scanner.getTreeContext(), (Delete & AAction<Delete>) action);
                                } else if (action instanceof Update) {
                                        ActionApplier.applyAUpdate((Factory) middle.getMetadata("Factory"),
                                                        scanner.getTreeContext(), (Update & AAction<Update>) action);
                                } else if (action instanceof Move) {
                                        ActionApplier.applyAMove((Factory) middle.getMetadata("Factory"),
                                                        scanner.getTreeContext(), (Move & AAction<Move>) action);
                                } else {
                                        throw null;
                                }
                                
                        } catch (gumtree.spoon.apply.WrongAstContextException e) {
                                retryList.add(action);
                        }
                }
                for (Action action : retryList) {
                        if (action instanceof Insert) {
                                ActionApplier.applyAInsert((Factory) middle.getMetadata("Factory"),
                                                scanner.getTreeContext(), (Insert & AAction<Insert>) action);
                        } else if (action instanceof Delete) {
                                ActionApplier.applyADelete((Factory) middle.getMetadata("Factory"),
                                                scanner.getTreeContext(), (Delete & AAction<Delete>) action);
                        } else if (action instanceof Update) {
                                ActionApplier.applyAUpdate((Factory) middle.getMetadata("Factory"),
                                                scanner.getTreeContext(), (Update & AAction<Update>) action);
                        } else if (action instanceof Move) {
                                ActionApplier.applyAMove((Factory) middle.getMetadata("Factory"),
                                                scanner.getTreeContext(), (Move & AAction<Move>) action);
                        } else {
                                throw null;
                        }
                }
                CtElement middleE = null;
                Queue<ITree> tmp = new LinkedBlockingDeque<>();
                tmp.add(middle);

                while (middleE == null) {
                        ITree curr = tmp.poll();
                        middleE = (CtElement) curr.getMetadata(SpoonGumTreeBuilder.SPOON_OBJECT);
                        List<ITree> children = curr.getChildren();
                        tmp.addAll(children);
                }
                for (CtType c : middleE.getFactory().getModel().getAllTypes()) {
                        System.out.println(c);
                }
        }

        @Test
        public void test() throws Exception {
                change("https://github.com/INRIA/spoon.git", "4b42324566bdd0da145a647d136a2f555c533978",
                                "904fb1e7001a8b686c6956e32c4cc0cdb6e2f80b");
        }

        /**
         * Same as fr.quentin.CLI ast --repo
         * https://github.com/quentinLeDilavrec/interacto-java-api.git
         * 5377ad5864cd54e776aa30f690fd84253153677a
         * 
         * @throws Exception
         */
        @Test
        public void InteractoModelShouldBuild() throws Exception {
                MavenLauncher launcher = build("https://github.com/quentinLeDilavrec/interacto-java-api.git",
                                "5377ad5864cd54e776aa30f690fd84253153677a");
                assertNotEquals(launcher.getFactory().Type().getAll().size(), 0,
                                "At least one top-level type should exist.");

                /**
                 * java.lang.IllegalStateException: Module should be known at
                 * org.eclipse.jdt.internal.compiler.batch.CompilationUnit.module(
                 * CompilationUnit.java:126)
                 * 
                 * solved by removing module.info
                 */
        }

        @Test
        public void SpoonModelShouldBuild() throws Exception {

                MavenLauncher launcher = build("https://github.com/INRIA/spoon.git",
                                "4b42324566bdd0da145a647d136a2f555c533978");
                assertNotEquals(launcher.getFactory().Type().getAll().size(), 0,
                                "At least one top-level type should exist.");

                MavenLauncher launcherAfter = build("https://github.com/INRIA/spoon.git",
                                "904fb1e7001a8b686c6956e32c4cc0cdb6e2f80b");
                assertNotEquals(launcherAfter.getFactory().Type().getAll().size(), 0,
                                "At least one top-level type should exist.");

        }

        @Test
        public void TruthModelShouldBuild() throws Exception {
                MavenLauncher launcher = build("https:// github.com/google/truth.git",
                                "fb7f2fe21d8ca690daabedbd31a0ade99244f99c");
                assertNotEquals(launcher.getFactory().Type().getAll().size(), 0,
                                "At least one top-level type should exist.");

                /*
                 * spoon.compiler.ModelBuildingException: The type Platform is already defined
                 * at spoon.support.compiler.jdt.JDTBasedSpoonCompiler.reportProblem(
                 * JDTBasedSpoonCompiler.java:573)
                 */
                MavenLauncher launcherAfter = build("https:// github.com/google/truth.git",
                                "1768840bf1e69892fd2a23776817f620edfed536");
                assertNotEquals(launcherAfter.getFactory().Type().getAll().size(), 0,
                                "At least one top-level type should exist.");

        }

        @Test
        public void antlr4rModelShouldBuild() throws Exception {
                MavenLauncher launcher = build("https://github.com/antlr/antlr4.git",
                                "53678867ca61ffb4aa79298b40efcc74bebf952c");
                assertNotEquals(launcher.getFactory().Type().getAll().size(), 0,
                                "At least one top-level type should exist.");

                /*
                 * evolution des not seem to be in the spoon AST
                 */
                MavenLauncher launcherAfter = build("https://github.com/antlr/antlr4.git",
                                "b395127e733b33c27f344695ebf155ecf5edeeab");
                assertNotEquals(launcherAfter.getFactory().Type().getAll().size(), 0,
                                "At least one top-level type should exist.");

        }

        @Test
        public void HiveModelShouldBuild() throws Exception {
                MavenLauncher launcher = build("https://github.com/apache/hive.git",
                                "42326958148c2558be9c3d4dfe44c9e735704617");
                assertNotEquals(launcher.getFactory().Type().getAll().size(), 0,
                                "At least one top-level type should exist.");

                MavenLauncher launcherAfter = build("https://github.com/apache/hive.git",
                                "240097b78b70172e1cf9bc37876a566ddfb9e115");
                assertNotEquals(launcherAfter.getFactory().Type().getAll().size(), 0,
                                "At least one top-level type should exist.");

        }

        @Test
        public void Neo4jModelShouldBuild() throws Exception {
                MavenLauncher launcher = build("https://github.com/neo4j/neo4j.git",
                                "5d73d6f87a7e5df53447a26c515ca5632466d374");
                assertNotEquals(launcher.getFactory().Type().getAll().size(), 0,
                                "At least one top-level type should exist.");

                MavenLauncher launcherAfter = build("https://github.com/neo4j/neo4j.git",
                                "021d17c8234904dcb1d54596662352395927fe7b");
                assertNotEquals(launcherAfter.getFactory().Type().getAll().size(), 0,
                                "At least one top-level type should exist.");

        }

        @Test
        public void WildflyModelShouldBuild() throws Exception {
                MavenLauncher launcher = build("https://github.com/wildfly/wildfly.git",
                                "727e0e0f7e2b75bc13f738d0543a1077cdd4edd8");
                assertNotEquals(launcher.getFactory().Type().getAll().size(), 0,
                                "At least one top-level type should exist.");

                MavenLauncher launcherAfter = build("https://github.com/wildfly/wildfly.git",
                                "4aa2e8746b5492bbc1cf2b36af956cf3b01e40f5");
                assertNotEquals(launcherAfter.getFactory().Type().getAll().size(), 0,
                                "At least one top-level type should exist.");

        }

        @Test
        public void testOutputProcessor() throws Exception {
                MavenLauncher launcher = new MavenLauncher(
                                "/home/quentin/resources/Versions/INRIA/spoon/77b7b4e0a34e471f44faa304d287445906da3a3e/",
                                MavenLauncher.SOURCE_TYPE.ALL_SOURCE);
                launcher.getEnvironment().setLevel("INFO");
                launcher.getFactory().getEnvironment().setLevel("INFO");

                // Build Spoon model
                launcher.buildModel();

                JavaOutputProcessor outWriter = launcher.createOutputWriter();
                outWriter.getEnvironment().setSourceOutputDirectory(Paths.get("/tmp/").toFile());
                outWriter.getEnvironment().setPrettyPrintingMode(PRETTY_PRINTING_MODE.AUTOIMPORT);
                for (CtType<?> cl : launcher.getModel().getAllTypes()) {
                        System.out.println("outting");
                        outWriter.createJavaFile(cl);
                        break;
                }
        }
}
