package fr.quentin;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

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

import fr.quentin.utils.ASTHelper;
import fr.quentin.utils.DiffHelper;
import fr.quentin.utils.SourcesHelper;
import gumtree.spoon.AstComparator;
import gumtree.spoon.diff.Diff;
import gumtree.spoon.diff.operations.Operation;
import spoon.MavenLauncher;
import spoon.support.compiler.jdt.JDTBasedSpoonCompiler;

/**
 * CLI
 */
public class CLI {
    static Logger logger = Logger.getLogger("ImpactRM commitHandler");

    public static void main(String[] args) {
        CommandLineParser parser = new DefaultParser();

        // create the Options
        Options options = new Options();
        options.addOption("r", "repo", true, "The repository where the code is located");

        try {
            if (args.length < 1) {
                throw new UnsupportedOperationException("use compare or ast");
            }
            CommandLine line = parser.parse(options, Arrays.copyOfRange(args, 1, args.length));
            if (Objects.equals(args[0], "ast")) {
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
                //         (a, b) -> a + " " + b);

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
                for (CategorizedProblem pb : ((JDTBasedSpoonCompiler) launcher.getModelBuilder())
                        .getProblems()) {
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