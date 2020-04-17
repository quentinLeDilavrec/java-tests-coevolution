package fr.quentin;

import org.eclipse.jdt.core.compiler.CategorizedProblem;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.ErrorCollector;
import org.junit.rules.ExternalResource;
import org.junit.ClassRule;
import org.junit.experimental.categories.Categories;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.refactoringminer.api.GitHistoryRefactoringMiner;
import org.refactoringminer.api.Refactoring;
import org.refactoringminer.api.RefactoringHandler;
import org.refactoringminer.api.RefactoringType;
import org.refactoringminer.rm1.GitHistoryRefactoringMinerImpl;

import fr.quentin.AppTest.MoveMethodEvolution;
import fr.quentin.utils.FilePathFilter;
import fr.quentin.utils.SourcesHelper;
import spoon.MavenLauncher;
import spoon.reflect.cu.SourcePosition;
import spoon.support.compiler.jdt.JDTBasedSpoonCompiler;
import spoon.template.Parameter;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

@RunWith(Parameterized.class)
public class ParamSpoonTest {

    static class RefactoringOriginal {
        String type;
        String description;
        String comment;
        String validation;
        String detectionTools;
        String validators;
    }

    static class CaseOriginal {
        int id;
        String repository;
        String sha1;
        String url;
        String author;
        String time;
        RefactoringOriginal[] refactorings;
        int refDiffExecutionTime;
    }

    static class Case {
        String id;
        String repository;
        String before; // left null by the prepocessor, mean consecutive commit, so it is resolved by
                       // getParent(after)
        String after; // correspond to sha1 in the original
        String error;
        String success;
        String author;
        String time;
        Collection<Evolution> evolutions;

        @Override
        public String toString() {
            return repository + (before != null ? ";" + before : "") + ";" + after;
        }
    }

    static class Evolution { // by default refminer seem consider ref per consecutive commits
        String type;
        Collection<Position> beforePositions;
        String description;
        String before; // idem
        String after;
        String comment;
        String validation;
        String validators;
        List<CoEvolution> resolutions;
    }

    static class CoEvolution {
        Collection<Position> impactPositions;
        String description;
        String comment;
        String validation;
        String validators;
    }

    public static void main(String[] args) throws FileNotFoundException, IOException {
        List<CaseOriginal> original = parse("data_original.json", new TypeToken<List<CaseOriginal>>() {
        }.getType());
        List<Case> preprocessed = preprocessDatabase(original);
        sortDatabase(preprocessed);
        // caution overide data_processed.json
        serialize("data_processed.json", preprocessed, new TypeToken<List<Case>>() {
        }.getType());
    }

    private static List<Case> preprocessDatabase(List<CaseOriginal> original)
            throws FileNotFoundException, IOException {
        List<Case> r = new ArrayList<>();
        for (CaseOriginal ori : original) {
            Case c = new Case();
            r.add(c);
            c.repository = ori.repository;
            c.after = ori.sha1;
            c.time = ori.time;
            c.author = ori.author;
            c.evolutions = new ArrayList<>();
            for (RefactoringOriginal ref : ori.refactorings) {
                Evolution e = new Evolution();
                c.evolutions.add(e);
                e.comment = ref.comment;
                e.after = ori.sha1;
                e.description = ref.description;
                e.type = ref.type;
                e.validation = ref.validation;
                e.validators = ref.validators;
            }
        }
        return r;
    }

    private static void sortDatabase(List<Case> preprocessed) throws FileNotFoundException, IOException {
        preprocessed.sort(new Comparator<Case>() {

            @Override
            public int compare(Case o1, Case o2) {
                int c1 = 0;
                int c2 = 0;
                if (o1.error != null) {
                    c1 = -2;
                }
                if (o2.error != null) {
                    c2 = -2;
                }
                if (o1.success != null) {
                    c1 = -1;
                }
                if (o2.success != null) {
                    c2 = -1;
                }
                if (o1.evolutions == null) {
                    c1 = 10;
                }
                if (o2.evolutions == null) {
                    c2 = 10;
                }
                if (c1 == 0 && c2 == 0) {
                    for (Evolution r : o1.evolutions) {
                        if (r.type.equals("Move Method") && r.validation != null && !r.validation.equals("FP")) {
                            c1 += 1;
                        }
                    }
                    for (Evolution r : o2.evolutions) {
                        if (r.type.equals("Move Method") && r.validation != null && !r.validation.equals("FP")) {
                            c2 += 1;
                        }
                    }
                }
                return c2 - c1;
            }

        });
    }

    private static List<Case> preprocessadditionalCases(List<String> more) throws FileNotFoundException, IOException {
        List<Case> r = new ArrayList<>();
        for (String m : more) {
            String[] elems = m.split(";");

            Case c = new Case();
            r.add(c);
            c.author = "Quentin";
            if (elems.length == 2) {
                c.after = elems[1];
            } else {
                c.before = elems[1];
                c.after = elems[2];
            }
            c.repository = elems[0];
        }
        return r;
    }

    @Parameters(name = "{index}: {0}")
    public static List<Case[]> data() throws JsonSyntaxException, IOException {
        List<Case> more = preprocessadditionalCases(Arrays.asList(
        // "https://github.com/quentinLeDilavrec/interacto-java-api.git;d022a91c49378cd182d6b1398dad3939164443b4;3bf9a6d0876fc5c99221934a5ecd161ea51204f0",
        // "https://github.com/INRIA/spoon.git;4b42324566bdd0da145a647d136a2f555c533978;904fb1e7001a8b686c6956e32c4cc0cdb6e2f80b"
        ));
        List<Case> alreadyProcessed = parse("data_processed.json", new TypeToken<List<Case>>() {
        }.getType());
        alreadyProcessed.addAll(more);
        alreadyProcessed.sort(new Comparator<Case>() {

            @Override
            public int compare(Case o1, Case o2) {
                int c1 = 0;
                int c2 = 0;
                if (o1.error != null) {
                    c1 = -2;
                }
                if (o2.error != null) {
                    c2 = -2;
                }
                if (o1.success != null) {
                    c1 = -1;
                }
                if (o2.success != null) {
                    c2 = -1;
                }
                if (o1.evolutions == null) {
                    c1 = 10;
                }
                if (o2.evolutions == null) {
                    c2 = 10;
                }
                if (c1 == 0 && c2 == 0) {
                    for (Evolution r : o1.evolutions) {
                        if (r.type.equals("Move Method") && r.validation != null && !r.validation.equals("FP")) {
                            c1 += 1;
                        }
                    }
                    for (Evolution r : o2.evolutions) {
                        if (r.type.equals("Move Method") && r.validation != null && !r.validation.equals("FP")) {
                            c2 += 1;
                        }
                    }
                }
                return c2 - c1;
            }

        });
        cases = alreadyProcessed.subList(more.size() + 5, alreadyProcessed.size());
        System.out.println(alreadyProcessed.subList(0, more.size() + 3));
        List<Case[]> r = new ArrayList<>();
        for (Case c : alreadyProcessed.subList(0, more.size() + 5)) {
            r.add(new Case[] { c });
        }
        return r;
    }

    private static List<Case> cases;

    private static Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static <T> T parse(String path, Type type) throws FileNotFoundException, IOException {
        try (JsonReader reader = GSON.newJsonReader(new FileReader(path))) {
            return (T) GSON.fromJson(reader, type);
        }
    }

    private static <T> void serialize(String path, T ele, Type klass) throws IOException {
        try (JsonWriter writer = GSON.newJsonWriter(new FileWriter(path))) {
            GSON.toJson(ele, klass, writer);
        }
    }

    @ClassRule
    public static ExternalResource externalClassResource = new ExternalResource() {
        protected void before() throws Throwable {
            // cases = new ArrayList<>();
        }

        protected void after() {
            try {
                serialize("data_processed.json", cases, new TypeToken<List<Case>>() {
                }.getType());
            } catch (JsonSyntaxException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    };

    private final Case evoCase;

    public ParamSpoonTest(Case evoCase) {
        this.evoCase = evoCase;
    }

    @Rule
    public ExternalResource externalResource = new ExternalResource() {

        protected void before() throws Throwable {
            System.out.println("before");
        }

        protected void after() {
            System.out.println("after");
        }
    };
    @Rule
    public ErrorCollector collector = new ErrorCollector();

    @Test
    public void spoonShouldBuildModel() throws Exception {
        System.out.println("getting " + evoCase.repository + " " + evoCase.after);
        try (SourcesHelper helper = new SourcesHelper(evoCase.repository);) {
            if (evoCase.before == null) {
                evoCase.before = helper.getBeforeCommit(evoCase.after);
            }
            Path path = helper.materialize(evoCase.before, new FilePathFilter() {

                @Override
                public boolean isAllowed(String filePath) {
                    if (filePath.equals("src/main/java/module-info.java")) {
                        return false;
                    } else {
                        return true;
                    }
                }

            });

            // Compile with maven to get deps
            try {
                SourcesHelper.prepare(path);
            } catch (Exception e) {
                collector.addError(e);
            }

            MavenLauncher launcher = new MavenLauncher(path.toString(), MavenLauncher.SOURCE_TYPE.ALL_SOURCE);
            launcher.getEnvironment().setComplianceLevel(11);
            launcher.getEnvironment().setShouldCompile(true);
            launcher.getPomFile().getModel().getModules();
            // launcher.getEnvironment().setIgnoreDuplicateDeclarations(true);
            launcher.getEnvironment().setLevel("INFO");
            launcher.getFactory().getEnvironment().setLevel("INFO");

            // Build Spoon model
            try {
                launcher.buildModel();
                System.out.println("model built");
            } catch (Throwable e) {
                throw e;
            }

            Collection<MoveMethodEvolution> evolutions = new ArrayList<>();
            try {
                assertNotEquals("At least one top-level type should exist.",
                        launcher.getFactory().Type().getAll().size(), 0);
            } catch (Throwable e) {
                collector.addError(e);
            }
            GitHistoryRefactoringMiner miner = new GitHistoryRefactoringMinerImpl();
            try {
                miner.detectBetweenCommits(helper.getRepo(), evoCase.before, evoCase.after, new RefactoringHandler() {
                    @Override
                    public void handle(String commitId, List<Refactoring> refactorings) {
                        String before;
                        try {
                            before = helper.getBeforeCommit(commitId);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                        for (Refactoring op : refactorings) {
                            if (op.getRefactoringType().equals(RefactoringType.MOVE_OPERATION)) {
                                MoveMethodEvolution tmp = new MoveMethodEvolution(path.toAbsolutePath().toString(), op,
                                        before, commitId);
                                evolutions.add(tmp);
                            }
                        }
                    }
                });
            } catch (Exception e) {
                collector.addError(e);
            }

            if (evoCase.evolutions == null) {
                evoCase.evolutions = new ArrayList<>();
                for (MoveMethodEvolution mme : evolutions) {
                    Evolution e = new Evolution();
                    evoCase.evolutions.add(e);
                    e.description = mme.getOriginal().toString();
                    e.beforePositions = mme.getPreEvolutionPositions();
                    e.type = "Move Method";
                    e.before = mme.getCommitIdBefore();
                    e.after = mme.getCommitIdAfter();
                }
            } else {

            }

            evoCase.success = "spoon model was build";
            cases.add(evoCase);
        } catch (Throwable e) {
            evoCase.error = e.toString();
            cases.add(evoCase);
            throw e;
        } finally {
            try {
                serialize("data_processed.json", cases, new TypeToken<List<Case>>() {
                }.getType());
            } catch (JsonSyntaxException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

}