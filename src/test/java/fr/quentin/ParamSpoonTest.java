package fr.quentin;

import org.eclipse.jdt.core.compiler.CategorizedProblem;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExternalResource;
import org.junit.experimental.categories.Categories;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import fr.quentin.utils.SourcesHelper;
import spoon.MavenLauncher;
import spoon.reflect.cu.SourcePosition;
import spoon.support.compiler.jdt.JDTBasedSpoonCompiler;
import spoon.template.Parameter;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

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
        int id;
        String repository;
        String before;
        String after;
        String author;
        String time;
        Collection<Evolution> evolutions;
        Collection<CoEvolution> coevolutions;
    }

    static class Evolution {
        String type;
        Position position;
        String description;
        String comment;
        String validation;
        String validators;
    }

    static class CoEvolution {
        Collection<Position> causes;
        String description;
        String comment;
        String validation;
        String validators;
    }

    @Parameters(name = "{index}: {0};{1}")
    public static Collection<Object[]> data() throws JsonSyntaxException, IOException {
        List<Object[]> r = new ArrayList<>();
        List<CaseOriginal> body = Arrays.asList(new Gson().fromJson(new String(Files.readAllBytes(Paths.get("data_original.json"))), CaseOriginal[].class));
        body.sort(new Comparator<CaseOriginal>(){

            @Override
            public int compare(CaseOriginal o1, CaseOriginal o2) {
                int c1 = 0;
                for (RefactoringOriginal r : o1.refactorings) {
                    if (r.type.equals("Move Method") && !r.validation.equals("FP")) {
                        c1+=1;
                    }
                }
                int c2 = 0;
                for (RefactoringOriginal r : o2.refactorings) {
                    if (r.type.equals("Move Method") && !r.validation.equals("FP")) {
                        c2+=1;
                    }
                }
                return c2-c1;
            }
            
        });
        for (CaseOriginal c : body) {
            r.add(new String[] { c.repository, c.sha1 });
        }
        System.out.println(r.subList(0, 2));
        return r.subList(0, 10);
    }

    private final String gitURL;
    private final String commitId;

    public ParamSpoonTest(String gitURL, String commitId) {
        this.gitURL = gitURL;
        this.commitId = commitId;
    }

    // @Rule
    // public ExternalResource externalResource = new ExternalResource() {
    //     protected void before() throws Throwable { System.out.println("before"); }
    //     protected void after() { System.out.println("after"); }
    // };


    @Test
    public void spoonShouldBuildModel() throws Exception {
        System.out.println("getting "+gitURL+" "+commitId);
        try (SourcesHelper helper = new SourcesHelper(gitURL);) {
            Path path = helper.materializePrev(commitId);
            MavenLauncher launcher = new MavenLauncher(path.toString(), MavenLauncher.SOURCE_TYPE.ALL_SOURCE);
            launcher.getEnvironment().setLevel("INFO");
            launcher.getFactory().getEnvironment().setLevel("INFO");

            // Compile with maven to get deps
            SourcesHelper.prepare(path);

            // Build Spoon model
            launcher.buildModel();

            assertNotEquals("At least one top-level type should exist.",launcher.getFactory().Type().getAll().size(),0);
        }
    }
}