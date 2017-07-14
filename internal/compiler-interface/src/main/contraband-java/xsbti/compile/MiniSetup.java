/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package xsbti.compile;
/** This is used as part of CompileResult. */
public final class MiniSetup implements java.io.Serializable {
    
    public static MiniSetup create(xsbti.compile.Output _output, xsbti.compile.MiniOptions _options, String _compilerVersion, xsbti.compile.CompileOrder _order, boolean _storeApis, xsbti.T2<String, String>[] _extra) {
        return new MiniSetup(_output, _options, _compilerVersion, _order, _storeApis, _extra);
    }
    public static MiniSetup of(xsbti.compile.Output _output, xsbti.compile.MiniOptions _options, String _compilerVersion, xsbti.compile.CompileOrder _order, boolean _storeApis, xsbti.T2<String, String>[] _extra) {
        return new MiniSetup(_output, _options, _compilerVersion, _order, _storeApis, _extra);
    }
    
    private xsbti.compile.Output output;
    private xsbti.compile.MiniOptions options;
    private String compilerVersion;
    private xsbti.compile.CompileOrder order;
    private boolean storeApis;
    private xsbti.T2<String, String>[] extra;
    protected MiniSetup(xsbti.compile.Output _output, xsbti.compile.MiniOptions _options, String _compilerVersion, xsbti.compile.CompileOrder _order, boolean _storeApis, xsbti.T2<String, String>[] _extra) {
        super();
        output = _output;
        options = _options;
        compilerVersion = _compilerVersion;
        order = _order;
        storeApis = _storeApis;
        extra = _extra;
    }
    public xsbti.compile.Output output() {
        return this.output;
    }
    public xsbti.compile.MiniOptions options() {
        return this.options;
    }
    public String compilerVersion() {
        return this.compilerVersion;
    }
    public xsbti.compile.CompileOrder order() {
        return this.order;
    }
    public boolean storeApis() {
        return this.storeApis;
    }
    public xsbti.T2<String, String>[] extra() {
        return this.extra;
    }
    public MiniSetup withOutput(xsbti.compile.Output output) {
        return new MiniSetup(output, options, compilerVersion, order, storeApis, extra);
    }
    public MiniSetup withOptions(xsbti.compile.MiniOptions options) {
        return new MiniSetup(output, options, compilerVersion, order, storeApis, extra);
    }
    public MiniSetup withCompilerVersion(String compilerVersion) {
        return new MiniSetup(output, options, compilerVersion, order, storeApis, extra);
    }
    public MiniSetup withOrder(xsbti.compile.CompileOrder order) {
        return new MiniSetup(output, options, compilerVersion, order, storeApis, extra);
    }
    public MiniSetup withStoreApis(boolean storeApis) {
        return new MiniSetup(output, options, compilerVersion, order, storeApis, extra);
    }
    public MiniSetup withExtra(xsbti.T2<String, String>[] extra) {
        return new MiniSetup(output, options, compilerVersion, order, storeApis, extra);
    }
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (!(obj instanceof MiniSetup)) {
            return false;
        } else {
            MiniSetup o = (MiniSetup)obj;
            return output().equals(o.output()) && options().equals(o.options()) && compilerVersion().equals(o.compilerVersion()) && order().equals(o.order()) && (storeApis() == o.storeApis()) && java.util.Arrays.deepEquals(extra(), o.extra());
        }
    }
    public int hashCode() {
        return 37 * (37 * (37 * (37 * (37 * (37 * (37 * (17 + "xsbti.compile.MiniSetup".hashCode()) + output().hashCode()) + options().hashCode()) + compilerVersion().hashCode()) + order().hashCode()) + (new Boolean(storeApis())).hashCode()) + extra().hashCode());
    }
    public String toString() {
        return "MiniSetup("  + "output: " + output() + ", " + "options: " + options() + ", " + "compilerVersion: " + compilerVersion() + ", " + "order: " + order() + ", " + "storeApis: " + storeApis() + ", " + "extra: " + extra() + ")";
    }
}
