package fr.quentin;

import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

/**
 * Unit test for spoon model builder.
 */
public class SpoonTest {
    // @Rule
    // public ExpectedException exceptionRule = ExpectedException.none();

    /**
     * Same as fr.quentin.CLI ast --repo
     * https://github.com/quentinLeDilavrec/interacto-java-api.git
     * 5377ad5864cd54e776aa30f690fd84253153677a
     */
    @Test
    public void InteractoModelShouldBuild() {
        CLI.ast("https://github.com/quentinLeDilavrec/interacto-java-api.git",
                "5377ad5864cd54e776aa30f690fd84253153677a");
        /**
         * java.lang.IllegalStateException: Module should be known at
         * org.eclipse.jdt.internal.compiler.batch.CompilationUnit.module(
         * CompilationUnit.java:126)
         * 
         * solved by removing module.info
         */
    }

    @Test
    public void SpoonModelShouldBuild() {

        CLI.ast("https://github.com/INRIA/spoon.git", "4b42324566bdd0da145a647d136a2f555c533978");
        CLI.ast("https://github.com/INRIA/spoon.git", "904fb1e7001a8b686c6956e32c4cc0cdb6e2f80b");
    }

    @Test
    public void TruthModelShouldBuild() {
        CLI.ast("https:// github.com/google/truth.git", "fb7f2fe21d8ca690daabedbd31a0ade99244f99c");
        /*
         * spoon.compiler.ModelBuildingException: The type Platform is already defined
         * at spoon.support.compiler.jdt.JDTBasedSpoonCompiler.reportProblem(
         * JDTBasedSpoonCompiler.java:573)
         */
        CLI.ast("https:// github.com/google/truth.git", "1768840bf1e69892fd2a23776817f620edfed536");
    }

    @Test
    public void antlr4rModelShouldBuild() {
        CLI.ast("https://github.com/antlr/antlr4.git", "53678867ca61ffb4aa79298b40efcc74bebf952c");
        /*
         * evolution des not seem to be in the spoon AST
         */
        CLI.ast("https://github.com/antlr/antlr4.git", "b395127e733b33c27f344695ebf155ecf5edeeab");
    }

    @Test
    public void HiveModelShouldBuild() {
        CLI.ast("https://github.com/apache/hive.git", "42326958148c2558be9c3d4dfe44c9e735704617");
        CLI.ast("https://github.com/apache/hive.git", "240097b78b70172e1cf9bc37876a566ddfb9e115");
    }

    @Test
    public void Neo4jModelShouldBuild() {
        CLI.ast("https://github.com/neo4j/neo4j.git", "5d73d6f87a7e5df53447a26c515ca5632466d374");
        CLI.ast("https://github.com/neo4j/neo4j.git", "021d17c8234904dcb1d54596662352395927fe7b");
    }

    @Test
    public void WildflyModelShouldBuild() {
        CLI.ast("https://github.com/wildfly/wildfly.git", "727e0e0f7e2b75bc13f738d0543a1077cdd4edd8");
        CLI.ast("https://github.com/wildfly/wildfly.git", "4aa2e8746b5492bbc1cf2b36af956cf3b01e40f5");
    }
}
