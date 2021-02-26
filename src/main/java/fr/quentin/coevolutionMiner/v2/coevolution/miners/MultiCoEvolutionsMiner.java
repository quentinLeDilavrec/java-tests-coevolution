package fr.quentin.coevolutionMiner.v2.coevolution.miners;

import java.util.AbstractSet;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Spliterator;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import fr.quentin.coevolutionMiner.v2.ast.Project.AST.FileSnapshot.Range;
import fr.quentin.coevolutionMiner.v2.ast.ProjectHandler;
import fr.quentin.coevolutionMiner.v2.coevolution.CoEvolutionHandler;
import fr.quentin.coevolutionMiner.v2.coevolution.CoEvolutions;
import fr.quentin.coevolutionMiner.v2.coevolution.CoEvolutionsMiner;
import fr.quentin.coevolutionMiner.v2.coevolution.miners.EImpact.ImpactedRange;
import fr.quentin.coevolutionMiner.v2.dependency.DependencyHandler;
import fr.quentin.coevolutionMiner.v2.evolution.EvolutionHandler;
import fr.quentin.coevolutionMiner.v2.sources.Sources;
import fr.quentin.coevolutionMiner.v2.sources.Sources.Commit;
import fr.quentin.coevolutionMiner.v2.sources.SourcesHandler;
import fr.quentin.coevolutionMiner.v2.utils.Iterators2;
import fr.quentin.coevolutionMiner.v2.utils.Utils;
import fr.quentin.coevolutionMiner.v2.utils.Utils.Spanning;

// CAUTION same limitation as MyImpactsMiner
public class MultiCoEvolutionsMiner implements CoEvolutionsMiner {

    static Logger logger = LogManager.getLogger();

    private final class CoEvolutionsManyCommit extends CoEvolutions {

        private int coevoSize = 0;

        CoEvolutionsManyCommit(Specifier spec) {
            super(spec);
        }

        @Override
        public JsonElement toJson() {
            return new JsonObject();
        }

        @Override
        public Set<CoEvolution> getCoEvolutions() {
            Sources sourcesProvider = srcHandler.handle(spec.evoSpec.sources);
            String initialCommitId = spec.evoSpec.commitIdBefore;
            List<Sources.Commit> commits = Utils.getCommitList(sourcesProvider, initialCommitId,
                    spec.evoSpec.commitIdAfter);
            return Collections.unmodifiableSet(new AbstractSet<CoEvolution>() {

                @Override
                public Iterator<CoEvolution> iterator() {

                    return new Iterators2.IteratorPairCustom<Commit, CoEvolutions.CoEvolution>(commits.iterator()) {

                        @Override
                        public Iterator<CoEvolution> makeIt(Commit prev, Commit next) {
                            return handler.handle(handler.buildSpec(spec.srcSpec,
                                    EvolutionHandler.buildSpec(spec.srcSpec, prev.getId(), next.getId()),
                                    MyCoEvolutionsMiner.class)).getCoEvolutions().iterator();
                        }

                    };
                }

                @Override
                public int size() {
                    return coevoSize;
                }

            });
        }

        @Override
        public Set<EImpact> getEImpacts() {
            return null;
            // TODO when needed
        }

        @Override
        public Set<ImpactedRange> getInitialTests() {
            return null;
            // TODO when needed
        }

        public void add(CoEvolutions r) {
            this.coevoSize += r.getCoEvolutions().size();
            // TODO when needed
        }
    }

    private ProjectHandler astHandler;
    private EvolutionHandler evoHandler;
    private CoEvolutions.Specifier spec;

    private SourcesHandler srcHandler;

    private CoEvolutionHandler handler;

    private DependencyHandler impactHandler;

    public static Spanning spanning = Spanning.PER_COMMIT;

    public MultiCoEvolutionsMiner(CoEvolutions.Specifier spec, SourcesHandler srcHandler, ProjectHandler astHandler,
            EvolutionHandler evoHandler, DependencyHandler impactHandler, CoEvolutionHandler handler) {
        this.spec = spec;
        this.srcHandler = srcHandler;
        this.astHandler = astHandler;
        this.evoHandler = evoHandler;
        this.impactHandler = impactHandler;
        this.handler = handler;
    }

    @Override
    public CoEvolutions compute() {
        assert spec.evoSpec != null : spec;
        Sources sourcesProvider = srcHandler.handle(spec.evoSpec.sources);
        String initialCommitId = spec.evoSpec.commitIdBefore;
        switch (spanning) {
            case ONCE: {
                return handler.handle(handler.buildSpec(spec.srcSpec, spec.evoSpec, MyCoEvolutionsMiner.class));
            }
            case PER_COMMIT:
            default: {
                List<Sources.Commit> commits = Utils.getCommitList(sourcesProvider, initialCommitId,
                        spec.evoSpec.commitIdAfter);
                logger.info(commits.size() > 2 ? "caution computation of coevolutions only between consecutive commits"
                        : "# of commits to analyze: " + commits.size());
                logger.info(commits);

                if (commits.size() <= 2) {
                    return handler.handle(handler.buildSpec(spec.srcSpec, spec.evoSpec, MyCoEvolutionsMiner.class));
                }

                CoEvolutionsManyCommit globalResult = new CoEvolutionsManyCommit(spec);
                Commit beforeCommit = null;
                for (Commit afterCommit : commits) {
                    if (beforeCommit != null) {
                        CoEvolutions r = handler.handle(handler.buildSpec(spec.srcSpec, EvolutionHandler
                                .buildSpec(spec.srcSpec, beforeCommit.getId(), afterCommit.getId(), spec.evoSpec.miner),
                                MyCoEvolutionsMiner.class));
                        globalResult.add(r);
                    }
                    beforeCommit = afterCommit;
                }
                return globalResult;
            }
        }

    }

}