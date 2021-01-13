package fr.quentin;

import static org.junit.Assert.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import org.apache.maven.shared.invoker.DefaultInvocationRequest;
import org.apache.maven.shared.invoker.DefaultInvoker;
import org.apache.maven.shared.invoker.InvocationRequest;
import org.apache.maven.shared.invoker.InvocationResult;
import org.apache.maven.shared.invoker.Invoker;
import org.apache.maven.shared.invoker.MavenInvocationException;
import org.apache.maven.shared.invoker.PrintStreamHandler;
import org.apache.maven.shared.utils.cli.StreamConsumer;
import org.junit.jupiter.api.Test;

import fr.quentin.coevolutionMiner.utils.MyProperties;

public class MavenTest {

    public static InvocationResult executeTests(Path path, String filter) throws Exception {
        InvocationRequest request = new DefaultInvocationRequest();
        request.setBatchMode(true);
        request.setBaseDirectory(path.toFile());
        request.setGoals(Arrays.asList("test"));
        request.setMavenOpts("-Dtest=" + filter);
        Invoker invoker = new DefaultInvoker();
        invoker.setOutputHandler(new PrintStreamHandler(System.err,true));
        invoker.setMavenHome(Paths.get(MyProperties.getPropValues().getProperty("mavenHome")).toFile());
        try {
            return invoker.execute(request);
        } catch (MavenInvocationException e) {
            throw new Exception("Error while running tests with maven", e);
        }
    }

    @Test
    public void aaa() throws Exception {
        InvocationResult tmp = executeTests(Paths.get("C:\\Users\\quentin\\Documents\\dev\\java-tests-coevolution"),"MavenTest#passing");
        assertEquals(tmp.getExitCode(), 0);
        assertEquals(tmp.getExecutionException(), null);
        InvocationResult tmp2 = executeTests(Paths.get("C:\\Users\\quentin\\Documents\\dev\\java-tests-coevolution"),"MavenTest#failing");
        assertEquals(tmp2.getExitCode(), 1);
        assertEquals(tmp2.getExecutionException(), null);
    }

    @Test
    public void failing() {
        System.out.println("coucou");
        assertFalse(true);
    }

    @Test
    public void passing() {
        System.out.println("coucou");
        assertFalse(false);
    }
}
