/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package xsbti.compile;
/** Compilation options. This is used as part of CompileSetup. */
public final class MiniOptions implements java.io.Serializable {
    
    public static MiniOptions create(FileHash[] _classpathHash, String[] _scalacOptions, String[] _javacOptions) {
        return new MiniOptions(_classpathHash, _scalacOptions, _javacOptions);
    }
    public static MiniOptions of(FileHash[] _classpathHash, String[] _scalacOptions, String[] _javacOptions) {
        return new MiniOptions(_classpathHash, _scalacOptions, _javacOptions);
    }
    private FileHash[] classpathHash;
    private String[] scalacOptions;
    private String[] javacOptions;
    protected MiniOptions(FileHash[] _classpathHash, String[] _scalacOptions, String[] _javacOptions) {
        super();
        classpathHash = _classpathHash;
        scalacOptions = _scalacOptions;
        javacOptions = _javacOptions;
    }
    /**
     * The classpath to use for compilation.
     * This will be modified according to the ClasspathOptions used to configure the ScalaCompiler.
     */
    public FileHash[] classpathHash() {
        return this.classpathHash;
    }
    /** The options to pass to the Scala compiler other than the sources and classpath to use. */
    public String[] scalacOptions() {
        return this.scalacOptions;
    }
    /** The options to pass to the Java compiler other than the sources and classpath to use. */
    public String[] javacOptions() {
        return this.javacOptions;
    }
    public MiniOptions withClasspathHash(FileHash[] classpathHash) {
        return new MiniOptions(classpathHash, scalacOptions, javacOptions);
    }
    public MiniOptions withScalacOptions(String[] scalacOptions) {
        return new MiniOptions(classpathHash, scalacOptions, javacOptions);
    }
    public MiniOptions withJavacOptions(String[] javacOptions) {
        return new MiniOptions(classpathHash, scalacOptions, javacOptions);
    }
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (!(obj instanceof MiniOptions)) {
            return false;
        } else {
            MiniOptions o = (MiniOptions)obj;
            return java.util.Arrays.deepEquals(this.classpathHash(), o.classpathHash()) && java.util.Arrays.deepEquals(this.scalacOptions(), o.scalacOptions()) && java.util.Arrays.deepEquals(this.javacOptions(), o.javacOptions());
        }
    }
    public int hashCode() {
        return 37 * (37 * (37 * (37 * (17 + "xsbti.compile.MiniOptions".hashCode()) + java.util.Arrays.deepHashCode(classpathHash())) + java.util.Arrays.deepHashCode(scalacOptions())) + java.util.Arrays.deepHashCode(javacOptions()));
    }
    public String toString() {
        return "MiniOptions("  + "classpathHash: " + classpathHash() + ", " + "scalacOptions: " + scalacOptions() + ", " + "javacOptions: " + javacOptions() + ")";
    }
}
