package fr.quentin.coevolutionMiner.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.maven.shared.invoker.DefaultInvocationRequest;
import org.apache.maven.shared.invoker.DefaultInvoker;
import org.apache.maven.shared.invoker.InvocationOutputHandler;
import org.apache.maven.shared.invoker.InvocationRequest;
import org.apache.maven.shared.invoker.InvocationResult;
import org.apache.maven.shared.invoker.Invoker;
import org.apache.maven.shared.invoker.InvokerLogger;
import org.apache.maven.shared.invoker.MavenInvocationException;
import org.apache.maven.shared.invoker.PrintStreamHandler;
import org.apache.maven.shared.utils.cli.CommandLineException;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoHeadException;
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
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.eclipse.jgit.treewalk.filter.PathSuffixFilter;
import org.refactoringminer.api.Refactoring;

import fr.quentin.impactMiner.AugmentedAST;
import fr.quentin.impactMiner.Evolution;
import fr.quentin.impactMiner.ImpactAnalysis;
import fr.quentin.impactMiner.ImpactChain;
import fr.quentin.impactMiner.Impacts;
import fr.quentin.impactMiner.JsonSerializable;
import fr.quentin.impactMiner.ToJson;
import fr.quentin.impactMiner.ImpactAnalysis.ImpactAnalysisException;
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

	/**
	 *
	 */
	public static final String MVN_HOME = MyProperties.getPropValues().getProperty("mavenHome");

	static Logger logger = Logger.getLogger("ImpactAna");

	public static final String RESOURCES_PATH = MyProperties.getPropValues().getProperty("resources");
	static String VERSIONS_PATH = Paths.get(RESOURCES_PATH, "Versions").toString();
	static String REPOS_PATH = Paths.get(RESOURCES_PATH, "Repos").toString();

	private Repository repo;
	private String gitRepoAddress;
	private String repoRawPath;

	private static FilePathFilter ALLOW_ALL = new FilePathFilter() {
		@Override
		public boolean isAllowed(String filePath) {
			return true;
		}
	};

	/**
	 * @return the repoRawPath
	 */
	public String getRepoRawPath() {
		return repoRawPath;
	}

	public SourcesHelper(String gitRepoAddress) throws Exception {
		this.gitRepoAddress = gitRepoAddress;
		this.repoRawPath = parseAddress(gitRepoAddress);
		this.repo = this.cloneIfNotExists();
	}

	public static String parseAddress(String gitRepoAddress) throws URISyntaxException {
		URIish parsedRepoURI = new URIish(gitRepoAddress);
		String repoRawPath = parsedRepoURI.getRawPath();
		if (repoRawPath.endsWith(".git")) {
			repoRawPath = repoRawPath.substring(0, repoRawPath.length() - 4);
		}
		return repoRawPath;
	}

	public final static ReentrantLock lock = new ReentrantLock();

	private Repository cloneIfNotExists() throws Exception {
		lock.lock();
		try {
			return GitHelper.cloneIfNotExists(Paths.get(REPOS_PATH, this.repoRawPath).toString(), // .substring(0,
					// repoRawPath.length()
					// - 4),
					gitRepoAddress);
		} catch (Exception e) {
			throw new Exception(e);
		} finally {
			lock.unlock();
		}
	}

	public Path materialize(String commitId, FilePathFilter filter) throws IOException {
		SourceFileSet sources = GitHelper.getSourcesAtCommit(repo, commitId, filter);
		Path path = Paths.get(VERSIONS_PATH, repoRawPath, // .substring(0, repoRawPath.length() - 4),
				commitId);
		path.toFile().delete();
		sources.materializeAt(path);
		return path;
	}

	public Path materialize(String commitId) throws IOException {
		return materialize(commitId, ALLOW_ALL);
	}

	public Path materializePrev(String commitId, FilePathFilter filter) throws IOException {
		SourceFileSet sources = GitHelper.getSourcesBeforeCommit(repo, commitId, filter);
		Path path = Paths.get(VERSIONS_PATH, repoRawPath, // .substring(0, repoRawPath.length() - 4),
				commitId);
		path.toFile().delete();
		sources.materializeAt(path);
		return path;
	}

	public Path materializePrev(String commitId) throws IOException {
		return materializePrev(commitId, ALLOW_ALL);
	}

	public void materializeExtended(String commitIdBefore, String commitIdAfter) {
		PathSuffixFilter.create(".java");
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

	public ImmutableTriple<RevCommit, Iterable<RevCommit>, RevCommit> getCommitsBetween(String commitIdBefore,
			String commitIdAfter) throws Exception {
		// try (RevWalk rw = new GitHelper().fetchAndCreateNewRevsWalk(repository)) {
		try (RevWalk walk = new GitHelper().fetchAndCreateNewRevsWalk(repo)) {
			// try (ObjectReader reader = repo.newObjectReader(); RevWalk walk = new
			// RevWalk(reader)) {
			RevCommit commitBefore = walk.parseCommit(repo.resolve(commitIdBefore));
			RevCommit commit = walk.parseCommit(repo.resolve(commitIdAfter));

			try (Git git = new Git(repo)) {
				return new ImmutableTriple<RevCommit, Iterable<RevCommit>, RevCommit>(commitBefore,
						git.log().addRange(commitBefore, commit).call(), commit);
			}
		} catch (Exception e) {
			// rm -fr .git
			try {
				runCommand(Paths.get(REPOS_PATH), "rm", "-rf", this.repoRawPath);
			} catch (Exception ee) {
				logger.log(Level.WARNING, "normal if on windows", ee);
			}
			// runCommand(Paths.get(REPOS_PATH + this.repoRawPath),"git", "reset",
			// "--hard");
			// runCommand(Paths.get(REPOS_PATH + this.repoRawPath),"git", "fsck");
			// runCommand(Paths.get(REPOS_PATH + this.repoRawPath),"git", "pull", "origin",
			// "master");
			// runCommand(Paths.get(REPOS_PATH + this.repoRawPath),"git",
			// "checkout","ede8888f9e23de3457e4e261e2337296b533a916");
			// System.err.println("native git pull working?");

			repo = cloneIfNotExists();

			try (RevWalk walk = new GitHelper().fetchAndCreateNewRevsWalk(repo)) {
				// try (ObjectReader reader = repo.newObjectReader(); RevWalk walk = new
				// RevWalk(reader)) {
				RevCommit commitBefore = walk.parseCommit(repo.resolve(commitIdBefore));
				RevCommit commit = walk.parseCommit(repo.resolve(commitIdAfter));

				try (Git git = new Git(repo)) {
					return new ImmutableTriple<RevCommit, Iterable<RevCommit>, RevCommit>(commitBefore,
							git.log().addRange(commitBefore, commit).call(), commit);
				}
			}
		}
	}

	public static void runCommand(Path directory, String... command) throws IOException, InterruptedException {
		Objects.requireNonNull(directory, "directory");
		if (!Files.exists(directory)) {
			throw new RuntimeException("can't run command in non-existing directory '" + directory + "'");
		}
		logger.info("In "+directory+" running: "+ String.join(" ", command));
		ProcessBuilder pb = new ProcessBuilder().command(command).directory(directory.toFile());
		Process p = pb.start();
		StreamGobbler errorGobbler = new StreamGobbler(p.getErrorStream(), "ERROR");
		StreamGobbler outputGobbler = new StreamGobbler(p.getInputStream(), "OUTPUT");
		outputGobbler.start();
		errorGobbler.start();
		int exit = p.waitFor();
		errorGobbler.join();
		outputGobbler.join();
		if (exit != 0) {
			throw new AssertionError(String.format("runCommand returned %d", exit));
		}
	}

	private static class StreamGobbler extends Thread {

		private final InputStream is;
		private final String type;

		private StreamGobbler(InputStream is, String type) {
			this.is = is;
			this.type = type;
		}

		@Override
		public void run() {
			try (BufferedReader br = new BufferedReader(new InputStreamReader(is));) {
				String line;
				while ((line = br.readLine()) != null) {
					System.out.println(type + "> " + line);
				}
			} catch (IOException ioe) {
				ioe.printStackTrace();
			}
		}
	}

	public static InvocationResult prepare(Path path, InvocationOutputHandler outputHandler) throws Exception {
		return prepare(path, (File) null, outputHandler);	
	}

	public static InvocationResult prepare(Path path, File MvnHome, InvocationOutputHandler outputHandler) throws Exception {
		InvocationRequest request = new DefaultInvocationRequest();
		request.setOutputHandler(outputHandler);
		request.setErrorHandler(outputHandler);
		request.setBaseDirectory(path.toFile());
		request.setGoals(Arrays.asList("compile", "clean"));
		request.setBatchMode(true);
		request.setReactorFailureBehavior(InvocationRequest.ReactorFailureBehavior.FailAtEnd);
		request.setTimeoutInSeconds(60 * 10);
		request.setShowErrors(true);
		// request.setThreads("3");
		Invoker invoker = new DefaultInvoker();
		invoker.setMavenHome(MvnHome);
		try {
			return invoker.execute(request);
		} catch (MavenInvocationException e) {
			throw new Exception("Error while compiling project with maven", e);
		}
	}

	public static InvocationResult prepare(Path path, String project, InvocationOutputHandler outputHandler) throws Exception {
		InvocationRequest request = new DefaultInvocationRequest();
		request.setOutputHandler(outputHandler);
		request.setErrorHandler(outputHandler);
		request.setGoals(Arrays.asList("compile", "clean"));
		request.setBaseDirectory(path.toFile());
		request.setProjects(Arrays.asList(project));
		return prepareAux(request);
	}

	public static InvocationResult prepareAll(Path path, String project, InvocationOutputHandler outputHandler) throws Exception {
		InvocationRequest request = new DefaultInvocationRequest();
		request.setOutputHandler(outputHandler);
		request.setErrorHandler(outputHandler);
		request.setGoals(Arrays.asList("test-compile", "clean"));
		request.setBaseDirectory(path.toFile());
		request.setProjects(Arrays.asList(project));
		return prepareAux(request);
	}

	private static InvocationResult prepareAux(InvocationRequest request) throws Exception {
		request.setAlsoMake(true);
		request.setBatchMode(true);
		request.setReactorFailureBehavior(InvocationRequest.ReactorFailureBehavior.FailAtEnd);
		request.setTimeoutInSeconds(60 * 10);
		request.setShowErrors(true);
		Invoker invoker = new DefaultInvoker();
		invoker.setMavenHome(Paths.get(MVN_HOME).toFile());
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
			throws IOException, ImpactAnalysisException {
		AugmentedAST<MavenLauncher> aug = new AugmentedAST<>(launcher);
		ImpactAnalysis l = new ImpactAnalysis(aug);
		return null;
	}

	public static InvocationResult executeTests(File baseDir, String filter, InvocationOutputHandler outputHandler)
			throws Exception {
		InvocationRequest request = new DefaultInvocationRequest();
		request.setBatchMode(true);
		request.setBaseDirectory(baseDir);
		request.setGoals(Arrays.asList("test"));
		request.setMavenOpts("-Dtest=" + filter);
		request.setOutputHandler(outputHandler);
		request.setErrorHandler(outputHandler);
		Invoker invoker = new DefaultInvoker();
		invoker.setMavenHome(Paths.get(MVN_HOME).toFile());
		try {
			return invoker.execute(request);
		} catch (MavenInvocationException e) {
			throw new Exception("Error while running tests with maven", e);
		}
	}

	public static InvocationResult compileAllTests(File baseDir, InvocationOutputHandler outputHandler) throws Exception {
		InvocationRequest request = new DefaultInvocationRequest();
		request.setBatchMode(true);
		request.setBaseDirectory(baseDir);
		request.setGoals(Arrays.asList("test-compile"));
		request.setOutputHandler(outputHandler);
		request.setErrorHandler(outputHandler);
		Invoker invoker = new DefaultInvoker();
		invoker.setMavenHome(Paths.get(MVN_HOME).toFile());
		try {
			return invoker.execute(request);
		} catch (MavenInvocationException e) {
			throw new Exception("Error while running tests with maven", e);
		}
	}

	public static InvocationResult compileApp(File baseDir, InvocationOutputHandler outputHandler) throws Exception {
		InvocationRequest request = new DefaultInvocationRequest();
		request.setBatchMode(true);
		request.setBaseDirectory(baseDir);
		request.setGoals(Arrays.asList("compile"));
		request.setOutputHandler(outputHandler);
		request.setErrorHandler(outputHandler);
		Invoker invoker = new DefaultInvoker();
		invoker.setMavenHome(Paths.get(MVN_HOME).toFile());
		try {
			return invoker.execute(request);
		} catch (MavenInvocationException e) {
			throw new Exception("Error while running tests with maven", e);
		}
	}

}