/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package xsbti.compile;
/** Configures a single compilation of a single set of sources. */
public final class Inputs implements java.io.Serializable {
    
    public static Inputs create(xsbti.compile.Compilers _compilers, xsbti.compile.CompileOptions _options, xsbti.compile.Setup _setup, xsbti.compile.PreviousResult _previousResult) {
        return new Inputs(_compilers, _options, _setup, _previousResult);
    }
    public static Inputs of(xsbti.compile.Compilers _compilers, xsbti.compile.CompileOptions _options, xsbti.compile.Setup _setup, xsbti.compile.PreviousResult _previousResult) {
        return new Inputs(_compilers, _options, _setup, _previousResult);
    }
    /** Return the Scala and Java compilers to use for compilation. */
    private xsbti.compile.Compilers compilers;
    /** Return the compilation options, such as the sources and classpath to use. */
    private xsbti.compile.CompileOptions options;
    /** Represent the configuration of incremental compilation. */
    private xsbti.compile.Setup setup;
    /** Represent tha latest result of incremental compilation. */
    private xsbti.compile.PreviousResult previousResult;
    protected Inputs(xsbti.compile.Compilers _compilers, xsbti.compile.CompileOptions _options, xsbti.compile.Setup _setup, xsbti.compile.PreviousResult _previousResult) {
        super();
        compilers = _compilers;
        options = _options;
        setup = _setup;
        previousResult = _previousResult;
    }
    public xsbti.compile.Compilers compilers() {
        return this.compilers;
    }
    public xsbti.compile.CompileOptions options() {
        return this.options;
    }
    public xsbti.compile.Setup setup() {
        return this.setup;
    }
    public xsbti.compile.PreviousResult previousResult() {
        return this.previousResult;
    }
    public Inputs withCompilers(xsbti.compile.Compilers compilers) {
        return new Inputs(compilers, options, setup, previousResult);
    }
    public Inputs withOptions(xsbti.compile.CompileOptions options) {
        return new Inputs(compilers, options, setup, previousResult);
    }
    public Inputs withSetup(xsbti.compile.Setup setup) {
        return new Inputs(compilers, options, setup, previousResult);
    }
    public Inputs withPreviousResult(xsbti.compile.PreviousResult previousResult) {
        return new Inputs(compilers, options, setup, previousResult);
    }
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (!(obj instanceof Inputs)) {
            return false;
        } else {
            Inputs o = (Inputs)obj;
            return compilers().equals(o.compilers()) && options().equals(o.options()) && setup().equals(o.setup()) && previousResult().equals(o.previousResult());
        }
    }
    public int hashCode() {
        return 37 * (37 * (37 * (37 * (37 * (17 + "xsbti.compile.Inputs".hashCode()) + compilers().hashCode()) + options().hashCode()) + setup().hashCode()) + previousResult().hashCode());
    }
    public String toString() {
        return "Inputs("  + "compilers: " + compilers() + ", " + "options: " + options() + ", " + "setup: " + setup() + ", " + "previousResult: " + previousResult() + ")";
    }
}
