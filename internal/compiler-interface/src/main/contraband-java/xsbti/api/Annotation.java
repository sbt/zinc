/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package xsbti.api;
public final class Annotation implements java.io.Serializable {
    
    public static Annotation create(Type _base, AnnotationArgument[] _arguments) {
        return new Annotation(_base, _arguments);
    }
    public static Annotation of(Type _base, AnnotationArgument[] _arguments) {
        return new Annotation(_base, _arguments);
    }
    
    private Type base;
    private AnnotationArgument[] arguments;
    protected Annotation(Type _base, AnnotationArgument[] _arguments) {
        super();
        base = _base;
        arguments = _arguments;
    }
    public Type base() {
        return this.base;
    }
    public AnnotationArgument[] arguments() {
        return this.arguments;
    }
    public Annotation withBase(Type base) {
        return new Annotation(base, arguments);
    }
    public Annotation withArguments(AnnotationArgument[] arguments) {
        return new Annotation(base, arguments);
    }
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (!(obj instanceof Annotation)) {
            return false;
        } else {
            Annotation o = (Annotation)obj;
            return base().equals(o.base()) && java.util.Arrays.deepEquals(arguments(), o.arguments());
        }
    }
    public int hashCode() {
        return 37 * (37 * (37 * (17 + "xsbti.api.Annotation".hashCode()) + base().hashCode()) + arguments().hashCode());
    }
    public String toString() {
        return "Annotation("  + "base: " + base() + ", " + "arguments: " + arguments() + ")";
    }
}
