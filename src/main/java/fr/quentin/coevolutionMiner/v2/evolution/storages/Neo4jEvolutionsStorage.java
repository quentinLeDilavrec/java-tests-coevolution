package fr.quentin.coevolutionMiner.v2.evolution.storages;

import org.apache.felix.resolver.util.ArrayMap;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Query;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.Transaction;
import org.neo4j.driver.TransactionWork;
import org.neo4j.driver.exceptions.TransientException;

import fr.quentin.coevolutionMiner.utils.MyProperties;
// import fr.quentin.impactMiner.Evolution;
import fr.quentin.coevolutionMiner.v2.evolution.Evolutions;
import fr.quentin.coevolutionMiner.v2.evolution.Evolutions.Evolution;
import fr.quentin.coevolutionMiner.v2.evolution.Evolutions.Specifier;
import fr.quentin.coevolutionMiner.v2.evolution.Evolutions.Evolution.DescRange;
import fr.quentin.coevolutionMiner.v2.sources.Sources;
import fr.quentin.coevolutionMiner.v2.sources.Sources.Commit;
import gr.uom.java.xmi.diff.CodeRange;
import spoon.reflect.declaration.CtElement;
import fr.quentin.coevolutionMiner.v2.evolution.EvolutionsStorage;

import static org.neo4j.driver.Values.parameters;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class Neo4jEvolutionsStorage implements EvolutionsStorage {

    @Override
    public void put(Specifier evos_spec, Evolutions value) {
        way2(evos_spec, value);
    }

    private void way2(Specifier evos_spec, Evolutions value) {
        List<Object> tmp = new ArrayList<>();
        for (Evolution evolution : value) {
            tmp.add(basifyEvo(evos_spec.sources.repository, evolution));
        }
        List<Map<String, Object>> commits = new ArrayList<>();
        try {
            for (Commit commit : value.getSources().getCommitsBetween(evos_spec.commitIdBefore,
                    evos_spec.commitIdAfter)) {
                Map<String, Object> o = new HashMap<>();
                o.put("repository", commit.getRepository().getUrl());
                o.put("sha1", commit.getId());
                o.put("children", commit.getChildrens().stream().map(x -> x.getId()).collect(Collectors.toList()));
                o.put("parents", commit.getParents().stream().map(x -> x.getId()).collect(Collectors.toList()));
            }
        } catch (Exception e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        }
        try (Session session = driver.session()) {
            String done = session.writeTransaction(new TransactionWork<String>() {
                @Override
                public String execute(Transaction tx) {
                    Result result = tx.run(getCypher(),
                            parameters("json", tmp, "tool", evos_spec.miner.getSimpleName() + 2));
                    result.consume();
                    Result result2 = tx.run(getCommitCypher(), parameters("commits", commits));
                    result2.consume();
                    return "done evolution on " + value.spec.sources.repository;
                }
            });
            System.out.println(done);
        } catch (TransientException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String formatedType(Object original) {
        String name = original.getClass().getSimpleName();
        return name.endsWith("Impl") ? name.substring(0, name.length() - "Impl".length()) : name;
    }

    public static String makeEvoUrl(String repository, Evolution evolution) {
        Map<String, EvoType> evoTypesByName = getCRefactoringTypes();
        // Refactoring ori = (Refactoring) evolution.getOriginal();

        StringBuilder url = new StringBuilder();
        url.append("http://176.180.199.146:50000/?repo=" + repository);
        url.append("&before=" + evolution.getCommitBefore().getId());
        url.append("&after=" + evolution.getCommitAfter().getId());
        url.append("&type=" + evolution.getType());
        EvoType aaa = evoTypesByName.get(evolution.getType());
        Map<String, List<String>> before_e = new HashMap<>();
        List<Object> leftSideLocations = new ArrayList<>();
        for (DescRange e : evolution.getBefore()) {
            Map<String, Object> o = new HashMap<>();
            leftSideLocations.add(o);
            o.put("filePath", e.getTarget().getFile().getPath());
            o.put("start", e.getTarget().getStart());
            o.put("end", e.getTarget().getEnd());
            CtElement e_ori = (CtElement) e.getTarget().getOriginal();
            if (e_ori != null) {
                o.put("type", formatedType(e_ori));
            } else {
                o.put("type", "evo null");
            }
            o.put("description", e.getDescription());
            e.getDescription();
            for (int i = 0; i < aaa.left.size(); i++) {
                if (aaa.left.get(i).description.equals(e.getDescription())) {
                    String tmp = e.getTarget().getFile().getPath() + ":" + Integer.toString(e.getTarget().getStart()) + "-"
                            + Integer.toString(e.getTarget().getEnd());
                    String key = Integer.toString(i);
                    List<String> tmp2 = before_e.getOrDefault(key, new ArrayList<>());
                    tmp2.add(tmp);
                    before_e.put(key, tmp2);
                    break;
                }
            }
        }
        for (Entry<String, List<String>> entry : before_e.entrySet()) {
            entry.getValue().sort(new Comparator<String>() {
                @Override
                public int compare(String a, String b) {
                    return a.compareTo(b);
                }
            });
            for (String s : entry.getValue()) {
                url.append("&before" + entry.getKey() + "=" + s);
            }
        }
        Map<String, List<String>> after_e = new HashMap<>();
        List<Object> rightSideLocations = new ArrayList<>();
        for (DescRange e : evolution.getAfter()) {
            Map<String, Object> o = new HashMap<>();
            rightSideLocations.add(o);
            o.put("filePath", e.getTarget().getFile().getPath());
            o.put("start", e.getTarget().getStart());
            o.put("end", e.getTarget().getEnd());
            CtElement e_ori = (CtElement) e.getTarget().getOriginal();
            if (e_ori != null) {
                o.put("type", formatedType(e_ori));
            } else {
                o.put("type", "evo null");
            }
            o.put("description", e.getDescription());
            e.getDescription();
            for (int i = 0; i < aaa.right.size(); i++) {
                if (aaa.right.get(i).description.equals(e.getDescription())) {
                    String tmp = e.getTarget().getFile().getPath() + ":" + Integer.toString(e.getTarget().getStart()) + "-"
                            + Integer.toString(e.getTarget().getEnd());
                    String key = Integer.toString(i);
                    List<String> tmp2 = after_e.getOrDefault(key, new ArrayList<>());
                    tmp2.add(tmp);
                    after_e.put(key, tmp2);
                    break;
                }
            }
        }
        for (Entry<String, List<String>> entry : after_e.entrySet()) {
            entry.getValue().sort(new Comparator<String>() {

                @Override
                public int compare(String a, String b) {
                    return a.compareTo(b);
                }
            });
            for (

            String s : entry.getValue()) {
                url.append("&after" + entry.getKey() + "=" + s);
            }
        }
        return url.toString();
    }

    private static class Side {
        public Boolean many;
        public String description;
    }

    public static class EvoType {
        public String name;
        public String displayName;
        public List<Side> left;
        public List<Side> right;
    }

    public static Map<String, Object> basifyEvo(String repository, Evolution evolution) {
        Map<String, EvoType> evoTypesByName = getCRefactoringTypes();
        Map<String, Object> res = new HashMap<>();
        Map<String, Object> evofields = new HashMap<>();
        res.put("content", evofields);
        evofields.put("repository", repository);
        evofields.put("commitIdBefore", evolution.getCommitBefore().getId());
        evofields.put("commitIdAfter", evolution.getCommitAfter().getId());
        // Refactoring ori = (Refactoring) evolution.getOriginal();
        evofields.put("type", evolution.getType());

        StringBuilder url = new StringBuilder();
        url.append("http://176.180.199.146:50000/?repo=" + repository);
        url.append("&before=" + evolution.getCommitBefore().getId());
        url.append("&after=" + evolution.getCommitAfter().getId());
        url.append("&type=" + evolution.getType());
        EvoType aaa = evoTypesByName.get(evolution.getType());
        Map<String, List<String>> before_e = new HashMap<>();
        List<Object> leftSideLocations = new ArrayList<>();
        res.put("leftSideLocations", leftSideLocations);
        for (DescRange e : evolution.getBefore()) {
            Map<String, Object> o = new HashMap<>();
            leftSideLocations.add(o);
            o.put("filePath", e.getTarget().getFile().getPath());
            o.put("start", e.getTarget().getStart());
            o.put("end", e.getTarget().getEnd());
            CtElement e_ori = (CtElement) e.getTarget().getOriginal();
            if (e_ori != null) {
                o.put("type", formatedType(e_ori));
            } else {
                o.put("type", "evo null");
            }
            o.put("description", e.getDescription());
            e.getDescription();

            for (int i = 0; i < aaa.left.size(); i++) {
                if (aaa.left.get(i).description.equals(e.getDescription())) {
                    String tmp = e.getTarget().getFile().getPath() + ":" + Integer.toString(e.getTarget().getStart()) + "-"
                            + Integer.toString(e.getTarget().getEnd());
                    String key = Integer.toString(i);
                    List<String> tmp2 = before_e.getOrDefault(key, new ArrayList<>());
                    tmp2.add(tmp);
                    before_e.put(key, tmp2);
                    break;
                }
            }
        }
        for (Entry<String, List<String>> entry : before_e.entrySet()) {
            entry.getValue().sort(new Comparator<String>() {
                @Override
                public int compare(String a, String b) {
                    return a.compareTo(b);
                }
            });
            evofields.put("before" + entry.getKey(), entry.getValue());
            for (String s : entry.getValue()) {
                url.append("&before" + entry.getKey() + "=" + s);
            }
        }
        Map<String, List<String>> after_e = new HashMap<>();
        List<Object> rightSideLocations = new ArrayList<>();
        res.put("rightSideLocations", rightSideLocations);
        for (DescRange e : evolution.getAfter()) {
            Map<String, Object> o = new HashMap<>();
            rightSideLocations.add(o);
            o.put("filePath", e.getTarget().getFile().getPath());
            o.put("start", e.getTarget().getStart());
            o.put("end", e.getTarget().getEnd());
            CtElement e_ori = (CtElement) e.getTarget().getOriginal();
            if (e_ori != null) {
                o.put("type", formatedType(e_ori));
            } else {
                o.put("type", "evo null");
            }
            o.put("description", e.getDescription());
            e.getDescription();
            for (int i = 0; i < aaa.right.size(); i++) {
                if (aaa.right.get(i).description.equals(e.getDescription())) {
                    String tmp = e.getTarget().getFile().getPath() + ":" + Integer.toString(e.getTarget().getStart()) + "-"
                            + Integer.toString(e.getTarget().getEnd());
                    String key = Integer.toString(i);
                    List<String> tmp2 = after_e.getOrDefault(key, new ArrayList<>());
                    tmp2.add(tmp);
                    after_e.put(key, tmp2);
                    break;
                }
            }
        }
        for (Entry<String, List<String>> entry : after_e.entrySet()) {
            entry.getValue().sort(new Comparator<String>() {

                @Override
                public int compare(String a, String b) {
                    return a.compareTo(b);
                }
            });
            evofields.put("after" + entry.getKey(), entry.getValue());
            for (

            String s : entry.getValue()) {
                url.append("&after" + entry.getKey() + "=" + s);
            }
        }
        evofields.put("url", url.toString());
        return res;
    }

    @Override
    public Evolutions get(Specifier impacts_spec) {
        // TODO Auto-generated method stub
        return null;
    }

    private final Driver driver;

    public Neo4jEvolutionsStorage(String uri, String user, String password) {
        driver = GraphDatabase.driver(uri, AuthTokens.basic(user, password));
        // driver.close(); // TODO should be closed at some point

    }

    public Neo4jEvolutionsStorage() {
        this(MyProperties.getPropValues().getProperty("neo4jAddress"),
                MyProperties.getPropValues().getProperty("neo4jId"),
                MyProperties.getPropValues().getProperty("neo4jPwd"));
    }

    private static String getCypher() {
        try {
            return new String(Files.readAllBytes(Paths.get(
                    Neo4jEvolutionsStorage.class.getClassLoader().getResource("evolutions_cypher.sql").getFile())));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected String getCommitCypher() {
        try {
            return new String(Files.readAllBytes(Paths
                    .get(Neo4jEvolutionsStorage.class.getClassLoader().getResource("commits_cypher.cql").getFile())));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static Map<String, EvoType> RefactoringTypes = null;

    public static Map<String, EvoType> getCRefactoringTypes() {
        if (RefactoringTypes != null) {
            return RefactoringTypes;
        }
        try {
            Map<String, EvoType> res = new Gson().fromJson(
                    new String(Files.readAllBytes(Paths.get(Neo4jEvolutionsStorage.class.getClassLoader()
                            .getResource("RefactoringTypes_named.json").getFile()))),
                    new TypeToken<Map<String, EvoType>>() {
                    }.getType());
            Map<String, EvoType> resByDN = new HashMap<>();
            for (Entry<String, EvoType> e : res.entrySet()) {
                e.getValue().name = e.getKey();
                resByDN.put(e.getValue().displayName, e.getValue());
            }
            return resByDN;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() throws Exception {
        driver.close();
    }
}