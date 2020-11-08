package fr.quentin.coevolutionMiner.v2.ast.storages;

import static org.neo4j.driver.Values.parameters;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.Transaction;
import org.neo4j.driver.TransactionWork;
import org.neo4j.driver.exceptions.TransientException;

import fr.quentin.coevolutionMiner.utils.MyProperties;
import fr.quentin.coevolutionMiner.v2.ast.Project;
import fr.quentin.coevolutionMiner.v2.ast.ProjectStorage;
import fr.quentin.coevolutionMiner.v2.ast.Stats;
import fr.quentin.coevolutionMiner.v2.ast.miners.SpoonMiner.ProjectSpoon;
import fr.quentin.coevolutionMiner.v2.sources.Sources.Commit;
import fr.quentin.coevolutionMiner.v2.utils.Utils;

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
                    Result result = tx.run(getCypher(),
                            parameters("json", tmp, "tool", value.spec.miner.getSimpleName()));
                    result.consume();
                    // Result result2 = tx.run(getCommitCypher(), parameters("commits", commits));
                    // result2.consume();
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

    private <T> Map<String, Object> extracted(Project.Specifier proj_spec, Project<T> project,
            List<Map<String, Object>> result) {
        if (project instanceof ProjectSpoon) {
            Map<String, Object> r = projectToMap((ProjectSpoon) project);

            result.add(r);
            for (Project<?> module : project.getModules()) {
                Map<String, Object> aaa = extracted(project.spec, module, result);
                // aaa.put("parent", r);
            }
            return r;
        } else {
            throw new RuntimeException(project + " is not a SpoonProject");
        }
    }

    private <T> Map<String, Object> projectToMap(ProjectSpoon project) {
        Map<String, Object> r = new HashMap<>();
        ProjectSpoon.SpoonAST ast = (ProjectSpoon.SpoonAST) project.getAst();
        Stats stats = ast.getGlobalStats();
        r.put("stats", stats.toMap());
        Commit commit = project.commit;
        Map<String, Object> commitMap = commit.toMap();
        Map<String, Object> content = new HashMap<>();
        r.put("content", content);
        r.put("released", null); // TODO get it from a file of global var or update graph independently

        content.putAll(commitMap);
        content.put("path", project.spec.relPath.toString());
        Path rootDir = ast.getRootDir();

        if (ast.launcher == null) {
            Exception exc = ast.compilerException;
            String msg = exc == null ? "Spoon failed the analysis" : exc.getMessage();
            r.put("exception", msg == null ? "Spoon failed the analysis" : exc.getClass().getName());
            return r;
        }

        List<String> srcs = ast.launcher.getPomFile().getSourceDirectories().stream()
                .map(x -> rootDir.relativize(x.toPath()).toString()).collect(Collectors.toList());
        srcs.add(Paths.get(project.spec.relPath.toString(), "src/main/java").toString());
        content.put("srcs", srcs);
        Model pom = ast.launcher.getPomFile().getModel();
        content.put("groupId", pom.getGroupId());
        content.put("artifactId", pom.getArtifactId());

        Parent parentRaw = pom.getParent();
        if (parentRaw != null) {
            Map<String, Object> parent = new HashMap<>();
            r.put("parent", parent);
            parent.put("groupId", parentRaw.getGroupId());
            parent.put("artifactId", parentRaw.getArtifactId());
            parent.put("version", parentRaw.getVersion());
            parent.put("id", parentRaw.getId());
            parent.put("relativePath", parentRaw.getRelativePath());
        }

        List<Map<String, Object>> dependencies = new ArrayList<>();
        r.put("dependencies", dependencies);
        for (Dependency dep : pom.getDependencies()) {
            Map<String, Object> dependency = new HashMap<>();
            dependencies.add(dependency);
            dependency.put("artifactId", dep.getArtifactId());
            dependency.put("groupId", dep.getGroupId());
            dependency.put("version", dep.getVersion());
            dependency.put("scope", dep.getScope());
            dependency.put("type", dep.getType());
            dependency.put("classifier", dep.getClassifier());
            dependency.put("managementKey", dep.getManagementKey());
            dependency.put("optional", dep.getOptional());
            dependency.put("exclusions", dep.getExclusions().stream().map(x -> x.getGroupId() + "." + x.getArtifactId())
                    .collect(Collectors.toList()));
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

    private static String getCypher() {
        return Utils.memoizedReadResource("project_cypher.cql");
    }

    protected String getCommitCypher() {
        return Utils.memoizedReadResource("commits_cypher.cql");
    }

    @Override
    public void close() throws Exception {
        driver.close();
    }
}