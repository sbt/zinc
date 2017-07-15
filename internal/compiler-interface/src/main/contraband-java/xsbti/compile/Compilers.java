/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package xsbti.compile;
/** The instances of Scalac/Javac used to compile the current project. */
public final class Compilers implements java.io.Serializable {
    
    public static Compilers create(xsbti.compile.ScalaCompiler _scalac, xsbti.compile.JavaTools _javaTools) {
        return new Compilers(_scalac, _javaTools);
    }
    public static Compilers of(xsbti.compile.ScalaCompiler _scalac, xsbti.compile.JavaTools _javaTools) {
        return new Compilers(_scalac, _javaTools);
    }
    /**
     * A `ScalaCompiler`.
     * It should be cached by the client if desired.
     */
    private xsbti.compile.ScalaCompiler scalac;
    /** Tool chain of Java. */
    private xsbti.compile.JavaTools javaTools;
    protected Compilers(xsbti.compile.ScalaCompiler _scalac, xsbti.compile.JavaTools _javaTools) {
        super();
        scalac = _scalac;
        javaTools = _javaTools;
    }
    public xsbti.compile.ScalaCompiler scalac() {
        return this.scalac;
    }
    public xsbti.compile.JavaTools javaTools() {
        return this.javaTools;
    }
    public Compilers withScalac(xsbti.compile.ScalaCompiler scalac) {
        return new Compilers(scalac, javaTools);
    }
    public Compilers withJavaTools(xsbti.compile.JavaTools javaTools) {
        return new Compilers(scalac, javaTools);
    }
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (!(obj instanceof Compilers)) {
            return false;
        } else {
            Compilers o = (Compilers)obj;
            return scalac().equals(o.scalac()) && javaTools().equals(o.javaTools());
        }
    }
    public int hashCode() {
        return 37 * (37 * (37 * (17 + "xsbti.compile.Compilers".hashCode()) + scalac().hashCode()) + javaTools().hashCode());
    }
    public String toString() {
        return "Compilers("  + "scalac: " + scalac() + ", " + "javaTools: " + javaTools() + ")";
    }
}
