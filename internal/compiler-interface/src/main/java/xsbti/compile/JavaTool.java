package xsbti.compile;

import java.io.File;
import xsbti.Logger;
import xsbti.Reporter;

/**
 * An interface for on of the tools in the java tool chain.
 *
 * We assume the following is true of tools:
 * - The all take sources and options and log error messages
 * - They return success or failure.
 */
public interface JavaTool {
  /**
   * This will run a java compiler / or other like tool (e.g. javadoc).
   *
   *
   * @param sources  The list of java source files to compile.
   * @param options  The set of options to pass to the java compiler (includes the classpath).
   * @param reporter The reporter for semantic error messages.
   * @param log      The logger to dump output into.
   * @return true if no errors, false otherwise.
   */
  boolean run(File[] sources, String[] options, Reporter reporter, Logger log);
}
