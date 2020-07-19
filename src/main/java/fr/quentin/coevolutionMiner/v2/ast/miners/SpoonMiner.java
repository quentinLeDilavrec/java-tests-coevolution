package fr.quentin.coevolutionMiner.v2.ast.miners;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

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
import fr.quentin.coevolutionMiner.v2.sources.Sources.Commit;
import fr.quentin.coevolutionMiner.v2.sources.Sources.Commit.Stats;
import fr.quentin.coevolutionMiner.v2.utils.Utils;
import spoon.MavenLauncher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtType;
import spoon.support.compiler.jdt.JDTBasedSpoonCompiler;

import java.util.logging.Level;
import java.util.logging.Logger;

public class SpoonMiner implements ASTMiner {
    private static Logger logger = Logger.getLogger(SpoonMiner.class.getName());

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
            if (compilerException != null) {
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
                    logger.log(Level.FINE, pb.toString());
                    // System.err.println(pb.toString());
                }
                throw new RuntimeException(e);
            }
            // TODO compute stats
            Commit commit = src.getCommit(spec.commitId);
            computeCounts(launcher, commit);
            computeLOC(path, commit);
            return new AST(spec, commit, path, launcher, compilerException);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 
     * @param launcher model must have been built
     * @param commit
     */
    public static void computeCounts(MavenLauncher launcher, Commit commit) {
        Stats g = commit.getGlobalStats();
        CtModel model = launcher.getModel();
        List<CtType> classes = model.map(x -> x).list(CtType.class);
        g.classes = classes.size();
        List<CtExecutable> executables = model.map(x -> x).list(CtExecutable.class);
        List<CtExecutable> tests = executables.stream().filter(x -> Utils.isTest(x)).collect(Collectors.toList());
        g.executables = executables.size() - tests.size();
        g.tests = tests.size();
    }

    /**
     * 
     * @param path code must be present as files (no git checkout is done by this method)
     * @param commit
     * @throws IOException
     * @throws InterruptedException
     */
    public static void computeLOC(Path path, Commit commit) throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder();
        String[] command = new String[] { "cloc", path.toAbsolutePath().toString(), "--md", "--hide-rate", "--quiet" };
        processBuilder.command(command);
        logger.info("executing subprocess: " + Arrays.asList(command).stream().reduce("", (a, b) -> a + " " + b));
        Process process = processBuilder.start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            Stats g = commit.getGlobalStats();
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
                if (line.startsWith("Java|")) {
                    g.javaLoC = Integer.parseInt(line.substring(line.lastIndexOf("|") + 1));
                } else if (line.startsWith("SUM:|")) {
                    g.loC = Integer.parseInt(line.substring(line.lastIndexOf("|") + 1));
                } else {

                }
            }
        }
        int exitCode = process.waitFor();
        System.out.printf("cloc ended with exitCode %d", exitCode);
    }

}