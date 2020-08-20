package fr.quentin.coevolutionMiner.v2.impact.miners;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Function;
import java.util.Set;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.gson.GsonBuilder;

import org.apache.commons.lang3.tuple.ImmutablePair;

import fr.quentin.coevolutionMiner.v2.ast.ProjectHandler;
import fr.quentin.coevolutionMiner.v2.ast.Project;
import fr.quentin.coevolutionMiner.v2.ast.Project.AST.FileSnapshot.Range;
import fr.quentin.coevolutionMiner.v2.ast.miners.SpoonMiner;
import fr.quentin.coevolutionMiner.v2.ast.miners.SpoonMiner.ProjectSpoon;
import fr.quentin.coevolutionMiner.v2.ast.miners.SpoonMiner.ProjectSpoon.SpoonAST;
import fr.quentin.coevolutionMiner.v2.evolution.EvolutionHandler;
import fr.quentin.coevolutionMiner.v2.evolution.Evolutions;
import fr.quentin.coevolutionMiner.v2.evolution.Evolutions.Evolution;
import fr.quentin.coevolutionMiner.v2.impact.Impacts;
import fr.quentin.coevolutionMiner.v2.impact.Impacts.Impact;
import fr.quentin.coevolutionMiner.v2.impact.Impacts.Impact.DescRange;
import fr.quentin.coevolutionMiner.v2.impact.ImpactsMiner;
import fr.quentin.coevolutionMiner.v2.utils.DbUtils;
import fr.quentin.coevolutionMiner.v2.utils.MySLL;
import fr.quentin.coevolutionMiner.v2.utils.Utils;
import fr.quentin.impactMiner.Explorer;
import fr.quentin.impactMiner.ImpactAnalysis;
import fr.quentin.impactMiner.ImpactChain;
import fr.quentin.impactMiner.ImpactElement;
import fr.quentin.impactMiner.ImpactType;
import fr.quentin.impactMiner.Impacts.Relations;
import fr.quentin.impactMiner.Position;
import fr.quentin.impactMiner.ImpactAnalysis.ImpactAnalysisException;
import spoon.MavenLauncher;
import spoon.reflect.cu.SourcePosition;
import spoon.reflect.cu.position.DeclarationSourcePosition;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtType;
import spoon.reflect.visitor.filter.TypeFilter;

// CAUTION only handle correctly evolution in consecutive commits, 
// reson impact only computer on first commit 
// and no implementation of forwarding ranges of code through commits (making sure they were not modified)
public class MyImpactsMiner implements ImpactsMiner {
    static Logger logger = Logger.getLogger(MyImpactsMiner.class.getName());

    private ProjectHandler astHandler;
    private EvolutionHandler evoHandler;
    private Impacts.Specifier spec;

    public MyImpactsMiner(Impacts.Specifier spec, ProjectHandler astHandler, EvolutionHandler evoHandler) {
        this.spec = spec;
        this.astHandler = astHandler;
        this.evoHandler = evoHandler;
    }

    @Override
    public Impacts compute() {
        assert spec.evoSpec != null : spec;
        boolean isOnBefore = spec.projSpec.commitId.equals(spec.evoSpec.commitIdBefore);
        ProjectSpoon project = (ProjectSpoon) astHandler.handle(spec.projSpec);
        try {
            ImpactsExtension result = computeAux(isOnBefore, project);
            return result;
        } catch (Exception e) {
            logger.log(Level.WARNING, e.getMessage(), e);
            return new ImpactsExtension(new Impacts.Specifier(project.spec, spec.evoSpec, spec.miner), project,
                    project.getAst().rootDir, null);
        }
    }

    private ImpactsExtension computeAux(boolean isOnBefore, ProjectSpoon project) throws ImpactAnalysisException {
        ProjectSpoon.SpoonAST ast = (ProjectSpoon.SpoonAST) project.getAst();
        Evolutions evo = evoHandler.handle(spec.evoSpec);
        // return res;

        Path rootDir = ast.rootDir;
        if (!ast.isUsable()) {
            // return null;
        }
        ImpactAnalysis l = new ImpactAnalysis(ast.augmented, 50);
        ImpactsExtension result = new ImpactsExtension(new Impacts.Specifier(project.spec, spec.evoSpec, spec.miner),
                project, rootDir, l);

        result.computeImpacts(isOnBefore, ast, evo);

        for (Project<?> childProj : project.getModules()) {
            try {
                result.addModule(computeAux(isOnBefore, (ProjectSpoon) childProj));
            } catch (Exception e) {
                logger.log(Level.WARNING, e.getMessage(), e);
            }
        }

        return result;
    }

    public class ImpactsExtension extends Impacts {
        private final Path root;
        ImpactAnalysis analyzer;
        Map<Path, ImpactsExtension> modules = new HashMap<>();

        public void addImpactedTest(Range range, Set<Object> evolutions) {
            Set<Object> tmp = impactedTests.get(range);
            if (tmp == null) {
                tmp = new HashSet<>();
                impactedTests.put(range, tmp);
            }
            impactedTests.get(range).addAll(evolutions);
        }

        public void addModule(ImpactsExtension impacts) {
            modules.put(impacts.spec.projSpec.relPath, impacts);
        }

        public ImpactsExtension getModule(Path relPath) {
            return modules.get(relPath);
        }

        public Collection<ImpactsExtension> getModules() {
            return Collections.unmodifiableCollection(modules.values());
        }

        public void addImpWithUniqEffect(Set<Object> roots, Set<Range> causes, Range effect, String type,
                String causeDesc, String effectDesc) {
            Impact imp = addImpact(type, Collections.emptySet(),
                    Collections.singleton(new ImmutablePair<>(effect, effectDesc)));
            for (Object root : roots) {
                perRoot.putIfAbsent(root, new HashSet<>());
                perRoot.get(root).add(imp);
            }
            for (Range range : causes) {
                addCause(imp, range, causeDesc);
            }
        }

        public void addImpWithUniqCause(Set<Object> roots, Range cause, Set<Range> effects, String type,
                String causeDesc, String effectDesc) {
            Impact imp = addImpact(type, Collections.singleton(new ImmutablePair<>(cause, causeDesc)),
                    Collections.emptySet());
            perRoot.putIfAbsent(root, new HashSet<>());
            perRoot.get(root).add(imp);
            for (Range range : effects) {
                addEffect(imp, range, effectDesc);
            }
        }

        public void addAdjusment(Object root, Range cause, Range effect) {
            Impact imp = addImpact("adjustment", Collections.singleton(new ImmutablePair<>(cause, "given")),
                    Collections.emptySet());
            perRoot.putIfAbsent(root, new HashSet<>());
            perRoot.get(root).add(imp);
            addEffect(imp, effect, "disambiguated");
        }

        public void addCalls(Set<Object> roots, Range cause, Set<Range> effects) {
            addImpWithUniqCause(roots, cause, effects, "call", "declaration", "reference");
        }

        public void addExpands(Set<Object> roots, Set<Range> causes, Range effect) {
            addImpWithUniqEffect(roots, causes, effect, "expand to executable", "instruction", "executable");
        }

        public Set<String> needs(Set<Evolution> evos, boolean onAfter) {
            Set<CtType<?>> tmp = new HashSet<>();
            TypeFilter<CtType<?>> filter = new TypeFilter<CtType<?>>(CtType.class);
            for (Evolution evo : evos) {
                for (Evolutions.Evolution.DescRange ds : onAfter ? evo.getBefore() : evo.getAfter()) {
                    tmp.add(((CtElement) ds.getTarget().getOriginal()).getParent(filter).getTopLevelType());
                }
            }
            Set<CtType> tmp2 = new HashSet<>();
            for (CtType<?> f : tmp) {
                tmp2.addAll(analyzer.augmented.needsDyn(f));
            }
            Set<String> result = new HashSet<>();
            for (CtType t : tmp) {
                SourcePosition position = t.getPosition();
                if (position.isValidPosition()) {
                    result.add(position.getFile().getAbsolutePath());
                }
            }
            return result;
        }

        @Override
        protected Map<String, Object> makeRange(DescRange descRange) {
            Map<String, Object> o = super.makeRange(descRange);
            Range target = descRange.getTarget();
            Object original = target.getOriginal();
            if (original != null) {
                o.put("type", Utils.formatedType(original));
                if (original instanceof CtExecutable) {
                    o.put("sig", ((CtExecutable<?>) original).getSignature());
                }
                if (original instanceof CtElement) {
                    if (Utils.isTest((CtElement) original)) {
                        o.put("isTest", true);
                    } else if (Utils.isParentTest((CtElement) original)) {
                        o.put("isTest", "parent");
                        // res.addAll(makeImpact(repository, commitIdBefore, rootDir, element,
                        // element.getContent().getParent(CtMethod.class), "expand to test"));
                    }
                }
            } else {
                o.put("type", "imp_null");
            }
            return o;
        }

        ImpactsExtension computeImpacts(boolean isOnBefore, SpoonAST ast, Evolutions evolutions) {
            logger.info("Number of executable refs mapped to positions " + evolutions.toSet().size());
            Explorer imptst1;
            try {
                Set<ImmutablePair<Object, CtElement>> tmp = new HashSet<>();
                for (Evolution evo : evolutions) {
                    for (Evolutions.Evolution.DescRange bef : isOnBefore ? evo.getBefore() : evo.getAfter()) {
                        final Range targ = bef.getTarget();
                        CtElement ori = (CtElement) targ.getOriginal();
                        // Position pos = new Position(targ.getFile().getPath(), targ.getStart(),
                        // targ.getEnd());
                        if (ori == null) {
                            logger.warning("no original element found at " + targ);
                        } else {
                            tmp.add(new ImmutablePair<>(bef, ori));
                        }
                    }
                }
                imptst1 = analyzer.getImpactedTests3(tmp, false);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            logger.info("Number of Impacted tests X number of evolutions " + imptst1.getFinishedChains().size());
            logger.info("Assembling Impacts");
            // fr.quentin.impactMiner.Impacts rawImpacts = new fr.quentin.impactMiner.Impacts(imptst1.getFinishedChains());
            // logger.info("Serializing Impacts");
            // logger.info(Integer.toString(rawImpacts.getRoots().size()));
            // logger.info(new GsonBuilder().setPrettyPrinting().create().toJson(rawImpacts.toJson()));

            // for (Entry<ImpactElement, Map<ImpactElement, Relations>> entry : rawImpacts.getVerticesPerRoots()
            //         .entrySet()) {
            Map<ImpactChain, Object> marched = new HashMap<>(); // Map<ImpactChain, Set<Evolutions.Evolution.DescRange> || ImpactChain>
            for (ImpactChain ic : imptst1.getFinishedChains()) {
                ConcurrentLinkedQueue<ImmutablePair<ImpactChain, MySLL<ImpactChain>>> toProcess = new ConcurrentLinkedQueue<>();
                toProcess.add(new ImmutablePair<>(ic, MySLL.EMPTY.cons(ic)));
                marched.putIfAbsent(ic, new HashSet<>());
                Set<Object> rootsDescsForTest = (Set) marched.get(ic);
                ImpactChain current;
                for (;;) {
                    ImmutablePair<ImpactChain, MySLL<ImpactChain>> currentPair = toProcess.poll();
                    if (currentPair == null) {
                        break;
                    }
                    current = currentPair.left;
                    List<ImpactChain> sinceLastRedOrEnd = new ArrayList<>(); // consecutive
                    MySLL<ImpactChain> prevsRedOrEnd = currentPair.right; // all since end of chain, in marched give Set<Evolutions.Evolution.DescRange>
                    for (;;) {
                        Object marchedVal = marched.get(current);
                        if (marchedVal != null && prevsRedOrEnd.tail != MySLL.EMPTY) {
                            if (marchedVal instanceof ImpactChain) {
                                marchedVal = marched.get(marchedVal);
                            }
                            assert marchedVal instanceof Set;
                            for (ImpactChain x : prevsRedOrEnd) {
                                ((Set) marched.get(x)).addAll((Set) marchedVal);
                            }
                            break;
                        }
                        ImpactElement last = current.getLast();
                        HashSet<ImpactChain> redundants = current.getMD(ImpactChain.REDUNDANT,
                                new HashSet<ImpactChain>());
                        if (redundants.size() > 0) { // current has redudant impacts
                            for (ImpactChain x : sinceLastRedOrEnd) {
                                Object tmp = marched.get(x);
                                if (tmp == null || tmp instanceof ImpactChain) {
                                    marched.put(x, current);
                                }
                            }
                            sinceLastRedOrEnd = new ArrayList<>();
                            prevsRedOrEnd = prevsRedOrEnd.cons(current);
                            marched.putIfAbsent(current, new HashSet<>());
                            for (ImpactChain redu : redundants) {
                                toProcess.add(new ImmutablePair<>(redu, prevsRedOrEnd));
                            }
                        }
                        ImpactChain prev = current.getPrevious();
                        if (prev == null) {
                            ImpactElement root = last; // equiv to current.getRoot()
                            for (Entry<Object, Position> b : root.getEvolutionWithNonCorrectedPosition().entrySet()) {
                                // roots.add(b.getKey());
                                Position rootPosition = root.getPosition();
                                String relPath = ast.rootDir.relativize(Paths.get(rootPosition.getFilePath()))
                                        .toString();
                                Project<CtElement>.AST.FileSnapshot.Range source = ast.getRange(relPath,
                                        rootPosition.getStart(), rootPosition.getEnd(), root.getContent());
                                Evolutions.Evolution.DescRange impactingDescRange = (Evolutions.Evolution.DescRange) b
                                        .getKey();
                                Project<CtElement>.AST.FileSnapshot.Range target = impactingDescRange.getTarget();
                                if (!source.equals(target)) {
                                    addAdjusment(b.getKey(), target, source);
                                }
                                for (ImpactChain x : prevsRedOrEnd) {
                                    ((Set) marched.get(x)).add(b.getKey());
                                }
                            }
                            break;
                        }
                        Impact imp;
                        switch (current.getType()) {
                            case CALL:
                                imp = addImpact(current.getType().toString(), // TODO try to put more metadata here
                                        Collections.singleton(
                                                new ImmutablePair<>(extracted(ast, prev.getLast()), "declaration")),
                                        Collections.emptySet());
                                addEffect(imp, extracted(ast, last), "reference");
                                break;
                            default:
                                imp = addImpact(current.getType().toString(), // TODO try to put more metadata here
                                        Collections.singleton(
                                                new ImmutablePair<>(extracted(ast, prev.getLast()), "cause")),
                                        Collections.singleton(
                                                new ImmutablePair<>(extracted(ast, ic.getLast()), "effect")));
                                break;
                        }
                        sinceLastRedOrEnd.add(current);
                        current = prev;
                    }
                }
                addImpactedTest(extracted(ast, ic.getLast()), rootsDescsForTest);
                // continue;
                //     ImpactElement root = ic.getRoot();
                //     Set<Object> roots = new HashSet<>();
                //     for (Entry<Object, Position> b : root.getEvolutionWithNonCorrectedPosition().entrySet()) {
                //         roots.add(b.getKey());
                //         Position rootPosition = root.getPosition();
                //         String relPath = ast.rootDir.relativize(Paths.get(rootPosition.getFilePath())).toString();
                //         Project<CtElement>.AST.FileSnapshot.Range source = ast.getRange(relPath, rootPosition.getStart(),
                //                 rootPosition.getEnd(), root.getContent());
                //         Evolutions.Evolution.DescRange impactingDescRange = (Evolutions.Evolution.DescRange) b.getKey();
                //         Project<CtElement>.AST.FileSnapshot.Range target = impactingDescRange.getTarget();
                //         if (!source.equals(target)) {
                //             addAdjusment(b.getKey(), target, source);
                //         }
                //     }
                //     for (Relations rel : entry.getValue().values()) {
                //         ImpactElement currIE = rel.getVertice();
                //         Position currIEPos = currIE.getPosition();
                //         String relPath = ast.rootDir.relativize(Paths.get(currIEPos.getFilePath())).toString();
                //         Project<CtElement>.AST.FileSnapshot.Range currRange = ast.getRange(relPath, currIEPos.getStart(),
                //                 currIEPos.getEnd(), currIE.getContent());

                //         Set<ImpactElement> calls = rel.getEffects().get("call");
                //         if (calls != null && calls.size() > 0) {
                //             addCalls(roots, currRange,
                //                     calls.stream().map(x -> extracted(ast, x)).collect(Collectors.toSet()));
                //         }

                //         for (ImpactType imp_t : rel.getCauses().keySet()) {
                //             String key = imp_t.toString();
                //             Set<ImpactElement> others = rel.getCauses().get(key);
                //             if (others == null) {
                //                 continue;
                //             } else if (key.equals("call")) {
                //             } else if (key.equals("expand to executable")) {
                //                 if (others.size() > 0) {
                //                     addExpands(roots,
                //                             others.stream().map(x -> extracted(ast, x)).collect(Collectors.toSet()),
                //                             currRange);
                //                 }
                //             } else {
                //                 for (ImpactElement other : others) {
                //                     Impact imp = addImpact(key,
                //                             Collections.singleton(new ImmutablePair<>(extracted(ast, other), "cause")),
                //                             Collections.singleton(new ImmutablePair<>(currRange, "effect")));
                //                     for (Object root1 : roots) {
                //                         perRoot.putIfAbsent(root1, new HashSet<>());
                //                         perRoot.get(root1).add(imp);
                //                     }
                //                 }
                //             }
                //         }
                //     }
            }
            // for (Entry<ImpactElement, Set<Object>> entry : rawImpacts.getTests().entrySet()) {
            //     ImpactElement test = entry.getKey();
            //     addImpactedTest(
            //             ast.getRange(ast.rootDir.relativize(Paths.get(test.getPosition().getFilePath())).toString(),
            //                     test.getPosition().getStart(), test.getPosition().getEnd(), test.getContent()),
            //             entry.getValue()); // TODO
            // }
            return this;
        }

        private Project<CtElement>.AST.FileSnapshot.Range extracted(SpoonAST ast, ImpactElement x) {
            return ast.getRange(ast.rootDir.relativize(Paths.get(x.getPosition().getFilePath())).toString(),
                    x.getPosition().getStart(), x.getPosition().getEnd(), x.getContent());
        }

        ImpactsExtension(Specifier spec, Project ast, Path root, ImpactAnalysis l) {
            super(spec, ast);
            this.root = root;
            this.analyzer = l;
        }
    }

}