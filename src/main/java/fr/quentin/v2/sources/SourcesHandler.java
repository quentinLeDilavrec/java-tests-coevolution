package fr.quentin.v2.sources;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import fr.quentin.utils.SourcesHelper;
import fr.quentin.v2.Data;
import fr.quentin.v2.sources.miners.JgitMiner;

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
            switch (spec.miner) {
                case "JGit":
                    JgitMiner minerInst = new JgitMiner(spec);
                    res = minerInst.compute();
                    break;
                default:
                    throw new RuntimeException(spec.miner + " is not a registered Sources miner.");
            }

            tmp.set(res);
            return res;
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            tmp.lock.unlock();
        }
    }
}