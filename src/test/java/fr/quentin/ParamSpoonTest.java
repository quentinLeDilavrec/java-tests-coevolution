package fr.quentin;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.experimental.categories.Categories;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

@RunWith(Parameterized.class)
public class ParamSpoonTest {

    static class Refactorings {
        String type;
        String description;
        String comment;
        String validation;
        String detectionTools;
        String validators;
    }

    static class Case {
        int id;
        String repository;
        String sha1;
        String url;
        String author;
        String time;
        Refactorings[] refactorings;
    }

    @Parameters
    public static Collection<Object[]> data() throws JsonSyntaxException, IOException {
        List<Object[]> r = new ArrayList<>();
        Case[] body = new Gson().fromJson(new String(Files.readAllBytes(Paths.get("data.json"))), Case[].class);
        for (Case c : body) {
            r.add(new String[] { c.repository, c.sha1 });
        }
        return r;
    }

    private final String gitURL;
    private final String commitId;

    public ParamSpoonTest(String first, String second) {

        this.gitURL = first;
        this.commitId = second;
    }

    @Test
    public void shouldReturnCorrectSum() {
        CLI.ast(gitURL, commitId);
    }
}