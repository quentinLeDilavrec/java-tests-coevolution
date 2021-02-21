package fr.quentin.coevolutionMiner.v2.coevolution.miners;

public class SmallMiningException extends Exception {

    public SmallMiningException(String string, Exception compilerException) {
        super(string, compilerException);
    }

    public SmallMiningException(String string) {
        super(string);
    }

    private static final long serialVersionUID = 6192596956456010689L;

}