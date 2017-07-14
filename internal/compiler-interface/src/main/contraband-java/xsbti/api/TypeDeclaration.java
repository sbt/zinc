/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package xsbti.api;
public final class TypeDeclaration extends xsbti.api.TypeMember {
    
    public static TypeDeclaration create(String _name, Access _access, Modifiers _modifiers, Annotation[] _annotations, TypeParameter[] _typeParameters, Type _lowerBound, Type _upperBound) {
        return new TypeDeclaration(_name, _access, _modifiers, _annotations, _typeParameters, _lowerBound, _upperBound);
    }
    public static TypeDeclaration of(String _name, Access _access, Modifiers _modifiers, Annotation[] _annotations, TypeParameter[] _typeParameters, Type _lowerBound, Type _upperBound) {
        return new TypeDeclaration(_name, _access, _modifiers, _annotations, _typeParameters, _lowerBound, _upperBound);
    }
    
    private Type lowerBound;
    private Type upperBound;
    protected TypeDeclaration(String _name, Access _access, Modifiers _modifiers, Annotation[] _annotations, TypeParameter[] _typeParameters, Type _lowerBound, Type _upperBound) {
        super(_name, _access, _modifiers, _annotations, _typeParameters);
        lowerBound = _lowerBound;
        upperBound = _upperBound;
    }
    public Type lowerBound() {
        return this.lowerBound;
    }
    public Type upperBound() {
        return this.upperBound;
    }
    public TypeDeclaration withName(String name) {
        return new TypeDeclaration(name, access(), modifiers(), annotations(), typeParameters(), lowerBound, upperBound);
    }
    public TypeDeclaration withAccess(Access access) {
        return new TypeDeclaration(name(), access, modifiers(), annotations(), typeParameters(), lowerBound, upperBound);
    }
    public TypeDeclaration withModifiers(Modifiers modifiers) {
        return new TypeDeclaration(name(), access(), modifiers, annotations(), typeParameters(), lowerBound, upperBound);
    }
    public TypeDeclaration withAnnotations(Annotation[] annotations) {
        return new TypeDeclaration(name(), access(), modifiers(), annotations, typeParameters(), lowerBound, upperBound);
    }
    public TypeDeclaration withTypeParameters(TypeParameter[] typeParameters) {
        return new TypeDeclaration(name(), access(), modifiers(), annotations(), typeParameters, lowerBound, upperBound);
    }
    public TypeDeclaration withLowerBound(Type lowerBound) {
        return new TypeDeclaration(name(), access(), modifiers(), annotations(), typeParameters(), lowerBound, upperBound);
    }
    public TypeDeclaration withUpperBound(Type upperBound) {
        return new TypeDeclaration(name(), access(), modifiers(), annotations(), typeParameters(), lowerBound, upperBound);
    }
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (!(obj instanceof TypeDeclaration)) {
            return false;
        } else {
            TypeDeclaration o = (TypeDeclaration)obj;
            return name().equals(o.name()) && access().equals(o.access()) && modifiers().equals(o.modifiers()) && java.util.Arrays.deepEquals(annotations(), o.annotations()) && java.util.Arrays.deepEquals(typeParameters(), o.typeParameters()) && lowerBound().equals(o.lowerBound()) && upperBound().equals(o.upperBound());
        }
    }
    public int hashCode() {
        return 37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (17 + "xsbti.api.TypeDeclaration".hashCode()) + name().hashCode()) + access().hashCode()) + modifiers().hashCode()) + annotations().hashCode()) + typeParameters().hashCode()) + lowerBound().hashCode()) + upperBound().hashCode());
    }
    public String toString() {
        return "TypeDeclaration("  + "name: " + name() + ", " + "access: " + access() + ", " + "modifiers: " + modifiers() + ", " + "annotations: " + annotations() + ", " + "typeParameters: " + typeParameters() + ", " + "lowerBound: " + lowerBound() + ", " + "upperBound: " + upperBound() + ")";
    }
}
