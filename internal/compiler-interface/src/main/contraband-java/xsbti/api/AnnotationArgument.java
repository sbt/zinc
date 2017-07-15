/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package xsbti.api;
public final class AnnotationArgument implements java.io.Serializable {
    
    public static AnnotationArgument create(String _name, String _value) {
        return new AnnotationArgument(_name, _value);
    }
    public static AnnotationArgument of(String _name, String _value) {
        return new AnnotationArgument(_name, _value);
    }
    
    private String name;
    private String value;
    protected AnnotationArgument(String _name, String _value) {
        super();
        name = _name;
        value = _value;
    }
    public String name() {
        return this.name;
    }
    public String value() {
        return this.value;
    }
    public AnnotationArgument withName(String name) {
        return new AnnotationArgument(name, value);
    }
    public AnnotationArgument withValue(String value) {
        return new AnnotationArgument(name, value);
    }
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (!(obj instanceof AnnotationArgument)) {
            return false;
        } else {
            AnnotationArgument o = (AnnotationArgument)obj;
            return name().equals(o.name()) && value().equals(o.value());
        }
    }
    public int hashCode() {
        return 37 * (37 * (37 * (17 + "xsbti.api.AnnotationArgument".hashCode()) + name().hashCode()) + value().hashCode());
    }
    public String toString() {
        return "AnnotationArgument("  + "name: " + name() + ", " + "value: " + value() + ")";
    }
}
