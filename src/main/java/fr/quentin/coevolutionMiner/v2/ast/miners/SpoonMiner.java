package fr.quentin.coevolutionMiner.v2.ast.miners;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.google.gson.stream.JsonReader;

import org.apache.maven.shared.invoker.InvocationResult;
import org.apache.maven.shared.utils.cli.CommandLineException;
import org.eclipse.jdt.core.compiler.CategorizedProblem;

import fr.quentin.coevolutionMiner.utils.MyProperties;
import fr.quentin.coevolutionMiner.utils.SourcesHelper;
// import fr.quentin.coevolutionMiner.utils.SourcesHelper.MavenResult;
import fr.quentin.coevolutionMiner.v2.ast.Project;
import fr.quentin.coevolutionMiner.v2.ast.ProjectMiner;
import fr.quentin.coevolutionMiner.v2.ast.Project.Specifier;
import fr.quentin.coevolutionMiner.v2.ast.Project.AST.FileSnapshot.Range;
import fr.quentin.coevolutionMiner.v2.sources.Sources;
import fr.quentin.coevolutionMiner.v2.sources.SourcesHandler;
import fr.quentin.coevolutionMiner.v2.sources.Sources.Commit;
import fr.quentin.coevolutionMiner.v2.utils.Utils;
import fr.quentin.impactMiner.AugmentedAST;
import spoon.Launcher;
import spoon.MavenLauncher;
import spoon.SpoonException;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtType;
import spoon.reflect.visitor.Filter;
import spoon.support.compiler.FileSystemFolder;
import spoon.support.compiler.FilteringFolder;
import spoon.support.compiler.SpoonPom;
import spoon.support.compiler.jdt.JDTBasedSpoonCompiler;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SpoonMiner implements ProjectMiner<CtElement> {
    public class ProjectSpoon extends Project<CtElement> {
        private ProjectSpoon(Specifier spec, Set<Project<?>> modules, Commit commit, Path rootDir,
                MavenLauncher launcher, Exception compilerException) {
            super(spec, modules, commit, rootDir);
            this.ast = new SpoonAST(rootDir, launcher, compilerException);
        }

        public ProjectSpoon(Specifier spec, Commit commit, Path rootDir, Exception compilerException) {
            super(spec, new HashSet<>(), commit, rootDir);
            this.ast = new SpoonAST(rootDir, null, compilerException);
        }

        public class SpoonAST extends Project<CtElement>.AST {
            public final MavenLauncher launcher;
            public final AugmentedAST<MavenLauncher> augmented;

            SpoonAST(Path rootDir, MavenLauncher launcher, Exception compilerException) {
                super(rootDir, compilerException);
                this.launcher = launcher;
                this.augmented = this.launcher == null ? null : new AugmentedAST<>(launcher);
            }

            @Override
            public boolean isUsable() {
                return this.launcher != null && this.augmented != null;
            }

            public CtType<?> getTop(String path) {
                return augmented.getTop(path);
            }

            @Override
            public boolean contains(File x) {
                return launcher.getModelBuilder().getInputSources().contains(x);
            }

            @Override
            public FileSnapshot.Range getRange(String path, Integer start, Integer end, CtElement original) {
                FileSnapshot.Range range = super.getRange(path, start, end, original);
                CtElement tmp = range.getOriginal();
                if (tmp == null) {
                    tmp = Utils.matchApproxChild((ProjectSpoon.SpoonAST) ast,
                            augmented.rootFolder.relativize(rootDir.resolve(path)).toString(), start, end);
                }
                // TODO also match imports and package decl, through the compilation unit
                if (tmp == null) {
                    logger.warn("no original element found at " + range);
                    return range;
                }
                Object old = range.setOriginal(tmp);
                if (old != null && old != tmp) {
                    throw new RuntimeException("Original value of range should have been unique");
                }
                return range;
            }

        }
    }

    private static Logger logger = LogManager.getLogger();

    private Specifier spec;
    private SourcesHandler srcHandler;

    public SpoonMiner(Specifier spec, SourcesHandler srcHandler) {
        this.spec = spec;
        this.srcHandler = srcHandler;
    }

    public ProjectSpoon compute() {
        logger.traceEntry("Compute()");
        Sources src = srcHandler.handle(spec.sources, "JGit");
        try (SourcesHelper helper = src.open();) {
            Path root = helper.materialize(spec.commitId);
            // Compile with maven

            StringBuilder prepareResult = new StringBuilder();
            InvocationResult prepared = SourcesHelper.prepare(root,
                    Paths.get(MyProperties.getPropValues().getProperty("mavenHome")).toFile(), x -> {
                        prepareResult.append(x + "\n");
                    });
            logger.debug(prepareResult.toString());
            CommandLineException compilerException = prepared.getExecutionException();
            if (compilerException != null) {
                logger.debug("Could not compile the source due to the following error", compilerException);
            }
            return extractedPrecise(src, root, root, null);
        } catch (SpoonException e) {
            throw new RuntimeException(e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            logger.traceExit();
        }
    }

    private ProjectSpoon extracted(Sources src, Path path, Path root, SpoonPom spoonPom)
            throws IOException, InterruptedException, Exception {
        MavenLauncher launcher = spoonPom != null ? new MavenLauncher(spoonPom, MavenLauncher.SOURCE_TYPE.ALL_SOURCE)
                : new MavenLauncher(path.toString(), MavenLauncher.SOURCE_TYPE.ALL_SOURCE);
        // FilteringFolder resources = new FilteringFolder();
        // resources.addFolder(new FileSystemFolder(path.toString()));
        // for (String string : x) {
        // resources.removeAllThatMatch(Paths.get(path.toString(), string).toString());
        // }
        // launcher.getModelBuilder().addInputSource(resources);
        launcher.getEnvironment().setLevel("INFO");
        launcher.getEnvironment().setCommentEnabled(false);
        launcher.getFactory().getEnvironment().setCommentEnabled(false);
        launcher.getFactory().getEnvironment().setLevel("INFO");
        // List<String> modules = launcher.getPomFile().getModel().getModules();
        // System.out.println(modules.get(0));

        StringBuilder prepareResult = new StringBuilder();
        InvocationResult prepared = SourcesHelper.prepare(path, ".", x -> {
            prepareResult.append(x + "\n");
        });
        logger.debug(prepareResult.toString());

        CommandLineException compilerException = prepared.getExecutionException();
        if (compilerException != null) {
            logger.debug("Could not compile the source due to the following error", compilerException);
        }

        try {
            launcher.buildModel();
        } catch (Exception e) {
            for (CategorizedProblem pb : ((JDTBasedSpoonCompiler) launcher.getModelBuilder()).getProblems()) {
                logger.debug(pb.toString());
                // System.err.println(pb.toString());
            }
            throw new RuntimeException(e);
        }
        // TODO compute stats
        Set<Project<?>> modules = new HashSet<>();
        Commit commit = src.getCommit(spec.commitId);
        Path relPath = root.relativize(path);
        ProjectSpoon r = new ProjectSpoon(new Specifier<>(spec.sources, relPath, spec.commitId, spec.miner), modules,
                commit, path, launcher, compilerException);
        computeCounts(launcher, r);
        computeLOC2(path, r);
        r.getAst().getGlobalStats().codeCompile = prepared.getExitCode();

        List<SpoonPom> x = launcher.getPomFile().getModules();
        System.out.println(x);
        for (SpoonPom qq : x) {
            modules.add(extracted(src, Paths.get(qq.getFileSystemParent().getAbsolutePath()), root, qq));
        }
        return r;
    }

    private SpoonMiner.ProjectSpoon extractedPrecise(Sources src, Path path, Path root, SpoonPom spoonPom)
            throws IOException, InterruptedException, Exception {
        logger.traceEntry("parameters (src={}, path={}, root={}, spoonPom={})", src, path, root, spoonPom);
        Commit commit = src.getCommit(spec.commitId);
        ProjectSpoon r = null;
        try {
            Set<Project<?>> modules = new HashSet<>();
            Path relPath = root.relativize(path);
            // APP_SOURCE
            MavenLauncher launcherCode = spoonPom != null
                    ? new MavenLauncher(spoonPom, MavenLauncher.SOURCE_TYPE.APP_SOURCE)
                    : new MavenLauncher(path.toString(), MavenLauncher.SOURCE_TYPE.APP_SOURCE);
            launcherCode.getEnvironment().setLevel("INFO");
            launcherCode.getFactory().getEnvironment().setLevel("INFO");
            launcherCode.getEnvironment().setCommentEnabled(false);
            launcherCode.getFactory().getEnvironment().setCommentEnabled(false);

            StringBuffer prepareCodeResult = new StringBuffer();
            InvocationResult preparedCode = SourcesHelper.prepare(path, ".", x -> {
                prepareCodeResult.append(x + "\n");
            });
            logger.debug(prepareCodeResult.toString());

            CommandLineException compilerExceptionCode = preparedCode.getExecutionException();
            if (compilerExceptionCode != null) {
                logger.debug("Could not compile the source due to the following error", compilerExceptionCode);
            }
            // ALL_SOURCE
            MavenLauncher launcherAll = spoonPom != null
                    ? new MavenLauncher(spoonPom, MavenLauncher.SOURCE_TYPE.ALL_SOURCE)
                    : new MavenLauncher(path.toString(), MavenLauncher.SOURCE_TYPE.ALL_SOURCE);
            launcherAll.getEnvironment().setLevel("INFO");
            launcherAll.getFactory().getEnvironment().setLevel("INFO");
            launcherAll.getEnvironment().setCommentEnabled(false);
            launcherAll.getFactory().getEnvironment().setCommentEnabled(false);

            try {
                logger.info("try build ast code");
                launcherCode.buildModel();
            } catch (Exception e) {
                for (CategorizedProblem pb : ((JDTBasedSpoonCompiler) launcherCode.getModelBuilder()).getProblems()) {
                    logger.debug(pb.toString());
                }

                r = new ProjectSpoon(new Specifier<>(spec.sources, relPath, spec.commitId, spec.miner), modules, commit,
                        root, launcherCode, compilerExceptionCode);
                r.getAst().getGlobalStats().codeAST = 1;
                r.getAst().getGlobalStats().testsAST = 1;
            }

            StringBuilder prepareAllResult = new StringBuilder();
            InvocationResult preparedAll = SourcesHelper.prepareAll(path, ".", x -> {
                prepareAllResult.append(x + "\n");
            });
            logger.debug(prepareAllResult.toString());
            CommandLineException compilerExceptionAll = preparedAll.getExecutionException();
            if (compilerExceptionAll != null) {
                logger.debug("Could not compile the source due to the following error", compilerExceptionAll);
            }

            if (r == null) {
                try {
                    logger.info("try build ast all");
                    launcherAll.buildModel();
                } catch (Exception e) {
                    for (CategorizedProblem pb : ((JDTBasedSpoonCompiler) launcherAll.getModelBuilder())
                            .getProblems()) {
                        logger.debug(pb.toString());
                        // System.err.println(pb.toString());
                    }
                    // throw new RuntimeException(e);
                    r = new ProjectSpoon(new Specifier<>(spec.sources, relPath, spec.commitId, spec.miner), modules,
                            commit, root, launcherAll, compilerExceptionAll);
                    computeCounts(launcherAll, r);
                    r.getAst().getGlobalStats().codeAST = 0;
                    r.getAst().getGlobalStats().testsAST = 1;
                }
            }

            if (r == null) {
                r = new ProjectSpoon(new Specifier<>(spec.sources, relPath, spec.commitId, spec.miner), modules, commit,
                        root, launcherAll, compilerExceptionCode);
                computeCounts(launcherAll, r);
                r.getAst().getGlobalStats().codeAST = 0;
                r.getAst().getGlobalStats().testsAST = 0;
            }

            r.getAst().getGlobalStats().codeCompile = preparedCode.getExitCode();
            r.getAst().getGlobalStats().testsCompile = preparedAll.getExitCode();

            List<SpoonPom> modulesPoms = launcherCode.getPomFile().getModules();
            System.out.println(modulesPoms);
            for (SpoonPom pom : modulesPoms) {
                modules.add(extractedPrecise(src, Paths.get(pom.getFileSystemParent().getAbsolutePath()), root, pom));
            }
            return r;

        } catch (Exception e) {
            r = new ProjectSpoon(new Specifier<>(spec.sources, spec.commitId, spec.miner), commit, root, e);
            return r;
        } finally {
            computeLOC2(path, r);
            logger.traceExit("result: {}", r);
        }
    }

    /**
     * 
     * @param launcher model must have been built
     * @param proj
     */
    public static void computeCounts(Launcher launcher, Project<CtElement> proj) {
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
     * @param path code must be present as files (no git checkout is done by this
     *             method)
     * @param proj
     * @throws IOException
     * @throws InterruptedException
     */
    public static synchronized void computeLOC(Path path, Project<?> proj) throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder();
        String[] command = new String[] { "cloc", path.toAbsolutePath().toString(), "--md", "--quiet" };
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
                    // } else if (line.startsWith("XML|")) {
                    // g.loC = Integer.parseInt(line.substring(line.lastIndexOf("|") + 1));
                    // } else if (line.startsWith("HTML|")) {
                    // g.loC = Integer.parseInt(line.substring(line.lastIndexOf("|") + 1));
                    // } else if (line.startsWith("Maven|")) {
                    // g.loC = Integer.parseInt(line.substring(line.lastIndexOf("|") + 1));
                    // } else if (line.startsWith("Python|")) {
                    // g.loC = Integer.parseInt(line.substring(line.lastIndexOf("|") + 1));
                    // } else if (line.startsWith("Ant|")) {
                    // g.loC = Integer.parseInt(line.substring(line.lastIndexOf("|") + 1));
                    // } else if (line.startsWith("Bourne Again Shell|")) {
                    // g.loC = Integer.parseInt(line.substring(line.lastIndexOf("|") + 1));
                    // } else if (line.startsWith("Bourne Shell|")) {
                    // g.loC = Integer.parseInt(line.substring(line.lastIndexOf("|") + 1));
                    // } else if (line.startsWith("DOS Batch|")) {
                    // g.loC = Integer.parseInt(line.substring(line.lastIndexOf("|") + 1));
                    // } else if (line.startsWith("Objective C++|")) {
                    // g.loC = Integer.parseInt(line.substring(line.lastIndexOf("|") + 1));
                } else {

                }
            }
        }
        int exitCode = process.waitFor();
        System.out.printf("cloc ended with exitCode %d\n", exitCode);
    }

    class Line {
        @SerializedName("Name")
        String name;
        @SerializedName("Code")
        Integer loc;

        @Override
        public String toString() {
            return "Line [loc=" + loc + ", name=" + name + "]";
        }

    }

    /**
     * Uses scc, need scc excutable in parent dir
     * @param path code must be present as files (no git checkout is done by this
     *             method)
     * @param proj
     * @throws IOException
     * @throws InterruptedException
     */
    public static synchronized void computeLOC2(Path path, Project<?> proj) throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder();
        String[] command = new String[] { "scc", "-f", "json", "-c", path.toAbsolutePath().toString() };
        processBuilder.command(command);
        logger.info(" executing subprocess: " + Arrays.asList(command).stream().reduce("", (a, b) -> a + " " + b));
        Process process = null;
        int i = 0;
        IOException ee = null;
        while (i < 10) {
            try {
                process = processBuilder.start();
                break;
            } catch (IOException e) {
                i++;
                ee = e;
                logger.warn("fail " + i);
                try {
                    logger.info("try other dir");
                    Process process2 = new ProcessBuilder()
                            .command(new String[] { "scc", "-f", "json", "-c", "/home/qledilav/bin" }).start();
                    int exitCode = process2.waitFor();
                    logger.info("ret code of try other dir " + exitCode);
                } catch (IOException eee) {
                    logger.info("fail try other dir");
                    eee.printStackTrace();
                }
                try {
                    logger.info("try abs exe");
                    Process process2 = new ProcessBuilder().command(new String[] { "/home/qledilav/bin/scc", "-f",
                            "json", "-c", path.toAbsolutePath().toString() }).start();
                    int exitCode = process2.waitFor();
                    logger.info("ret code of try abs exe " + exitCode);
                } catch (IOException eee) {
                    logger.info("fail try abs exe");
                    eee.printStackTrace();
                }
                try {
                    logger.info("try both");
                    Process process2 = new ProcessBuilder()
                            .command(
                                    new String[] { "/home/qledilav/bin/scc", "-f", "json", "-c", "/home/qledilav/bin" })
                            .start();
                    int exitCode = process2.waitFor();
                    logger.info("ret code of try both " + exitCode);
                } catch (IOException eee) {
                    logger.info("fail try both");
                    eee.printStackTrace();
                }
            }
            Thread.sleep(5000);
            ;
        }
        if (i >= 10) {
            throw ee;
        }
        Gson gson = new Gson();
        try (JsonReader reader = new JsonReader(new InputStreamReader(process.getInputStream()))) {
            fr.quentin.coevolutionMiner.v2.ast.Stats g = proj.getAst().getGlobalStats();
            g.loC = 0;
            reader.beginArray();
            while (reader.hasNext()) {
                Line line = gson.fromJson(reader, Line.class);
                if (line.name.equals("Java")) {
                    g.javaLoC = line.loc;
                } else {
                    g.loC += line.loc;
                }
            }
        }
        int exitCode = process.waitFor();
        System.out.printf("scc ended with exitCode %d\n", exitCode);
    }
}