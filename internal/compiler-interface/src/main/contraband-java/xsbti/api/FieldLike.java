/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package xsbti.api;
public abstract class FieldLike extends xsbti.api.ClassDefinition {
    
    
    private Type tpe;
    protected FieldLike(String _name, Access _access, Modifiers _modifiers, Annotation[] _annotations, Type _tpe) {
        super(_name, _access, _modifiers, _annotations);
        tpe = _tpe;
    }
    public Type tpe() {
        return this.tpe;
    }
    
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (!(obj instanceof FieldLike)) {
            return false;
        } else {
            FieldLike o = (FieldLike)obj;
            return name().equals(o.name()) && access().equals(o.access()) && modifiers().equals(o.modifiers()) && java.util.Arrays.deepEquals(annotations(), o.annotations()) && tpe().equals(o.tpe());
        }
    }
    public int hashCode() {
        return 37 * (37 * (37 * (37 * (37 * (37 * (17 + "xsbti.api.FieldLike".hashCode()) + name().hashCode()) + access().hashCode()) + modifiers().hashCode()) + annotations().hashCode()) + tpe().hashCode());
    }
    public String toString() {
        return "FieldLike("  + "name: " + name() + ", " + "access: " + access() + ", " + "modifiers: " + modifiers() + ", " + "annotations: " + annotations() + ", " + "tpe: " + tpe() + ")";
    }
}
