package fr.quentin.coevolutionMiner.v2.sources.miners;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.jgit.revwalk.RevCommit;

import fr.quentin.coevolutionMiner.utils.SourcesHelper;
import fr.quentin.coevolutionMiner.v2.sources.Sources;
import fr.quentin.coevolutionMiner.v2.sources.SourcesMiner;
import fr.quentin.coevolutionMiner.v2.sources.Sources.Specifier;

public class JgitMiner implements SourcesMiner {

    private Specifier spec;

    public JgitMiner(Sources.Specifier spec) {
        this.spec = spec;
    }

    @Override
    public Sources compute() {
        return new Sources(spec) {

            @Override
            public SourcesHelper open() throws Exception {
                return new SourcesHelper(spec.repository);
            }

            @Override
            public Set<Sources.Commit> getCommitBetween(String commitIdBefore, String commitIdAfter) {
                try (SourcesHelper helper = open();) {
                    Map<String, Sources.Commit> result = new HashMap<>(commits);
                    helper.getCommitsBetween(commitIdBefore, commitIdAfter).forEach(x -> {
                        String name = x.getId().getName();
                        Commit o = result.getOrDefault(name,createCommit(name));
                        for (RevCommit commit : x.getParents()) {
                            String pname = commit.getId().getName();
                            Commit p = result.getOrDefault(pname, createCommit(pname));
                            commits.putIfAbsent(p.getId(), p);
                            result.putIfAbsent(p.getId(), p);
                            addParent(o, p);
                        }
                        commits.putIfAbsent(o.getId(), o);
                        result.putIfAbsent(o.getId(), o);
                    });
                    for (Commit commit : result.values()){
                        for (Commit parent : commit.getParents()) {
							Commit tmp = result.get(parent.getId());
                            if (tmp != null) {
                                addChildren(tmp, commit);
                            }
                        }
                    }
                    return new HashSet<Commit>(result.values());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }

}