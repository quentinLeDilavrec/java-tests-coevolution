package fr.quentin.coevolutionMiner.v2.evolution.miners;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import com.sun.tools.javac.util.Iterators;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.refactoringminer.api.GitHistoryRefactoringMiner;
import org.refactoringminer.api.Refactoring;
import org.refactoringminer.api.RefactoringHandler;
import org.refactoringminer.rm1.GitHistoryRefactoringMinerImpl;

import fr.quentin.coevolutionMiner.utils.SourcesHelper;
import fr.quentin.coevolutionMiner.v2.ast.Project;
import fr.quentin.coevolutionMiner.v2.ast.Project.AST.FileSnapshot.Range;
import fr.quentin.coevolutionMiner.v2.ast.ProjectHandler;
import fr.quentin.coevolutionMiner.v2.ast.miners.SpoonMiner;
import fr.quentin.coevolutionMiner.v2.evolution.EvolutionHandler;
import fr.quentin.coevolutionMiner.v2.evolution.Evolutions;
import fr.quentin.coevolutionMiner.v2.evolution.EvolutionsMiner;
import fr.quentin.coevolutionMiner.v2.evolution.miners.GumTreeSpoonMiner.EvolutionsMany;
import fr.quentin.coevolutionMiner.v2.evolution.Evolutions.Evolution.DescRange;
import fr.quentin.coevolutionMiner.v2.sources.Sources;
import fr.quentin.coevolutionMiner.v2.sources.SourcesHandler;
import fr.quentin.coevolutionMiner.v2.sources.Sources.Commit;
import fr.quentin.coevolutionMiner.v2.sources.Sources.Specifier;
import fr.quentin.coevolutionMiner.v2.utils.Iterators2;
import gr.uom.java.xmi.diff.CodeRange;
import spoon.reflect.declaration.CtElement;

public class MixedMiner implements EvolutionsMiner {
    private final class EvolutionsExtension extends Evolutions {
        private final Evolutions rmEvos;
        private final Evolutions gtsEvos;

        private EvolutionsExtension(Evolutions.Specifier spec, Sources sources, Evolutions rmEvos, Evolutions gtsEvos) {
            super(spec, sources);
            this.rmEvos = rmEvos;
            this.gtsEvos = gtsEvos;
        }

        public Iterator<Evolution> iterator() {
            return Iterators2.<Evolution>createChainIterable(Arrays.asList(rmEvos, gtsEvos)).iterator();
        }

        @Override
        public Set<Evolution> toSet() {
            Set<Evolution> r = new LinkedHashSet<>();
            r.addAll(rmEvos.toSet());
            r.addAll(gtsEvos.toSet());
            return Collections.unmodifiableSet(r);
        }

        @Override
        public Map<Commit, Evolutions> perBeforeCommit() {
            Map<Commit, Evolutions> r = new HashMap<>();
            Map<Commit, Evolutions> perBeforeCommitRM = rmEvos.perBeforeCommit();
            Map<Commit, Evolutions> perBeforeCommitGTS = gtsEvos.perBeforeCommit();
            Set<Commit> keys = new LinkedHashSet<>();
            keys.addAll(perBeforeCommitRM.keySet());
            keys.addAll(perBeforeCommitGTS.keySet());
            for (Commit commit : keys) {
                Evolutions currRM = perBeforeCommitRM.get(commit);
                Evolutions currGTS = perBeforeCommitGTS.get(commit);
                if (currRM != null && currGTS != null) {
                    r.put(commit,
                            new EvolutionsExtension(new Evolutions.Specifier(spec.sources, currGTS.spec.commitIdAfter,
                                    currGTS.spec.commitIdAfter, MixedMiner.class), sources, currRM, currGTS));
                } else if (currRM != null) {
                    r.put(commit, currRM);
                } else if (currGTS != null) {
                    r.put(commit, currGTS);
                }
            }
            return Collections.unmodifiableMap(r);
        }

        @Override
        public JsonElement toJson() {
            throw new UnsupportedOperationException("MixedMiner.toJson not implemented yet");
        }

        @Override
        public Set<Evolution> getEvolution(String type, Project<?> source, List<ImmutablePair<Range, String>> before,
                Project<?> target, List<ImmutablePair<Range, String>> after) {
            throw new UnsupportedOperationException("MixedMiner.getEvolution not implemented yet");
        }

        @Override
        public Project<?>.AST.FileSnapshot.Range map(Project<?>.AST.FileSnapshot.Range testBefore, Project<?> target) {
            throw new UnsupportedOperationException("MixedMiner.map not implemented yet");
        }
    }

    Logger logger = LogManager.getLogger();
    // public static List<Class<? extends EvolutionsMiner>> slaves = Collections
    // .unmodifiableList(Arrays.asList(RefactoringMiner.class,
    // GumTreeSpoonMiner.class));

    // public static class MixedSpecifier extends Evolutions.Specifier {

    // public final List<Evolutions.Specifier> specs;

    // public MixedSpecifier(Specifier sources, String commitIdBefore, String
    // commitIdAfter) {
    // super(sources, commitIdBefore, commitIdAfter, Mixed_Miner.class);
    // this.specs = slaves.stream().map(x -> new Evolutions.Specifier(sources,
    // commitIdBefore, commitIdAfter, x))
    // .collect(Collectors.toUnmodifiableList());
    // }

    // }

    private SourcesHandler srcHandler;
    private ProjectHandler astHandler;
    private EvolutionHandler evolutionHandler;
    private Evolutions.Specifier spec;

    // TODO instanciate filters correctly and make use of them
    private List<Object> filters;

    private fr.quentin.coevolutionMiner.v2.evolution.Evolutions.Specifier rmMinerSpec;

    private fr.quentin.coevolutionMiner.v2.evolution.Evolutions.Specifier gtsMinerSpec;

    public MixedMiner(Evolutions.Specifier spec, SourcesHandler srcHandler, ProjectHandler astHandler,
            EvolutionHandler evolutionHandler) {
        this(spec, srcHandler, astHandler, evolutionHandler, null);
    }

    public MixedMiner(Evolutions.Specifier spec, SourcesHandler srcHandler, ProjectHandler astHandler,
            EvolutionHandler evolutionHandler, List<Object> filters) {
        this.spec = spec;
        this.srcHandler = srcHandler;
        this.astHandler = astHandler;
        this.evolutionHandler = evolutionHandler;
        this.filters = filters;
        this.rmMinerSpec = new Evolutions.Specifier(spec.sources, spec.commitIdBefore, spec.commitIdAfter,
                RefactoringMiner.class);
        this.gtsMinerSpec = new Evolutions.Specifier(spec.sources, spec.commitIdBefore, spec.commitIdAfter,
                GumTreeSpoonMiner.class);
    }

    @Override
    public Evolutions compute() {
        Sources src = srcHandler.handle(spec.sources, "jgit");
        Evolutions rmEvos = evolutionHandler.handle(rmMinerSpec);
        GumTreeSpoonMiner.EvolutionsMany gtsEvos = (GumTreeSpoonMiner.EvolutionsMany) evolutionHandler
                .handle(gtsMinerSpec);

        return new EvolutionsExtension(spec, src, rmEvos, gtsEvos);
    }

}