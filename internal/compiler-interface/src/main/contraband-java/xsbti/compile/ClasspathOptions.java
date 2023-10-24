/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
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
    private boolean bootLibrary;
    private boolean compiler;
    private boolean extra;
    private boolean autoBoot;
    private boolean filterLibrary;
    protected ClasspathOptions(boolean _bootLibrary, boolean _compiler, boolean _extra, boolean _autoBoot, boolean _filterLibrary) {
        super();
        bootLibrary = _bootLibrary;
        compiler = _compiler;
        extra = _extra;
        autoBoot = _autoBoot;
        filterLibrary = _filterLibrary;
    }
    /**
     * If true, includes the Scala library on the boot classpath.
     * This should usually be false.
     */
    public boolean bootLibrary() {
        return this.bootLibrary;
    }
    /**
     * If true, includes the Scala compiler on the standard classpath.
     * This is typically false and is instead managed by the build tool or environment.
     */
    public boolean compiler() {
        return this.compiler;
    }
    /**
     * If true, includes extra jars from the Scala instance on the standard classpath.
     * This is typically false and is instead managed by the build tool or environment.
     */
    public boolean extra() {
        return this.extra;
    }
    /**
     * If true, automatically configures the boot classpath.
     * This should usually be false.
     */
    public boolean autoBoot() {
        return this.autoBoot;
    }
    /**
     * If true, the Scala library jar is filtered from the standard classpath.
     * This should usually be false unless the library is included on the boot
     * classpath of the Scala compiler and not the standard classpath.
     */
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
            return (this.bootLibrary() == o.bootLibrary()) && (this.compiler() == o.compiler()) && (this.extra() == o.extra()) && (this.autoBoot() == o.autoBoot()) && (this.filterLibrary() == o.filterLibrary());
        }
    }
    public int hashCode() {
        return 37 * (37 * (37 * (37 * (37 * (37 * (17 + "xsbti.compile.ClasspathOptions".hashCode()) + Boolean.valueOf(bootLibrary()).hashCode()) + Boolean.valueOf(compiler()).hashCode()) + Boolean.valueOf(extra()).hashCode()) + Boolean.valueOf(autoBoot()).hashCode()) + Boolean.valueOf(filterLibrary()).hashCode());
    }
    public String toString() {
        return "ClasspathOptions("  + "bootLibrary: " + bootLibrary() + ", " + "compiler: " + compiler() + ", " + "extra: " + extra() + ", " + "autoBoot: " + autoBoot() + ", " + "filterLibrary: " + filterLibrary() + ")";
    }
}
