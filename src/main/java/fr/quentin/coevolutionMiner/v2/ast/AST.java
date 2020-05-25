package fr.quentin.coevolutionMiner.v2.ast;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import fr.quentin.coevolutionMiner.v2.ast.AST.FileSnapshot.Range;
import fr.quentin.coevolutionMiner.v2.sources.Sources;
import fr.quentin.coevolutionMiner.v2.sources.Sources.Commit;
import fr.quentin.coevolutionMiner.v2.utils.Utils;
import spoon.MavenLauncher;
import spoon.reflect.declaration.CtElement;

public class AST {
    public final MavenLauncher launcher;
    public final Path rootDir;
    public final Exception compilerException;
    public final Specifier spec;
    public final Commit commit;

    public AST(Specifier spec, Commit commit, Path rootDir, MavenLauncher launcher, Exception compilerException) {
        this.spec = spec;
        this.commit = commit;
        this.rootDir = rootDir;
        this.launcher = launcher;
        this.compilerException = compilerException;
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

    private Map<FileSnapshot.Range, Object> originals = new HashMap<>();

    public Object getOriginal(FileSnapshot.Range range) {
        return originals.get(range);
    }

    Object putOriginal(FileSnapshot.Range range, Object original) {
        return originals.put(range, original);
    }

    private Map<String, FileSnapshot> snaps = new HashMap<>();

    public FileSnapshot.Range getRange(String path, Integer start, Integer end) {
        if (Paths.get(path).isAbsolute()) {
            System.out.println(path);
        }
        FileSnapshot s = snaps.get(path);
        if (s == null) {
            s = new FileSnapshot(path);
            snaps.put(path, s);
        }
        return s.getRange(start, end);
    }

    public FileSnapshot.Range getRange(String path, Integer start, Integer end, Object original) {
        Range range = getRange(path, start, end);
        Object old = putOriginal(range, original);
        if (old!=null && old!=original) {
            throw new RuntimeException("Original value of range should be unique");
        }
        return range;
    }

    public class FileSnapshot {
        String path;

        FileSnapshot(String path) {
            this.path = path;
        }

        /**
         * @return the commit
         */
        public Commit getCommit() {
            return AST.this.commit;
        }
        /**
         * @return the path
         */
        public String getAbsolutePath() {
            return rootDir+"/"+path;
        }

        /**
         * @return the path
         */
        public String getPath() {
            return path;
        }

        private Map<FileSnapshot.Range, FileSnapshot.Range> ranges = new HashMap<>();

        Range getRange(Integer start, Integer end) {
            Range tmp = new FileSnapshot.Range(start, end);
            FileSnapshot.Range r = ranges.putIfAbsent(tmp, tmp);
            if (r == null) {
                return tmp;
            }
            return r;
        }

        public class Range {

            private Integer end;
            private Integer start;

            public FileSnapshot getFile() {
                return FileSnapshot.this;
            }

            public Integer getStart() {
                return start;
            }

            public Integer getEnd() {
                return end;
            }

            Range(Integer start, Integer end) {
                this.start = start;
                this.end = end;
            }

            @Override
            public int hashCode() {
                final int prime = 31;
                int result = 1;
                result = prime * result + getEnclosingInstance().hashCode();
                result = prime * result + ((end == null) ? 0 : end.hashCode());
                result = prime * result + ((start == null) ? 0 : start.hashCode());
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
                Range other = (Range) obj;
                if (!getEnclosingInstance().equals(other.getEnclosingInstance()))
                    return false;
                if (end == null) {
                    if (other.end != null)
                        return false;
                } else if (!end.equals(other.end))
                    return false;
                if (start == null) {
                    if (other.start != null)
                        return false;
                } else if (!start.equals(other.start))
                    return false;
                return true;
            }

            private FileSnapshot getEnclosingInstance() {
                return FileSnapshot.this;
            }
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((this.getCommit() == null) ? 0 : this.getCommit().hashCode());
            result = prime * result + ((path == null) ? 0 : path.hashCode());
            // result = prime * result + ((ranges == null) ? 0 : ranges.hashCode());
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
            FileSnapshot other = (FileSnapshot) obj;
            if (this.getCommit() == null) {
                if (other.getCommit() != null)
                    return false;
            } else if (!this.getCommit().equals(other.getCommit()))
                return false;
            if (path == null) {
                if (other.path != null)
                    return false;
            } else if (!path.equals(other.path))
                return false;
            // if (ranges == null) {
            //     if (other.ranges != null)
            //         return false;
            // } else if (!ranges.equals(other.ranges))
            //     return false;
            return true;
        }
    }

	public boolean isTest(Range target) {
        Object original = getOriginal(target);
        if (original!=null && original instanceof CtElement) {
            if (Utils.isTest((CtElement) original)) {
                return true;
            }
        }
		return false;
	}
}