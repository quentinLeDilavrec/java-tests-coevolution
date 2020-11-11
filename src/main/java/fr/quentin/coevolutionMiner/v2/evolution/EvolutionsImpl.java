package fr.quentin.coevolutionMiner.v2.evolution;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import com.google.gson.JsonElement;
import com.googlecode.javaewah.IteratorUtil;

import org.apache.commons.compress.utils.Sets;
import org.apache.commons.lang3.tuple.ImmutablePair;

import fr.quentin.coevolutionMiner.v2.ast.Project;
import fr.quentin.coevolutionMiner.v2.ast.Project.AST.FileSnapshot.Range;
import fr.quentin.coevolutionMiner.v2.evolution.Evolutions.Evolution;
import fr.quentin.coevolutionMiner.v2.evolution.Evolutions.Specifier;
import fr.quentin.coevolutionMiner.v2.sources.Sources;
import fr.quentin.coevolutionMiner.v2.sources.Sources.Commit;

public class EvolutionsImpl extends Evolutions {

    public EvolutionsImpl(Specifier spec, Sources sources) {
        super(spec, sources);
    }

    protected final Set<Evolution> evolutions = new HashSet<>();
    private final Map<ImmutablePair<String, List<ImmutablePair<Range, String>>>, Evolution> evoByBeforeList = new HashMap<>();

    protected final Evolution addEvolution(final String type, final List<ImmutablePair<Range, String>> before,
            final List<ImmutablePair<Range, String>> after, final Commit commitBefore, final Commit commitAfter,
            final Object original) {
        final Evolution evo = new Evolutions.Evolution(original, type, commitBefore, commitAfter);
        for (final ImmutablePair<Range, String> immutablePair : before) {
            evo.addBefore(immutablePair.getLeft(), immutablePair.getRight());
        }
        for (final ImmutablePair<Range, String> immutablePair : after) {
            evo.addAfter(immutablePair.getLeft(), immutablePair.getRight());
        }
        evolutions.add(evo);
        final Evolution old = evoByBeforeList.put(new ImmutablePair<>(type, before), evo);
        if (old != null && evo.equals(old))
            Logger.getLogger("evo").info("evo sharing same type and before");
        return evo;
    }

    @Override
    public Set<Evolution> getEvolution(String type, Project<?> source, List<ImmutablePair<Range, String>> before,
            Project<?> target, List<ImmutablePair<Range, String>> after) {
        final Evolution tmp = evoByBeforeList.get(new ImmutablePair<>(type, before));
        if (tmp == null) {
            throw new RuntimeException("evo of type " + type + " and " + before + " is not in list");
        }
        // TODO do some checks on target
        return Collections.singleton(tmp);
    }

    @Override
    public Set<Evolution> toSet() {
        return Collections.unmodifiableSet(evolutions);
    }

    @Override
    public Map<Commit, Evolutions> perBeforeCommit() {
        throw new UnsupportedOperationException();
    }

    @Override
    public JsonElement toJson() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Iterator<Evolution> iterator() {
        return evolutions.iterator();
    }

    @Override
    public Project<?>.AST.FileSnapshot.Range map(Project<?>.AST.FileSnapshot.Range testBefore, Project<?> target) {
        // TODO Auto-generated method stub
        return null;
    }
}