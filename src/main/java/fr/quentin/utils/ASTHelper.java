package fr.quentin.utils;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.URIish;

import spoon.MavenLauncher;

/**
 * ASTHelper
 * 
 */
public class ASTHelper {

    static String VERSIONS_PATH = "/home/quentin/resources/Versions";
    static String REPOS_PATH = "/home/quentin/resources/Repos";
    
    private Repository repo;
    private String gitRepoAddress;
    private MavenLauncher launcher;
    private String repoRawPath;
    private Path path;

	private static FilePathFilter ALLOW_ALL = new FilePathFilter() {
		@Override
		public boolean isAllowed(String filePath) {
			return true;
		}
	};

    public ASTHelper(String gitRepoAddress, String commitId) throws Exception {
		this.gitRepoAddress = gitRepoAddress;
		URIish parsedRepoURI = new URIish(gitRepoAddress);
		this.repoRawPath = parsedRepoURI.getRawPath();
		this.repo = this.cloneIfNotExists();
		new GitHelper().createAllRevsWalk(repo);
		
		SourceFileSet sources = GitHelper.getSourcesAtCommit(repo,commitId,ALLOW_ALL);

		this.path = Paths.get(VERSIONS_PATH, repoRawPath.substring(0, repoRawPath.length() - 4), commitId);
		sources.materializeAt(this.path);	
		this.launcher = new MavenLauncher(this.path.toString(), MavenLauncher.SOURCE_TYPE.ALL_SOURCE);
	}

	private Repository cloneIfNotExists() throws Exception {
		return GitHelper.cloneIfNotExists(REPOS_PATH + this.repoRawPath.substring(0, repoRawPath.length() - 4),
				gitRepoAddress);
	}

	public Repository getRepo() {
		return repo;
	}

	public MavenLauncher getLauncher() {
		return launcher;
	}

	public Path getPathBefore() {
		return path;
	}
	
}