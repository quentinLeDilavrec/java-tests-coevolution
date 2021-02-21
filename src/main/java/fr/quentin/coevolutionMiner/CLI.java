package fr.quentin.coevolutionMiner;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import com.github.gumtreediff.actions.model.Insert;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.maven.shared.invoker.DefaultInvocationRequest;
import org.apache.maven.shared.invoker.DefaultInvoker;
import org.apache.maven.shared.invoker.InvocationRequest;
import org.apache.maven.shared.invoker.Invoker;
import org.apache.maven.shared.invoker.MavenInvocationException;
import org.eclipse.jdt.core.compiler.CategorizedProblem;
import org.eclipse.jgit.errors.MissingObjectException;
import org.refactoringminer.api.GitHistoryRefactoringMiner;
import org.refactoringminer.api.Refactoring;
import org.refactoringminer.api.RefactoringHandler;
import org.refactoringminer.rm1.GitHistoryRefactoringMinerImpl;

import fr.quentin.coevolutionMiner.utils.SourcesHelper;
import fr.quentin.coevolutionMiner.utils.ThreadPrintStream;
import fr.quentin.coevolutionMiner.v2.ast.Project;
import fr.quentin.coevolutionMiner.v2.ast.ProjectHandler;
import fr.quentin.coevolutionMiner.v2.ast.miners.SpoonMiner;
import fr.quentin.coevolutionMiner.v2.ast.miners.SpoonMiner.ProjectSpoon.SpoonAST;
import fr.quentin.coevolutionMiner.v2.coevolution.CoEvolutionHandler;
import fr.quentin.coevolutionMiner.v2.coevolution.CoEvolutions;
import fr.quentin.coevolutionMiner.v2.dependency.DependencyHandler;
import fr.quentin.coevolutionMiner.v2.dependency.Dependencies;
import fr.quentin.coevolutionMiner.v2.evolution.EvolutionHandler;
import fr.quentin.coevolutionMiner.v2.evolution.Evolutions;
import fr.quentin.coevolutionMiner.v2.evolution.miners.RefactoringMiner;
import fr.quentin.coevolutionMiner.v2.sources.Sources;
import fr.quentin.coevolutionMiner.v2.sources.SourcesHandler;
import fr.quentin.coevolutionMiner.v2.sources.Sources.Specifier;
import gumtree.spoon.AstComparator;
import gumtree.spoon.diff.Diff;
import gumtree.spoon.diff.operations.Operation;
import spoon.MavenLauncher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtElement;
import spoon.support.compiler.jdt.JDTBasedSpoonCompiler;

/**
 * CLI
 */
public class CLI {
    static Logger logger = LogManager.getLogger();

    static Stream<ImmutablePair<Integer, String>> indexedLines(BufferedReader br) {
        Iterator<ImmutablePair<Integer, String>> iter = new Iterator<ImmutablePair<Integer, String>>() {
            String nextLine = null;
            int index = 0;

            @Override
            public boolean hasNext() {
                if (nextLine != null) {
                    return true;
                } else {
                    try {
                        nextLine = br.readLine();
                        index++;
                        return (nextLine != null);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }
            }

            @Override
            public ImmutablePair<Integer, String> next() {
                if (nextLine != null || hasNext()) {
                    String line = nextLine;
                    nextLine = null;
                    return new ImmutablePair<Integer, String>(index, line);
                } else {
                    throw new NoSuchElementException();
                }
            }
        };
        return StreamSupport
                .stream(Spliterators.spliteratorUnknownSize(iter, Spliterator.ORDERED | Spliterator.NONNULL), false);
    }

    public static void main(String[] args) throws IOException {
        CommandLineParser parser = new DefaultParser();

        // create the Options
        Options options = new Options();
        options.addOption("r", "repo", true, "The repository where the code is located");
        options.addOption("t", "thread", true, "thread count");
        options.addOption("l", "limit", true, "limit lines processed");
        options.addOption("s", "start", true, "starting line 0-indexed");
        options.addOption("c", "commitsMax", true, "number of commits to compute impacts");
        options.addOption(null, "splitOut", false, "split outputs");
        options.addOption(null, "once", false,
                "compute coevolutions between commit pairs, commits in between are ignored");
        options.addOption(null, "greedy", false, "search for coevo even if there is no RM evolutions");
        options.addOption(null, "allModules", false, "also parse all modules present in profiles (by default only parse the modules in the default profile)");
        options.addOption("f", "file", true,
                "a file that contain per line <repo> <stars> <list of important commitId time ordered and starting with the most recent>");

        if (args.length < 1) {
            throw new UnsupportedOperationException("use batch, compare or ast");
        }
        CommandLine line;
        try {
            line = parser.parse(options, Arrays.copyOfRange(args, 1, args.length));
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
        if (line.hasOption("splitOut")) {
            splitedOut = true;
        }
        if (line.hasOption("greedy")) {
            SEARCH_ONLY_IF_RM_FOUND = false;
        }
        if (line.hasOption("allModules")) {
            SpoonMiner.ALL_MODULES_FROM_PROFILES = true;
        }
        if (line.hasOption("once")) {
            // TODO do something cleaner
            fr.quentin.coevolutionMiner.v2.evolution.miners.RefactoringMiner.spanning = fr.quentin.coevolutionMiner.v2.utils.Utils.Spanning.ONCE;
            fr.quentin.coevolutionMiner.v2.evolution.miners.MultiGTSMiner.spanning = fr.quentin.coevolutionMiner.v2.utils.Utils.Spanning.ONCE;
            fr.quentin.coevolutionMiner.v2.coevolution.miners.MultiCoEvolutionsMiner.spanning = fr.quentin.coevolutionMiner.v2.utils.Utils.Spanning.ONCE;
        }
        if (Objects.equals(args[0], "batch")) {
            if (line.getOptionValue("file") != null) {
                try (Stream<ImmutablePair<Integer, String>> lines = indexedLines(
                        Files.newBufferedReader(Paths.get(line.getOptionValue("file"))));) {
                    batch(lines.skip(Integer.parseInt(line.getOptionValue("start", "0")))
                            .limit(Integer.parseInt(line.getOptionValue("limit", "1"))),
                            Integer.parseInt(line.getOptionValue("thread", "1")),
                            Integer.parseInt(line.getOptionValue("commitsMax", "1")));
                }
            }
        } else if (Objects.equals(args[0], "simpleBatch")) {
            if (line.getOptionValue("file") != null) {
                try (Stream<ImmutablePair<Integer, String>> lines = indexedLines(
                        Files.newBufferedReader(Paths.get(line.getOptionValue("file"))));) {
                    simpleBatch(
                            lines.skip(Integer.parseInt(line.getOptionValue("start", "0")))
                                    .limit(Integer.parseInt(line.getOptionValue("limit", "1"))),
                            Integer.parseInt(line.getOptionValue("thread", "1")),
                            Integer.parseInt(line.getOptionValue("commitsMax", "1")));
                }
            }
        } else if (Objects.equals(args[0], "batchPreEval")) {
            if (line.getOptionValue("file") != null) {
                try (Stream<ImmutablePair<Integer, String>> lines = indexedLines(
                        Files.newBufferedReader(Paths.get(line.getOptionValue("file"))));) {
                    batchPreEval(
                            lines.skip(Integer.parseInt(line.getOptionValue("start", "0")))
                                    .limit(Integer.parseInt(line.getOptionValue("limit", "1"))),
                            Integer.parseInt(line.getOptionValue("thread", "1")),
                            Integer.parseInt(line.getOptionValue("commitsMax", "1")));
                }
            }
        } else if (Objects.equals(args[0], "ast")) {
            if (line.hasOption("repo")) {
                System.out.println(ast(line.getOptionValue("repo"), line.getArgList().get(0)));
            } else {
                System.out.println(ast(line.getArgList().get(0)));
            }
        } else if (Objects.equals(args[0], "compare")) {
            if (line.hasOption("repo")) {
                System.out.println(
                        compare(line.getOptionValue("repo"), line.getArgList().get(0), line.getArgList().get(1)));
            } else {
                System.out.println(compare(line.getArgList().get(0), line.getArgList().get(1)));
            }
        } else {
            throw new UnsupportedOperationException("use compare or ast");
        }
        System.exit(0);
    }

    static boolean SEARCH_ONLY_IF_RM_FOUND = true;

    static boolean splitedOut = false;

    public static void batchPreEval(Stream<ImmutablePair<Integer, String>> stream, int pool_size,
            int max_commits_impacts) {
        try (BatchExecutor executor = new BatchExecutor2(pool_size, max_commits_impacts);) {
            executor.process(stream);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void printThings(String url, String commitIdBefore, Project<?> project) {
        Project<?>.AST ast = project.getAst();
        if (ast instanceof SpoonAST && ((SpoonAST) ast).launcher == null) {
            logger.info("Spoon failed to analyize " + url);
        } else if (ast instanceof SpoonAST) {
            CtModel model = ((SpoonAST) ast).launcher.getModel();
            logger.info("done statistics " + url + "/commit/" + commitIdBefore + "/" + ast.rootDir.toString());
            logger.info("modules in pom: " + ((SpoonAST) ast).launcher.getPomFile().getModel().getModules().stream()
                    .reduce("", (a, b) -> a + "," + b));
            logger.info("modules parsed: "
                    + model.getAllModules().stream().map(x -> x.getSimpleName()).reduce("", (a, b) -> a + "," + b));
            logger.info("statistics: " + ast.getGlobalStats().toString());
        }
    }

    abstract static class BatchExecutor implements AutoCloseable {

        protected SourcesHandler srcH = new SourcesHandler();

        protected Logger loggerFixedOutPut;
        ThreadPoolExecutor executor;

        @Override
        public final void close() throws Exception {
            System.out.println("Shutdown");
            executor.shutdown();
            try {
                System.out.println("almost");
                while (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                }
                System.out.println("done");
                executor.shutdownNow();
            } catch (InterruptedException e) {
                executor.shutdownNow();
            } catch (Throwable e) {
                executor.shutdownNow();
            }
            overridableClose();
            srcH.close();
        }

        protected void overridableClose() throws Exception {
        }

        public BatchExecutor(int pool_size) {
            PrintStream saved_out = System.out;
            if (splitedOut) {
                ThreadPrintStream.replaceSystemOut();
                ThreadPrintStream.replaceSystemErr();
            }
            loggerFixedOutPut = LogManager.getLogger(CLI.class.getName() + "#batch");

            // loggerFixedOutPut.addHandler(new Handler() {

            //     @Override
            //     public void close() throws SecurityException {
            //         saved_out.close();
            //     }

            //     @Override
            //     public void flush() {
            //         saved_out.flush();
            //     }

            //     @Override
            //     public void publish(LogRecord record) {
            //         logger.log(record);
            //         saved_out.println(record.getMessage());
            //     }

            // });
            executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(pool_size, Executors.privilegedThreadFactory());
        }

        public final void process(Stream<ImmutablePair<Integer, String>> stream) {
            stream.forEach(this::process);
        }

        public final void process(ImmutablePair<Integer, String> entry) {
            loggerFixedOutPut.info("(launcher start) CLI status " + entry.left + " "
                    + Long.toString(executor.getTaskCount()) + " " + Integer.toString(executor.getActiveCount()) + " "
                    + Long.toString(executor.getCompletedTaskCount()));
            List<String> s = Arrays.asList(entry.right.split(" "));
            if (s.size() > 2) {
                executor.submit(() -> {
                    try {
                        loggerFixedOutPut.info(
                                "(submit start) CLI status " + entry.left + " " + Long.toString(executor.getTaskCount())
                                        + " " + Integer.toString(executor.getActiveCount()) + " "
                                        + Long.toString(executor.getCompletedTaskCount()));
                        Thread.currentThread().setName("coevoAna " + entry.left);
                        return process(s, entry.left);
                    } catch (Throwable e) {
                        logger.error("failed whole analysis of " + s.get(0), e);
                        return 1;
                    } finally {
                        Thread.currentThread().setName("coEana " + entry.left + " done");
                        loggerFixedOutPut.info(
                                "(submit end) CLI status " + entry.left + " " + Long.toString(executor.getTaskCount())
                                        + " " + Integer.toString(executor.getActiveCount()) + " "
                                        + Long.toString(executor.getCompletedTaskCount()));
                    }
                });
            } else {
                System.out.println("no commits for " + s.get(0));
            }
            loggerFixedOutPut.info("(launch end) CLI status " + entry.left + " "
                    + Long.toString(executor.getTaskCount()) + " " + Integer.toString(executor.getActiveCount()) + " "
                    + Long.toString(executor.getCompletedTaskCount()));
        }

        abstract public int process(List<String> line, int lineNumber) throws Exception;

    }

    static class BatchExecutor1 extends BatchExecutor {
        private int max_commits_impacts;

        protected ProjectHandler astH = new ProjectHandler(srcH);
        protected EvolutionHandler evoH = new EvolutionHandler(srcH, astH);
        protected DependencyHandler impactH = new DependencyHandler(srcH, astH, evoH);
        protected CoEvolutionHandler coevoH = new CoEvolutionHandler(srcH, astH, evoH, impactH);

        public BatchExecutor1(int pool_size, int max_commits_impacts) {
            super(pool_size);
            this.max_commits_impacts = max_commits_impacts;
        }

        @Override
        protected void overridableClose() throws Exception {
            coevoH.close();
            impactH.close();
            evoH.close();
            astH.close();
            super.overridableClose();
        }

        class ReleaseExecutor {

            private Specifier srcSpec;
            private int lineNumber;
            private String rawPath;

            public ReleaseExecutor(Specifier srcSpec, int lineNumber) throws URISyntaxException {
                this.srcSpec = srcSpec;
                this.lineNumber = lineNumber;
                this.rawPath = SourcesHelper.parseAddress(srcSpec.repository);
            }

            public void process(List<String> waypoints) throws Exception {
                String commitIdAfter = null;
                String commitIdBefore = null;
                int coevoAnaTried = 0;
                for (int index = 0; index < waypoints.size() - 1; index++) {
                    try {
                        if (coevoAnaTried > max_commits_impacts) {
                            break;
                        }
                        commitIdAfter = waypoints.get(index);
                        commitIdBefore = waypoints.get(index + 1);
                        if (commitIdBefore.equals(commitIdAfter)) {
                            continue;
                        }
                        Thread.currentThread().setName("coEana " + lineNumber + " " + index);
                        if (splitedOut) {
                            ThreadPrintStream.redirectThreadLogs(
                                    Paths.get(SourcesHelper.RESOURCES_PATH, "Logs", rawPath, commitIdBefore));
                        }

                        // try to build the ast and to some stats
                        try {
                            Sources src = srcH.handle(srcSpec);
                            try {
                                src.getCommitsBetween(commitIdBefore, commitIdAfter);
                            } catch (MissingObjectException e) {
                                continue;
                            }
                            Project<CtElement> project = astH.handle(astH.buildSpec(srcSpec, commitIdBefore));
                            printThings(srcSpec.repository, commitIdBefore, project);
                            for (Project<?> x : project.getModules()) {
                                printThings(srcSpec.repository, commitIdBefore, x);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                            logger.error("failed statistics " + srcSpec.repository, e);
                            break;
                        }

                        // try to find evolutions
                        try {
                            Evolutions evos = evoH.handle(evoH.buildSpec(srcSpec, commitIdBefore, commitIdAfter));
                            logger.info("done evolution analysis " + srcSpec.repository);
                            if (!SEARCH_ONLY_IF_RM_FOUND || evos != null && evos.toSet().size() > 0) {
                                logger.info(Integer.toString(evos.toSet().size()) + " evolutions found for "
                                        + srcSpec.repository + " from " + commitIdBefore + " to " + commitIdAfter);
                                coevoAnaTried++;
                                // try to find coevolutions
                                processCoEvo(commitIdAfter, commitIdBefore);
                            }
                        } catch (Exception e) {
                            logger.error("failed evolution analysis " + srcSpec.repository, e);
                            e.printStackTrace();
                            break;
                        }

                    } catch (Throwable e) {
                        final String cA = commitIdAfter;
                        final String cB = commitIdBefore;
                        logger.error("failed to analyze the interval [" + cB + "," + cA + "] of " + srcSpec.repository,
                                e);
                        // break;
                    } finally {
                        if (splitedOut) {
                            ((ThreadPrintStream) System.out).flush();
                            ((ThreadPrintStream) System.err).flush();
                            ((ThreadPrintStream) System.out).close();
                            ((ThreadPrintStream) System.err).close();
                        }
                    }
                }
            }

            private void processCoEvo(String commitIdAfter, String commitIdBefore) {
                try {
                    CoEvolutions coevo = coevoH.handle(CoEvolutionHandler.buildSpec(srcSpec,
                            EvolutionHandler.buildSpec(srcSpec, commitIdBefore, commitIdAfter)));
                    logger.info(Integer.toString(coevo.getCoEvolutions().size()) + " coevolutions found for "
                            + srcSpec.repository + " from " + commitIdBefore + " to " + commitIdAfter);
                } catch (Throwable e) {
                    logger.error("failed coevolution analysis " + srcSpec.repository, e);
                }
            }

            private void processImpacts(String commitIdAfter, String commitIdBefore) {
                try {
                    Dependencies impacts = impactH.handle(impactH.buildSpec(astH.buildSpec(srcSpec, commitIdBefore),
                            EvolutionHandler.buildSpec(srcSpec, commitIdBefore, commitIdAfter)));
                    logger.info(Integer.toString(impacts.getPerRootCause().size()) + " impacts found for "
                            + srcSpec.repository + " from " + commitIdBefore + " to " + commitIdAfter);
                } catch (Throwable e) {
                    logger.error("failed impact analysis " + srcSpec.repository, e);
                }
            }
        }

        @Override
        public int process(List<String> s, int lineNumber) throws Exception {
            if (s.size() < 1) {
                logger.warn("there is no project to analyze o this line");
                return 1;
            } else if (s.size() < 2) {
                logger.warn("you should give the aproximate number of stars of this project");
                return 2;
            } else if (s.size() < 4) {
                logger.warn("need at least 2 release/commits to analyze " + s.get(0) + " but got " + s.size());
                return 3;
            }
            Sources.Specifier srcSpec = srcH.buildSpec(s.get(0), Integer.parseInt(s.get(1)));
            ReleaseExecutor executor = new ReleaseExecutor(srcSpec, lineNumber);
            List<String> waypoints = s.subList(2, s.size());
            executor.process(waypoints);
            return 0;
        }

    }

    static class BatchExecutor2 extends BatchExecutor {
        private int max_commits_impacts;

        protected ProjectHandler astH = new ProjectHandler(srcH);
        protected EvolutionHandler evoH = new EvolutionHandler(srcH, astH);
        protected DependencyHandler impactH = new DependencyHandler(srcH, astH, evoH);
        protected CoEvolutionHandler coevoH = new CoEvolutionHandler(srcH, astH, evoH, impactH);

        public BatchExecutor2(int pool_size, int max_commits_impacts) {
            super(pool_size);
            this.max_commits_impacts = max_commits_impacts;
        }

        @Override
        protected void overridableClose() throws Exception {
            coevoH.close();
            impactH.close();
            evoH.close();
            astH.close();
            super.overridableClose();
        }

        class ReleaseExecutor {

            private Specifier srcSpec;
            private int lineNumber;
            private String rawPath;

            public ReleaseExecutor(Specifier srcSpec, int lineNumber) throws URISyntaxException {
                this.srcSpec = srcSpec;
                this.lineNumber = lineNumber;
                this.rawPath = SourcesHelper.parseAddress(srcSpec.repository);
            }

            public void process(List<String> waypoints) throws Exception {
                String commitIdAfter = null;
                String commitIdBefore = null;
                int coevoAnaTried = 0;
                for (int index = 0; index < waypoints.size() - 1; index++) {
                    try {
                        if (coevoAnaTried > max_commits_impacts) {
                            break;
                        }
                        commitIdAfter = waypoints.get(index);
                        commitIdBefore = waypoints.get(index + 1);
                        if (commitIdBefore.equals(commitIdAfter)) {
                            continue;
                        }
                        Thread.currentThread().setName("coEana " + lineNumber + " " + index);
                        if (splitedOut) {
                            ThreadPrintStream.redirectThreadLogs(
                                    Paths.get(SourcesHelper.RESOURCES_PATH, "Logs", rawPath, commitIdBefore));
                        }

                        // try to build the ast and do some stats
                        try {
                            Sources src = srcH.handle(srcSpec);
                            try {
                                src.getCommitsBetween(commitIdBefore, commitIdAfter);
                            } catch (MissingObjectException e) {
                                continue;
                            }
                            Project<CtElement> project = astH.handle(astH.buildSpec(srcSpec, commitIdBefore));
                            printThings(srcSpec.repository, commitIdBefore, project);
                            for (Project<?> x : project.getModules()) {
                                printThings(srcSpec.repository, commitIdBefore, x);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                            logger.error("failed ast build or stats", e);
                            break;
                        }

                        // try to find evolutions
                        try {
                            Evolutions evos = evoH.handle(evoH.buildSpec(srcSpec, commitIdBefore, commitIdAfter));
                            logger.info("done evolution analysis " + srcSpec.repository);
                            if (evos != null && evos.toSet().size() > 0) {
                                logger.info(Integer.toString(evos.toSet().size()) + " evolutions found for "
                                        + srcSpec.repository + " from " + commitIdBefore + " to " + commitIdAfter);
                                coevoAnaTried++;
                                // try to find coevolutions
                                processCoEvo(commitIdAfter, commitIdBefore);
                            }
                        } catch (Exception e) {
                            logger.error("failed evolution analysis", e);
                            break;
                        }

                    } catch (Throwable e) {
                        logger.error("failed to analyze the interval [" + commitIdBefore + "," + commitIdAfter + "] of "
                                + srcSpec.repository, e);
                        // break;
                    } finally {
                        if (splitedOut) {
                            ((ThreadPrintStream) System.out).flush();
                            ((ThreadPrintStream) System.err).flush();
                            ((ThreadPrintStream) System.out).close();
                            ((ThreadPrintStream) System.err).close();
                        }
                    }
                }
            }

            private void processCoEvo(String commitIdAfter, String commitIdBefore) {
                try {
                    CoEvolutions coevo = coevoH.handle(CoEvolutionHandler.buildSpec(srcSpec,
                            EvolutionHandler.buildSpec(srcSpec, commitIdBefore, commitIdAfter)));
                    logger.info(Integer.toString(coevo.getCoEvolutions().size()) + " coevolutions found for "
                            + srcSpec.repository + " from " + commitIdBefore + " to " + commitIdAfter);
                } catch (Throwable e) {
                    logger.error("failed coevolution analysis " + srcSpec.repository, e);
                }
            }

            private void processImpacts(String commitIdAfter, String commitIdBefore) {
                try {
                    Dependencies impacts = impactH.handle(impactH.buildSpec(astH.buildSpec(srcSpec, commitIdBefore),
                            EvolutionHandler.buildSpec(srcSpec, commitIdBefore, commitIdAfter)));
                    logger.info(Integer.toString(impacts.getPerRootCause().size()) + " impacts found for "
                            + srcSpec.repository + " from " + commitIdBefore + " to " + commitIdAfter);
                } catch (Throwable e) {
                    logger.error("failed impact analysis " + srcSpec.repository, e);
                }
            }
        }

        @Override
        public int process(List<String> s, int lineNumber) throws Exception {
            if (s.size() < 1) {
                logger.warn("there is no project to analyze o this line");
                return 1;
            } else if (s.size() < 2) {
                logger.warn("you should give the aproximate number of stars of this project");
                return 2;
            } else if (s.size() < 4) {
                logger.warn("need at least 2 release/commits to analyze " + s.get(0) + " but got " + s.size());
                return 3;
            }
            Sources.Specifier srcSpec = srcH.buildSpec(s.get(0), Integer.parseInt(s.get(1)));
            ReleaseExecutor executor = new ReleaseExecutor(srcSpec, lineNumber);
            List<String> waypoints = s.subList(2, s.size());
            executor.process(waypoints);
            return 0;
        }

    }

    public static void batch(Stream<ImmutablePair<Integer, String>> stream, int pool_size, int max_commits_impacts) {
        try (BatchExecutor executor = new BatchExecutor1(pool_size, max_commits_impacts);) {
            executor.process(stream);
        } catch (Exception e) {
            logger.warn("whole batch crached", e);
        }
    }

    public static void simpleBatch(Stream<ImmutablePair<Integer, String>> stream, int pool_size,
            int max_commits_impacts) {
        ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(pool_size);

        try (SourcesHandler srcH = new SourcesHandler();
                ProjectHandler astH = new ProjectHandler(srcH);
                EvolutionHandler evoH = new EvolutionHandler(srcH, astH);
                DependencyHandler impactH = new DependencyHandler(srcH, astH, evoH);
                CoEvolutionHandler coevoH = new CoEvolutionHandler(srcH, astH, evoH, impactH)) {
            System.out.println("Starting");
            stream.forEach(line -> {
                logger.info("(laucher start) CLI status " + Long.toString(executor.getTaskCount()) + " "
                        + Integer.toString(executor.getActiveCount()) + " "
                        + Long.toString(executor.getCompletedTaskCount()));
                List<String> s = Arrays.asList(line.right.split(" "));
                if (s.size() > 2) {
                    executor.submit(() -> {
                        logger.info("(submit start) CLI status " + Long.toString(executor.getTaskCount()) + " "
                                + Integer.toString(executor.getActiveCount()) + " "
                                + Long.toString(executor.getCompletedTaskCount()));
                        Sources.Specifier srcSpec = srcH.buildSpec(s.get(0), Integer.parseInt(s.get(1)));

                        Evolutions evos = null;
                        String commitIdAfter = null;
                        String commitIdBefore = null;
                        int commit_index = 1;
                        int impact_computed = 0;
                        while (commit_index < s.size() - 2 && impact_computed < max_commits_impacts) {

                            while (commit_index < s.size() - 2) {
                                commit_index++;
                                commitIdAfter = s.get(commit_index);
                                commitIdBefore = s.get(commit_index + 1);
                                if (commitIdBefore.equals(commitIdAfter)) {
                                    continue;
                                }

                                try { // https://github.com/chrisbanes/Android-PullToRefresh/commit/1f7a7e1daf89167b11166180d96bac54a9306c80
                                      // evos = spoon compile + count tests/methods/class
                                    Sources src = srcH.handle(srcSpec);
                                    src.getCommitsBetween(commitIdBefore, commitIdAfter);
                                    Project<CtElement> project = astH.handle(astH.buildSpec(srcSpec, commitIdBefore));
                                    printThings(s.get(0), commitIdBefore, project);
                                    for (Project<?> x : project.getModules()) {
                                        printThings(s.get(0), commitIdBefore, x);
                                    }
                                } catch (Exception e) {
                                    e.printStackTrace();
                                    logger.info("failed statistics " + s.get(0));
                                    break;
                                }

                                try {
                                    evos = evoH.handle(evoH.buildSpec(srcSpec, commitIdBefore, commitIdAfter));
                                    logger.info("done evolution analysis " + s.get(0));
                                } catch (Throwable e) {
                                    logger.error("failed evolution analysis " + s.get(0), e);
                                    e.printStackTrace();
                                    break;
                                } finally {
                                    logger.info("finished an evolution analysis " + s.get(0));
                                }
                                if (evos != null && evos.toSet().size() > 0) {
                                    break;
                                }
                            }
                            if (s.size() < 3) {
                                logger.info("no commits for " + s.get(0));
                            } else if (evos == null) {
                                logger.info("evolution not working " + s.get(0));
                            } else if (evos.toSet().size() <= 0) {
                                logger.info("no evolutions found for " + s.get(0));
                            } else {
                                impact_computed += 1;
                                logger.info(Integer.toString(evos.toSet().size()) + " evolutions found for " + s.get(0)
                                        + " from " + commitIdBefore + " to " + commitIdAfter);
                                try {
                                    Dependencies impacts = impactH.handle(impactH.buildSpec(
                                            astH.buildSpec(srcSpec, commitIdBefore), EvolutionHandler.buildSpec(srcSpec,
                                                    commitIdBefore, commitIdAfter, RefactoringMiner.class)));
                                    System.out.println(
                                            Integer.toString(impacts.getPerRootCause().size()) + " impacts found for "
                                                    + s.get(0) + " from " + commitIdBefore + " to " + commitIdAfter);
                                } catch (Throwable e) {
                                    logger.info("failed impacts analysis for " + s.get(0));
                                    e.printStackTrace();
                                }
                            }
                        }

                        logger.info("(submit end) CLI status " + Long.toString(executor.getTaskCount()) + " "
                                + Integer.toString(executor.getActiveCount()) + " "
                                + Long.toString(executor.getCompletedTaskCount()));
                        return 0;
                    });
                } else

                {
                    System.out.println("no commits for " + s.get(0));
                }
                logger.info("(launch end) CLI status " + Long.toString(executor.getTaskCount()) + " "
                        + Integer.toString(executor.getActiveCount()) + " "
                        + Long.toString(executor.getCompletedTaskCount()));
            });
            System.out.println("Shutdown");
            executor.shutdown();
            try {
                System.out.println("almost done submittings");
                while (!executor.awaitTermination(1, TimeUnit.MINUTES)) {
                }
                System.out.println("done submitting");
                executor.shutdownNow();
            } catch (InterruptedException e) {
                executor.shutdownNow();
            } catch (Throwable e) {
                executor.shutdownNow();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void batchFromBDD(int pool_size, int max_commits, Map<String, Object> thresholds,
            Map<String, Object> sortings) {
        ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(pool_size);

        try (SourcesHandler srcH = new SourcesHandler();
                ProjectHandler astH = new ProjectHandler(srcH);
                EvolutionHandler evoH = new EvolutionHandler(srcH, astH);
                DependencyHandler impactH = new DependencyHandler(srcH, astH, evoH);
                CoEvolutionHandler coevoH = new CoEvolutionHandler(srcH, astH, evoH, impactH)) {

            System.out.println("Starting batch from bdd");
            // TODO need to store releases given in csv from djamel for the folowing to work

            List<String> lines = new ArrayList<>();
            lines.forEach(line -> {
                logger.info("(laucher start) CLI status " + Long.toString(executor.getTaskCount()) + " "
                        + Integer.toString(executor.getActiveCount()) + " "
                        + Long.toString(executor.getCompletedTaskCount()));
                List<String> s = Arrays.asList(line.split(" "));
                if (s.size() > 2) {
                    executor.submit(() -> {
                        logger.info("(submit start) CLI status " + Long.toString(executor.getTaskCount()) + " "
                                + Integer.toString(executor.getActiveCount()) + " "
                                + Long.toString(executor.getCompletedTaskCount()));
                        Sources.Specifier srcSpec = srcH.buildSpec(s.get(0), Integer.parseInt(s.get(1)));

                        Evolutions evos = null;
                        String commitIdAfter = null;
                        String commitIdBefore = null;
                        int commit_index = 1;
                        int impact_computed = 0;
                        while (commit_index < s.size() - 2 && impact_computed < max_commits) {

                            while (commit_index < s.size() - 2) {
                                commit_index++;
                                commitIdAfter = s.get(commit_index);
                                commitIdBefore = s.get(commit_index + 1);
                                if (commitIdBefore.equals(commitIdAfter)) {
                                    continue;
                                }

                                try { // https://github.com/chrisbanes/Android-PullToRefresh/commit/1f7a7e1daf89167b11166180d96bac54a9306c80
                                      // evos = spoon compile + count tests/methods/class
                                    Sources src = srcH.handle(srcSpec);
                                    src.getCommitsBetween(commitIdBefore, commitIdAfter);
                                    Project<CtElement> project = astH.handle(astH.buildSpec(srcSpec, commitIdBefore));
                                    printThings(s.get(0), commitIdBefore, project);
                                    for (Project<?> x : project.getModules()) {
                                        printThings(s.get(0), commitIdBefore, x);
                                    }
                                } catch (Throwable e) {
                                    e.printStackTrace();
                                    logger.info("failed statistics " + s.get(0));
                                    break;
                                }

                                try {
                                    evos = evoH.handle(evoH.buildSpec(srcSpec, commitIdBefore, commitIdAfter));
                                    logger.info("done evolution analysis " + s.get(0));
                                } catch (Throwable e) {
                                    logger.error("failed evolution analysis " + s.get(0), e);
                                    e.printStackTrace();
                                    break;
                                } finally {
                                    logger.info("finished an evolution analysis " + s.get(0));
                                }
                                if (evos != null && evos.toSet().size() > 0) {
                                    break;
                                }
                            }
                            if (s.size() < 3) {
                                logger.info("no commits for " + s.get(0));
                            } else if (evos == null) {
                                logger.info("evolution not working " + s.get(0));
                            } else if (evos.toSet().size() <= 0) {
                                logger.info("no evolutions found for " + s.get(0));
                            } else {
                                impact_computed += 1;
                                logger.info(Integer.toString(evos.toSet().size()) + " evolutions found for " + s.get(0)
                                        + " from " + commitIdBefore + " to " + commitIdAfter);
                                try {
                                    Dependencies impacts = impactH.handle(impactH.buildSpec(
                                            astH.buildSpec(srcSpec, commitIdBefore), EvolutionHandler.buildSpec(srcSpec,
                                                    commitIdBefore, commitIdAfter, RefactoringMiner.class)));
                                    System.out.println(
                                            Integer.toString(impacts.getPerRootCause().size()) + " impacts found for "
                                                    + s.get(0) + " from " + commitIdBefore + " to " + commitIdAfter);
                                } catch (Throwable e) {
                                    logger.info("failed impacts analysis for " + s.get(0));
                                    e.printStackTrace();
                                }
                            }
                        }

                        logger.info("(submit end) CLI status " + Long.toString(executor.getTaskCount()) + " "
                                + Integer.toString(executor.getActiveCount()) + " "
                                + Long.toString(executor.getCompletedTaskCount()));
                        return 0;
                    });
                } else

                {
                    System.out.println("no commits for " + s.get(0));
                }
                logger.info("(launch end) CLI status " + Long.toString(executor.getTaskCount()) + " "
                        + Integer.toString(executor.getActiveCount()) + " "
                        + Long.toString(executor.getCompletedTaskCount()));
            });
            System.out.println("Shutdown");
            executor.shutdown();
            try {
                System.out.println("almost done submittings");
                while (!executor.awaitTermination(1, TimeUnit.MINUTES)) {
                }
                System.out.println("done submitting");
                executor.shutdownNow();
            } catch (InterruptedException e) {
                executor.shutdownNow();
            } catch (Throwable e) {
                executor.shutdownNow();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String ast(String path) {
        File f = new File(path);
        if (f.isFile()) {
            throw new UnsupportedOperationException("Not Implemented Yet");
        } else if (f.isDirectory()) {
            try {
                MavenLauncher launcher = new MavenLauncher(f.toPath().toString(), MavenLauncher.SOURCE_TYPE.ALL_SOURCE);
                launcher.getEnvironment().setLevel("INFO");
                launcher.getFactory().getEnvironment().setLevel("INFO");

                // Compile with maven to get deps
                InvocationRequest request = new DefaultInvocationRequest();
                request.setBatchMode(true);
                request.setBaseDirectory(f);
                request.setGoals(Collections.singletonList("compile"));
                Invoker invoker = new DefaultInvoker();
                invoker.setMavenHome(Paths.get("/usr").toFile());
                try {
                    invoker.execute(request);
                } catch (MavenInvocationException e) {
                    throw new RuntimeException("Error while compiling project with maven", e);
                }

                // Build Spoon model
                try {
                    launcher.buildModel();
                } catch (Throwable e) {
                    for (CategorizedProblem pb : ((JDTBasedSpoonCompiler) launcher.getModelBuilder()).getProblems()) {
                        logger.info(pb.toString());
                    }
                    throw new RuntimeException("Error while building the Spoon model", e);
                }

                return Integer.toString(launcher.getFactory().Type().getAll().size());
                // .stream().map(x -> x.getQualifiedName()).reduce("",
                // (a, b) -> a + " " + b);

            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        } else {
            throw new UnsupportedOperationException("The given path should be a file or a directory.");
        }
        // Json4SpoonGenerator x = new Json4SpoonGenerator();
        // AstComparator comp = new AstComparator();
        // Gson gson = new GsonBuilder().setPrettyPrinting().create();
        // JsonObject r = x.getJSONasJsonObject(comp.getCtType(fl));
        // System.out.println(gson.toJson(r));
    }

    public static String ast(String gitURL, String commitId) {
        try (SourcesHelper helper = new SourcesHelper(gitURL);) {
            Path path = helper.materialize(commitId);
            MavenLauncher launcher = new MavenLauncher(path.toString(), MavenLauncher.SOURCE_TYPE.ALL_SOURCE);
            launcher.getEnvironment().setLevel("INFO");
            launcher.getFactory().getEnvironment().setLevel("INFO");

            // Compile with maven to get deps
            StringBuilder prepareResult = new StringBuilder();
            SourcesHelper.prepare(path, x -> {
                prepareResult.append(x + "\n");
            });
            logger.trace(prepareResult);

            // Build Spoon model
            try {
                launcher.buildModel();
            } catch (Exception e) {
                for (CategorizedProblem pb : ((JDTBasedSpoonCompiler) launcher.getModelBuilder()).getProblems()) {
                    logger.info(pb.toString());
                }

                throw new RuntimeException("Error while building the Spoon model", e);
            }

            return launcher.getFactory().Type().getAll().get(0).getQualifiedName();

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String compare(String gitURL, String before, String after) {
        List<Refactoring> detectedRefactorings = new ArrayList<Refactoring>();

        GitHistoryRefactoringMiner miner = new GitHistoryRefactoringMinerImpl();
        JsonObject r = new JsonObject();
        try (SourcesHelper helper = new SourcesHelper(gitURL);) {

            try {
                miner.detectBetweenCommits(helper.getRepo(), before, after, new RefactoringHandler() {
                    @Override
                    public void handle(String commitId, List<Refactoring> refactorings) {
                        detectedRefactorings.addAll(refactorings);
                    }
                });
            } catch (Exception e) {
                throw new RuntimeException("Error while computing refacoring with RefactoringMiner", e);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        logger.info("Build result");
        r.addProperty("handler", "ImpactRMinerHandler");
        // detectedRefactorings.get(0).rightSide().get(0).
        Object diff = new Gson().fromJson(JSON(gitURL, after, detectedRefactorings), new TypeToken<Object>() {
        }.getType());
        r.add("diff", new Gson().toJsonTree(diff));
        return new GsonBuilder().setPrettyPrinting().create().toJson(r);

    }

    public static String compare(String before, String after) {
        File fl = new File(before);
        File fr = new File(after);
        AstComparator comp = new AstComparator();
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        Diff diff;
        try {
            diff = comp.compare(fl, fr);
        } catch (Exception e) {
            throw new RuntimeException("Error while computing diffs with Gumtree", e);
        }
        // System.out.println(diff);
        // System.out.println(neww);
        // System.out.println(diff.getAllOperations().get(0));
        List<Operation> ops = new ArrayList<Operation>();
        for (Operation<?> tch : diff.getRootOperations()) {
            if (tch.getAction() instanceof Insert) {
                ops.add(tch);
            }
        }
        JsonElement o = comp.formatDiff(ops);
        return gson.toJson(o.getAsJsonObject().get("actions").getAsJsonArray().get(0));
    }

    public static String JSON(String gitURL, String currentCommitId, List<Refactoring> refactoringsAtRevision) {
        StringBuilder sb = new StringBuilder();
        sb.append("{").append("\n");
        sb.append("\"").append("commits").append("\"").append(": ");
        sb.append("[");
        sb.append("{");
        sb.append("\t").append("\"").append("repository").append("\"").append(": ").append("\"").append(gitURL)
                .append("\"").append(",").append("\n");
        sb.append("\t").append("\"").append("sha1").append("\"").append(": ").append("\"").append(currentCommitId)
                .append("\"").append(",").append("\n");
        String url = "https://github.com/" + gitURL.substring(19, gitURL.indexOf(".git")) + "/commit/"
                + currentCommitId;
        sb.append("\t").append("\"").append("url").append("\"").append(": ").append("\"").append(url).append("\"")
                .append(",").append("\n");
        sb.append("\t").append("\"").append("refactorings").append("\"").append(": ");
        sb.append("[");
        int counter = 0;
        for (Refactoring refactoring : refactoringsAtRevision) {
            sb.append(refactoring.toJSON());
            if (counter < refactoringsAtRevision.size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
            counter++;
        }
        sb.append("]");
        sb.append("}");
        sb.append("]").append("\n");
        sb.append("}");
        return sb.toString();
    }

}