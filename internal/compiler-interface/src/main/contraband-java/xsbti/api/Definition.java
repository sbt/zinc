/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package xsbti.api;
public abstract class Definition implements java.io.Serializable {
    
    
    private String name;
    private Access access;
    private Modifiers modifiers;
    private Annotation[] annotations;
    protected Definition(String _name, Access _access, Modifiers _modifiers, Annotation[] _annotations) {
        super();
        name = _name;
        access = _access;
        modifiers = _modifiers;
        annotations = _annotations;
    }
    public String name() {
        return this.name;
    }
    public Access access() {
        return this.access;
    }
    public Modifiers modifiers() {
        return this.modifiers;
    }
    public Annotation[] annotations() {
        return this.annotations;
    }
    
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (!(obj instanceof Definition)) {
            return false;
        } else {
            Definition o = (Definition)obj;
            return name().equals(o.name()) && access().equals(o.access()) && modifiers().equals(o.modifiers()) && java.util.Arrays.deepEquals(annotations(), o.annotations());
        }
    }
    public int hashCode() {
        return 37 * (37 * (37 * (37 * (37 * (17 + "xsbti.api.Definition".hashCode()) + name().hashCode()) + access().hashCode()) + modifiers().hashCode()) + annotations().hashCode());
    }
    public String toString() {
        return "Definition("  + "name: " + name() + ", " + "access: " + access() + ", " + "modifiers: " + modifiers() + ", " + "annotations: " + annotations() + ")";
    }
}
