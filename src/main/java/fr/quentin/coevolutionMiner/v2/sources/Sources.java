package fr.quentin.coevolutionMiner.v2.sources;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.github.gumtreediff.tree.Version;

import fr.quentin.coevolutionMiner.utils.SourcesHelper;

public abstract class Sources {

    public final Specifier spec;
    private Repository repository;

    public Sources(Specifier spec) {
        this.spec = spec;
        this.repository = new Repository(spec.repository);
    }

    public abstract SourcesHelper open() throws Exception;

    public static class Specifier {
        public final String repository;
        public final String miner;
        public final Integer stars;

        public Specifier(String repository, String miner) {
            this.repository = repository;
            this.miner = miner;
            this.stars = null;
        }

        public Specifier(String repository, String miner, Integer stars) {
            this.repository = repository;
            this.miner = miner;
            this.stars = stars;
        }

        // TODO allow to specify Impacts more precisely with filters
        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((miner == null) ? 0 : miner.hashCode());
            result = prime * result + ((repository == null) ? 0 : repository.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            Specifier other = (Specifier) obj;
            if (miner == null) {
                if (other.miner != null)
                    return false;
            } else if (!miner.equals(other.miner))
                return false;
            if (repository == null) {
                if (other.repository != null)
                    return false;
            } else if (!repository.equals(other.repository))
                return false;
            return true;
        }

        @Override
        public String toString() {
            return "Specifier [repository=" + repository + ", stars=" + stars + ", miner=" + miner + "]";
        }
    }

    public Repository getRepository() {
        return repository;
    }

    public class Repository {

        Set<String> releases = new HashSet<>();

        Repository(String url) {
            this.url = url;
            this.hashCode = hashCodeCompute();
        };

        private String url;

        public String getUrl() {
            return url;
        }

        public void addReleases(Collection<String> validReleases) {
            this.releases.addAll(validReleases);
        }

        public Set<String> getReleases() {
            return Collections.unmodifiableSet(releases);
        }

        public Sources getEnclosingInstance() {
            return Sources.this;
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

        private final int hashCode;

        private int hashCodeCompute() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((url == null) ? 0 : url.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            Repository other = (Repository) obj;
            if (url == null) {
                if (other.url != null)
                    return false;
            } else if (!url.equals(other.url))
                return false;
            return true;
        }
    }

    Set<SourcesStorage> storers = new HashSet<>();

    public void onCommitsUpdate(SourcesStorage storer) {
        storers.add(storer);
    }

    private final Map<String, Commit> commits = new HashMap<>();
    private Set<Commit> updatedRelations = new HashSet<>();

    public final void uploadCommits() {
        if (updatedRelations.size() > 0) {
            Set<Commit> r = Collections.unmodifiableSet(updatedRelations);
            updatedRelations = new HashSet<>();
            for (SourcesStorage storer : storers) {
                storer.putUpdatedCommits(r);
            }
        }
    }

    public final Commit getCommit(String id) {
        if (commits.containsKey(id)) {
            return commits.get(id);
        } else {
            Commit commit = new Commit(id);
            commits.put(id, commit);
            updatedRelations.add(commit);
            return commit;
        }
    }

    public final Commit getCommit(String id, boolean tryAna) {
        Commit r = getCommit(id);
        if (tryAna && !r.tryAnalyze) {
               r.tryAnalyze = true;
               updatedRelations.add(r);
        }
        return r;
    }

    protected final void addParent(Commit x, Commit c) {
        if (!x.parents.contains(c)) {
            x.parents.add(c);
        }
    }

    protected final void addChildren(Commit x, Commit c) {
        if (!x.childrens.contains(c)) {
            x.childrens.add(c);
        }
    }

    public class Commit implements Version {
        final List<Commit> parents = new ArrayList<>();
        final List<Commit> childrens = new ArrayList<>();
        boolean tryAnalyze = false;
        public boolean getTryAnalyze() {return tryAnalyze;}
        private final String id;

        public Repository getRepository() {
            return Sources.this.getRepository();
        }

        /**
         * @return the id
         */
        public String getId() {
            return id;
        }

        /**
         * @return the parents
         */
        public List<Commit> getParents() {
            return Collections.unmodifiableList(parents);
        }

        /**
         * @return the childrens
         */
        public List<Commit> getChildrens() {
            return Collections.unmodifiableList(childrens);
        }

        Commit(String id) {
            this.id = id;
            this.hashCode = hashCodeCompute();
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

        private final int hashCode;

        private int hashCodeCompute() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((id == null) ? 0 : id.hashCode());
            result = prime * result + ((getRepository() == null) ? 0 : getRepository().hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            Commit other = (Commit) obj;
            if (id == null) {
                if (other.id != null)
                    return false;
            } else if (!id.equals(other.id))
                return false;
            if (getRepository() == null) {
                if (other.getRepository() != null)
                    return false;
            } else if (!getRepository().equals(other.getRepository()))
                return false;
            return true;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> r = new HashMap<>();
            // repo:e.content.repository, sha1:e.content.commitIdBefore
            r.put("repository", getRepository().getUrl());
            r.put("commitId", getId());
            return r;
        }

        @Override
        public String toString() {
            return getRepository().getUrl() + "/" + id.substring(0, Math.min(id.length(), 16));
        }

        LRU<Commit, Boolean> isAncestor = new LRU<>(1 << 3);

        @Override
        public COMP_RES partiallyCompareTo(Version other) {
            if (other instanceof Commit) {
                Commit o = (Commit) other;
                if (this == o) {
                    return COMP_RES.EQUAL;
                } else if (isAncestor(o)) {
                    return COMP_RES.SUPERIOR;
                } else if (o.isAncestor(this)) {
                    return COMP_RES.INFERIOR;
                } else {
                    return COMP_RES.PARALLEL;
                }
            } else {
                return null;
            }
        }

        private boolean isAncestor(Commit o) {
            Boolean cached = isAncestor.get(o);
            if (cached == null) {
                try (SourcesHelper sh = open();) {
                    cached = sh.isAncestor(o.getId(), this.getId());
                    isAncestor.put(o, cached);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
            return cached;
        }

    }

    public final List<Commit> getCommitsBetween(String commitIdBefore, String commitIdAfter) throws Exception {
        return getCommitsBetween(commitIdBefore, commitIdAfter, true);
    };

    public abstract List<Commit> getCommitsBetween(String commitIdBefore, String commitIdAfter, boolean tryAnalyze) throws Exception;

    // /*
    // * Not sure, for example, it would be used to represent maven artefacts.
    // */
    // public static class Artefact {
    // private String id;
    // private Repository repository;
    // private Commit commit;

    // public Artefact(String id, Repository repository, Commit commit) {
    // this.id = id;
    // this.repository = repository;
    // this.commit = commit;
    // }

    // }
}

class LRU<K, V> extends LinkedHashMap<K, V> {
    private static final long serialVersionUID = 1L;
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