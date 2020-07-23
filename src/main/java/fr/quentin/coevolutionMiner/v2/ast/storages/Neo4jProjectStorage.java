package fr.quentin.coevolutionMiner.v2.ast.storages;

import static org.neo4j.driver.Values.parameters;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.Transaction;
import org.neo4j.driver.TransactionWork;

import fr.quentin.coevolutionMiner.utils.MyProperties;
import fr.quentin.coevolutionMiner.v2.ast.Project;
import fr.quentin.coevolutionMiner.v2.ast.ProjectStorage;
import fr.quentin.coevolutionMiner.v2.ast.Stats;
import fr.quentin.coevolutionMiner.v2.ast.Project.AST;
import fr.quentin.coevolutionMiner.v2.sources.Sources.Commit;
import fr.quentin.coevolutionMiner.v2.utils.DbUtils;

public class Neo4jProjectStorage implements ProjectStorage {

    @Override
    public <T> void put(Project.Specifier proj_spec, Project<T> value) {
        List<Map<String, Object>> tmp = new ArrayList<>();
        // Object commits = null;

        // proj_spec.relPath;
        extracted(value.spec, value, tmp);

        try (Session session = driver.session()) {
            String done = session.writeTransaction(new TransactionWork<String>() {
                @Override
                public String execute(Transaction tx) {
                    Result result = tx.run(getCypher(), parameters("json", tmp, "tool", value.spec.miner));
                    result.consume();
                    // Result result2 = tx.run(getCommitCypher(), parameters("commits", commits));
                    // result2.consume();
                    return "done evolution on " + value.spec.sources.repository;
                }
            });
            System.out.println(done);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private <T> Map<String, Object> extracted(Project.Specifier proj_spec, Project<T> project,
            List<Map<String, Object>> result) {
        Map<String, Object> r = projectToMap(project);

        result.add(r);
        for (Project<?> module : project.getModules()) {
            Map<String, Object> aaa = extracted(project.spec, module, result);
            // aaa.put("parent", r);
        }
        return r;
    }

    private <T> Map<String, Object> projectToMap(Project<T> project) {
        Map<String, Object> r = new HashMap<>();
        Project<T>.AST ast = project.getAst();
        Stats stats = ast.getGlobalStats();
        r.put("stats", stats.toMap());
        Commit commit = project.commit;
        Map<String, Object> commitMap = commit.toMap();
        Map<String, Object> content = new HashMap<>();
        r.put("content", content);

        content.putAll(commitMap);
        content.put("path", project.spec.relPath.toString());
        content.put("srcs", ast.launcher.getPomFile().getSourceDirectories().stream().map(x -> x.getPath())
                .collect(Collectors.toList()));
        Model pom = ast.launcher.getPomFile().getModel();
        content.put("groupId", pom.getGroupId());
        content.put("artifactId", pom.getArtifactId());

        Map<String, Object> parent = new HashMap<>();
        r.put("parent", parent);
        parent.put("groupId", pom.getParent().getGroupId());
        parent.put("artifactId", pom.getParent().getArtifactId());
        parent.put("version", pom.getParent().getVersion());
        parent.put("id", pom.getParent().getId());
        parent.put("relativePath", pom.getParent().getRelativePath());

        List<Map<String, Object>> dependencies = new ArrayList<>();
        r.put("dependencies", dependencies);
        for (Dependency dep : pom.getDependencies()) {
            Map<String, Object> dependency = new HashMap<>();
            dependency.put("artifactId", dep.getArtifactId());
            dependency.put("groupId", dep.getGroupId());
            dependency.put("version", dep.getVersion());
            dependency.put("scope", dep.getScope());
            dependency.put("type", dep.getType());
            dependency.put("classifier", dep.getClassifier());
            dependency.put("managementKey", dep.getManagementKey());
            dependency.put("optional", dep.getOptional());
            dependency.put("exclusions", dep.getExclusions().stream().map(x->x.getGroupId()+"."+x.getArtifactId()).collect(Collectors.toList()));
        }

        r.put("version", pom.getVersion());
        r.put("packaging", pom.getPackaging());
        r.put("id", pom.getId());
        return r;
    }

    @Override
    public Project get(Project.Specifier impacts_spec) {
        // TODO Auto-generated method stub
        return null;
    }

    private final Driver driver;

    public Neo4jProjectStorage(String uri, String user, String password) {
        driver = GraphDatabase.driver(uri, AuthTokens.basic(user, password));
        // driver.close(); // TODO should be closed at some point

    }

    public Neo4jProjectStorage() {
        this(MyProperties.getPropValues().getProperty("neo4jAddress"),
                MyProperties.getPropValues().getProperty("neo4jId"),
                MyProperties.getPropValues().getProperty("neo4jPwd"));
    }

    private static String cypher = null;

    private static String getCypher() {
        if (cypher != null) {
            return cypher;
        }
        try {
            return new String(Files.readAllBytes(
                    Paths.get(Neo4jProjectStorage.class.getClassLoader().getResource("project_cypher.cql").getFile())));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String commitCypher = null;

    protected String getCommitCypher() {
        if (commitCypher != null) {
            return commitCypher;
        }
        try {
            return new String(Files.readAllBytes(
                    Paths.get(Neo4jProjectStorage.class.getClassLoader().getResource("commits_cypher.cql").getFile())));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() throws Exception {
        driver.close();
    }
}