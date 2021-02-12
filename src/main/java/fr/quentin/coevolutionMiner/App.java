package fr.quentin.coevolutionMiner;

import java.util.Arrays;
import java.util.Objects;

public class App {

    public static void main(String[] args) throws Exception {
        if (args.length <= 1) {
            Server.main(args);
        } else if (Objects.equals(args[0], "server")) {
            Server.main(Arrays.copyOfRange(args, 1, args.length));
        } else if (Objects.equals(args[0], "cli")) {
            CLI.main(Arrays.copyOfRange(args, 1, args.length));
        }
    }

    // public static void serve(String[] args) {

    // Spark.exception(Exception.class, (exception, request, response) -> {
    // // Handle the exception here
    // System.out.println("Exception");
    // System.out.println(exception.getMessage());
    // System.out.println(request);
    // System.out.println(response);
    // response.status(400);
    // });
    // }

    // public static void original() throws IOException {

    // File left=null, right=null;
    // try {

    // left = makeFile("MyClass.java_v1", "public class A {}");
    // right = makeFile("MyClass.java_v2", "public class B {}");

    // FileDistiller distiller = ChangeDistiller.createFileDistiller(Language.JAVA);
    // try {
    // distiller.extractClassifiedSourceCodeChanges(left, right);
    // } catch (Exception e) {
    // /*
    // * An exception most likely indicates a bug in ChangeDistiller. Please file a
    // * bug report at https://bitbucket.org/sealuzh/tools-changedistiller/issues
    // and
    // * attach the full stack trace along with the two files that you tried to
    // * distill.
    // */
    // System.err.println("Warning: error while change distilling. " +
    // e.getMessage());
    // }

    // List<SourceCodeChange> changes = distiller.getSourceCodeChanges();
    // System.out.println(changes);
    // if (changes != null) {
    // for (SourceCodeChange change : changes) {
    // System.out.println(change);
    // }
    // }
    // } finally {
    // if (left != null) {
    // left.delete();
    // }
    // if (right != null) {
    // right.delete();
    // }
    // }

    // }
}