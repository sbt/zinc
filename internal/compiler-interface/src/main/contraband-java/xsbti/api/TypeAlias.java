/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package xsbti.api;
public final class TypeAlias extends xsbti.api.TypeMember {
    
    public static TypeAlias create(String _name, Access _access, Modifiers _modifiers, Annotation[] _annotations, TypeParameter[] _typeParameters, Type _tpe) {
        return new TypeAlias(_name, _access, _modifiers, _annotations, _typeParameters, _tpe);
    }
    public static TypeAlias of(String _name, Access _access, Modifiers _modifiers, Annotation[] _annotations, TypeParameter[] _typeParameters, Type _tpe) {
        return new TypeAlias(_name, _access, _modifiers, _annotations, _typeParameters, _tpe);
    }
    
    private Type tpe;
    protected TypeAlias(String _name, Access _access, Modifiers _modifiers, Annotation[] _annotations, TypeParameter[] _typeParameters, Type _tpe) {
        super(_name, _access, _modifiers, _annotations, _typeParameters);
        tpe = _tpe;
    }
    public Type tpe() {
        return this.tpe;
    }
    public TypeAlias withName(String name) {
        return new TypeAlias(name, access(), modifiers(), annotations(), typeParameters(), tpe);
    }
    public TypeAlias withAccess(Access access) {
        return new TypeAlias(name(), access, modifiers(), annotations(), typeParameters(), tpe);
    }
    public TypeAlias withModifiers(Modifiers modifiers) {
        return new TypeAlias(name(), access(), modifiers, annotations(), typeParameters(), tpe);
    }
    public TypeAlias withAnnotations(Annotation[] annotations) {
        return new TypeAlias(name(), access(), modifiers(), annotations, typeParameters(), tpe);
    }
    public TypeAlias withTypeParameters(TypeParameter[] typeParameters) {
        return new TypeAlias(name(), access(), modifiers(), annotations(), typeParameters, tpe);
    }
    public TypeAlias withTpe(Type tpe) {
        return new TypeAlias(name(), access(), modifiers(), annotations(), typeParameters(), tpe);
    }
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (!(obj instanceof TypeAlias)) {
            return false;
        } else {
            TypeAlias o = (TypeAlias)obj;
            return name().equals(o.name()) && access().equals(o.access()) && modifiers().equals(o.modifiers()) && java.util.Arrays.deepEquals(annotations(), o.annotations()) && java.util.Arrays.deepEquals(typeParameters(), o.typeParameters()) && tpe().equals(o.tpe());
        }
    }
    public int hashCode() {
        return 37 * (37 * (37 * (37 * (37 * (37 * (37 * (17 + "xsbti.api.TypeAlias".hashCode()) + name().hashCode()) + access().hashCode()) + modifiers().hashCode()) + annotations().hashCode()) + typeParameters().hashCode()) + tpe().hashCode());
    }
    public String toString() {
        return "TypeAlias("  + "name: " + name() + ", " + "access: " + access() + ", " + "modifiers: " + modifiers() + ", " + "annotations: " + annotations() + ", " + "typeParameters: " + typeParameters() + ", " + "tpe: " + tpe() + ")";
    }
}
