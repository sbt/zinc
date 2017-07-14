/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package xsbti.api;
public final class Val extends xsbti.api.FieldLike {
    
    public static Val create(String _name, Access _access, Modifiers _modifiers, Annotation[] _annotations, Type _tpe) {
        return new Val(_name, _access, _modifiers, _annotations, _tpe);
    }
    public static Val of(String _name, Access _access, Modifiers _modifiers, Annotation[] _annotations, Type _tpe) {
        return new Val(_name, _access, _modifiers, _annotations, _tpe);
    }
    
    protected Val(String _name, Access _access, Modifiers _modifiers, Annotation[] _annotations, Type _tpe) {
        super(_name, _access, _modifiers, _annotations, _tpe);
        
    }
    
    public Val withName(String name) {
        return new Val(name, access(), modifiers(), annotations(), tpe());
    }
    public Val withAccess(Access access) {
        return new Val(name(), access, modifiers(), annotations(), tpe());
    }
    public Val withModifiers(Modifiers modifiers) {
        return new Val(name(), access(), modifiers, annotations(), tpe());
    }
    public Val withAnnotations(Annotation[] annotations) {
        return new Val(name(), access(), modifiers(), annotations, tpe());
    }
    public Val withTpe(Type tpe) {
        return new Val(name(), access(), modifiers(), annotations(), tpe);
    }
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (!(obj instanceof Val)) {
            return false;
        } else {
            Val o = (Val)obj;
            return name().equals(o.name()) && access().equals(o.access()) && modifiers().equals(o.modifiers()) && java.util.Arrays.deepEquals(annotations(), o.annotations()) && tpe().equals(o.tpe());
        }
    }
    public int hashCode() {
        return 37 * (37 * (37 * (37 * (37 * (37 * (17 + "xsbti.api.Val".hashCode()) + name().hashCode()) + access().hashCode()) + modifiers().hashCode()) + annotations().hashCode()) + tpe().hashCode());
    }
    public String toString() {
        return "Val("  + "name: " + name() + ", " + "access: " + access() + ", " + "modifiers: " + modifiers() + ", " + "annotations: " + annotations() + ", " + "tpe: " + tpe() + ")";
    }
}
