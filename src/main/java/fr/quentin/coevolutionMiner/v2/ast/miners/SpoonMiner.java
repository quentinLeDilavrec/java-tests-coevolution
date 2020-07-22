package fr.quentin.coevolutionMiner.v2.ast.miners;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.maven.shared.invoker.InvocationResult;
import org.apache.maven.shared.utils.cli.CommandLineException;
import org.eclipse.jdt.core.compiler.CategorizedProblem;

import fr.quentin.coevolutionMiner.utils.SourcesHelper;
import fr.quentin.coevolutionMiner.v2.ast.Project;
import fr.quentin.coevolutionMiner.v2.ast.ASTMiner;
import fr.quentin.coevolutionMiner.v2.ast.Project.Specifier;
import fr.quentin.coevolutionMiner.v2.ast.Project.AST.FileSnapshot.Range;
import fr.quentin.coevolutionMiner.v2.sources.Sources;
import fr.quentin.coevolutionMiner.v2.sources.SourcesHandler;
import fr.quentin.coevolutionMiner.v2.sources.Sources.Commit;
import fr.quentin.coevolutionMiner.v2.utils.Utils;
import spoon.Launcher;
import spoon.MavenLauncher;
import spoon.SpoonException;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtType;
import spoon.reflect.visitor.Filter;
import spoon.support.compiler.FileSystemFolder;
import spoon.support.compiler.FilteringFolder;
import spoon.support.compiler.SpoonPom;
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

    public Project compute() {
        Sources src = srcHandler.handle(spec.sources, "JGit");
        try (SourcesHelper helper = src.open();) {
            Path root = helper.materialize(spec.commitId);
            // Compile with maven to get deps
            CommandLineException compilerException = SourcesHelper.prepare(root).getExecutionException();
            if (compilerException != null) {
                compilerException.printStackTrace();
            }
            return extracted(src, root, root, compilerException, null);
        } catch (SpoonException e) {
            throw new RuntimeException(e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Project extracted(Sources src, Path path, Path root, CommandLineException compilerException, SpoonPom spoonPom)
            throws IOException, InterruptedException {
        MavenLauncher launcher = spoonPom != null ? new MavenLauncher(spoonPom, MavenLauncher.SOURCE_TYPE.ALL_SOURCE)
                : new MavenLauncher(path.toString(), MavenLauncher.SOURCE_TYPE.ALL_SOURCE);
        // FilteringFolder resources = new FilteringFolder();
        // resources.addFolder(new FileSystemFolder(path.toString()));
        // for (String string : x) {
        // resources.removeAllThatMatch(Paths.get(path.toString(), string).toString());
        // }
        // launcher.getModelBuilder().addInputSource(resources);
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
        Set<Project> modules = new HashSet<>();
        Commit commit = src.getCommit(spec.commitId);
        String efwrsgw = root.relativize(path).toString();
        Project r = new Project(new Specifier(spec.sources,efwrsgw,spec.commitId,spec.miner), modules, commit, path, launcher, compilerException);
        computeCounts(launcher, r);
        computeLOC(path, r);

        List<SpoonPom> x = launcher.getPomFile().getModules();
        System.out.println(x);
        for (SpoonPom qq : x) {
            modules.add(extracted(src, Paths.get(qq.getFileSystemParent().getAbsolutePath()),root, compilerException, qq));
        }

        return r;
    }

    /**
     * 
     * @param launcher model must have been built
     * @param proj
     */
    public static void computeCounts(Launcher launcher, Project proj) {
        fr.quentin.coevolutionMiner.v2.ast.Stats g = proj.getAst().getGlobalStats();
        CtModel model = launcher.getModel();
        List<CtType> classes = model.getElements(new Filter<CtType>() {
            @Override
            public boolean matches(CtType element) {
                return true;
            }
        });
        g.classes = classes.size();
        List<CtExecutable> executables = model.getElements(new Filter<CtExecutable>() {
            @Override
            public boolean matches(CtExecutable element) {
                return true;
            }
        });
        List<CtExecutable> tests = executables.stream().filter(x -> Utils.isTest(x)).collect(Collectors.toList());
        g.executables = executables.size() - tests.size();
        g.tests = tests.size();
    }

    /**
     * 
     * @param path   code must be present as files (no git checkout is done by this
     *               method)
     * @param proj
     * @throws IOException
     * @throws InterruptedException
     */
    public static void computeLOC(Path path, Project proj) throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder();
        String[] command = new String[] { "cloc", path.toAbsolutePath().toString(), "--md", "--hide-rate", "--quiet" };
        processBuilder.command(command);
        logger.info("executing subprocess: " + Arrays.asList(command).stream().reduce("", (a, b) -> a + " " + b));
        Process process = processBuilder.start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            fr.quentin.coevolutionMiner.v2.ast.Stats g = proj.getAst().getGlobalStats();
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