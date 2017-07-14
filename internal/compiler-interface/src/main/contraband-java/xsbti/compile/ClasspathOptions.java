/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package xsbti.compile;
/**
 * Define modifications to classpaths based on the {@link ScalaInstance} used for
 * compilation. This class tells how to instrument the classpaths given certain Scala
 * compiler-related parameters. Usually, values are all false for Java compilation.
 */
public final class ClasspathOptions implements java.io.Serializable {
    
    public static ClasspathOptions create(boolean _bootLibrary, boolean _compiler, boolean _extra, boolean _autoBoot, boolean _filterLibrary) {
        return new ClasspathOptions(_bootLibrary, _compiler, _extra, _autoBoot, _filterLibrary);
    }
    public static ClasspathOptions of(boolean _bootLibrary, boolean _compiler, boolean _extra, boolean _autoBoot, boolean _filterLibrary) {
        return new ClasspathOptions(_bootLibrary, _compiler, _extra, _autoBoot, _filterLibrary);
    }
    /**
     * If true, includes the Scala library on the boot classpath.
     * This should usually be true.
     */
    private boolean bootLibrary;
    /**
     * If true, includes the Scala compiler on the standard classpath.
     * This is typically false and is instead managed by the build tool or environment.
     */
    private boolean compiler;
    /**
     * If true, includes extra jars from the Scala instance on the standard classpath.
     * This is typically false and is instead managed by the build tool or environment.
     */
    private boolean extra;
    /**
     * If true, automatically configures the boot classpath.
     * This should usually be true.
     */
    private boolean autoBoot;
    /**
     * If true, the Scala library jar is filtered from the standard classpath.
     * This should usually be true because the library should be included on the boot classpath of the Scala compiler and not the standard classpath.
     */
    private boolean filterLibrary;
    protected ClasspathOptions(boolean _bootLibrary, boolean _compiler, boolean _extra, boolean _autoBoot, boolean _filterLibrary) {
        super();
        bootLibrary = _bootLibrary;
        compiler = _compiler;
        extra = _extra;
        autoBoot = _autoBoot;
        filterLibrary = _filterLibrary;
    }
    public boolean bootLibrary() {
        return this.bootLibrary;
    }
    public boolean compiler() {
        return this.compiler;
    }
    public boolean extra() {
        return this.extra;
    }
    public boolean autoBoot() {
        return this.autoBoot;
    }
    public boolean filterLibrary() {
        return this.filterLibrary;
    }
    public ClasspathOptions withBootLibrary(boolean bootLibrary) {
        return new ClasspathOptions(bootLibrary, compiler, extra, autoBoot, filterLibrary);
    }
    public ClasspathOptions withCompiler(boolean compiler) {
        return new ClasspathOptions(bootLibrary, compiler, extra, autoBoot, filterLibrary);
    }
    public ClasspathOptions withExtra(boolean extra) {
        return new ClasspathOptions(bootLibrary, compiler, extra, autoBoot, filterLibrary);
    }
    public ClasspathOptions withAutoBoot(boolean autoBoot) {
        return new ClasspathOptions(bootLibrary, compiler, extra, autoBoot, filterLibrary);
    }
    public ClasspathOptions withFilterLibrary(boolean filterLibrary) {
        return new ClasspathOptions(bootLibrary, compiler, extra, autoBoot, filterLibrary);
    }
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (!(obj instanceof ClasspathOptions)) {
            return false;
        } else {
            ClasspathOptions o = (ClasspathOptions)obj;
            return (bootLibrary() == o.bootLibrary()) && (compiler() == o.compiler()) && (extra() == o.extra()) && (autoBoot() == o.autoBoot()) && (filterLibrary() == o.filterLibrary());
        }
    }
    public int hashCode() {
        return 37 * (37 * (37 * (37 * (37 * (37 * (17 + "xsbti.compile.ClasspathOptions".hashCode()) + (new Boolean(bootLibrary())).hashCode()) + (new Boolean(compiler())).hashCode()) + (new Boolean(extra())).hashCode()) + (new Boolean(autoBoot())).hashCode()) + (new Boolean(filterLibrary())).hashCode());
    }
    public String toString() {
        return "ClasspathOptions("  + "bootLibrary: " + bootLibrary() + ", " + "compiler: " + compiler() + ", " + "extra: " + extra() + ", " + "autoBoot: " + autoBoot() + ", " + "filterLibrary: " + filterLibrary() + ")";
    }
}
