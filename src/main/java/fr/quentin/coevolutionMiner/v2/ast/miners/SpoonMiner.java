package fr.quentin.coevolutionMiner.v2.ast.miners;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.List;

import org.apache.maven.shared.invoker.InvocationResult;
import org.apache.maven.shared.utils.cli.CommandLineException;
import org.eclipse.jdt.core.compiler.CategorizedProblem;

import fr.quentin.coevolutionMiner.utils.SourcesHelper;
import fr.quentin.coevolutionMiner.v2.ast.AST;
import fr.quentin.coevolutionMiner.v2.ast.ASTMiner;
import fr.quentin.coevolutionMiner.v2.ast.AST.Specifier;
import fr.quentin.coevolutionMiner.v2.ast.AST.FileSnapshot.Range;
import fr.quentin.coevolutionMiner.v2.sources.Sources;
import fr.quentin.coevolutionMiner.v2.sources.SourcesHandler;
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
            CommandLineException compilerException = SourcesHelper.prepare(path).getExecutionException();
            if (compilerException!=null) {
                compilerException.printStackTrace();
            }
            MavenLauncher launcher = new MavenLauncher(path.toString(), MavenLauncher.SOURCE_TYPE.ALL_SOURCE);
            launcher.getEnvironment().setLevel("INFO");
            launcher.getFactory().getEnvironment().setLevel("INFO");
            // List<String> modules = launcher.getPomFile().getModel().getModules();
            // System.out.println(modules.get(0));
            try {
                launcher.buildModel();
            } catch (Exception e) {
                for (CategorizedProblem pb : ((JDTBasedSpoonCompiler) launcher.getModelBuilder()).getProblems()) {
                    System.err.println(pb.toString());
                }
                throw new RuntimeException(e);
            }
            // launcher.getModel();
            // TODO compute stats
            // computeLOC(path);
            src.getCommit(spec.commitId).setGlobalStats(spec.miner, 0, 0, 0, 0);
            return new AST(spec, src.getCommit(spec.commitId), path, launcher, compilerException);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void computeLOC(Path path) throws IOException, InterruptedException {
        Process exec = Runtime.getRuntime()
                .exec(new String[] { "cloc", path.toAbsolutePath().toString(), "--csv", "--hide-rate", "--quiet" });
        exec.waitFor();
        OutputStream loc = exec.getOutputStream();
    }

}