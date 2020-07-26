package fr.quentin.coevolutionMiner.v2.ast;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import fr.quentin.coevolutionMiner.v2.ast.Project.AST.FileSnapshot.Range;
import fr.quentin.coevolutionMiner.v2.sources.Sources;
import fr.quentin.coevolutionMiner.v2.sources.Sources.Commit;
import fr.quentin.coevolutionMiner.v2.utils.Utils;
import spoon.MavenLauncher;
import spoon.reflect.declaration.CtElement;

public class Project<T> {
    public final Specifier spec;
    public final Commit commit;

    public Project(Specifier spec, Set<Project<?>> modules, Commit commit, Path rootDir) {
        this.spec = spec;
        this.commit = commit;
        this.ast = null;
        this.modules = modules;
    }

    public static class Specifier {
        public final Sources.Specifier sources;
        public final Class<? extends ProjectMiner> miner;
        public final String commitId;
        public final Path relPath;

        public Specifier(Sources.Specifier sources, String commitId, Class<? extends ProjectMiner> miner) {
            this.sources = sources;
            this.relPath = Paths.get("");
            this.commitId = commitId;
            this.miner = miner;
        }

        public Specifier(Sources.Specifier sources, Path relPath, String commitId,
                Class<? extends ProjectMiner> miner) {
            this.sources = sources;
            this.relPath = relPath;
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

    protected AST ast;

    public AST getAst() {
        return ast;
    }

    private final Set<Project<?>> modules;

    public Collection<Project<?>> getModules() {
        return Collections.unmodifiableSet(modules);
    }

    public AST.FileSnapshot.Range getRange(String path, Integer start, Integer end) {
        if (Paths.get(path).isAbsolute()) {
            System.err.println(path);
            return null;
        }
        return getRangeAux(path, start, end);
    }

    private AST.FileSnapshot.Range getRangeAux(String path, Integer start, Integer end) {
        File x = Paths.get(path).toFile();
        if (ast.launcher.getModelBuilder().getInputSources().contains(x)) {
            return ast.getRange(path, start, end);
        } else {
            for (Project project : modules) {
                Range tmp = project.getRangeAux(path, start, end);
                if (tmp != null) {
                    return tmp;
                }
            }
        }
        return null;
    }

    protected AST makeAST(Path rootDir, MavenLauncher launcher, Exception compilerException) {
        return new AST(rootDir, launcher, compilerException);
    }

    public class AST {
        public final MavenLauncher launcher;
        public final Path rootDir;

        public Path getRootDir() {
            return rootDir;
        }

        public final Exception compilerException;

        AST(Path rootDir, MavenLauncher launcher, Exception compilerException) {
            this.rootDir = rootDir;
            this.launcher = launcher;
            this.compilerException = compilerException;
        }

        private Map<FileSnapshot.Range, T> originals = new HashMap<>();

        public T getOriginal(FileSnapshot.Range range) {
            return originals.get(range);
        }

        Object putOriginal(FileSnapshot.Range range, T original) {
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

        public FileSnapshot.Range getRange(String path, Integer start, Integer end, T original) {
            FileSnapshot.Range range = getRange(path, start, end);
            Object old = putOriginal(range, original);
            if (old != null && old != original) {
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
                return Project.this.commit;
            }

            /**
             * @return the path
             */
            public String getAbsolutePath() {
                return Paths.get(rootDir.toString(), path).toString();
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

                public Map<String, Object> toMap() {
                    Map<String, Object> r = new HashMap<>();
                    FileSnapshot file = getFile();
                    r.put("repository", file.getCommit().getRepository().getUrl());
                    r.put("commitId", file.getCommit().getId());
                    r.put("file", file.getPath());
                    r.put("start", getStart());
                    r.put("end", getEnd());
                    return r;
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
                // if (other.ranges != null)
                // return false;
                // } else if (!ranges.equals(other.ranges))
                // return false;
                return true;
            }
        }

        public boolean isTest(FileSnapshot.Range target) {
            Object original = getOriginal(target);
            if (original != null && original instanceof CtElement) {
                if (Utils.isTest((CtElement) original)) {
                    return true;
                }
            }
            return false;
        }

        private Stats globalStats = new Stats();

        public Stats getGlobalStats() {
            return globalStats;
        }
    }

}