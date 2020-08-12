package fr.quentin.coevolutionMiner;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// import org.apache.commons.cli;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import com.github.gumtreediff.actions.model.Insert;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import org.apache.maven.shared.invoker.DefaultInvocationRequest;
import org.apache.maven.shared.invoker.DefaultInvoker;
import org.apache.maven.shared.invoker.InvocationRequest;
import org.apache.maven.shared.invoker.Invoker;
import org.apache.maven.shared.invoker.MavenInvocationException;
import org.eclipse.jdt.core.compiler.CategorizedProblem;
import org.refactoringminer.api.GitHistoryRefactoringMiner;
import org.refactoringminer.api.Refactoring;
import org.refactoringminer.api.RefactoringHandler;
import org.refactoringminer.rm1.GitHistoryRefactoringMinerImpl;

import fr.quentin.coevolutionMiner.utils.SourcesHelper;
import fr.quentin.coevolutionMiner.utils.ThreadPrintStream;
import fr.quentin.coevolutionMiner.v2.ast.Project;
import fr.quentin.coevolutionMiner.v2.ast.Project.AST;
import fr.quentin.coevolutionMiner.v2.ast.ProjectHandler;
import fr.quentin.coevolutionMiner.v2.ast.miners.SpoonMiner;
import fr.quentin.coevolutionMiner.v2.ast.miners.SpoonMiner.ProjectSpoon.SpoonAST;
import fr.quentin.coevolutionMiner.v2.coevolution.CoEvolutionHandler;
import fr.quentin.coevolutionMiner.v2.coevolution.CoEvolutions;
import fr.quentin.coevolutionMiner.v2.evolution.EvolutionHandler;
import fr.quentin.coevolutionMiner.v2.evolution.Evolutions;
import fr.quentin.coevolutionMiner.v2.evolution.miners.GumTreeSpoonMiner;
import fr.quentin.coevolutionMiner.v2.evolution.miners.RefactoringMiner;
import fr.quentin.coevolutionMiner.v2.impact.ImpactHandler;
import fr.quentin.coevolutionMiner.v2.impact.Impacts;
import fr.quentin.coevolutionMiner.v2.sources.Sources;
import fr.quentin.coevolutionMiner.v2.sources.SourcesHandler;
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
    private static Logger logger = Logger.getLogger(CLI.class.getName());

    public static void main(String[] args) throws IOException {
        CommandLineParser parser = new DefaultParser();

        // create the Options
        Options options = new Options();
        options.addOption("r", "repo", true, "The repository where the code is located");
        options.addOption("t", "thread", true, "thread count");
        options.addOption("l", "limit", true, "limit lines processed");
        options.addOption("s", "start", true, "starting line 0-indexed");
        options.addOption("c", "commitsMax", true, "number of commits to compute impacts");
        options.addOption("f", "file", true,
                "a file that contain per line <repo> <stars> <list of important commitId time ordered and starting with the most recent>");

        try {
            if (args.length < 1) {
                throw new UnsupportedOperationException("use batch, compare or ast");
            }
            CommandLine line = parser.parse(options, Arrays.copyOfRange(args, 1, args.length));
            if (Objects.equals(args[0], "batch")) {
                if (line.getOptionValue("file") != null) {
                    batch(Files.lines(Paths.get(line.getOptionValue("file")))
                            .skip(Integer.parseInt(line.getOptionValue("start", "0")))
                            .limit(Integer.parseInt(line.getOptionValue("limit", "1"))),
                            Integer.parseInt(line.getOptionValue("thread", "1")),
                            Integer.parseInt(line.getOptionValue("commitsMax", "1")));
                }
            } else if (Objects.equals(args[0], "simpleBatch")) {
                if (line.getOptionValue("file") != null) {
                    SimpleBatch(
                            Files.lines(Paths.get(line.getOptionValue("file")))
                                    .skip(Integer.parseInt(line.getOptionValue("start", "0")))
                                    .limit(Integer.parseInt(line.getOptionValue("limit", "1"))),
                            Integer.parseInt(line.getOptionValue("thread", "1")),
                            Integer.parseInt(line.getOptionValue("commitsMax", "1")));
                }
            } else if (Objects.equals(args[0], "batchPreEval")) {
                if (line.getOptionValue("file") != null) {
                    batchPreEval(
                            Files.lines(Paths.get(line.getOptionValue("file")))
                                    .skip(Integer.parseInt(line.getOptionValue("start", "0")))
                                    .limit(Integer.parseInt(line.getOptionValue("limit", "1"))),
                            Integer.parseInt(line.getOptionValue("thread", "1")),
                            Integer.parseInt(line.getOptionValue("commitsMax", "1")));
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
        } catch (ParseException exp) {
            System.out.println("Unexpected exception:" + exp.getMessage());
        }
        System.exit(0);
    }

    static boolean splitedOut = true;

    private static void batchPreEval(Stream<String> lines, int pool_size, int max_commits_impacts) {
        PrintStream saved_out = System.out;
        if (splitedOut) {
            ThreadPrintStream.replaceSystemOut();
            ThreadPrintStream.replaceSystemErr();
        }
        ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(pool_size);
        SourcesHandler srcH = new SourcesHandler();
        ProjectHandler astH = new ProjectHandler(srcH);
        EvolutionHandler evoH = new EvolutionHandler(srcH, astH);
        ImpactHandler impactH = new ImpactHandler(srcH, astH, evoH);
        CoEvolutionHandler coevoH = new CoEvolutionHandler(srcH, astH, evoH, impactH);
        logger.info("Starting");
        Logger logger2 = Logger.getLogger(CLI.class.getName() + "#batchPreEval");

        logger2.addHandler(new Handler() {

            @Override
            public void close() throws SecurityException {
                saved_out.close();
            }

            @Override
            public void flush() {
                saved_out.flush();
            }

            @Override
            public void publish(LogRecord record) {
                logger.log(record);
                saved_out.println(record.getMessage());
            }

        });
        lines.forEach(line -> {
            logger.info("(laucher start) CLI status " + Long.toString(executor.getTaskCount()) + " "
                    + Integer.toString(executor.getActiveCount()) + " "
                    + Long.toString(executor.getCompletedTaskCount()));
            List<String> releases = Arrays.asList(line.split(" "));
            if (releases.size() > 2) {
                executor.submit(() -> {
                    try {
                        if (splitedOut) {
                            ThreadPrintStream.redirectThreadLogs(ThreadPrintStream.DEFAULT);
                        }
                        Sources.Specifier srcSpec = srcH.buildSpec(releases.get(0), Integer.parseInt(releases.get(1)));
                        String rawPath = SourcesHelper.parseAddress(srcSpec.repository);

                        logger2.info("(submit start) CLI status " + Long.toString(executor.getTaskCount()) + " "
                                + Integer.toString(executor.getActiveCount()) + " "
                                + Long.toString(executor.getCompletedTaskCount()));

                        String commitIdAfter = null;
                        String commitIdBefore = null;
                        int commit_index = 2;
                        int impact_computed = 0;
                        Project<?> project = null;
                        for (; commit_index < releases.size() - 1; commit_index++) {
                            commitIdAfter = releases.get(commit_index);
                            commitIdBefore = releases.get(commit_index + 1);
                            if (splitedOut) {
                                ThreadPrintStream.redirectThreadLogs(
                                        Paths.get(SourcesHelper.RESOURCES_PATH, "Logs", rawPath, commitIdBefore));
                            }
                            try { // https://github.com/chrisbanes/Android-PullToRefresh/commit/1f7a7e1daf89167b11166180d96bac54a9306c80
                                  // evos = spoon compile + count tests/methods/class
                                Sources src = srcH.handle(srcSpec, "JGit");
                                src.getCommitsBetween(commitIdBefore, commitIdAfter);
                                project = astH.handle(astH.buildSpec(srcSpec, commitIdBefore, true));
                                printThings(releases, commitIdBefore, project);
                                for (Project<?> x : project.getModules()) {
                                    printThings(releases, commitIdBefore, x);
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                                logger.info("failed statistics " + releases.get(0));
                                break;
                            } finally {
                                System.out.flush();

                                // Close System.out for this thread which will
                                // flush and close this thread's text file.
                                System.out.close();
                                System.err.close();
                            }
                            // if (project != null) {
                            // break;
                            // }
                        }
                        logger2.info("(submit end) CLI status " + Long.toString(executor.getTaskCount()) + " "
                                + Integer.toString(executor.getActiveCount()) + " "
                                + Long.toString(executor.getCompletedTaskCount()));

                        return 0;
                    } catch (Exception e) {
                        String tmp = e.getMessage();
                        e.printStackTrace();
                        return 1;
                    } finally {
                    }
                });
            } else {
                System.out.println("no commits for " + releases.get(0));
            }
            logger.info("(launch end) CLI status " + Long.toString(executor.getTaskCount()) + " "
                    + Integer.toString(executor.getActiveCount()) + " "
                    + Long.toString(executor.getCompletedTaskCount()));
        });
        System.out.println("Shutdown");
        executor.shutdown();
        try {
            System.out.println("almost");
            while (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
            }
            System.out.println("done");
            impactH.close();
            evoH.close();
            coevoH.close();
            executor.shutdownNow();
        } catch (InterruptedException e) {
            executor.shutdownNow();
        } catch (Exception e) {
            executor.shutdownNow();
        }
        // try {
        // System.out.println("wait");
        // executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
        // System.out.println("done");
        // executor.shutdownNow();
        // Thread.currentThread().interrupt();
        // } catch (InterruptedException e) {
        // executor.shutdownNow();
        // throw new RuntimeException(e);
        // }
    }

    private static void printThings(List<String> releases, String commitIdBefore, Project<?> project) {
        Project<?>.AST ast = project.getAst();
        if (ast instanceof SpoonAST && ((SpoonAST) ast).launcher == null) {
            logger.info("Spoon failed to analyize " + releases.get(0));
        } else if (ast instanceof SpoonAST) {
            CtModel model = ((SpoonAST) ast).launcher.getModel();
            logger.info(
                    "done statistics " + releases.get(0) + "/commit/" + commitIdBefore + "/" + ast.rootDir.toString());
            logger.info("modules in pom: " + ((SpoonAST) ast).launcher.getPomFile().getModel().getModules().stream()
                    .reduce("", (a, b) -> a + "," + b));
            logger.info("modules parsed: "
                    + model.getAllModules().stream().map(x -> x.getSimpleName()).reduce("", (a, b) -> a + "," + b));
            logger.info("statistics: " + ast.getGlobalStats().toString());
        }
    }

    private static void batch(Stream<String> lines, int pool_size, int max_commits_impacts) {
        ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(pool_size);
        SourcesHandler srcH = new SourcesHandler();
        ProjectHandler astH = new ProjectHandler(srcH);
        EvolutionHandler evoH = new EvolutionHandler(srcH, astH);
        ImpactHandler impactH = new ImpactHandler(srcH, astH, evoH);
        CoEvolutionHandler coevoH = new CoEvolutionHandler(srcH, astH, evoH, impactH);
        System.out.println("Starting");
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
                    while (commit_index < s.size() && impact_computed < max_commits_impacts) {

                        while (commit_index < s.size()) {
                            commit_index++;
                            commitIdAfter = s.get(commit_index);
                            commitIdBefore = s.get(commit_index + 1);
                            try { // https://github.com/chrisbanes/Android-PullToRefresh/commit/1f7a7e1daf89167b11166180d96bac54a9306c80
                                  // evos = spoon compile + count tests/methods/class
                                Sources src = srcH.handle(srcSpec, "JGit");
                                src.getCommitsBetween(commitIdBefore, commitIdAfter);
                                Project<CtElement> project = astH.handle(astH.buildSpec(srcSpec, commitIdBefore, true));
                                printThings(s, commitIdBefore, project);
                                for (Project<?> x : project.getModules()) {
                                    printThings(s, commitIdBefore, x);
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                                logger.info("failed statistics " + s.get(0));
                                break;
                            }
                            try {
                                evos = evoH.handle(evoH.buildSpec(srcSpec, commitIdBefore, commitIdAfter));
                                logger.info("done evolution analysis " + s.get(0));
                            } catch (Exception e) {
                                logger.log(Level.WARNING, "failed evolution analysis " + s.get(0), e);
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
                                // Impacts impacts = impactH.handle(impactH.buildSpec(astH.buildSpec(srcSpec,
                                // commitIdBefore),
                                // EvolutionHandler.buildSpec(srcSpec, commitIdBefore, commitIdAfter)));
                                // System.out
                                // .println(Integer.toString(impacts.getPerRootCause().size()) + " impacts found
                                // for "
                                // + s.get(0) + " from " + commitIdBefore + " to " + commitIdAfter);
                                CoEvolutions coevo = coevoH.handle(CoEvolutionHandler.buildSpec(srcSpec,
                                        EvolutionHandler.buildSpec(srcSpec, commitIdBefore, commitIdAfter)));
                                System.out.println(
                                        Integer.toString(coevo.getValidated().size()) + " coevolutions found for "
                                                + s.get(0) + " from " + commitIdBefore + " to " + commitIdAfter);
                            } catch (Exception e) {
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
            } else {
                System.out.println("no commits for " + s.get(0));
            }
            logger.info("(launch end) CLI status " + Long.toString(executor.getTaskCount()) + " "
                    + Integer.toString(executor.getActiveCount()) + " "
                    + Long.toString(executor.getCompletedTaskCount()));
        });
        System.out.println("Shutdown");
        executor.shutdown();
        try {
            System.out.println("almost");
            while (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
            }
            System.out.println("done");
            impactH.close();
            evoH.close();
            coevoH.close();
            executor.shutdownNow();
        } catch (InterruptedException e) {
            executor.shutdownNow();
        } catch (Exception e) {
            executor.shutdownNow();
        }
        // try {
        // System.out.println("wait");
        // executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
        // System.out.println("done");
        // executor.shutdownNow();
        // Thread.currentThread().interrupt();
        // } catch (InterruptedException e) {
        // executor.shutdownNow();
        // throw new RuntimeException(e);
        // }
    }

    private static void SimpleBatch(Stream<String> lines, int pool_size, int max_commits_impacts) {
        ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(pool_size);
        SourcesHandler srcH = new SourcesHandler();
        ProjectHandler astH = new ProjectHandler(srcH);
        EvolutionHandler evoH = new EvolutionHandler(srcH, astH);
        ImpactHandler impactH = new ImpactHandler(srcH, astH, evoH);
        CoEvolutionHandler coevoH = new CoEvolutionHandler(srcH, astH, evoH, impactH);
        System.out.println("Starting");
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
                    while (commit_index < s.size() && impact_computed < max_commits_impacts) {

                        while (commit_index < s.size()) {
                            commit_index++;
                            commitIdAfter = s.get(commit_index);
                            commitIdBefore = s.get(commit_index + 1);

                            try { // https://github.com/chrisbanes/Android-PullToRefresh/commit/1f7a7e1daf89167b11166180d96bac54a9306c80
                                  // evos = spoon compile + count tests/methods/class
                                Sources src = srcH.handle(srcSpec, "JGit");
                                src.getCommitsBetween(commitIdBefore, commitIdAfter);
                                Project<CtElement> project = astH.handle(astH.buildSpec(srcSpec, commitIdBefore, true));
                                printThings(s, commitIdBefore, project);
                                for (Project<?> x : project.getModules()) {
                                    printThings(s, commitIdBefore, x);
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                                logger.info("failed statistics " + s.get(0));
                                break;
                            }

                            try {
                                evos = evoH.handle(evoH.buildSpec(srcSpec, commitIdBefore, commitIdAfter));
                                logger.info("done evolution analysis " + s.get(0));
                            } catch (Exception e) {
                                logger.log(Level.WARNING, "failed evolution analysis " + s.get(0), e);
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
                                Impacts impacts = impactH.handle(impactH.buildSpec(
                                        astH.buildSpec(srcSpec, commitIdBefore), EvolutionHandler.buildSpec(srcSpec,
                                                commitIdBefore, commitIdAfter, RefactoringMiner.class)));
                                System.out.println(
                                        Integer.toString(impacts.getPerRootCause().size()) + " impacts found for "
                                                + s.get(0) + " from " + commitIdBefore + " to " + commitIdAfter);
                            } catch (Exception e) {
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
            impactH.close();
            evoH.close();
            coevoH.close();
            executor.shutdownNow();
        } catch (InterruptedException e) {
            executor.shutdownNow();
        } catch (Exception e) {
            executor.shutdownNow();
        }
    }


    private static void batchFromBDD(int pool_size, int max_commits, Map<String,Object> thresholds, Map<String,Object> sortings) {
        ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(pool_size);
        SourcesHandler srcH = new SourcesHandler();
        ProjectHandler astH = new ProjectHandler(srcH);
        EvolutionHandler evoH = new EvolutionHandler(srcH, astH);
        ImpactHandler impactH = new ImpactHandler(srcH, astH, evoH);
        CoEvolutionHandler coevoH = new CoEvolutionHandler(srcH, astH, evoH, impactH);
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
                    while (commit_index < s.size() && impact_computed < max_commits) {

                        while (commit_index < s.size()) {
                            commit_index++;
                            commitIdAfter = s.get(commit_index);
                            commitIdBefore = s.get(commit_index + 1);

                            try { // https://github.com/chrisbanes/Android-PullToRefresh/commit/1f7a7e1daf89167b11166180d96bac54a9306c80
                                  // evos = spoon compile + count tests/methods/class
                                Sources src = srcH.handle(srcSpec, "JGit");
                                src.getCommitsBetween(commitIdBefore, commitIdAfter);
                                Project<CtElement> project = astH.handle(astH.buildSpec(srcSpec, commitIdBefore, true));
                                printThings(s, commitIdBefore, project);
                                for (Project<?> x : project.getModules()) {
                                    printThings(s, commitIdBefore, x);
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                                logger.info("failed statistics " + s.get(0));
                                break;
                            }

                            try {
                                evos = evoH.handle(evoH.buildSpec(srcSpec, commitIdBefore, commitIdAfter));
                                logger.info("done evolution analysis " + s.get(0));
                            } catch (Exception e) {
                                logger.log(Level.WARNING, "failed evolution analysis " + s.get(0), e);
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
                                Impacts impacts = impactH.handle(impactH.buildSpec(
                                        astH.buildSpec(srcSpec, commitIdBefore), EvolutionHandler.buildSpec(srcSpec,
                                                commitIdBefore, commitIdAfter, RefactoringMiner.class)));
                                System.out.println(
                                        Integer.toString(impacts.getPerRootCause().size()) + " impacts found for "
                                                + s.get(0) + " from " + commitIdBefore + " to " + commitIdAfter);
                            } catch (Exception e) {
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
            impactH.close();
            evoH.close();
            coevoH.close();
            executor.shutdownNow();
        } catch (InterruptedException e) {
            executor.shutdownNow();
        } catch (Exception e) {
            executor.shutdownNow();
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
                } catch (Exception e) {
                    for (CategorizedProblem pb : ((JDTBasedSpoonCompiler) launcher.getModelBuilder()).getProblems()) {
                        logger.info(pb.toString());
                    }
                    throw new RuntimeException("Error while building the Spoon model", e);
                }

                return Integer.toString(launcher.getFactory().Type().getAll().size());
                // .stream().map(x -> x.getQualifiedName()).reduce("",
                // (a, b) -> a + " " + b);

            } catch (Exception e) {
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
            SourcesHelper.prepare(path);

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

    private static File makeFile(String name, String s) throws IOException {
        // Create temp file.
        File temp = File.createTempFile("tempchdist" + name, ".java");

        // Write to temp file
        BufferedWriter out = new BufferedWriter(new FileWriter(temp));
        out.write(s);
        out.close();
        return temp;
    }
}