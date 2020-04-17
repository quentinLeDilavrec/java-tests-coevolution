package fr.quentin.v2.ast;

import java.nio.file.Path;

import fr.quentin.v2.sources.Sources;
import spoon.MavenLauncher;

public class AST {
    public final MavenLauncher launcher;
    public final Path rootDir;

    public AST(Path rootDir, MavenLauncher launcher) {
        this.rootDir = rootDir;
        this.launcher = launcher;
    }

    public static class Specifier {
        public final Sources.Specifier sources;
        public final String miner;
        public final String commitId;

        public Specifier(Sources.Specifier sources, String commitId, String miner) {
            this.sources = sources;
            this.commitId = commitId;
            this.miner = miner;
        }
        // TODO allow to specify Impacts more precisely with filters

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((miner == null) ? 0 : miner.hashCode());
            result = prime * result + ((commitId == null) ? 0 : commitId.hashCode());
            result = prime * result + ((sources == null) ? 0 : sources.hashCode());
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
            if (commitId == null) {
                if (other.commitId != null)
                    return false;
            } else if (!commitId.equals(other.commitId))
                return false;
            if (sources == null) {
                if (other.sources != null)
                    return false;
            } else if (!sources.equals(other.sources))
                return false;
            return true;
        }
    }
}