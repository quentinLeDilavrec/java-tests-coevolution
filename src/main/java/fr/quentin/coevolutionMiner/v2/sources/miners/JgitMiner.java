package fr.quentin.coevolutionMiner.v2.sources.miners;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import org.apache.commons.lang3.tuple.ImmutableTriple;
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
            public List<Sources.Commit> getCommitsBetween(String commitIdBefore, String commitIdAfter, boolean tryAnalyze)
                    throws Exception {
                try (SourcesHelper helper = open();) {
                    Set<Sources.Commit> main = new HashSet<>();
                    ImmutableTriple<RevCommit, Iterable<RevCommit>, RevCommit> tmp0 = helper
                            .getCommitsBetween(commitIdBefore, commitIdAfter);
                    Consumer<? super RevCommit> consumer = x -> {
                        String name = x.getId().getName();
                        Commit o = getCommit(name);
                        for (RevCommit commit : x.getParents()) {
                            String pname = commit.getId().getName();
                            Commit p = getCommit(pname, tryAnalyze);
                            main.add(p);
                            addParent(o, p);
                        }
                        main.add(o);
                    };
                    consumer.accept(tmp0.left);
                    tmp0.middle.forEach(consumer);
                    consumer.accept(tmp0.right);

                    for (Commit commit : main) {
                        for (Commit parent : commit.getParents()) {
                            Commit tmp = getCommit(parent.getId());
                            if (tmp != null) {
                                addChildren(tmp, commit);
                            }
                        }
                    }

                    List<Sources.Commit> rlist = new ArrayList<>();
                    Sources.Commit curr = getCommit(commitIdBefore);
                    while (true) {
                        rlist.add(curr);
                        if (curr.getId().equals(commitIdAfter)) {
                            break;
                        } else {
                            boolean b = true;
                            for (Commit child : curr.getChildrens()) {
                                if (main.contains(child)) {
                                    b = false;
                                    curr = child;
                                }
                            }
                            if (b) {
                                break;
                            }
                        }
                    }
                    uploadCommits();
                    return rlist;
                }
            }
        };
    }

}