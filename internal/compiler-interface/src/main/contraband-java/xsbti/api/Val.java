/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package xsbti.api;
public final class Val extends xsbti.api.FieldLike implements java.io.Serializable {
    
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
            return this.name().equals(o.name()) && this.access().equals(o.access()) && this.modifiers().equals(o.modifiers()) && java.util.Arrays.deepEquals(this.annotations(), o.annotations()) && this.tpe().equals(o.tpe());
        }
    }
    public int hashCode() {
        return 37 * (37 * (37 * (37 * (37 * (37 * (17 + "xsbti.api.Val".hashCode()) + name().hashCode()) + access().hashCode()) + modifiers().hashCode()) + java.util.Arrays.deepHashCode(annotations())) + tpe().hashCode());
    }
    public String toString() {
        return "Val("  + "name: " + name() + ", " + "access: " + access() + ", " + "modifiers: " + modifiers() + ", " + "annotations: " + annotations() + ", " + "tpe: " + tpe() + ")";
    }
}
