package fr.quentin.coevolutionMiner.v2.evolution.miners;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import com.github.gumtreediff.actions.model.Action;
import com.github.gumtreediff.actions.model.Delete;
import com.github.gumtreediff.actions.model.Move;
import com.github.gumtreediff.tree.AbstractVersionedTree;
import com.github.gumtreediff.tree.ITree;
import com.github.gumtreediff.tree.Version;
import com.github.gumtreediff.tree.VersionInt;
import com.github.gumtreediff.tree.VersionedTree;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.MutablePair;
import org.bouncycastle.util.Iterable;

import fr.quentin.coevolutionMiner.utils.SourcesHelper;
import fr.quentin.coevolutionMiner.v2.ast.Project;
import fr.quentin.coevolutionMiner.v2.ast.Project.AST.FileSnapshot.Range;
import fr.quentin.coevolutionMiner.v2.ast.ProjectHandler;
import fr.quentin.coevolutionMiner.v2.ast.miners.SpoonMiner;
import fr.quentin.coevolutionMiner.v2.ast.miners.SpoonMiner.ProjectSpoon;
import fr.quentin.coevolutionMiner.v2.ast.miners.SpoonMiner.ProjectSpoon.SpoonAST;
import fr.quentin.coevolutionMiner.v2.evolution.EvolutionHandler;
import fr.quentin.coevolutionMiner.v2.evolution.Evolutions;
import fr.quentin.coevolutionMiner.v2.evolution.EvolutionsImpl;
import fr.quentin.coevolutionMiner.v2.evolution.EvolutionsMiner;
import fr.quentin.coevolutionMiner.v2.evolution.Evolutions.Evolution.DescRange;
import fr.quentin.coevolutionMiner.v2.sources.Sources;
import fr.quentin.coevolutionMiner.v2.sources.Sources.Commit;
import fr.quentin.coevolutionMiner.v2.sources.SourcesHandler;
import gumtree.spoon.AstComparator;
import gumtree.spoon.apply.AAction;
import gumtree.spoon.apply.operations.MyScriptGenerator;
import gumtree.spoon.builder.CtWrapper;
import gumtree.spoon.builder.SpoonGumTreeBuilder;
import gumtree.spoon.diff.Diff;
import gumtree.spoon.diff.DiffImpl;
import gumtree.spoon.diff.MultiDiffImpl;
import gumtree.spoon.diff.operations.Operation;
import gumtree.spoon.diff.support.SpoonSupport;
import spoon.MavenLauncher;
import spoon.reflect.code.CtAbstractInvocation;
import spoon.reflect.cu.CompilationUnit;
import spoon.reflect.cu.SourcePosition;
import spoon.reflect.cu.position.DeclarationSourcePosition;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtPackage;
import spoon.reflect.path.CtPath;
import spoon.reflect.reference.CtReference;

public class GumTreeSpoonMiner implements EvolutionsMiner {
    Logger LOGGER = Logger.getLogger("ImpactGT commitHandler");

    private ProjectHandler astHandler;
    private SourcesHandler srcHandler;
    private Evolutions.Specifier spec;

    // TODO instanciate filters correctly and make use of them
    private List<Object> filters;

    public GumTreeSpoonMiner(Evolutions.Specifier spec, SourcesHandler srcHandler, ProjectHandler astHandler) {
        this.spec = spec;
        this.astHandler = astHandler;
        this.srcHandler = srcHandler;
    }

    public GumTreeSpoonMiner(Evolutions.Specifier spec, SourcesHandler srcHandler, ProjectHandler astHandler,
            List<Object> filters) {
        this(spec, srcHandler, astHandler);
        this.filters = filters;
    }

    @Override
    public Evolutions compute() {
        Sources src = srcHandler.handle(spec.sources, "jgit");

        EvolutionsMany result = new EvolutionsMany(spec, src);
        return result.compute();
    }

    public static class SpecificifierAtProj extends Evolutions.Specifier {

        private Project.Specifier<SpoonMiner> before_spec;
        private Project.Specifier<SpoonMiner> after_spec;

        public SpecificifierAtProj(fr.quentin.coevolutionMiner.v2.sources.Sources.Specifier sources,
                Project.Specifier<SpoonMiner> before_spec, Project.Specifier<SpoonMiner> after_spec,
                Class<? extends EvolutionsMiner> miner) {
            super(sources, before_spec.commitId, after_spec.commitId, miner);
            this.before_spec = before_spec;
            this.after_spec = after_spec;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = super.hashCode();
            result = prime * result + ((after_spec == null) ? 0 : after_spec.hashCode());
            result = prime * result + ((before_spec == null) ? 0 : before_spec.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (!super.equals(obj))
                return false;
            if (getClass() != obj.getClass())
                return false;
            SpecificifierAtProj other = (SpecificifierAtProj) obj;
            if (after_spec == null) {
                if (other.after_spec != null)
                    return false;
            } else if (!after_spec.equals(other.after_spec))
                return false;
            if (before_spec == null) {
                if (other.before_spec != null)
                    return false;
            } else if (!before_spec.equals(other.before_spec))
                return false;
            return true;
        }

    }

    public class EvolutionsAtCommit extends EvolutionsImpl {
        Map<SpecificifierAtProj, EvolutionsAtProj> modules = new HashMap<>();
        Map<Project.Specifier<?>, Project.Specifier<?>> projSpecMapping = new HashMap<>();
        private EvolutionsAtProj rootModule;
        public final VersionCommit beforeVersion;
        public final VersionCommit afterVersion;

        public Project.Specifier getProjectSpec(Project.Specifier spec) {
            return projSpecMapping.get(spec);
        }

        public EvolutionsAtProj getRootModule() {
            return rootModule;
        }

        public EvolutionsAtCommit(Specifier spec, Sources sources, Commit beforeCom, Commit commit) {
            super(spec, sources);
            this.beforeVersion = VersionCommit.build(sources.getCommit(spec.commitIdBefore));
            this.afterVersion = VersionCommit.build(sources.getCommit(spec.commitIdAfter));
        }

        private EvolutionsAtProj compute() {
            SpecificifierAtProj projSpec = new SpecificifierAtProj(spec.sources,
                    astHandler.buildSpec(spec.sources, spec.commitIdBefore),
                    astHandler.buildSpec(spec.sources, spec.commitIdAfter), spec.miner);
            EvolutionsAtProj result = new EvolutionsAtProj(projSpec);
            this.rootModule = result;
            this.modules.put(projSpec, result);
            result.compute();
            return result;
        }
        
        @Override
        public Map<Commit, Evolutions> perBeforeCommit() {
            throw new UnsupportedOperationException(); // TODO put another exception such as CurrEvo already split by commit
        }

        public class EvolutionsAtProj extends EvolutionsImpl {
            List<EvolutionsAtProj> modules = new ArrayList<>();
            private Diff diff = null;
            private MultiDiffImpl mdiff = null;
            private Project<?> beforeProj;
            private Project<?> afterProj;
            private SpoonGumTreeBuilder scanner;

            public Diff getDiff() {
                return diff;
            }

            public MultiDiffImpl getMdiff() {
                return mdiff;
            }

            public SpoonGumTreeBuilder getScanner() {
                return scanner;
            }

            public Project<?> getBeforeProj() {
                return beforeProj;
            }

            public Project<?> getAfterProj() {
                return afterProj;
            }

            public EvolutionsAtProj(SpecificifierAtProj spec) {
                super(spec, EvolutionsAtCommit.this.sources);
            }

            @Override
            public Map<Commit, Evolutions> perBeforeCommit() {
                throw new UnsupportedOperationException();
            }

            public EvolutionsAtProj compute() {
                Project<?> beforeProj = astHandler.handle(((SpecificifierAtProj) spec).before_spec);
                Project<?> afterProj = astHandler.handle(((SpecificifierAtProj) spec).after_spec);
                linkProjSpecs(beforeProj.spec, afterProj.spec);
                return compute(beforeProj, afterProj);
            }

            private EvolutionsAtProj compute(Project<?> beforeProj, Project<?> afterProj) {
                this.beforeProj = beforeProj;
                this.afterProj = afterProj;
                // AstComparator comp = new AstComparator();
                String relPath = beforeProj.spec.relPath.toString();
                assert relPath.equals(afterProj.spec.relPath.toString()) : relPath;
                Map<String, MutablePair<Project<?>, Project<?>>> modulesPairs = new HashMap<>();
                for (Project<?> p : beforeProj.getModules()) {
                    modulesPairs.putIfAbsent(p.spec.relPath.toString(), new MutablePair<>());
                    modulesPairs.get(p.spec.relPath.toString()).setLeft(p);
                }
                for (Project<?> p : afterProj.getModules()) {
                    modulesPairs.putIfAbsent(p.spec.relPath.toString(), new MutablePair<>());
                    modulesPairs.get(p.spec.relPath.toString()).setRight(p);
                }

                if (beforeProj.getAst().isUsable() && afterProj.getAst().isUsable()) {
                    CtPackage left = ((ProjectSpoon.SpoonAST) beforeProj.getAst()).launcher.getModel().getRootPackage();
                    // TODO make a checkable API, to access use :
                    // (ImmutableTriple<Path,Path,MavenLauncher.SOURCE_TYPE>())cu.getMetadata("SourceTypeNRootDirectory");
                    for (CompilationUnit cu : left.getFactory().CompilationUnit().getMap().values()) {
                        for (File root : ((ProjectSpoon.SpoonAST) beforeProj.getAst()).launcher.getPomFile()
                                .getSourceDirectories()) {
                            if (cu.getFile().toPath().startsWith(root.toPath())) {
                                cu.putMetadata("SourceTypeNRootDirectory", new ImmutableTriple<>(
                                        ((ProjectSpoon.SpoonAST) beforeProj.getAst()).rootDir,
                                        ((ProjectSpoon.SpoonAST) beforeProj.getAst()).rootDir.relativize(root.toPath()),
                                        MavenLauncher.SOURCE_TYPE.APP_SOURCE));
                            }
                        }
                        for (File root : ((ProjectSpoon.SpoonAST) beforeProj.getAst()).launcher.getPomFile()
                                .getTestDirectories()) {
                            if (cu.getFile().toPath().startsWith(root.toPath())) {
                                cu.putMetadata("SourceTypeNRootDirectory", new ImmutableTriple<>(
                                        ((ProjectSpoon.SpoonAST) beforeProj.getAst()).rootDir,
                                        ((ProjectSpoon.SpoonAST) beforeProj.getAst()).rootDir.relativize(root.toPath()),
                                        MavenLauncher.SOURCE_TYPE.TEST_SOURCE));
                            }
                        }
                    }
                    CtPackage right = ((ProjectSpoon.SpoonAST) afterProj.getAst()).launcher.getModel().getRootPackage();
                    for (CompilationUnit cu : right.getFactory().CompilationUnit().getMap().values()) {
                        for (File root : ((ProjectSpoon.SpoonAST) afterProj.getAst()).launcher.getPomFile()
                                .getSourceDirectories()) {
                            if (cu.getFile().toPath().startsWith(root.toPath())) {
                                cu.putMetadata("SourceTypeNRootDirectory", new ImmutableTriple<>(
                                        ((ProjectSpoon.SpoonAST) afterProj.getAst()).rootDir,
                                        ((ProjectSpoon.SpoonAST) afterProj.getAst()).rootDir.relativize(root.toPath()),
                                        MavenLauncher.SOURCE_TYPE.APP_SOURCE));
                            }
                        }
                        for (File root : ((ProjectSpoon.SpoonAST) afterProj.getAst()).launcher.getPomFile()
                                .getTestDirectories()) {
                            if (cu.getFile().toPath().startsWith(root.toPath())) {
                                cu.putMetadata("SourceTypeNRootDirectory", new ImmutableTriple<>(
                                        ((ProjectSpoon.SpoonAST) afterProj.getAst()).rootDir,
                                        ((ProjectSpoon.SpoonAST) afterProj.getAst()).rootDir.relativize(root.toPath()),
                                        MavenLauncher.SOURCE_TYPE.TEST_SOURCE));
                            }
                        }
                    }
                    this.scanner = new SpoonGumTreeBuilder();
                    ITree srcTree;
                    srcTree = scanner.getTree(left);
                    this.mdiff = new MultiDiffImpl(srcTree, beforeVersion);
                    ITree dstTree = scanner.getTree(right);
                    this.diff = mdiff.compute(scanner.getTreeContext(), dstTree, afterVersion);
                    // this.diff = comp.compare(
                    // ((ProjectSpoon.SpoonAST)
                    // beforeProj.getAst()).launcher.getModel().getRootPackage(),
                    // ((ProjectSpoon.SpoonAST)
                    // afterProj.getAst()).launcher.getModel().getRootPackage());
                    for (Action op : ((DiffImpl) diff).getComposedActions()) {
                        addEvolution(op, beforeProj, afterProj);
                        try {
                            LOGGER.info("O- " + op + "\n");
                        } catch (Exception e) {
                        }
                    }
                    for (Action op : ((DiffImpl) diff).getAtomicActions()) {
                        addEvolution(op, beforeProj, afterProj);
                        try {
                            LOGGER.info("O- " + op + "\n");
                        } catch (Exception e) {
                        }
                    }
                }

                // TODO handle moved/renamed projects
                for (Entry<String, MutablePair<Project<?>, Project<?>>> entry : modulesPairs.entrySet()) {
                    MutablePair<Project<?>, Project<?>> value = entry.getValue();
                    SpecificifierAtProj projSpec = new SpecificifierAtProj(spec.sources, value.left.spec,
                            value.right.spec, spec.miner);
                    EvolutionsAtProj child = new EvolutionsAtProj(projSpec);
                    modules.add(child.compute(value.left, value.right));
                    EvolutionsAtCommit.this.modules.put(projSpec, child.compute(value.left, value.right));
                    linkProjSpecs(value.left.spec, value.right.spec);
                }
                return this;
            }

            private void linkProjSpecs(Project.Specifier a, Project.Specifier b) {
                EvolutionsAtCommit.this.projSpecMapping.put(a, b);
                EvolutionsAtCommit.this.projSpecMapping.put(b, a);
            }

            void addEvolution(Action op, Project<?> astBefore, Project<?> astAfter) {
                List<ImmutablePair<Range, String>> before = new ArrayList<>();
                ITree source = ((AAction) op).getSource();
                AbstractVersionedTree target = ((AAction) op).getTarget();
                ImmutablePair<Range, String> rangeBef = toRange(astBefore, op instanceof Delete ? target : source,
                        "src", beforeVersion);
                if (rangeBef != null) {
                    before.add(rangeBef);
                }
                List<ImmutablePair<Range, String>> after = new ArrayList<>();
                ImmutablePair<Range, String> rangeAft = op instanceof Delete ? null
                        : toRange(astAfter, target, "dst", afterVersion);
                if (rangeAft != null) {
                    after.add(rangeAft);
                }
                Evolution evo = super.addEvolution(op.getName(), before, after, astBefore.commit, astAfter.commit,
                        (Object) op);
                for (DescRange dr : evo.getBefore()) {
                    augment(dr);
                }
                for (DescRange dr : evo.getAfter()) {
                    augment(dr);
                }
            }

            private <T> ImmutablePair<Range, String> toRange(Project<T> proj, ITree tree, String desc,
                    Version version) {
                CtElement ele = (CtElement) tree.getMetadata(VersionedTree.ORIGINAL_SPOON_OBJECT);
                int start, end;
                Path path;
                if (ele == null) {
                    Map<Version, CtElement> map = (Map<Version, CtElement>) tree.getParent()
                            .getMetadata(MyScriptGenerator.ORIGINAL_SPOON_OBJECT_PER_VERSION);
                    if (map != null) {
                        ele = map.get(version);
                    }
                }
                if (ele == null) {
                    ele = (CtElement) tree.getParent().getMetadata(VersionedTree.ORIGINAL_SPOON_OBJECT);
                    if (ele == null) {
                        ele = (CtElement) tree.getMetadata(SpoonGumTreeBuilder.SPOON_OBJECT);
                    }
                    SourcePosition position = ele.getPosition();
                    if (position == null || !position.isValidPosition()) {
                        return null;
                    }
                    path = position.getFile().toPath();
                    // if(position instanceof DeclarationSourcePosition) {
                    // start = ((DeclarationSourcePosition)position).getNameStart();
                    // end = ((DeclarationSourcePosition)position).getNameEnd();
                    // } else {
                    start = position.getSourceStart();
                    end = position.getSourceEnd();
                    // }
                } else {
                    SourcePosition position = ele.getPosition();
                    if (position == null || !position.isValidPosition()) {
                        return null;
                    }
                    path = position.getFile().toPath();
                    start = position.getSourceStart();
                    end = position.getSourceEnd();
                }
                Range range = proj.getRange(proj.getAst().rootDir.relativize(path).toString(), start, end, ele);
                if (range == null) {
                    return null;
                }
                return new ImmutablePair<>(range, desc);
            }

            void addEvolution(Operation<?> op, Project<?> astBefore, Project<?> astAfter) {
                List<ImmutablePair<Range, String>> before = new ArrayList<>();
                ImmutablePair<Range, String> rangeBef = toRange(astBefore, op.getSrcNode(), "src");
                if (rangeBef != null) {
                    before.add(rangeBef);
                }
                List<ImmutablePair<Range, String>> after = new ArrayList<>();
                ImmutablePair<Range, String> rangeAft = toRange(astAfter, op.getDstNode(), "dst");
                if (rangeAft != null) {
                    after.add(rangeAft);
                }
                Evolution evo = super.addEvolution(op.getAction().getName(), before, after, astBefore.commit,
                        astAfter.commit, (Object) op);
                for (DescRange dr : evo.getBefore()) {
                    augment(dr);
                }
                for (DescRange dr : evo.getAfter()) {
                    augment(dr);
                }
            }

            private <T> ImmutablePair<Range, String> toRange(Project<T> proj, CtElement ele, String desc) {
                if (ele == null) {
                    return null;
                }
                SourcePosition position = ele.getPosition();
                if (position == null || !position.isValidPosition()) {
                    return null;
                }
                Range range = proj.getRange(proj.getAst().rootDir.relativize(position.getFile().toPath()).toString(),
                        position.getSourceStart(), position.getSourceEnd(), ele);
                if (range == null) {
                    return null;
                }
                return new ImmutablePair<>(range, desc);
            }

            private void augment(DescRange dr) {
                CtElement ori = (CtElement) dr.getTarget().getOriginal();
                assert ori != null;
                if (ori instanceof CtReference || ori instanceof CtWrapper) {
                    ori = ori.getParent();
                }
                Set<DescRange> md = (Set<DescRange>) ori.getMetadata(METADATA_KEY_EVO);
                if (md == null) {
                    md = new HashSet<>();
                    ori.putMetadata(METADATA_KEY_EVO, md);
                }
                md.add(dr);
            }

            @Override
            public Project<?>.AST.FileSnapshot.Range map(Project<?>.AST.FileSnapshot.Range range, Project<?> target) {
                Project<?> source = range.getFile().getAST().getProject();
                CtElement element = (CtElement) range.getOriginal();
                boolean fromSource = source.spec.equals(((SpecificifierAtProj) spec).before_spec);
                CtElement aaa = null;
                ITree tree = (ITree) element.getMetadata(SpoonGumTreeBuilder.GUMTREE_NODE);
                if (tree != null) {
                    if (fromSource) {
                        ITree tmp = ((DiffImpl) diff).getMappingsComp().getDst(tree);
                        if (tmp != null) {
                            aaa = (CtElement) tmp.getMetadata(SpoonGumTreeBuilder.SPOON_OBJECT);
                        }
                    } else {
                        ITree tmp = diff.getMappingsComp().getSrc(tree);
                        if (tmp != null) {
                            aaa = (CtElement) tmp.getMetadata(SpoonGumTreeBuilder.SPOON_OBJECT);
                        }
                    }
                } else {
                    if (aaa == null) {
                        aaa = new SpoonSupport().getMappedElement(diff, (CtElement) element, fromSource);
                    }
                }
                if (aaa == null) {
                    return null;
                }
                Project<?> outProj = fromSource ? afterProj : beforeProj;
                SourcePosition position = aaa.getPosition();
                return (Project<?>.AST.FileSnapshot.Range) outProj.getRange(position.getFile().getAbsolutePath(),
                        position.getSourceStart(), position.getSourceEnd());
            }
        }

        public Diff getDiff(Project.Specifier before, Project.Specifier after) {
            // assert !(before instanceof SpecificifierAtProj);
            SpecificifierAtProj projSpec = new SpecificifierAtProj(spec.sources, before, after, spec.miner);
            EvolutionsAtProj tmp = modules.get(projSpec);
            return tmp == null ? null : tmp.diff;
        }

        public EvolutionsAtProj getModule(Project.Specifier before, Project.Specifier after) {
            // assert !(before instanceof SpecificifierAtProj);
            SpecificifierAtProj projSpec = new SpecificifierAtProj(spec.sources, before, after, spec.miner);
            EvolutionsAtProj tmp = modules.get(projSpec);
            return tmp;
        }

        @Override
        public Project<?>.AST.FileSnapshot.Range map(Project<?>.AST.FileSnapshot.Range range, Project<?> target) {
            Project<?> source = range.getFile().getAST().getProject();
            EvolutionsAtProj diff = getModule(source.spec, target.spec);
            return diff.map(range, null);
        }

        @Override
        public Set<Evolution> getEvolution(String type, Project<?> source, List<ImmutablePair<Range, String>> before,
                Project<?> target, List<ImmutablePair<Range, String>> after) {
            // Project source = before.get(0).left.getFile().getAST().getProject();
            return getModule(source.spec, target.spec).getEvolution(type, source, before, target, after);
        }

        private boolean evoSetWasBuilt = false;

        @Override
        @Deprecated
        public Set<Evolution> toSet() {
            if (evoSetWasBuilt) {
                return evolutions;
            }
            for (Evolution e : this) {
                evolutions.add(e);
            }
            evoSetWasBuilt = true;
            return evolutions;
        }

        @Override
        public Iterator<Evolution> iterator() {
            if (evoSetWasBuilt) {
                return evolutions.iterator();
            }
            return new Iterator<Evolutions.Evolution>() {

                Iterator<EvolutionsAtProj> commitIt = EvolutionsAtCommit.this.modules.values().iterator();
                Iterator<Evolutions.Evolution> it = EvolutionsAtCommit.this.evolutions.iterator();

                @Override
                public boolean hasNext() {
                    return commitIt.hasNext() || (it != null && it.hasNext());
                }

                @Override
                public Evolution next() {
                    while (true) {
                        try {
                            return it.next();
                        } catch (NoSuchElementException e) {
                            it = commitIt.next().iterator();
                        }
                    }
                }

            };
        }
    }

    public final class EvolutionsMany extends EvolutionsImpl {

        private EvolutionsMany(Specifier spec, Sources sources) {
            super(spec, sources);
        }

        private EvolutionsMany(Specifier spec, Sources sources, Set<Evolution> subSet) {
            this(spec, sources);
            evolutions.addAll(subSet);
        }

        public Diff getDiff(Project.Specifier<?> before, Project.Specifier<?> after, String relPath) {
            EvolutionsAtCommit tmp = getPerCommit(before.commitId, after.commitId);
            return tmp.getDiff(before, after);
        }

        public EvolutionsAtCommit getPerCommit(String before, String after) {
            return perCommit.get(new ImmutablePair<>(before, after));
        }

        @Override
        public Project<?>.AST.FileSnapshot.Range map(Project<?>.AST.FileSnapshot.Range range, Project<?> target) {
            return getPerCommit(range.getFile().getCommit().getId(), target.spec.commitId).map(range, target);
        }

        EvolutionsMany compute() {
            List<Commit> commits;
            Commit beforeCom = null;
            try (SourcesHelper helper = this.sources.open()) {
                commits = this.sources.getCommitsBetween(spec.commitIdBefore, spec.commitIdAfter);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            for (Commit commit : commits) {
                if (beforeCom != null) {
                    // per commit evolutions
                    EvolutionsAtCommit perCommit = new EvolutionsAtCommit(
                            new Evolutions.Specifier(spec.sources, beforeCom.getId(), commit.getId(), spec.miner),
                            sources, beforeCom, commit);
                    perCommit.compute();
                    putPerCommit(beforeCom, commit, perCommit);
                }
                beforeCom = commit;
            }
            return this;
        }

        @Override
        public Set<Evolution> getEvolution(String type, Project<?> source, List<ImmutablePair<Range, String>> before,
                Project<?> target, List<ImmutablePair<Range, String>> after) {
            // Project source = before.get(0).left.getFile().getAST().getProject();
            return getPerCommit(source.spec.commitId, target.spec.commitId).getEvolution(type, source, before, target,
                    after);
        }

        Map<ImmutablePair<String, String>, EvolutionsAtCommit> perCommit = new HashMap<>();

        private EvolutionsAtCommit putPerCommit(Commit beforeCom, Commit commit, EvolutionsAtCommit perCommit) {
            return this.perCommit.put(new ImmutablePair<>(beforeCom.getId(), commit.getId()), perCommit);
        }

        private boolean evoSetWasBuilt = false;

        @Override
        public Set<Evolution> toSet() {
            if (evoSetWasBuilt) {
                return evolutions;
            }
            for (Evolution e : this) {
                evolutions.add(e);
            }
            evoSetWasBuilt = true;
            return evolutions;
        }

        @Override
        public Iterator<Evolution> iterator() {
            if (evoSetWasBuilt) {
                return evolutions.iterator();
            }
            return new Iterator<Evolutions.Evolution>() {

                Iterator<EvolutionsAtCommit> commitIt = EvolutionsMany.this.perCommit.values().iterator();
                Iterator<Evolutions.Evolution> it = EvolutionsMany.this.evolutions.iterator();

                @Override
                public boolean hasNext() {
                    return commitIt.hasNext() || (it != null && it.hasNext());
                }

                @Override
                public Evolution next() {
                    while (true) {
                        try {
                            return it.next();
                        } catch (NoSuchElementException e) {
                            it = commitIt.next().iterator();
                        }
                    }
                }

            };
        }

        @Override
        public JsonElement toJson() {
            // TODO optimize (serializing then deserializing is a dirty hack)
            Object diff = new Gson().fromJson(
                    JSON(spec.sources.repository, spec.commitIdAfter,
                            evolutions.stream().map(x -> (Action) x.getOriginal()).collect(Collectors.toList())),
                    new TypeToken<Object>() {
                    }.getType());
            return new Gson().toJsonTree(diff);
        }

        @Override
        public Map<Commit, Evolutions> perBeforeCommit() {
            Map<String, Set<Evolution>> tmp = new HashMap<>();
            for (Evolution evolution : this) {
                String cidb = evolution.getCommitBefore().getId();
                tmp.putIfAbsent(cidb, new HashSet<>());
                tmp.get(cidb).add(evolution);
            }
            Map<Commit, Evolutions> r = new HashMap<>();
            for (Set<Evolution> evolutionsSubSet : tmp.values()) {
                if (evolutionsSubSet.size() == 0) {
                    continue;
                }
                Evolutions newEvo = new EvolutionsMany(
                        new Evolutions.Specifier(spec.sources,
                                evolutionsSubSet.iterator().next().getCommitBefore().getId(),
                                evolutionsSubSet.iterator().next().getCommitAfter().getId(), spec.miner),
                        getSources(), evolutionsSubSet);
                r.put(evolutionsSubSet.iterator().next().getCommitBefore(), newEvo);
            }
            return r;
        }
    }

    public static String JSON(String gitURL, String currentCommitId, List<?> refactoringsAtRevision) {
        StringBuilder sb = new StringBuilder();
        // TODO
        return sb.toString();
    }

    /**
     * Only consider added nodes to find a path between 2 VersionCommit at least one
     * of the path must have all its Commit wrapped as VersionCommit
     */
    public static class VersionCommit implements Version {

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
            for (Commit x : this.commit.getChildrens()) {
                VersionCommit y = commitsToVersions.get(x);
                if (y == null) {
                    return false;
                } else if (o == y) {
                    return true;
                } else if (y.isDescendant(o)) {
                    return true;
                }
            }
            return false;
        }

        private boolean isAncestor(VersionCommit o) {
            for (Commit x : this.commit.getParents()) {
                VersionCommit y = commitsToVersions.get(x);
                if (y == null) {
                    return false;
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
            VersionCommit res = new VersionCommit(commit);
            commitsToVersions.put(commit, res);
            return res;
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
}