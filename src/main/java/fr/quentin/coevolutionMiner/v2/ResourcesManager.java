package fr.quentin.coevolutionMiner.v2;

import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.locks.Lock;

import fr.quentin.coevolutionMiner.v2.evolution.Evolutions;
import fr.quentin.coevolutionMiner.v2.impact.Impacts;
import fr.quentin.coevolutionMiner.v2.ast.AST;
import fr.quentin.coevolutionMiner.v2.sources.SourcesHandler;

public class ResourcesManager {
    /**
     * The only instance of ResourcesManager, multiple instances would make the whole
     * program very weak to concurency errors as it might interact with the GC create threads, ...
     */
    public static final ResourcesManager INSTANCE = new ResourcesManager();

    private ResourcesManager() {
    }

    public static enum Status {
        COMPUTING(),
        DONE()
    }

    public static interface Resource {
        Status getStatus();
        String getStdout();
    }

    private static class ResourceImpl implements Resource {

        @Override
        public Status getStatus() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public String getStdout() {
            // TODO Auto-generated method stub
            return null;
        }

    }
    
    public <T extends SourcesHandler> Resource getSources(T handler, String repository) throws Exception {
        return null;
    }

    public AST getAST(AST.Specifier id){
        return null;
    }

    public Set<Evolutions> getEvolutions(Evolutions.Specifier id){
        return null;
    }

    public Set<Impacts> getImpacts(Impacts.Specifier id){
        return null;
    }
}