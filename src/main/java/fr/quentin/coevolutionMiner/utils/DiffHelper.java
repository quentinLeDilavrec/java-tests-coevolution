package fr.quentin.coevolutionMiner.utils;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import org.apache.log4j.Logger;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.URIish;

import fr.quentin.impactMiner.Evolution;
import fr.quentin.impactMiner.ImpactAnalysis;
import fr.quentin.impactMiner.ImpactChain;
import fr.quentin.impactMiner.Impacts;
import fr.quentin.impactMiner.JsonSerializable;
import fr.quentin.impactMiner.ToJson;
import spoon.MavenLauncher;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.cu.SourcePosition;
import spoon.reflect.cu.SourcePositionHolder;
import spoon.reflect.declaration.CtConstructor;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class DiffHelper {
	
    static String VERSIONS_PATH = "/home/quentin/resources/Versions";
	static String REPOS_PATH = "/home/quentin/resources/Repos";
	
	static FilePathFilter ALLOW_ALL = new FilePathFilter() {
		@Override
		public boolean isAllowed(String filePath) {
			return true;
		}
	};

	private String repoRawPath;
	private String gitRepoAddress;
	private Repository repo;

	private Path pathBefore;
	private MavenLauncher launcherBefore;
	private MavenLauncher launcherAfter;
	private Path pathAfter;

    public DiffHelper(String gitRepoAddress, String commitIdBefore, String commitIdAfter) throws Exception {
		this.gitRepoAddress = gitRepoAddress;
		URIish parsedRepoURI = new URIish(gitRepoAddress);
		this.repoRawPath = parsedRepoURI.getRawPath();
		this.repo = this.cloneIfNotExists();
		new GitHelper().createAllRevsWalk(repo);
		
		PairBeforeAfter<SourceFileSet> beforeAndAfter = new PairBeforeAfter<SourceFileSet>(
			GitHelper.getSourcesAtCommit(repo,commitIdBefore,ALLOW_ALL), 
			GitHelper.getSourcesAtCommit(repo,commitIdAfter,ALLOW_ALL));
		// GitHelper.getSourcesBeforeAndAfterCommit(repo, commitIdBefore,
				// commitIdAfter, ALLOW_ALL);

		// TODO maybe only instanciate these when the getter is first used
		this.pathBefore = Paths.get(VERSIONS_PATH, repoRawPath.substring(0, repoRawPath.length() - 4), commitIdBefore);
		beforeAndAfter.getBefore().materializeAt(this.pathBefore);	
		this.launcherBefore = new MavenLauncher(this.pathBefore.toString(), MavenLauncher.SOURCE_TYPE.ALL_SOURCE);

		this.pathAfter = Paths.get(VERSIONS_PATH, repoRawPath.substring(0, repoRawPath.length() - 4), commitIdAfter);
		beforeAndAfter.getAfter().materializeAt(this.pathAfter);
		this.launcherAfter = new MavenLauncher(this.pathAfter.toString(), MavenLauncher.SOURCE_TYPE.ALL_SOURCE);
	}

	private Repository cloneIfNotExists() throws Exception {
		return GitHelper.cloneIfNotExists(REPOS_PATH + this.repoRawPath.substring(0, repoRawPath.length() - 4),
				gitRepoAddress);
	}

	public Repository getRepo() {
		return repo;
	}

	public MavenLauncher getLauncherBefore() {
		return launcherBefore;
	}

	public MavenLauncher getLauncherAfter() {
		return launcherAfter;
	}

	public Path getPathBefore() {
		return pathBefore;
	}

	public Path getPathAfter() {
		return pathAfter;
	}

	public <T> JsonElement impactAnalysis(MavenLauncher launcher, List<Evolution<T>> evolutions) throws IOException {
		ImpactAnalysis l = new ImpactAnalysis(launcher);

		List<CtExecutableReference<?>> allExecutableReference = new ArrayList<>();
		for (CtMethod<?> m : launcher.getModel().getElements(new TypeFilter<>(CtMethod.class))) {
			allExecutableReference.add(m.getReference());
		}
		for (CtConstructor<?> m : launcher.getModel().getElements(new TypeFilter<>(CtConstructor.class))) {
			allExecutableReference.add(m.getReference());
		}
		Logger.getLogger("ImpactAna").info("Number of executable refs " + allExecutableReference.size());

		Logger.getLogger("ImpactAna").info("Number of executable refs mapped to positions " + evolutions.size());
		List<ImpactChain> imptst1 = l.getImpactedTests(evolutions.subList(0, Math.min(evolutions.size(), 30)));
		// List<ImpactChain> imptst2 = new ArrayList<>();
		// for (ImpactChain impactChain : imptst1) {
		// CtElement tmp = impactChain.getRoot().getContent();
		// if (tmp instanceof SourcePositionHolder) {
		// imptst2.add(impactChain);
		// System.out.println(impactChain.toJson());
		// }
		// }
		Logger.getLogger("ImpactAna").info("Number of Impacted tests X number of evolutions " + imptst1.size());
		Logger.getLogger("ImpactAna").info("Assembling Impacts");
		Impacts x = new Impacts(imptst1);
		Logger.getLogger("ImpactAna").info("Serializing Impacts");
		Logger.getLogger("ImpactAna").info(x.getRoots().size());
		Logger.getLogger("ImpactAna").info(x.toJson());
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
					SourcePosition p = y.getPosition();
					o.addProperty("id", p.hashCode());
					return o;
				} else if (x instanceof CtConstructor) {
					CtConstructor<?> y = (CtConstructor<?>) x;
					JsonObject o = new JsonObject();
					o.addProperty("declType", y.getDeclaringType().getQualifiedName());
					o.addProperty("signature", y.getSignature());
					o.addProperty("name", y.getSimpleName());
					return o;
				} else if (x instanceof CtExecutable) {
					CtExecutable<?> y = (CtExecutable<?>) x;
					return new JsonPrimitive("anonymous block" + y.getSignature());
				} else if (x instanceof CtInvocation) {
					CtInvocation<?> y = (CtInvocation<?>) x;
					JsonObject o = new JsonObject();
					o.add("sig", apply(y.getExecutable().getDeclaration()));
					JsonObject o2 = new JsonObject();
					o2.addProperty("isTest", ImpactAnalysis.isTest(y.getParent(CtMethod.class)));
					o2.addProperty("file", getPathBefore().relativize(y.getPosition().getFile().toPath()).toString());
					o2.addProperty("start", y.getPosition().getSourceStart());
					o2.addProperty("end", y.getPosition().getSourceEnd());
					o2.add("method", apply(y.getParent(CtMethod.class)));
					o.add("position", o2);
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