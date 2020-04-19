package fr.quentin.v2.sources;

import fr.quentin.utils.SourcesHelper;

public abstract class Sources {

    private Specifier spec;

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
}