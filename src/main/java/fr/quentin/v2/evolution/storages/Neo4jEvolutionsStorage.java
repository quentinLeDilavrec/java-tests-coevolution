package fr.quentin.v2.evolution.storages;

import org.apache.felix.resolver.util.ArrayMap;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Query;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.Transaction;
import org.neo4j.driver.TransactionWork;
import org.refactoringminer.api.Refactoring;

import fr.quentin.Evolution;
import fr.quentin.v2.evolution.Evolutions;
import fr.quentin.v2.evolution.Evolutions.Specifier;
import fr.quentin.v2.sources.Sources;
import gr.uom.java.xmi.diff.CodeRange;
import fr.quentin.v2.evolution.EvolutionsStorage;

import static org.neo4j.driver.Values.parameters;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.reflect.TypeToken;

public class Neo4jEvolutionsStorage implements EvolutionsStorage {

    @Override
    public void put(Specifier impacts_spec, Evolutions value) {
        way2(impacts_spec, value);
    }

    
    private void way2(Specifier impacts_spec, Evolutions value) {
        try (Session session = driver.session()) {
            String done = session.writeTransaction(new TransactionWork<String>() {
                @Override
                public String execute(Transaction tx) {
                    Map<String, EvoType> evoTypesByName = getCRefactoringTypes();
                    List<Evolution<Refactoring>> a = value.toList();
                    List<Object> tmp = new ArrayList<>();
                    for (Evolution<Refactoring> evolution : a) {
                        tmp.add(basifyEvo(impacts_spec.sources, evolution, evoTypesByName));
                    }
                    Result result = tx.run(getCypher(), parameters("json", tmp, "tool", impacts_spec.miner));
                    result.consume();
                    return "done evolution";
                }
            });
            System.out.println(done);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static class Side {
        public Boolean many;
        public String description;
    }

    private static class EvoType {
        public String name;
        public String displayName;
        public List<Side> left;
        public List<Side> right;
    }

    private Object basifyEvo(Sources.Specifier sources, Evolution<Refactoring> evolution,
            Map<String, EvoType> evoTypesByName) {
        Map<String, Object> res = new HashMap<>();
        Map<String, Object> evofields = new HashMap<>();
        res.put("content", evofields);
        evofields.put("repository", sources.repository);
        evofields.put("commitIdBefore", evolution.getCommitIdBefore());
        evofields.put("commitIdAfter", evolution.getCommitIdAfter());
        Refactoring ori = evolution.getOriginal();
        evofields.put("type", ori.getRefactoringType().getDisplayName());

        StringBuilder url = new StringBuilder();
        url.append("http://176.180.199.146:50000/?repo=" + sources.repository);
        url.append("&before=" + evolution.getCommitIdBefore());
        url.append("&after=" + evolution.getCommitIdAfter());
        url.append("&type=" + ori.getRefactoringType().getDisplayName());
        EvoType aaa = evoTypesByName.get(ori.getRefactoringType().name());
        Map<String, List<String>> before_e = new HashMap<>();
        List<Object> leftSideLocations = new ArrayList<>();
        res.put("leftSideLocations", leftSideLocations);
        for (CodeRange e : ori.leftSide()) {
            Map<String, Object> o = new HashMap<>();
            leftSideLocations.add(o);
            o.put("filePath", e.getFilePath());
            o.put("start", e.getStartOffset());
            o.put("end", e.getEndOffset());
            String e_type = e.getCodeElementType().getName();
            if (e_type == null) {
                List<String> e_type_tmp = new ArrayList<>();
                for (String s : e.getCodeElementType().name().split("_")) {
                    e_type_tmp.add(s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase());
                }
                e_type = String.join(" ", e_type_tmp);
            }
            o.put("type", e_type);
            o.put("description", e.getDescription());
            for (int i = 0; i < aaa.left.size(); i++) {
                if (aaa.left.get(i).description.equals(e.getDescription())) {
                    String tmp = e.getFilePath() + ":" + Integer.toString(e.getStartOffset()) + "-"
                            + Integer.toString(e.getEndOffset());
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
        for (CodeRange e : ori.rightSide()) {
            Map<String, Object> o = new HashMap<>();
            rightSideLocations.add(o);
            o.put("filePath", e.getFilePath());
            o.put("start", e.getStartOffset());
            o.put("end", e.getEndOffset());
            String e_type = e.getCodeElementType().getName();
            if (e_type == null) {
                List<String> e_type_tmp = new ArrayList<>();
                for (String s : e.getCodeElementType().name().split("_")) {
                    e_type_tmp.add(s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase());
                }
                e_type = String.join(" ", e_type_tmp);
            }
            o.put("type", e_type);
            o.put("description", e.getDescription());
            for (int i = 0; i < aaa.right.size(); i++) {
                if (aaa.right.get(i).description.equals(e.getDescription())) {
                    String tmp = e.getFilePath() + ":" + Integer.toString(e.getStartOffset()) + "-"
                            + Integer.toString(e.getEndOffset());
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
            for (String s : entry.getValue()) {
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
        this("bolt://localhost:7687", "neo4j", "neo4j");
    }
    private void way1(Specifier impacts_spec, Evolutions value) {
        try (Session session = driver.session()) {
            String done = session.writeTransaction(new TransactionWork<String>() {
                @Override
                public String execute(Transaction tx) {
                    Object tmp = basify(value.toJson().getAsJsonObject().get("commits").getAsJsonArray().get(0));
                    if (tmp instanceof Map) {
                        Map<String, Object> tmp2 = (Map) tmp;
                        tmp2.put("commitIdBefore", impacts_spec.commitIdBefore);
                        tmp2.put("commitIdAfter", impacts_spec.commitIdAfter);
                    }
                    Result result = tx.run(getCypher(), parameters("json", tmp, "tool", impacts_spec.miner));
                    result.consume();
                    return "done";
                }
            });
            System.out.println(done);
        }
    }

    // TODO miner should provide this output
    private Object basify(JsonElement e) {
        if (e.isJsonNull()) {
            return null;
        } else if (e.isJsonPrimitive()) {
            JsonPrimitive tmp = e.getAsJsonPrimitive();
            if (tmp.isString()) {
                return tmp.getAsString();
            } else if (tmp.isBoolean()) {
                return tmp.getAsBoolean();
            } else if (tmp.isNumber()) {
                try {
                    return Integer.parseInt(tmp.getAsString());
                } catch (Exception ee) {
                    return Double.parseDouble(tmp.getAsString());
                }
            } else {
                throw new RuntimeException();
            }
        } else if (e.isJsonObject()) {
            Map<String, Object> res = new HashMap<>();
            for (Entry<String, JsonElement> entry : e.getAsJsonObject().entrySet()) {
                res.put(entry.getKey(), basify(entry.getValue()));
            }
            return res;
        } else if (e.isJsonArray()) {
            List<Object> res = new ArrayList<>();
            for (JsonElement element : e.getAsJsonArray()) {
                res.add(basify(element));
            }
            return res;
        } else {
            throw new RuntimeException();
        }
    }

    public void printGreeting(final String message) {
        try (Session session = driver.session()) {
            String greeting = session.writeTransaction(new TransactionWork<String>() {
                @Override
                public String execute(Transaction tx) {
                    Result result = tx.run("CREATE (a:Greeting) " + "SET a.message = $message "
                            + "RETURN a.message + ', from node ' + id(a)", parameters("message", message));
                    return result.single().get(0).asString();
                }
            });
            System.out.println(greeting);
        }
    }

    private static String getCypher() {
        try {
            return new String(Files.readAllBytes(Paths.get(Neo4jEvolutionsStorage.class.getClassLoader().getResource("evolutions_cypher.sql").getFile())));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static Map<String, EvoType> getCRefactoringTypes() {
        try {
            Map<String, EvoType> res = new Gson().fromJson(
                    new String(Files.readAllBytes(
                            Paths.get(Neo4jEvolutionsStorage.class.getClassLoader().getResource("RefactoringTypes_named.json").getFile()))),
                    new TypeToken<Map<String, EvoType>>() {
                    }.getType());
            for (Entry<String, EvoType> e : res.entrySet()) {
                e.getValue().name = e.getKey();
            }
            return res;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}