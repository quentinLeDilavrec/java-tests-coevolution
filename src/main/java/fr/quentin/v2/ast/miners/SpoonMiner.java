package fr.quentin.v2.ast.miners;

import java.nio.file.Path;

import org.eclipse.jdt.core.compiler.CategorizedProblem;

import fr.quentin.utils.SourcesHelper;
import fr.quentin.v2.ast.AST;
import fr.quentin.v2.ast.ASTMiner;
import fr.quentin.v2.ast.AST.Specifier;
import fr.quentin.v2.sources.Sources;
import fr.quentin.v2.sources.SourcesHandler;
import spoon.MavenLauncher;
import spoon.support.compiler.jdt.JDTBasedSpoonCompiler;

public class SpoonMiner implements ASTMiner {

    private Specifier spec;
    private SourcesHandler srcHandler;

    public SpoonMiner(Specifier spec, SourcesHandler srcHandler) {
        this.spec = spec;
        this.srcHandler = srcHandler;
    }

    public AST compute() {
        Sources src = srcHandler.handle(spec.sources, "JGit");
        try (SourcesHelper helper = src.open();) {
            Path path = helper.materialize(spec.commitId);
            // Compile with maven to get deps
            SourcesHelper.prepare(path);
            MavenLauncher launcher = new MavenLauncher(path.toString(), MavenLauncher.SOURCE_TYPE.ALL_SOURCE);
            launcher.getEnvironment().setLevel("INFO");
            launcher.getFactory().getEnvironment().setLevel("INFO");

            try {
                launcher.buildModel();
            } catch (Exception e) {
                for (CategorizedProblem pb : ((JDTBasedSpoonCompiler) launcher.getModelBuilder()).getProblems()) {
                    System.err.println(pb.toString());
                }
                throw new RuntimeException(e);
            }
            // launcher.getModel();
            return new AST(path, launcher);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}