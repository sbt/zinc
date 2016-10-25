package xsbti.compile;

import xsbti.Maybe;

/**
 * Helper class for xsbti.compile.IncToolOptions.
 */
public class IncToolOptionsUtil {
  public static boolean defaultUseCustomizedFileManager() {
    return false;
  }
  public static Maybe<ClassFileManager> defaultClassFileManager() {
    return Maybe.<ClassFileManager>nothing();
  }

  public static IncToolOptions defaultIncToolOptions() {
    return new IncToolOptions(defaultClassFileManager(), defaultUseCustomizedFileManager());
  }
}
