package fr.quentin.coevolutionMiner.v2.impact.miners;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

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
import fr.quentin.coevolutionMiner.v2.impact.Impacts.Impact.DescRange;
import fr.quentin.coevolutionMiner.v2.impact.ImpactsMiner;
import fr.quentin.coevolutionMiner.v2.utils.DbUtils;
import fr.quentin.coevolutionMiner.v2.utils.Utils;
import fr.quentin.impactMiner.ImpactAnalysis;
import fr.quentin.impactMiner.ImpactChain;
import fr.quentin.impactMiner.ImpactElement;
import fr.quentin.impactMiner.Impacts.Relations;
import fr.quentin.impactMiner.Position;
import spoon.MavenLauncher;
import spoon.reflect.cu.SourcePosition;
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
        boolean isOnBefore = spec.astSpec.commitId.equals(spec.evoSpec.commitIdBefore);
        ProjectSpoon project = (ProjectSpoon) astHandler.handle(spec.astSpec);
        ProjectSpoon.SpoonAST ast = project.getAst();
        Evolutions evo = evoHandler.handle(spec.evoSpec);
        // return res;

        Path rootDir = ast.rootDir;

        ImpactAnalysis l = new ImpactAnalysis(ast.augmented, 1);
        ImpactsExtension result = new ImpactsExtension(spec, project, rootDir, l);

        Set<Evolution> evolutions = evo.toSet();
        return result.computeImpacts(isOnBefore, ast, evolutions);
    }

    final class ImpactsExtension extends Impacts {
        private final Path root;
        ImpactAnalysis analyzer;

        public void addImpactedTest(Range range) {
            impactedTests.add(range);
        }

        public void addAdjusment(Object root, Range cause, Range effect) {
            Impact imp = addImpact("adjustment", Collections.singleton(new ImmutablePair<>(cause, "given")),
                    Collections.emptySet());
            perRoot.putIfAbsent(root, new HashSet<>());
            perRoot.get(root).add(imp);
            addEffect(imp, effect, "disambiguated");
        }

        public void addCalls(Set<Object> roots, Range cause, Set<Range> effects) {
            Impact imp = addImpact("call", Collections.singleton(new ImmutablePair<>(cause, "declaration")),
                    Collections.emptySet());
            for (Object root : roots) {
                perRoot.putIfAbsent(root, new HashSet<>());
                perRoot.get(root).add(imp);
            }
            for (Range range : effects) {
                addEffect(imp, range, "reference");
            }
        }

        public void addExpands(Set<Object> roots, Set<Range> causes, Range effect) {
            Impact imp = addImpact("expand to executable", Collections.emptySet(),
                    Collections.singleton(new ImmutablePair<>(effect, "executable")));
            for (Object root : roots) {
                perRoot.putIfAbsent(root, new HashSet<>());
                perRoot.get(root).add(imp);
            }
            for (Range range : causes) {
                addCause(imp, range, "instruction");
            }
        }

        // public Set<String> needs(Set<Evolution> evos, boolean onAfter) {
        //     Set<CtType<?>> tmp = new HashSet<>();
        //     TypeFilter<CtType<?>> filter = new TypeFilter<CtType<?>>(CtType.class);
        //     for (Evolution evo : evos) {
        //         for (Evolutions.Evolution.DescRange ds : onAfter ? evo.getBefore() : evo.getAfter()) {
        //             tmp.add(((CtElement) ds.getTarget().getOriginal()).getParent(filter).getTopLevelType());
        //         }
        //     }
        //     Set<CtType> tmp2 = new HashSet<>();
        //     for (CtType<?> f : tmp) {
        //         tmp2.addAll(analyzer.augmented.needsDyn(f));
        //     }
        //     Set<String> result = new HashSet<>();
        //     for (CtType t : tmp) {
        //         SourcePosition position = t.getPosition();
        //         if (position.isValidPosition()) {
        //             result.add(position.getFile().getAbsolutePath());
        //         }
        //     }
        //     return result;
        // }

        @Override
        protected Map<String, Object> makeRange(DescRange descRange) {
            Map<String, Object> o = super.makeRange(descRange);
            Range target = descRange.getTarget();
            Object original = target.getOriginal();
            if (original != null) {
                o.put("type", original.getClass().getSimpleName());
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
            }
            return o;
        }

        ImpactsExtension computeImpacts(boolean isOnBefore, SpoonAST ast, Set<Evolution> evolutions) {
            logger.info("Number of executable refs mapped to positions " + evolutions.size());
            List<ImpactChain> imptst1;
            try {
                Set<ImmutablePair<Object, CtElement>> tmp = new HashSet<>();
                for (Evolution evo : evolutions) {
                    for (Evolutions.Evolution.DescRange bef : isOnBefore ? evo.getBefore() : evo.getAfter()) {
                        final Range targ = bef.getTarget();
                        CtElement ori = (CtElement)targ.getOriginal();
                        // Position pos = new Position(targ.getFile().getPath(), targ.getStart(), targ.getEnd());
                        tmp.add(new ImmutablePair<>(bef, ori));
                    }
                }
                imptst1 = analyzer.getImpactedTests3(tmp, false);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            logger.info("Number of Impacted tests X number of evolutions " + imptst1.size());
            logger.info("Assembling Impacts");
            fr.quentin.impactMiner.Impacts rawImpacts = new fr.quentin.impactMiner.Impacts(imptst1);
            logger.info("Serializing Impacts");
            logger.info(Integer.toString(rawImpacts.getRoots().size()));
            logger.info(new GsonBuilder().setPrettyPrinting().create().toJson(rawImpacts.toJson()));

            for (Entry<ImpactElement, Map<ImpactElement, Relations>> entry : rawImpacts.getVerticesPerRoots()
                    .entrySet()) {
                ImpactElement root = entry.getKey();
                Set<Object> roots = new HashSet<>();
                for (Entry<Object, Position> b : root.getEvolutionWithNonCorrectedPosition().entrySet()) {
                    roots.add(b.getKey());
                    Position rootPosition = root.getPosition();
                    String relPath = ast.rootDir.relativize(Paths.get(rootPosition.getFilePath())).toString();
                    Project<CtElement>.AST.FileSnapshot.Range source = ast.getRange(relPath, rootPosition.getStart(),
                            rootPosition.getEnd() + 1, root.getContent());
                    Evolutions.Evolution.DescRange impactingDescRange = (Evolutions.Evolution.DescRange) b.getKey();
                    Project<CtElement>.AST.FileSnapshot.Range target = impactingDescRange.getTarget();
                    if (!source.equals(target)) {
                        addAdjusment(b.getKey(), target, source);
                    }
                }
                for (Relations rel : entry.getValue().values()) {
                    ImpactElement currIE = rel.getVertice();
                    Position currIEPos = currIE.getPosition();
                    String relPath = ast.rootDir.relativize(Paths.get(currIEPos.getFilePath())).toString();
                    Project<CtElement>.AST.FileSnapshot.Range currRange = ast.getRange(relPath, currIEPos.getStart(),
                            currIEPos.getEnd() + 1);
                    Set<ImpactElement> calls = rel.getEffects().get("call");
                    if (calls != null && calls.size() > 0) {
                        addCalls(roots, currRange, calls.stream()
                                .map(x -> ast.getRange(
                                        ast.rootDir.relativize(Paths.get(x.getPosition().getFilePath())).toString(),
                                        x.getPosition().getStart(), x.getPosition().getEnd() + 1, x.getContent()))
                                .collect(Collectors.toSet()));
                    }
                    Set<ImpactElement> exp2exe = rel.getCauses().get("expand to executable");
                    if (exp2exe != null && exp2exe.size() > 0) {
                        addExpands(roots, exp2exe.stream()
                                .map(x -> ast.getRange(
                                        ast.rootDir.relativize(Paths.get(x.getPosition().getFilePath())).toString(),
                                        x.getPosition().getStart(), x.getPosition().getEnd() + 1, x.getContent()))
                                .collect(Collectors.toSet()), currRange);
                    }
                }
            }
            for (ImpactElement test : rawImpacts.getTests()) {
                addImpactedTest(
                        ast.getRange(ast.rootDir.relativize(Paths.get(test.getPosition().getFilePath())).toString(),
                                test.getPosition().getStart(), test.getPosition().getEnd() + 1, test.getContent()));
            }
            return this;
        }

        ImpactsExtension(Specifier spec, Project ast, Path root, ImpactAnalysis l) {
            super(spec, ast);
            this.root = root;
            this.analyzer = l;
        }
    }

}