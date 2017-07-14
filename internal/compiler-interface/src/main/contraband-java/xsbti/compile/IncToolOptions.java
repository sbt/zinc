/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package xsbti.compile;
/**
 * Define the component that manages the generated class files and defines the
 * configuration options for an incremental compiler. This component is used by
 * any Java compiler that implements {@link xsbti.compile.JavaTools} and they
 * should return empty values by default because the incremental tool options
 * are not enabled for Java tools, they are only enabled for Scala's incremental
 * compiler.
 */
public final class IncToolOptions implements java.io.Serializable {
    
    public static IncToolOptions create(java.util.Optional<xsbti.compile.ClassFileManager> _classFileManager, boolean _useCustomizedFileManager) {
        return new IncToolOptions(_classFileManager, _useCustomizedFileManager);
    }
    public static IncToolOptions of(java.util.Optional<xsbti.compile.ClassFileManager> _classFileManager, boolean _useCustomizedFileManager) {
        return new IncToolOptions(_classFileManager, _useCustomizedFileManager);
    }
    /** Define a component that manages the generated class files in every compilation cycle. */
    private java.util.Optional<xsbti.compile.ClassFileManager> classFileManager;
    /** Flag that enables use of a customized {@link xsbti.compile.ClassFileManager}. */
    private boolean useCustomizedFileManager;
    protected IncToolOptions(java.util.Optional<xsbti.compile.ClassFileManager> _classFileManager, boolean _useCustomizedFileManager) {
        super();
        classFileManager = _classFileManager;
        useCustomizedFileManager = _useCustomizedFileManager;
    }
    public java.util.Optional<xsbti.compile.ClassFileManager> classFileManager() {
        return this.classFileManager;
    }
    public boolean useCustomizedFileManager() {
        return this.useCustomizedFileManager;
    }
    public IncToolOptions withClassFileManager(java.util.Optional<xsbti.compile.ClassFileManager> classFileManager) {
        return new IncToolOptions(classFileManager, useCustomizedFileManager);
    }
    public IncToolOptions withUseCustomizedFileManager(boolean useCustomizedFileManager) {
        return new IncToolOptions(classFileManager, useCustomizedFileManager);
    }
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (!(obj instanceof IncToolOptions)) {
            return false;
        } else {
            IncToolOptions o = (IncToolOptions)obj;
            return classFileManager().equals(o.classFileManager()) && (useCustomizedFileManager() == o.useCustomizedFileManager());
        }
    }
    public int hashCode() {
        return 37 * (37 * (37 * (17 + "xsbti.compile.IncToolOptions".hashCode()) + classFileManager().hashCode()) + (new Boolean(useCustomizedFileManager())).hashCode());
    }
    public String toString() {
        return "IncToolOptions("  + "classFileManager: " + classFileManager() + ", " + "useCustomizedFileManager: " + useCustomizedFileManager() + ")";
    }
}
