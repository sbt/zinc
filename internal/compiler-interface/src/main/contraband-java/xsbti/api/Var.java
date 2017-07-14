/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package xsbti.api;
public final class Var extends xsbti.api.FieldLike {
    
    public static Var create(String _name, Access _access, Modifiers _modifiers, Annotation[] _annotations, Type _tpe) {
        return new Var(_name, _access, _modifiers, _annotations, _tpe);
    }
    public static Var of(String _name, Access _access, Modifiers _modifiers, Annotation[] _annotations, Type _tpe) {
        return new Var(_name, _access, _modifiers, _annotations, _tpe);
    }
    
    protected Var(String _name, Access _access, Modifiers _modifiers, Annotation[] _annotations, Type _tpe) {
        super(_name, _access, _modifiers, _annotations, _tpe);
        
    }
    
    public Var withName(String name) {
        return new Var(name, access(), modifiers(), annotations(), tpe());
    }
    public Var withAccess(Access access) {
        return new Var(name(), access, modifiers(), annotations(), tpe());
    }
    public Var withModifiers(Modifiers modifiers) {
        return new Var(name(), access(), modifiers, annotations(), tpe());
    }
    public Var withAnnotations(Annotation[] annotations) {
        return new Var(name(), access(), modifiers(), annotations, tpe());
    }
    public Var withTpe(Type tpe) {
        return new Var(name(), access(), modifiers(), annotations(), tpe);
    }
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (!(obj instanceof Var)) {
            return false;
        } else {
            Var o = (Var)obj;
            return name().equals(o.name()) && access().equals(o.access()) && modifiers().equals(o.modifiers()) && java.util.Arrays.deepEquals(annotations(), o.annotations()) && tpe().equals(o.tpe());
        }
    }
    public int hashCode() {
        return 37 * (37 * (37 * (37 * (37 * (37 * (17 + "xsbti.api.Var".hashCode()) + name().hashCode()) + access().hashCode()) + modifiers().hashCode()) + annotations().hashCode()) + tpe().hashCode());
    }
    public String toString() {
        return "Var("  + "name: " + name() + ", " + "access: " + access() + ", " + "modifiers: " + modifiers() + ", " + "annotations: " + annotations() + ", " + "tpe: " + tpe() + ")";
    }
}
