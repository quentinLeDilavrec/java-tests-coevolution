package fr.quentin.coevolutionMiner.v2.sources;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import fr.quentin.coevolutionMiner.utils.SourcesHelper;
import fr.quentin.coevolutionMiner.v2.Data;
import fr.quentin.coevolutionMiner.v2.sources.miners.JgitMiner;

public class SourcesHandler {

    private Map<Sources.Specifier, Data<Sources>> memoizedSources = new ConcurrentHashMap<>();

    public SourcesHandler() {
    }

    public Sources.Specifier buildSpec(String repository) {
        return buildSpec(repository, "JGit");
    }

    private Sources.Specifier buildSpec(String repository, String miner) {
        return new Sources.Specifier(repository, miner);
    }
    
    public Sources.Specifier buildSpec(String repository, Integer stars) {
        return new Sources.Specifier(repository, "JGit", stars);
    }

    public Sources handle(Sources.Specifier spec, String miner) {
        Sources res = null;
        memoizedSources.putIfAbsent(spec, new Data<>());
        Data<Sources> tmp = memoizedSources.get(spec);
        tmp.lock.lock();
        try {
            res = tmp.get();
            if (res != null) {
                return res;
            }
            // CAUTION miners should mind about circular deps of data given by handlers
            SourcesMiner minerInst = minerBuilder(spec);
            res = minerInst.compute();

            tmp.set(res);
            return res;
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            tmp.lock.unlock();
        }
    }

    public static final SourcesMiner minerBuilder(Sources.Specifier spec) {
        SourcesMiner minerInst;
        switch (spec.miner) {
            case "JGit":
                minerInst = new JgitMiner(spec);
                break;
            default:
                throw new RuntimeException(spec.miner + " is not a registered Sources miner.");
        }
        return minerInst;
    }
}