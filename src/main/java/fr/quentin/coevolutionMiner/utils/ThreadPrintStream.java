package fr.quentin.coevolutionMiner.utils;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * A ThreadPrintStream replaces the normal System.out and ensures that output to
 * System.out goes to a different PrintStream for each thread. It does this by
 * using ThreadLocal to maintain a PrintStream for each thread.
 */
public class ThreadPrintStream extends PrintStream {

  public static final PrintStream DEFAULT = System.err;

  public PrintStream original;

  /**
   * Changes System.out to a ThreadPrintStream which will send output to a
   * separate file for each thread.
   */
  public static void replaceSystemOut() {

    // Save the existing System.out
    PrintStream console = System.out;

    // Create a ThreadPrintStream and install it as System.out
    ThreadPrintStream threadOut = new ThreadPrintStream();
    System.setOut(threadOut);

    // Use the original System.out as the current thread's System.out
    threadOut.setThreadOut(console);
    threadOut.original = console;
  }

  public static void replaceSystemErr() {

    // Save the existing System.err
    PrintStream console = System.err;

    // Create a ThreadPrintStream and install it as System.out
    ThreadPrintStream threadErr = new ThreadPrintStream();
    System.setErr(threadErr);

    // Use the original System.out as the current thread's System.out
    threadErr.setThreadOut(console);
    threadErr.original = console;
  }

  /** Thread specific storage to hold a PrintStream for each thread */
  // private ThreadLocal<PrintStream> out = ThreadLocal.withInitial(()->DEFAULT);

  // anonymous inner class  for overriding childValue method. 
  private InheritableThreadLocal<PrintStream> out = new InheritableThreadLocal<PrintStream>() {
    public PrintStream childValue(PrintStream parentValue) {
      return parentValue;
    }
  };

  private ThreadPrintStream() {
    super(new ByteArrayOutputStream(0));
    // out = new ThreadLocal<PrintStream>();
  }

  /** Sets the PrintStream for the currently executing thread. */
  public void setThreadOut(PrintStream out) {
    this.out.set(out);
  }

  /** Returns the PrintStream for the currently executing thread. */
  public PrintStream getThreadOut() {
    return this.out.get();
  }

  @Override
  public boolean checkError() {
    return getThreadOut().checkError();
  }

  @Override
  public void write(byte[] buf, int off, int len) {
    getThreadOut().write(buf, off, len);
    flush();
  }

  @Override
  public void write(int b) {
    getThreadOut().write(b);
    flush();
  }

  @Override
  public void flush() {
    getThreadOut().flush();
  }

  @Override
  public void close() {
    PrintStream o = this.out.get();
    setThreadOut(original);
    o.close();
  }

  public static void redirectThreadLogs() throws IOException {
    // Create a text file where System.out.println()
    // will send its data for this thread.
    String thread_name = Thread.currentThread().getName();
    redirectThreadLogs(Paths.get("logs", thread_name));
  }

  public static void redirectThreadLogs(Path path) throws IOException {
    path.toFile().mkdirs();
    Path logPath;
    try {
      String string = path.toString();
      File file = path.toFile();
      String[] list = file.list();
      int length = list.length;
      String string2 = Integer.toString(length);
      logPath = Paths.get(string, string2 + ".log");
    } catch (NullPointerException e) {
      throw new RuntimeException(e);
    }
    File file = logPath.toFile();
    file.createNewFile();
    FileOutputStream fos = new FileOutputStream(file);
    // Create a PrintStream that will write to the new file.
    PrintStream stream = new PrintStream(new BufferedOutputStream(fos));

    redirectThreadLogs(stream);
  }

  public static void redirectThreadLogs(PrintStream stream) throws IOException {
    // // Install the PrintStream to be used as System.out for this thread.
    if (!(System.out instanceof ThreadPrintStream)) {
      throw new RuntimeException("Wrong sysout"+ Thread.currentThread().getName());
      // replaceSystemOut();
    }
    if (!(System.err instanceof ThreadPrintStream)) {
      throw new RuntimeException("Wrong syserr"+ Thread.currentThread().getName());
      // replaceSystemErr();
    }
    ((ThreadPrintStream) System.out).setThreadOut(stream);
    ((ThreadPrintStream) System.err).setThreadOut(stream);
  }
}