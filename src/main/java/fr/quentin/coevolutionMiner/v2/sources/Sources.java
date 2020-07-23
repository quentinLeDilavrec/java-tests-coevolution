package fr.quentin.coevolutionMiner.v2.sources;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import fr.quentin.coevolutionMiner.utils.SourcesHelper;

public abstract class Sources {

    public final Specifier spec;

    public Sources(Specifier spec) {
        this.spec = spec;
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
    }

    public Repository getRepository() {
        return new Repository(spec.repository);
    }

    public class Repository {

        Repository(String url) {
            this.url = url;
        };

        private String url;

        public String getUrl() {
            return url;
        }

        @Override
        public int hashCode() {
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

    protected final Map<String, Commit> commits = new HashMap<>();

    // TODO remove it when not used anymore i.e Evolution has bee refactored
    public final Commit temporaryCreateCommit(String id) {
        return new Commit(id);
    }

    public final Commit getCommit(String id) {
        return commits.get(id);
    }

    protected final Commit createCommit(String id) {
        return new Commit(id);
    }

    protected final void addParent(Commit x, Commit c) {
        x.parents.add(c);
    }

    protected final void addChildren(Commit x, Commit c) {
        x.childrens.add(c);
    }

    public class Commit {
        final Set<Commit> parents = new HashSet<>();
        final Set<Commit> childrens = new HashSet<>();
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
        public Set<Commit> getParents() {
            return Collections.unmodifiableSet(parents);
        }

        /**
         * @return the childrens
         */
        public Set<Commit> getChildrens() {
            return Collections.unmodifiableSet(childrens);
        }

        Commit(String id) {
            this.id = id;
        }

        @Override
        public int hashCode() {
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

        public Map<String,Object> toMap() {
            Map<String,Object> r = new HashMap<>();
            // repo:e.content.repository, sha1:e.content.commitIdBefore
            r.put("repository",getRepository().getUrl());
            r.put("commitId",getId());
            return r;
        }
    }

    public abstract Set<Commit> getCommitsBetween(String commitIdBefore, String commitIdAfter) throws Exception;

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