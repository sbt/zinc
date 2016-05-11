package xsbti.compile;

/**
 * Determines if a classpath entry contains a class.
 *
 * The corresponding classpath entry is not exposed by this interface. It's tied
 * to it by a specific class that implements this interface.
 */
public interface DefinesClass {
    /**
     * Returns true if the classpath entry contains the requested class.
     */
    boolean apply(String className);
}
