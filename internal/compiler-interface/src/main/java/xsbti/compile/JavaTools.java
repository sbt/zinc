package xsbti.compile;

import java.io.File;
import xsbti.Logger;
import xsbti.Reporter;

/**
 * An interface to the toolchain of Java.
 *
 * Specifically, access to run javadoc + javac.
 */
public interface JavaTools {
  /** The raw interface of the java compiler for direct access. */
  JavaCompiler javac();

  /** The raw interface of the javadoc for direct access. */
  Javadoc javadoc();
}
