package fr.quentin.utils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import org.apache.maven.shared.invoker.DefaultInvocationRequest;
import org.apache.maven.shared.invoker.DefaultInvoker;
import org.apache.maven.shared.invoker.InvocationRequest;
import org.apache.maven.shared.invoker.InvocationResult;
import org.apache.maven.shared.invoker.Invoker;
import org.apache.maven.shared.invoker.MavenInvocationException;
import org.eclipse.jgit.errors.AmbiguousObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.RevisionSyntaxException;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.treewalk.TreeWalk;

import fr.quentin.Evolution;
import fr.quentin.ImpactAnalysis;
import fr.quentin.ImpactChain;
import fr.quentin.Impacts;
import fr.quentin.JsonSerializable;
import fr.quentin.ToJson;
import spoon.MavenLauncher;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.cu.SourcePosition;
import spoon.reflect.declaration.CtConstructor;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.visitor.filter.TypeFilter;

/**
 * SourcesHelper
 */
public class SourcesHelper implements AutoCloseable {

	static Logger logger = Logger.getLogger("ImpactAna");

	static String VERSIONS_PATH = "/home/quentin/resources/Versions";
	static String REPOS_PATH = "/home/quentin/resources/Repos";

	private Repository repo;
	private String gitRepoAddress;
	private String repoRawPath;

	private static FilePathFilter ALLOW_ALL = new FilePathFilter() {
		@Override
		public boolean isAllowed(String filePath) {
			return true;
		}
	};

	public SourcesHelper(String gitRepoAddress) throws Exception {
		this.gitRepoAddress = gitRepoAddress;
		URIish parsedRepoURI = new URIish(gitRepoAddress);
		this.repoRawPath = parsedRepoURI.getRawPath();
		this.repo = this.cloneIfNotExists();
	}

	private Repository cloneIfNotExists() throws Exception {
		return GitHelper.cloneIfNotExists(REPOS_PATH + this.repoRawPath.substring(0, repoRawPath.length() - 4),
				gitRepoAddress);
	}

	public Path materialize(String commitId, FilePathFilter filter) throws IOException {
		SourceFileSet sources = GitHelper.getSourcesAtCommit(repo, commitId, filter);
		Path path = Paths.get(VERSIONS_PATH, repoRawPath.substring(0, repoRawPath.length() - 4), commitId);
		path.toFile().delete();
		sources.materializeAt(path);
		return path;
	}

	public Path materialize(String commitId) throws IOException {
		return materialize(commitId, ALLOW_ALL);
	}

	public Path materializePrev(String commitId, FilePathFilter filter) throws IOException {
		SourceFileSet sources = GitHelper.getSourcesBeforeCommit(repo, commitId, filter);
		Path path = Paths.get(VERSIONS_PATH, repoRawPath.substring(0, repoRawPath.length() - 4), commitId);
		path.toFile().delete();
		sources.materializeAt(path);
		return path;
	}

	public Path materializePrev(String commitId) throws IOException {
		return materializePrev(commitId, ALLOW_ALL);
	}

	public String getBeforeCommit(String commitId) throws IOException {
		return GitHelper.getBeforeCommit(repo, commitId);
	}

	public String getContent(String commitId, String path) throws RevisionSyntaxException, MissingObjectException,
			IncorrectObjectTypeException, AmbiguousObjectException, IOException {
		// try (RevWalk rw = new GitHelper().fetchAndCreateNewRevsWalk(repository)) {
		try (ObjectReader reader = repo.newObjectReader(); RevWalk walk = new RevWalk(reader)) {
			RevCommit commit = walk.parseCommit(repo.resolve(commitId));
			RevTree tree = commit.getTree();
			TreeWalk treewalk = TreeWalk.forPath(reader, path, tree);
			
			if (treewalk != null) {
				byte[] bytes = reader.open(treewalk.getObjectId(0)).getBytes();
				return new String(bytes, StandardCharsets.UTF_8.name());
			} else {
				throw new FileNotFoundException(path);
			}
		}
	}

    public static InvocationResult prepare(Path path) throws Exception {
            InvocationRequest request = new DefaultInvocationRequest();
            request.setBaseDirectory(path.toFile());
            request.setGoals(Arrays.asList("compile","clean"));
            Invoker invoker = new DefaultInvoker();
            invoker.setMavenHome(Paths.get("/usr").toFile());
            try {
                return invoker.execute(request);
            } catch (MavenInvocationException e) {
                throw new Exception("Error while compiling project with maven", e);
            }
    }

    public Repository getRepo() {
        return repo;
    }

    @Override
    public void close() throws IOException {
        this.repo.close();
    }

	public static <T> JsonElement impactAnalysis(Path root, MavenLauncher launcher, List<Evolution<T>> evolutions)
            throws IOException {
		ImpactAnalysis l = new ImpactAnalysis(launcher);

		logger.info("Number of executable refs mapped to positions " + evolutions.size());
		List<ImpactChain> imptst1 = l.getImpactedTests(evolutions);
		logger.info("Number of Impacted tests X number of evolutions " + imptst1.size());
		logger.info("Assembling Impacts");
		Impacts x = new Impacts(imptst1);
		logger.info("Serializing Impacts");
		logger.info(Integer.toString(x.getRoots().size()));
		logger.info(new GsonBuilder().setPrettyPrinting().create().toJson(x.toJson()));
		return x.toJson(new ToJson() {
			public JsonElement apply(Object x) {
				if (x instanceof JsonSerializable) {
					JsonSerializable y = (JsonSerializable) x;
					return y.toJson(this);
				} else if (x instanceof CtMethod) {
					CtMethod<?> y = (CtMethod<?>) x;
					JsonObject o = new JsonObject();
					o.addProperty("declType", y.getDeclaringType().getQualifiedName());
					o.addProperty("signature", y.getSignature());
					o.addProperty("name", y.getSimpleName());
					JsonObject o2 = new JsonObject();
					o2.addProperty("isTest", ImpactAnalysis.isTest(y));
					o2.addProperty("file", root.relativize(y.getPosition().getFile().toPath()).toString());
					o2.addProperty("start", y.getPosition().getSourceStart());
					o2.addProperty("end", y.getPosition().getSourceEnd());
					o.add("position", o2);
					return o;
				} else if (x instanceof CtConstructor) {
					CtConstructor<?> y = (CtConstructor<?>) x;
					JsonObject o = new JsonObject();
					o.addProperty("declType", y.getDeclaringType().getQualifiedName());
					o.addProperty("signature", y.getSignature());
					o.addProperty("name", y.getSimpleName());
					JsonObject o2 = new JsonObject();
					o2.addProperty("file", root.relativize(y.getPosition().getFile().toPath()).toString());
					o2.addProperty("start", y.getPosition().getSourceStart());
					o2.addProperty("end", y.getPosition().getSourceEnd());
					o.add("position", o2);
					return o;
				} else if (x instanceof CtExecutable) {
					CtExecutable<?> y = (CtExecutable<?>) x;
					return new JsonPrimitive("anonymous block" + y.getSignature());
				} else if (x instanceof CtInvocation) {
					CtInvocation<?> y = (CtInvocation<?>) x;
					JsonObject o = new JsonObject();
					o.add("sig", apply(y.getExecutable().getDeclaration()));
					JsonObject oPos = new JsonObject();
					oPos.addProperty("isTest", ImpactAnalysis.isTest(y.getParent(CtMethod.class)));
					oPos.addProperty("file", root.relativize(y.getPosition().getFile().toPath()).toString());
					oPos.addProperty("start", y.getPosition().getSourceStart());
					oPos.addProperty("end", y.getPosition().getSourceEnd());
					oPos.add("method", apply(y.getParent(CtMethod.class)));
					o.add("position", oPos);
					return o;
				} else if (x instanceof Collection) {
					JsonArray a = new JsonArray();
					for (Object b : (Collection<?>) x) {
						a.add(apply(b));
					}
					return a;
				} else {
					return new JsonPrimitive(x.getClass().toString());
				}
			}
		});
	}

}