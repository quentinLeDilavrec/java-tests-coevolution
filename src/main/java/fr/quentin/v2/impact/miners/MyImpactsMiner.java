package fr.quentin.v2.impact.miners;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import org.refactoringminer.api.Refactoring;

import fr.quentin.v2.evolution.EvolutionHandler;
import fr.quentin.v2.evolution.Evolutions;
import fr.quentin.v2.impact.Impacts;
import fr.quentin.v2.impact.ImpactsMiner;
import spoon.MavenLauncher;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtConstructor;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtMethod;
import fr.quentin.Evolution;
import fr.quentin.ImpactAnalysis;
import fr.quentin.ImpactChain;
import fr.quentin.ImpactElement;
import fr.quentin.JsonSerializable;
import fr.quentin.ToJson;
import fr.quentin.Impacts.Relations;
import fr.quentin.v2.ast.AST;
import fr.quentin.v2.ast.ASTHandler;

public class MyImpactsMiner implements ImpactsMiner {
    Logger logger = Logger.getLogger(MyImpactsMiner.class.getName());

    private ASTHandler astHandler;
    private EvolutionHandler evoHandler;
    private Impacts.Specifier spec;

    public MyImpactsMiner(Impacts.Specifier spec, ASTHandler astHandler, EvolutionHandler evoHandler) {
        this.spec = spec;
        this.astHandler = astHandler;
        this.evoHandler = evoHandler;
    }

    @Override
    public Impacts compute() {
        AST ast = astHandler.handle(spec.astSpec, "Spoon");
        Evolutions evo = null;
        if (spec.evoSpec != null) {
            evo = evoHandler.handle(spec.evoSpec);
        }
        // return res;

        Path root = ast.rootDir;
        MavenLauncher launcher = ast.launcher; // TODO clone model and/or launcher
        List<Evolution<Refactoring>> evolutions = evo.toList();

        ImpactAnalysis l = new ImpactAnalysis(launcher);

        logger.info("Number of executable refs mapped to positions " + evolutions.size());
        List<ImpactChain> imptst1;
        try {
            imptst1 = l.getImpactedTests(evolutions);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        logger.info("Number of Impacted tests X number of evolutions " + imptst1.size());
        logger.info("Assembling Impacts");
        fr.quentin.Impacts x = new fr.quentin.Impacts(imptst1);
        logger.info("Serializing Impacts");
        logger.info(Integer.toString(x.getRoots().size()));
        logger.info(new GsonBuilder().setPrettyPrinting().create().toJson(x.toJson()));
        return new Impacts() {
            @Override
            public Map<ImpactElement, Map<ImpactElement, Relations>> getPerRootCause() {
                Map<ImpactElement, Map<ImpactElement, Relations>> res = x.getVerticesPerRoots();
                return res;
            }

            @Override
            public Path getCauseRootDir() {
                return ast.rootDir;
            }

            @Override
            public JsonElement toJson() {
                // TODO Auto-generated method stub
                return x.toJson(new ToJson() {
                    public JsonElement apply(Object x) {
                        if (x instanceof JsonSerializable) {
                            JsonSerializable y = (JsonSerializable) x;
                            return y.toJson(this);
                        } else if (x instanceof CtMethod) {
                            CtMethod<?> y = (CtMethod<?>) x;
                            JsonObject o = new JsonObject();
                            o.addProperty("declType", y.getDeclaringType().getQualifiedName());
                            o.addProperty("signature", y.getSignature());
                            o.addProperty("name", y.getSimpleName());
                            JsonObject o2 = new JsonObject();
                            o2.addProperty("isTest", ImpactAnalysis.isTest(y));
                            o2.addProperty("file", root.relativize(y.getPosition().getFile().toPath()).toString());
                            o2.addProperty("start", y.getPosition().getSourceStart());
                            o2.addProperty("end", y.getPosition().getSourceEnd());
                            o.add("position", o2);
                            return o;
                        } else if (x instanceof CtConstructor) {
                            CtConstructor<?> y = (CtConstructor<?>) x;
                            JsonObject o = new JsonObject();
                            o.addProperty("declType", y.getDeclaringType().getQualifiedName());
                            o.addProperty("signature", y.getSignature());
                            o.addProperty("name", y.getSimpleName());
                            JsonObject o2 = new JsonObject();
                            o2.addProperty("file", root.relativize(y.getPosition().getFile().toPath()).toString());
                            o2.addProperty("start", y.getPosition().getSourceStart());
                            o2.addProperty("end", y.getPosition().getSourceEnd());
                            o.add("position", o2);
                            return o;
                        } else if (x instanceof CtExecutable) {
                            CtExecutable<?> y = (CtExecutable<?>) x;
                            return new JsonPrimitive("anonymous block" + y.getSignature());
                        } else if (x instanceof CtInvocation) {
                            CtInvocation<?> y = (CtInvocation<?>) x;
                            JsonObject o = new JsonObject();
                            o.add("sig", apply(y.getExecutable().getDeclaration()));
                            JsonObject oPos = new JsonObject();
                            oPos.addProperty("isTest", ImpactAnalysis.isTest(y.getParent(CtMethod.class)));
                            oPos.addProperty("file", root.relativize(y.getPosition().getFile().toPath()).toString());
                            oPos.addProperty("start", y.getPosition().getSourceStart());
                            oPos.addProperty("end", y.getPosition().getSourceEnd());
                            oPos.add("method", apply(y.getParent(CtMethod.class)));
                            o.add("position", oPos);
                            return o;
                        } else if (x instanceof Collection) {
                            JsonArray a = new JsonArray();
                            for (Object b : (Collection<?>) x) {
                                a.add(apply(b));
                            }
                            return a;
                        } else {
                            return new JsonPrimitive(x.getClass().toString());
                        }
                    }
                });
            }
        };

    }
}