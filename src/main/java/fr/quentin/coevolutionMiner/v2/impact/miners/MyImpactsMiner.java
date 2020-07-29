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
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtExecutable;

// CAUTION only handle correctly evolution in consecutive commits, 
// reson impact only computer on first commit 
// and no implementation of forwarding ranges of code through commits (making sure they were not modified)
public class MyImpactsMiner implements ImpactsMiner {
    Logger logger = Logger.getLogger(MyImpactsMiner.class.getName());

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
        boolean isOnBefore = spec.astSpec.commitId.equals(spec.evoSpec.commitIdBefore);
        ProjectSpoon project = (ProjectSpoon)astHandler.handle(spec.astSpec, SpoonMiner.class);
        ProjectSpoon.SpoonAST ast = project.getAst();
        Evolutions evo = null;
        if (spec.evoSpec != null) {
            evo = evoHandler.handle(spec.evoSpec);
        }
        // return res;

        Path rootDir = ast.rootDir;
        MavenLauncher launcher = ((ProjectSpoon.SpoonAST)ast).launcher; // TODO clone model and/or launcher
        Set<Evolution> evolutions = evo.toSet();

        ImpactAnalysis l = new ImpactAnalysis(launcher, 1);

        logger.info("Number of executable refs mapped to positions " + evolutions.size());
        List<ImpactChain> imptst1;
        try {
            Set<ImmutablePair<Object, Position>> tmp = new HashSet<>();
            for (Evolution aaa : evolutions) {

                for (Evolution.DescRange bef : isOnBefore ? aaa.getBefore() : aaa.getAfter()) {
                    final Range targ = bef.getTarget();
                    Position pos = new Position(targ.getFile().getPath(), targ.getStart(), targ.getEnd());
                    tmp.add(new ImmutablePair<>(bef, pos));
                }
            }
            imptst1 = l.getImpactedTests2(tmp);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        logger.info("Number of Impacted tests X number of evolutions " + imptst1.size());
        logger.info("Assembling Impacts");
        fr.quentin.impactMiner.Impacts rawImpacts = new fr.quentin.impactMiner.Impacts(imptst1);
        logger.info("Serializing Impacts");
        logger.info(Integer.toString(rawImpacts.getRoots().size()));
        logger.info(new GsonBuilder().setPrettyPrinting().create().toJson(rawImpacts.toJson()));
        ImpactsExtension result = new ImpactsExtension(spec, project, rootDir);
        for (Entry<ImpactElement, Map<ImpactElement, Relations>> a : rawImpacts.getVerticesPerRoots().entrySet()) {
            ImpactElement root = a.getKey();
            Set<Object> roots = new HashSet<>();
            for (Entry<Object, Position> b : root.getEvolutionWithNonCorrectedPosition().entrySet()) {
                roots.add(b.getKey());
                Position rootPosition = root.getPosition();
                String relPath = ast.rootDir.relativize(Paths.get(rootPosition.getFilePath())).toString();
                ProjectSpoon.SpoonAST.FileSnapshot.Range source = ast.getRange(relPath, rootPosition.getStart(), rootPosition.getEnd() + 1,
                        root.getContent());
                Evolutions.Evolution.DescRange impactingDescRange = (Evolutions.Evolution.DescRange) b.getKey();
                ProjectSpoon.SpoonAST.FileSnapshot.Range target = impactingDescRange.getTarget();
                if (!source.equals(target)) {
                    result.addAdjusment(b.getKey(), target, source);
                }
            }
            for (Relations rel : a.getValue().values()) {
                ImpactElement currIE = rel.getVertice();
                Position currIEPos = currIE.getPosition();
                String relPath = ast.rootDir.relativize(Paths.get(currIEPos.getFilePath())).toString();
                ProjectSpoon.SpoonAST.FileSnapshot.Range currRange = ast.getRange(relPath, currIEPos.getStart(), currIEPos.getEnd() + 1);
                Set<ImpactElement> calls = rel.getEffects().get("call");
                if (calls != null && calls.size() > 0) {
                    result.addCalls(roots, currRange,
                            calls.stream()
                                    .map(x -> ast.getRange(
                                            ast.rootDir.relativize(Paths.get(x.getPosition().getFilePath())).toString(),
                                            x.getPosition().getStart(), x.getPosition().getEnd() + 1, x.getContent()))
                                    .collect(Collectors.toSet()));
                }
                Set<ImpactElement> exp2exe = rel.getCauses().get("expand to executable");
                if (exp2exe != null && exp2exe.size() > 0) {
                    result.addExpands(roots,
                            exp2exe.stream()
                                    .map(x -> ast.getRange(
                                            ast.rootDir.relativize(Paths.get(x.getPosition().getFilePath())).toString(),
                                            x.getPosition().getStart(), x.getPosition().getEnd() + 1, x.getContent()))
                                    .collect(Collectors.toSet()),
                            currRange);
                }
            }
        }
        for (ImpactElement test : rawImpacts.getTests()) {
            result.addImpactedTest(
                    ast.getRange(ast.rootDir.relativize(Paths.get(test.getPosition().getFilePath())).toString(),
                            test.getPosition().getStart(), test.getPosition().getEnd() + 1, test.getContent()));
        }
        return result;

    }

    private final class ImpactsExtension extends Impacts {
        private final Path root;

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

        @Override
        protected Map<String, Object> makeRange(DescRange descRange) {
            Map<String, Object> o = super.makeRange(descRange);
            Range target = descRange.getTarget();
            Object original = ast.getAst().getOriginal(target);
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

        ImpactsExtension(Specifier spec, Project ast, Path root) {
            super(spec, ast);
            this.root = root;
        }

        // @Override
        // public Map<ImpactElement, Map<ImpactElement, Relations>> getPerRootCause() {
        // Map<ImpactElement, Map<ImpactElement, Relations>> res =
        // x.getVerticesPerRoots();
        // return res;
        // }

        // @Override
        // public Path getCauseRootDir() {
        // return ast.rootDir;
        // }

        // @Override
        // public Set<Position> getImpactedTests() {
        // return x.getTests().stream().map(x ->
        // x.getPosition()).collect(Collectors.toSet());
        // }

        // @Override
        // public JsonElement toJson() {
        // int idCount = 0;
        // JsonObject a = new JsonObject();
        // JsonArray perRoots = new JsonArray();
        // a.add("perRoot", perRoots);
        // for (ImpactElement e : this.roots) {
        // JsonObject o = new JsonObject();
        // perRoots.add(o);
        // Map<ImpactElement, Relations> curr = this.verticesPerRoots.get(e);
        // o.add("vertices", g.apply(curr.values()));
        // // o.add("edges", f.apply(curr.values()));
        // o.addProperty("root", e.hashCode());
        // }
        // JsonArray perTests = new JsonArray();
        // a.add("perTests", perTests);
        // for (ImpactElement e : this.tests) {
        // JsonObject o = new JsonObject();
        // perTests.add(o);
        // Map<ImpactElement, Relations> curr = this.verticesPerTests.get(e);
        // o.add("vertices", g.apply(curr.values()));
        // // o.add("edges", f.apply(curr.values()));
        // o.addProperty("root", e.hashCode());
        // }
        // a.add("roots", h.apply(this.roots));
        // a.add("tests", h.apply(this.tests));
        // return a;
        // // return x.toJson(new ToJson() {
        // // public JsonElement apply(Object x) {
        // // CtMethod<?> aaa = null;
        // // if (x instanceof JsonSerializable) {
        // // JsonSerializable y = (JsonSerializable) x;
        // // return y.toJson(this);
        // // } else if (x instanceof CtMethod) {
        // // CtMethod<?> y = (CtMethod<?>) x;
        // // JsonObject o = new JsonObject();
        // // o.addProperty("declType", y.getDeclaringType().getQualifiedName());
        // // o.addProperty("signature", y.getSignature());
        // // o.addProperty("name", y.getSimpleName());
        // // JsonObject o2 = new JsonObject();
        // // o2.addProperty("isTest", ImpactAnalysis.isTest(y));
        // // o2.addProperty("file",
        // // root.relativize(y.getPosition().getFile().toPath()).toString());
        // // o2.addProperty("start", y.getPosition().getSourceStart());
        // // o2.addProperty("end", y.getPosition().getSourceEnd());
        // // o.add("position", o2);
        // // return o;
        // // } else if (x instanceof CtConstructor) {
        // // CtConstructor<?> y = (CtConstructor<?>) x;
        // // JsonObject o = new JsonObject();
        // // o.addProperty("declType", y.getDeclaringType().getQualifiedName());
        // // o.addProperty("signature", y.getSignature());
        // // o.addProperty("name", y.getSimpleName());
        // // JsonObject o2 = new JsonObject();
        // // o2.addProperty("file",
        // // root.relativize(y.getPosition().getFile().toPath()).toString());
        // // o2.addProperty("start", y.getPosition().getSourceStart());
        // // o2.addProperty("end", y.getPosition().getSourceEnd());
        // // o.add("position", o2);
        // // return o;
        // // } else if (x instanceof CtExecutable) {
        // // CtExecutable<?> y = (CtExecutable<?>) x;
        // // return new JsonPrimitive("anonymous block" + y.getSignature());
        // // } else if (x instanceof CtInvocation) {
        // // CtInvocation<?> y = (CtInvocation<?>) x;
        // // JsonObject o = new JsonObject();
        // // o.add("sig", apply(y.getExecutable().getDeclaration()));
        // // JsonObject oPos = new JsonObject();
        // // CtMethod<?> p = y.getParent(CtMethod.class);
        // // if (p != null) {
        // // oPos.addProperty("isTest", ImpactAnalysis.isTest(p));
        // // oPos.add("method", apply(p));
        // // }
        // // oPos.addProperty("file",
        // // root.relativize(y.getPosition().getFile().toPath()).toString());
        // // oPos.addProperty("start", y.getPosition().getSourceStart());
        // // oPos.addProperty("end", y.getPosition().getSourceEnd());
        // // o.add("position", oPos);
        // // return o;
        // // } else if (x instanceof Collection) {
        // // JsonArray a = new JsonArray();
        // // for (Object b : (Collection<?>) x) {
        // // a.add(apply(b));
        // // }
        // // return a;
        // // } else {
        // // return new JsonPrimitive(x.getClass().getCanonicalName());
        // // }
        // // }
        // // });
        // }
    }

}