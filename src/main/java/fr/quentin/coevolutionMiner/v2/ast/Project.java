package fr.quentin.coevolutionMiner.v2.ast;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import com.sun.tools.javac.util.Iterators;

import fr.quentin.coevolutionMiner.v2.ast.Project.AST.FileSnapshot.Range;
import fr.quentin.coevolutionMiner.v2.ast.miners.SpoonMiner.ProjectSpoon.SpoonAST;
import fr.quentin.coevolutionMiner.v2.sources.Sources;
import fr.quentin.coevolutionMiner.v2.sources.Sources.Commit;
import fr.quentin.coevolutionMiner.v2.utils.Iterators2;
import fr.quentin.coevolutionMiner.v2.utils.Utils;
import spoon.reflect.code.CtBinaryOperator;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.code.CtTypeAccess;
import spoon.reflect.code.CtUnaryOperator;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtNamedElement;
import spoon.reflect.path.CtRole;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtReference;
import spoon.reflect.reference.CtTypeReference;

public class Project<T> implements Iterable<Project> {
    public final Specifier spec;
    public final Commit commit;

    public Project(Specifier spec, Set<Project<?>> modules, Commit commit, Path rootDir) {
        this.spec = spec;
        this.commit = commit;
        this.ast = null;
        this.modules = modules;
    }

    public static class Specifier<U extends ProjectMiner> {
        public final Sources.Specifier sources;
        public final Class<U> miner;
        public final String commitId;
        public final Path relPath;

        public Specifier(Sources.Specifier sources, String commitId, Class<U> miner) {
            this(sources, Paths.get(""), commitId, miner);
        }

        public Specifier(Sources.Specifier sources, Path relPath, String commitId, Class<U> miner) {
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
            result = prime * result + ((commitId == null) ? 0 : commitId.hashCode());
            result = prime * result + ((miner == null) ? 0 : miner.hashCode());
            result = prime * result + ((relPath == null) ? 0 : relPath.hashCode());
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
            if (commitId == null) {
                if (other.commitId != null)
                    return false;
            } else if (!commitId.equals(other.commitId))
                return false;
            if (miner == null) {
                if (other.miner != null)
                    return false;
            } else if (!miner.equals(other.miner))
                return false;
            if (relPath == null) {
                if (other.relPath != null)
                    return false;
            } else if (!relPath.equals(other.relPath))
                return false;
            if (sources == null) {
                if (other.sources != null)
                    return false;
            } else if (!sources.equals(other.sources))
                return false;
            return true;
        }

        @Override
        public String toString() {
            return "Specifier [sources=" + sources + ", commitId=" + commitId + ", miner=" + miner.getSimpleName()
                    + ", relPath=" + relPath.toString() + "]";
        }

    }

    protected Project<T>.AST ast;

    public Project<T>.AST getAst() {
        return ast;
    }

    private final Set<Project<?>> modules;

    public Collection<Project<?>> getModules() {
        return Collections.unmodifiableSet(modules);
    }

    public AST.FileSnapshot.Range getRange(String path, Integer start, Integer end) throws RangeMatchingException {
        return getRangeAux(path, start, end, null);
    }

    public AST.FileSnapshot.Range getRange(String path, Integer start, Integer end, Object original)
            throws RangeMatchingException {
        if (Paths.get(path).isAbsolute()) {
            System.err.println(path);
            return null;
        }
        if (path.endsWith("/")) {
            if (path.startsWith(ast.rootDir.toString())) {
                throw new RangeMatchingException("The following dir cannot be in " + ast.rootDir.toString() + ": " + path.toString());
            }
            return getRangeDir(path, start, end, original);
        } else {
            return getRangeAux(path, start, end, original);
        }
    }

    private AST.FileSnapshot.Range getRangeDir(String path, Integer start, Integer end, Object original)
            throws RangeMatchingException {
        assert !Paths.get(path).isAbsolute() : path;
        assert ast != null;
        assert path != null;
        boolean unusableAstFound = false;
        boolean matchACompilationUnit = false;
        for (Project project : this) {
            Project.AST ast = project.ast;
            File parentDir = Paths.get(ast.rootDir.toString(), path).getParent().toFile();
            if (!ast.isUsable()) {
                unusableAstFound = true;
            } else if (((SpoonAST)ast).launcher.getModelBuilder().getInputSources().stream().anyMatch(x->x.getAbsolutePath().startsWith(path))) {
                Range r = ast.getRange(path, start, end, original);
                if (r != null) {
                    return r;
                }
                matchACompilationUnit = true;
            }
        }
        if (matchACompilationUnit) {
            return null;
        } else if (unusableAstFound) {
            // Probably caused by failed parsing
            throw new RangeMatchingException("unusable AST found while trying to match the cu at: " + path.toString());
        } else {
            // Probably caused by range being a resource and not functional source code
            throw new RangeMatchingException("No cu corresponding to " + path.toString());
        }
    }

    private AST.FileSnapshot.Range getRangeAux(String path, Integer start, Integer end, Object original)
            throws RangeMatchingException {
        assert !Paths.get(path).isAbsolute() : path;
        assert ast != null;
        assert path != null;
        boolean unusableAstFound = false;
        boolean matchACompilationUnit = false;
        for (Project project : this) {
            Project.AST ast = project.ast;
            File parentDir = Paths.get(ast.rootDir.toString(), path).getParent().toFile();
            if (!ast.isUsable()) {
                unusableAstFound = true;
            } else if (ast.contains(parentDir)) {
                Range r = ast.getRange(path, start, end, original);
                if (r != null) {
                    return r;
                }
                matchACompilationUnit = true;
            }
        }
        if (matchACompilationUnit) {
            return null;
        } else if (unusableAstFound) {
            // Probably caused by failed parsing
            throw new RangeMatchingException("unusable AST found while trying to match the cu at: " + path.toString());
        } else {
            // Probably caused by range being a resource and not functional source code
            throw new RangeMatchingException("No cu corresponding to " + path.toString());
        }
    }

    public class AST {
        public final Path rootDir;

        public Project<T> getProject() {
            return Project.this;
        }

        public boolean isUsable() {
            return true;
        }

        public Path getRootDir() {
            return rootDir;
        }

        protected boolean contains(File x) {
            return true;
        }

        public final Exception compilerException;

        protected AST(Path rootDir, Exception compilerException) {
            this.rootDir = rootDir;
            this.compilerException = compilerException;
        }

        // private Map<FileSnapshot.Range, T> originals = new HashMap<>();

        public T getOriginal(FileSnapshot.Range range) {
            return range.getOriginal();
        }

        Object putOriginal(FileSnapshot.Range range, T original) {
            return range.setOriginal(original);
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
            if (original != null) {
                Object old = range.setOriginal(original);
                if (old != null && old != original) {
                    throw new RuntimeException("Original value of range should be unique");
                }
            }
            return range;
        }

        public class FileSnapshot {
            String path;

            FileSnapshot(String path) {
                this.path = path;
                this.hashCode = hashCodeCompute();
            }

            /**
             * @return the commit
             */
            public Commit getCommit() {
                return Project.this.commit;
            }

            public AST getAST() {
                return AST.this;
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

                private final int end;
                private final int start;
                private T original;

                public FileSnapshot getFile() {
                    return FileSnapshot.this;
                }

                public T getOriginal() {
                    return original;
                }

                public Object setOriginal(T original) {
                    return this.original = original;
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
                    result = prime * result + getFileSnapshot().hashCode();
                    result = prime * result + end;
                    result = prime * result + start;
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
                    if (!getFileSnapshot().equals(other.getFileSnapshot()))
                        return false;
                    if (end != other.end)
                        return false;
                    if (start != other.start)
                        return false;
                    return true;
                }

                private FileSnapshot getFileSnapshot() {
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
                    String astPath = makeAstPath((CtElement) original);
                    if (astPath != null) {
                        r.put("astPath", astPath);
                    }
                    if (original instanceof CtReference) {
                        r.put("value", ((CtReference) original).getSimpleName().toString());
                    }
                    if (original instanceof CtBinaryOperator) {
                        r.put("value", ((CtBinaryOperator) original).getKind().name());
                    }
                    if (original instanceof CtUnaryOperator) {
                        r.put("value", ((CtUnaryOperator) original).getKind().name());
                    } else if (original instanceof CtLiteral) {
                        r.put("value", litToString((CtLiteral<?>) original));
                    }
                    return r;
                }

                private String makeAstPath(CtElement original) {
                    if (original == null) {
                        return null;
                    } else if (original instanceof CtReference) {
                        try {
                            return original.getPath().toString();
                        } catch (Exception e) {
                            if (!original.isParentInitialized()) {
                                return null;
                            }
                            try {
                                return original.getParent().getPath().toString() + "#"
                                        + ((CtReference) original).getRoleInParent().toString();
                            } catch (Exception ee) {
                                if (original instanceof CtExecutableReference) {
                                    return original.getParent().getPath().toString() + "#"
                                            + CtRole.EXECUTABLE_REF.toString();
                                } else if (original instanceof CtTypeReference
                                        && original.getParent() instanceof CtTypeAccess) {
                                    return original.getParent().getPath().toString() + "#"
                                            + CtRole.ACCESSED_TYPE.toString();
                                } else if (original.getParent() instanceof CtNamedElement) {
                                    return original.getParent().getPath().toString() + "#" + CtRole.NAME.toString();
                                } else {
                                    return makeAstPath(original.getParent()) + "#?";
                                }
                            }
                        }
                    } else if (original instanceof CtElement) {
                        try {
                            return original.getPath().toString();
                        } catch (Exception e) {
                            return makeAstPath(original.getParent()) + "#?";
                        }
                    }
                    return null;
                }

                @Override
                public String toString() {
                    return "Range [snap=" + getFileSnapshot().toString() + " start=" + start + ", end=" + end + "]";
                }
            }

            private <T> String litToString(CtLiteral<T> literal) {
                T val = literal.getValue();
                String label;
                if (val instanceof String) {
                    label = "\"" + ((String) val) + "\"";
                } else if (val instanceof Character) {
                    label = "'" + ((Character) val).toString() + "'";
                } else if (val instanceof Number) {
                    try {
                        Class<?> c = Class.forName("spoon.reflect.visitor.LiteralHelper");
                        Method m = c.getDeclaredMethod("getLiteralToken", CtLiteral.class);
                        m.setAccessible(true);
                        label = (String) m.invoke(null, literal);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                } else if (val != null) {
                    label = val.toString();
                } else {
                    label = "null";
                }
                return label;
            }

            @Override
            public int hashCode() {
                return hashCode;
            }

            private final int hashCode;

            private int hashCodeCompute() {
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

            @Override
            public String toString() {
                return "FileSnapshot [path=" + getAst().toString() + ", path=" + path + "]";
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

        @Override
        public String toString() {
            return "AST [project=" + getProject().toString() + "]";
        }

    }

    public Iterator<Project> iterator() {
        return Iterators2.<Project>createCompoundIterator(Project.this, (Project x) -> {
            return x.getModules().iterator();
        });
    }

    @Override
    public String toString() {
        return "Project [spec=" + spec.toString() + "]";
    }
}