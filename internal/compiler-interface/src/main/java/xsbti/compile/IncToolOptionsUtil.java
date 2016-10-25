package xsbti.compile;

/**
 * Helper class for xsbti.compile.IncToolOptions.
 */
public class IncToolOptionsUtil {
  public static boolean defaultUseCustomizedFileManager() {
    return false;
  }
  public static ClassFileManager defaultClassFileManager() {
    return new NoopClassFileManager();
  }

  public static IncToolOptions defaultIncToolOptions() {
    return new IncToolOptions(defaultClassFileManager(), defaultUseCustomizedFileManager());
  }
}
