/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package xsbti.compile;
/** Compilation options */
public final class CompileOptions implements java.io.Serializable {
    
    public static CompileOptions create() {
        return new CompileOptions();
    }
    public static CompileOptions of() {
        return new CompileOptions();
    }
    public static CompileOptions create(xsbti.VirtualFile[] _classpath, xsbti.VirtualFile[] _sources, java.nio.file.Path _classesDirectory, String[] _scalacOptions, String[] _javacOptions, int _maxErrors, java.util.function.Function<xsbti.Position, xsbti.Position> _sourcePositionMapper, xsbti.compile.CompileOrder _order) {
        return new CompileOptions(_classpath, _sources, _classesDirectory, _scalacOptions, _javacOptions, _maxErrors, _sourcePositionMapper, _order);
    }
    public static CompileOptions of(xsbti.VirtualFile[] _classpath, xsbti.VirtualFile[] _sources, java.nio.file.Path _classesDirectory, String[] _scalacOptions, String[] _javacOptions, int _maxErrors, java.util.function.Function<xsbti.Position, xsbti.Position> _sourcePositionMapper, xsbti.compile.CompileOrder _order) {
        return new CompileOptions(_classpath, _sources, _classesDirectory, _scalacOptions, _javacOptions, _maxErrors, _sourcePositionMapper, _order);
    }
    public static CompileOptions create(xsbti.VirtualFile[] _classpath, xsbti.VirtualFile[] _sources, java.nio.file.Path _classesDirectory, String[] _scalacOptions, String[] _javacOptions, int _maxErrors, java.util.function.Function<xsbti.Position, xsbti.Position> _sourcePositionMapper, xsbti.compile.CompileOrder _order, java.util.Optional<java.nio.file.Path> _temporaryClassesDirectory) {
        return new CompileOptions(_classpath, _sources, _classesDirectory, _scalacOptions, _javacOptions, _maxErrors, _sourcePositionMapper, _order, _temporaryClassesDirectory);
    }
    public static CompileOptions of(xsbti.VirtualFile[] _classpath, xsbti.VirtualFile[] _sources, java.nio.file.Path _classesDirectory, String[] _scalacOptions, String[] _javacOptions, int _maxErrors, java.util.function.Function<xsbti.Position, xsbti.Position> _sourcePositionMapper, xsbti.compile.CompileOrder _order, java.util.Optional<java.nio.file.Path> _temporaryClassesDirectory) {
        return new CompileOptions(_classpath, _sources, _classesDirectory, _scalacOptions, _javacOptions, _maxErrors, _sourcePositionMapper, _order, _temporaryClassesDirectory);
    }
    public static CompileOptions create(xsbti.VirtualFile[] _classpath, xsbti.VirtualFile[] _sources, java.nio.file.Path _classesDirectory, String[] _scalacOptions, String[] _javacOptions, int _maxErrors, java.util.function.Function<xsbti.Position, xsbti.Position> _sourcePositionMapper, xsbti.compile.CompileOrder _order, java.nio.file.Path _temporaryClassesDirectory) {
        return new CompileOptions(_classpath, _sources, _classesDirectory, _scalacOptions, _javacOptions, _maxErrors, _sourcePositionMapper, _order, _temporaryClassesDirectory);
    }
    public static CompileOptions of(xsbti.VirtualFile[] _classpath, xsbti.VirtualFile[] _sources, java.nio.file.Path _classesDirectory, String[] _scalacOptions, String[] _javacOptions, int _maxErrors, java.util.function.Function<xsbti.Position, xsbti.Position> _sourcePositionMapper, xsbti.compile.CompileOrder _order, java.nio.file.Path _temporaryClassesDirectory) {
        return new CompileOptions(_classpath, _sources, _classesDirectory, _scalacOptions, _javacOptions, _maxErrors, _sourcePositionMapper, _order, _temporaryClassesDirectory);
    }
    public static CompileOptions create(xsbti.VirtualFile[] _classpath, xsbti.VirtualFile[] _sources, java.nio.file.Path _classesDirectory, String[] _scalacOptions, String[] _javacOptions, int _maxErrors, java.util.function.Function<xsbti.Position, xsbti.Position> _sourcePositionMapper, xsbti.compile.CompileOrder _order, java.util.Optional<java.nio.file.Path> _temporaryClassesDirectory, java.util.Optional<xsbti.FileConverter> _converter, java.util.Optional<xsbti.compile.analysis.ReadStamps> _stamper, java.util.Optional<xsbti.compile.Output> _earlyOutput) {
        return new CompileOptions(_classpath, _sources, _classesDirectory, _scalacOptions, _javacOptions, _maxErrors, _sourcePositionMapper, _order, _temporaryClassesDirectory, _converter, _stamper, _earlyOutput);
    }
    public static CompileOptions of(xsbti.VirtualFile[] _classpath, xsbti.VirtualFile[] _sources, java.nio.file.Path _classesDirectory, String[] _scalacOptions, String[] _javacOptions, int _maxErrors, java.util.function.Function<xsbti.Position, xsbti.Position> _sourcePositionMapper, xsbti.compile.CompileOrder _order, java.util.Optional<java.nio.file.Path> _temporaryClassesDirectory, java.util.Optional<xsbti.FileConverter> _converter, java.util.Optional<xsbti.compile.analysis.ReadStamps> _stamper, java.util.Optional<xsbti.compile.Output> _earlyOutput) {
        return new CompileOptions(_classpath, _sources, _classesDirectory, _scalacOptions, _javacOptions, _maxErrors, _sourcePositionMapper, _order, _temporaryClassesDirectory, _converter, _stamper, _earlyOutput);
    }
    public static CompileOptions create(xsbti.VirtualFile[] _classpath, xsbti.VirtualFile[] _sources, java.nio.file.Path _classesDirectory, String[] _scalacOptions, String[] _javacOptions, int _maxErrors, java.util.function.Function<xsbti.Position, xsbti.Position> _sourcePositionMapper, xsbti.compile.CompileOrder _order, java.nio.file.Path _temporaryClassesDirectory, xsbti.FileConverter _converter, xsbti.compile.analysis.ReadStamps _stamper, xsbti.compile.Output _earlyOutput) {
        return new CompileOptions(_classpath, _sources, _classesDirectory, _scalacOptions, _javacOptions, _maxErrors, _sourcePositionMapper, _order, _temporaryClassesDirectory, _converter, _stamper, _earlyOutput);
    }
    public static CompileOptions of(xsbti.VirtualFile[] _classpath, xsbti.VirtualFile[] _sources, java.nio.file.Path _classesDirectory, String[] _scalacOptions, String[] _javacOptions, int _maxErrors, java.util.function.Function<xsbti.Position, xsbti.Position> _sourcePositionMapper, xsbti.compile.CompileOrder _order, java.nio.file.Path _temporaryClassesDirectory, xsbti.FileConverter _converter, xsbti.compile.analysis.ReadStamps _stamper, xsbti.compile.Output _earlyOutput) {
        return new CompileOptions(_classpath, _sources, _classesDirectory, _scalacOptions, _javacOptions, _maxErrors, _sourcePositionMapper, _order, _temporaryClassesDirectory, _converter, _stamper, _earlyOutput);
    }
    private xsbti.VirtualFile[] classpath;
    private xsbti.VirtualFile[] sources;
    private java.nio.file.Path classesDirectory;
    private String[] scalacOptions;
    private String[] javacOptions;
    private int maxErrors;
    private java.util.function.Function<xsbti.Position, xsbti.Position> sourcePositionMapper;
    private xsbti.compile.CompileOrder order;
    private java.util.Optional<java.nio.file.Path> temporaryClassesDirectory;
    private java.util.Optional<xsbti.FileConverter> converter;
    private java.util.Optional<xsbti.compile.analysis.ReadStamps> stamper;
    private java.util.Optional<xsbti.compile.Output> earlyOutput;
    protected CompileOptions() {
        super();
        classpath = new xsbti.VirtualFile[0];
        sources = new xsbti.VirtualFile[0];
        classesDirectory = java.nio.file.Paths.get("classes");
        scalacOptions = new String[0];
        javacOptions = new String[0];
        maxErrors = 100;
        sourcePositionMapper = new java.util.function.Function<xsbti.Position, xsbti.Position>() { public xsbti.Position apply(xsbti.Position a) { return a; } };
        order = xsbti.compile.CompileOrder.Mixed;
        temporaryClassesDirectory = java.util.Optional.<java.nio.file.Path>empty();
        converter = java.util.Optional.<xsbti.FileConverter>empty();
        stamper = java.util.Optional.<xsbti.compile.analysis.ReadStamps>empty();
        earlyOutput = java.util.Optional.<xsbti.compile.Output>empty();
    }
    protected CompileOptions(xsbti.VirtualFile[] _classpath, xsbti.VirtualFile[] _sources, java.nio.file.Path _classesDirectory, String[] _scalacOptions, String[] _javacOptions, int _maxErrors, java.util.function.Function<xsbti.Position, xsbti.Position> _sourcePositionMapper, xsbti.compile.CompileOrder _order) {
        super();
        classpath = _classpath;
        sources = _sources;
        classesDirectory = _classesDirectory;
        scalacOptions = _scalacOptions;
        javacOptions = _javacOptions;
        maxErrors = _maxErrors;
        sourcePositionMapper = _sourcePositionMapper;
        order = _order;
        temporaryClassesDirectory = java.util.Optional.<java.nio.file.Path>empty();
        converter = java.util.Optional.<xsbti.FileConverter>empty();
        stamper = java.util.Optional.<xsbti.compile.analysis.ReadStamps>empty();
        earlyOutput = java.util.Optional.<xsbti.compile.Output>empty();
    }
    protected CompileOptions(xsbti.VirtualFile[] _classpath, xsbti.VirtualFile[] _sources, java.nio.file.Path _classesDirectory, String[] _scalacOptions, String[] _javacOptions, int _maxErrors, java.util.function.Function<xsbti.Position, xsbti.Position> _sourcePositionMapper, xsbti.compile.CompileOrder _order, java.util.Optional<java.nio.file.Path> _temporaryClassesDirectory) {
        super();
        classpath = _classpath;
        sources = _sources;
        classesDirectory = _classesDirectory;
        scalacOptions = _scalacOptions;
        javacOptions = _javacOptions;
        maxErrors = _maxErrors;
        sourcePositionMapper = _sourcePositionMapper;
        order = _order;
        temporaryClassesDirectory = _temporaryClassesDirectory;
        converter = java.util.Optional.<xsbti.FileConverter>empty();
        stamper = java.util.Optional.<xsbti.compile.analysis.ReadStamps>empty();
        earlyOutput = java.util.Optional.<xsbti.compile.Output>empty();
    }
    protected CompileOptions(xsbti.VirtualFile[] _classpath, xsbti.VirtualFile[] _sources, java.nio.file.Path _classesDirectory, String[] _scalacOptions, String[] _javacOptions, int _maxErrors, java.util.function.Function<xsbti.Position, xsbti.Position> _sourcePositionMapper, xsbti.compile.CompileOrder _order, java.nio.file.Path _temporaryClassesDirectory) {
        super();
        classpath = _classpath;
        sources = _sources;
        classesDirectory = _classesDirectory;
        scalacOptions = _scalacOptions;
        javacOptions = _javacOptions;
        maxErrors = _maxErrors;
        sourcePositionMapper = _sourcePositionMapper;
        order = _order;
        temporaryClassesDirectory = java.util.Optional.<java.nio.file.Path>ofNullable(_temporaryClassesDirectory);
        converter = java.util.Optional.<xsbti.FileConverter>empty();
        stamper = java.util.Optional.<xsbti.compile.analysis.ReadStamps>empty();
        earlyOutput = java.util.Optional.<xsbti.compile.Output>empty();
    }
    protected CompileOptions(xsbti.VirtualFile[] _classpath, xsbti.VirtualFile[] _sources, java.nio.file.Path _classesDirectory, String[] _scalacOptions, String[] _javacOptions, int _maxErrors, java.util.function.Function<xsbti.Position, xsbti.Position> _sourcePositionMapper, xsbti.compile.CompileOrder _order, java.util.Optional<java.nio.file.Path> _temporaryClassesDirectory, java.util.Optional<xsbti.FileConverter> _converter, java.util.Optional<xsbti.compile.analysis.ReadStamps> _stamper, java.util.Optional<xsbti.compile.Output> _earlyOutput) {
        super();
        classpath = _classpath;
        sources = _sources;
        classesDirectory = _classesDirectory;
        scalacOptions = _scalacOptions;
        javacOptions = _javacOptions;
        maxErrors = _maxErrors;
        sourcePositionMapper = _sourcePositionMapper;
        order = _order;
        temporaryClassesDirectory = _temporaryClassesDirectory;
        converter = _converter;
        stamper = _stamper;
        earlyOutput = _earlyOutput;
    }
    protected CompileOptions(xsbti.VirtualFile[] _classpath, xsbti.VirtualFile[] _sources, java.nio.file.Path _classesDirectory, String[] _scalacOptions, String[] _javacOptions, int _maxErrors, java.util.function.Function<xsbti.Position, xsbti.Position> _sourcePositionMapper, xsbti.compile.CompileOrder _order, java.nio.file.Path _temporaryClassesDirectory, xsbti.FileConverter _converter, xsbti.compile.analysis.ReadStamps _stamper, xsbti.compile.Output _earlyOutput) {
        super();
        classpath = _classpath;
        sources = _sources;
        classesDirectory = _classesDirectory;
        scalacOptions = _scalacOptions;
        javacOptions = _javacOptions;
        maxErrors = _maxErrors;
        sourcePositionMapper = _sourcePositionMapper;
        order = _order;
        temporaryClassesDirectory = java.util.Optional.<java.nio.file.Path>ofNullable(_temporaryClassesDirectory);
        converter = java.util.Optional.<xsbti.FileConverter>ofNullable(_converter);
        stamper = java.util.Optional.<xsbti.compile.analysis.ReadStamps>ofNullable(_stamper);
        earlyOutput = java.util.Optional.<xsbti.compile.Output>ofNullable(_earlyOutput);
    }
    /**
     * The classpath to use for compilation.
     * This will be modified according to the ClasspathOptions used to configure the ScalaCompiler.
     */
    public xsbti.VirtualFile[] classpath() {
        return this.classpath;
    }
    /**
     * All sources that should be recompiled.
     * This should include Scala and Java sources, which are identified by their extension.
     */
    public xsbti.VirtualFile[] sources() {
        return this.sources;
    }
    public java.nio.file.Path classesDirectory() {
        return this.classesDirectory;
    }
    /** The options to pass to the Scala compiler other than the sources and classpath to use. */
    public String[] scalacOptions() {
        return this.scalacOptions;
    }
    /** The options to pass to the Java compiler other than the sources and classpath to use. */
    public String[] javacOptions() {
        return this.javacOptions;
    }
    public int maxErrors() {
        return this.maxErrors;
    }
    public java.util.function.Function<xsbti.Position, xsbti.Position> sourcePositionMapper() {
        return this.sourcePositionMapper;
    }
    /** Controls the order in which Java and Scala sources are compiled. */
    public xsbti.compile.CompileOrder order() {
        return this.order;
    }
    /**
     * Points to a temporary classes directory where the compiler can put compilation products
     * of any kind. The lifetime of these compilation products is short and the temporary
     * classes directory only needs to exist during one incremental compiler cycle.
     */
    public java.util.Optional<java.nio.file.Path> temporaryClassesDirectory() {
        return this.temporaryClassesDirectory;
    }
    /** FileConverter to convert between Path and VirtualFileRef. */
    public java.util.Optional<xsbti.FileConverter> converter() {
        return this.converter;
    }
    /** ReadStamps to calculate timestamp or hash. */
    public java.util.Optional<xsbti.compile.analysis.ReadStamps> stamper() {
        return this.stamper;
    }
    /** Output for pickle JAR used for build pipelining */
    public java.util.Optional<xsbti.compile.Output> earlyOutput() {
        return this.earlyOutput;
    }
    public CompileOptions withClasspath(xsbti.VirtualFile[] classpath) {
        return new CompileOptions(classpath, sources, classesDirectory, scalacOptions, javacOptions, maxErrors, sourcePositionMapper, order, temporaryClassesDirectory, converter, stamper, earlyOutput);
    }
    public CompileOptions withSources(xsbti.VirtualFile[] sources) {
        return new CompileOptions(classpath, sources, classesDirectory, scalacOptions, javacOptions, maxErrors, sourcePositionMapper, order, temporaryClassesDirectory, converter, stamper, earlyOutput);
    }
    public CompileOptions withClassesDirectory(java.nio.file.Path classesDirectory) {
        return new CompileOptions(classpath, sources, classesDirectory, scalacOptions, javacOptions, maxErrors, sourcePositionMapper, order, temporaryClassesDirectory, converter, stamper, earlyOutput);
    }
    public CompileOptions withScalacOptions(String[] scalacOptions) {
        return new CompileOptions(classpath, sources, classesDirectory, scalacOptions, javacOptions, maxErrors, sourcePositionMapper, order, temporaryClassesDirectory, converter, stamper, earlyOutput);
    }
    public CompileOptions withJavacOptions(String[] javacOptions) {
        return new CompileOptions(classpath, sources, classesDirectory, scalacOptions, javacOptions, maxErrors, sourcePositionMapper, order, temporaryClassesDirectory, converter, stamper, earlyOutput);
    }
    public CompileOptions withMaxErrors(int maxErrors) {
        return new CompileOptions(classpath, sources, classesDirectory, scalacOptions, javacOptions, maxErrors, sourcePositionMapper, order, temporaryClassesDirectory, converter, stamper, earlyOutput);
    }
    public CompileOptions withSourcePositionMapper(java.util.function.Function<xsbti.Position, xsbti.Position> sourcePositionMapper) {
        return new CompileOptions(classpath, sources, classesDirectory, scalacOptions, javacOptions, maxErrors, sourcePositionMapper, order, temporaryClassesDirectory, converter, stamper, earlyOutput);
    }
    public CompileOptions withOrder(xsbti.compile.CompileOrder order) {
        return new CompileOptions(classpath, sources, classesDirectory, scalacOptions, javacOptions, maxErrors, sourcePositionMapper, order, temporaryClassesDirectory, converter, stamper, earlyOutput);
    }
    public CompileOptions withTemporaryClassesDirectory(java.util.Optional<java.nio.file.Path> temporaryClassesDirectory) {
        return new CompileOptions(classpath, sources, classesDirectory, scalacOptions, javacOptions, maxErrors, sourcePositionMapper, order, temporaryClassesDirectory, converter, stamper, earlyOutput);
    }
    public CompileOptions withTemporaryClassesDirectory(java.nio.file.Path temporaryClassesDirectory) {
        return new CompileOptions(classpath, sources, classesDirectory, scalacOptions, javacOptions, maxErrors, sourcePositionMapper, order, java.util.Optional.<java.nio.file.Path>ofNullable(temporaryClassesDirectory), converter, stamper, earlyOutput);
    }
    public CompileOptions withConverter(java.util.Optional<xsbti.FileConverter> converter) {
        return new CompileOptions(classpath, sources, classesDirectory, scalacOptions, javacOptions, maxErrors, sourcePositionMapper, order, temporaryClassesDirectory, converter, stamper, earlyOutput);
    }
    public CompileOptions withConverter(xsbti.FileConverter converter) {
        return new CompileOptions(classpath, sources, classesDirectory, scalacOptions, javacOptions, maxErrors, sourcePositionMapper, order, temporaryClassesDirectory, java.util.Optional.<xsbti.FileConverter>ofNullable(converter), stamper, earlyOutput);
    }
    public CompileOptions withStamper(java.util.Optional<xsbti.compile.analysis.ReadStamps> stamper) {
        return new CompileOptions(classpath, sources, classesDirectory, scalacOptions, javacOptions, maxErrors, sourcePositionMapper, order, temporaryClassesDirectory, converter, stamper, earlyOutput);
    }
    public CompileOptions withStamper(xsbti.compile.analysis.ReadStamps stamper) {
        return new CompileOptions(classpath, sources, classesDirectory, scalacOptions, javacOptions, maxErrors, sourcePositionMapper, order, temporaryClassesDirectory, converter, java.util.Optional.<xsbti.compile.analysis.ReadStamps>ofNullable(stamper), earlyOutput);
    }
    public CompileOptions withEarlyOutput(java.util.Optional<xsbti.compile.Output> earlyOutput) {
        return new CompileOptions(classpath, sources, classesDirectory, scalacOptions, javacOptions, maxErrors, sourcePositionMapper, order, temporaryClassesDirectory, converter, stamper, earlyOutput);
    }
    public CompileOptions withEarlyOutput(xsbti.compile.Output earlyOutput) {
        return new CompileOptions(classpath, sources, classesDirectory, scalacOptions, javacOptions, maxErrors, sourcePositionMapper, order, temporaryClassesDirectory, converter, stamper, java.util.Optional.<xsbti.compile.Output>ofNullable(earlyOutput));
    }
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (!(obj instanceof CompileOptions)) {
            return false;
        } else {
            CompileOptions o = (CompileOptions)obj;
            return java.util.Arrays.deepEquals(this.classpath(), o.classpath()) && java.util.Arrays.deepEquals(this.sources(), o.sources()) && this.classesDirectory().equals(o.classesDirectory()) && java.util.Arrays.deepEquals(this.scalacOptions(), o.scalacOptions()) && java.util.Arrays.deepEquals(this.javacOptions(), o.javacOptions()) && (this.maxErrors() == o.maxErrors()) && this.sourcePositionMapper().equals(o.sourcePositionMapper()) && this.order().equals(o.order()) && this.temporaryClassesDirectory().equals(o.temporaryClassesDirectory()) && this.converter().equals(o.converter()) && this.stamper().equals(o.stamper()) && this.earlyOutput().equals(o.earlyOutput());
        }
    }
    public int hashCode() {
        return 37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (17 + "xsbti.compile.CompileOptions".hashCode()) + java.util.Arrays.deepHashCode(classpath())) + java.util.Arrays.deepHashCode(sources())) + classesDirectory().hashCode()) + java.util.Arrays.deepHashCode(scalacOptions())) + java.util.Arrays.deepHashCode(javacOptions())) + Integer.valueOf(maxErrors()).hashCode()) + sourcePositionMapper().hashCode()) + order().hashCode()) + temporaryClassesDirectory().hashCode()) + converter().hashCode()) + stamper().hashCode()) + earlyOutput().hashCode());
    }
    public String toString() {
        return "CompileOptions("  + "classpath: " + classpath() + ", " + "sources: " + sources() + ", " + "classesDirectory: " + classesDirectory() + ", " + "scalacOptions: " + scalacOptions() + ", " + "javacOptions: " + javacOptions() + ", " + "maxErrors: " + maxErrors() + ", " + "sourcePositionMapper: " + sourcePositionMapper() + ", " + "order: " + order() + ", " + "temporaryClassesDirectory: " + temporaryClassesDirectory() + ", " + "converter: " + converter() + ", " + "stamper: " + stamper() + ", " + "earlyOutput: " + earlyOutput() + ")";
    }
}
