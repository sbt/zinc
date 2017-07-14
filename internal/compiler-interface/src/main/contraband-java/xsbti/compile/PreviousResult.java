/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package xsbti.compile;
/** The previous source dependency analysis result from compilation. */
public final class PreviousResult implements java.io.Serializable {
    
    public static PreviousResult create(java.util.Optional<xsbti.compile.CompileAnalysis> _analysis, java.util.Optional<xsbti.compile.MiniSetup> _setup) {
        return new PreviousResult(_analysis, _setup);
    }
    public static PreviousResult of(java.util.Optional<xsbti.compile.CompileAnalysis> _analysis, java.util.Optional<xsbti.compile.MiniSetup> _setup) {
        return new PreviousResult(_analysis, _setup);
    }
    
    private java.util.Optional<xsbti.compile.CompileAnalysis> analysis;
    private java.util.Optional<xsbti.compile.MiniSetup> setup;
    protected PreviousResult(java.util.Optional<xsbti.compile.CompileAnalysis> _analysis, java.util.Optional<xsbti.compile.MiniSetup> _setup) {
        super();
        analysis = _analysis;
        setup = _setup;
    }
    public java.util.Optional<xsbti.compile.CompileAnalysis> analysis() {
        return this.analysis;
    }
    public java.util.Optional<xsbti.compile.MiniSetup> setup() {
        return this.setup;
    }
    public PreviousResult withAnalysis(java.util.Optional<xsbti.compile.CompileAnalysis> analysis) {
        return new PreviousResult(analysis, setup);
    }
    public PreviousResult withSetup(java.util.Optional<xsbti.compile.MiniSetup> setup) {
        return new PreviousResult(analysis, setup);
    }
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (!(obj instanceof PreviousResult)) {
            return false;
        } else {
            PreviousResult o = (PreviousResult)obj;
            return analysis().equals(o.analysis()) && setup().equals(o.setup());
        }
    }
    public int hashCode() {
        return 37 * (37 * (37 * (17 + "xsbti.compile.PreviousResult".hashCode()) + analysis().hashCode()) + setup().hashCode());
    }
    public String toString() {
        return "PreviousResult("  + "analysis: " + analysis() + ", " + "setup: " + setup() + ")";
    }
}
