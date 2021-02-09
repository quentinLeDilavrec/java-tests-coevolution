package fr.quentin.coevolutionMiner.v2.evolution.miners;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import com.github.gumtreediff.tree.Version;
import com.github.gumtreediff.tree.Version.COMP_RES;

import fr.quentin.coevolutionMiner.v2.sources.Sources;
import fr.quentin.coevolutionMiner.v2.sources.Sources.Commit;

/**
 * Only consider added nodes to find a path between 2 VersionCommit at least one
 * of the path must have all its Commit wrapped as VersionCommit
 */
public class VersionCommit implements Version {
    public class LRU<K, V> extends LinkedHashMap<K, V> {
        private final int limit;

        public LRU(int limit) {
            super(limit, 1.0f, true);
            this.limit = limit - 1;
        }

        @Override
        protected boolean removeEldestEntry(java.util.Map.Entry<K, V> eldest) {
            return (size() > this.limit);
        }
    }

    // LRU<VersionCommit, Boolean> isAncestor = new LRU<>(1 << 4);
    // LRU<VersionCommit, Boolean> isDescendant = new LRU<>(1 << 4);

    @Override
    public COMP_RES partiallyCompareTo(Version other) {
        if (other instanceof VersionCommit) {
            VersionCommit o = (VersionCommit) other;
            if (this == o) {
                return COMP_RES.EQUAL;
            } else if (isAncestor(o)) {
                return COMP_RES.SUPERIOR;
            } else if (isDescendant(o)) {
                return COMP_RES.INFERIOR;
            } else {
                return COMP_RES.PARALLEL;
            }
        } else {
            return null;
        }
    }

    private boolean isDescendant(VersionCommit o) {
        // if (isDescendant.containsKey(o)) {
        //     return isDescendant.get(o);
        // }
        for (Commit x : this.commit.getChildrens()) {
            VersionCommit y = commitsToVersions.get(x);
            if (y == null) {
                continue;
            } else if (o == y) {
                // isDescendant.put(o, true);
                return true;
            } else if (y.isDescendant(o)) {
                // isDescendant.put(o, true);
                return true;
            }
        }
        // isDescendant.put(o, false);
        return false;
    }

    private boolean isAncestor(VersionCommit o) {
        for (Commit x : this.commit.getParents()) {
            VersionCommit y = commitsToVersions.get(x);
            if (y == null) {
                continue;
            } else if (o == y) {
                return true;
            } else if (y.isAncestor(o)) {
                return true;
            }
        }
        return false;
    }

    static Map<Commit, VersionCommit> commitsToVersions = new HashMap<>();
    public final Sources.Commit commit;

    private VersionCommit(Sources.Commit commit) {
        this.commit = commit;
    }

    public static VersionCommit build(Sources.Commit commit) {
        VersionCommit res = commitsToVersions.get(commit);
        if (res == null) {
            res = new VersionCommit(commit);
            commitsToVersions.put(commit, res);
        }
        return res;
    }

    @Override
    public String toString() {
        return commit.toString();
    }

    // // negTopo | posTopo
    // private static final ArrayList<Collection<VersionCommit>> posTopo = new
    // ArrayList<>();
    // private static final ArrayList<Collection<VersionCommit>> negTopo = new
    // ArrayList<>();

    // private Collection<VersionCommit> getCut(int topoIndex) {
    // if (topoIndex<0) {
    // return negTopo.get(-topoIndex);
    // } else {
    // return posTopo.get(topoIndex);
    // }
    // }

    // private boolean add(VersionCommit v) {
    // if (posTopo.size()==0) {
    // ArrayList<VersionCommit> tmp = new ArrayList<>();
    // tmp.add(v);
    // return posTopo.add(tmp);
    // } else {
    // return posTopo.get(topoIndex);
    // }
    // }
}