/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package xsbti.api;
public final class ClassLikeDef extends xsbti.api.ParameterizedDefinition {
    
    public static ClassLikeDef create(String _name, Access _access, Modifiers _modifiers, Annotation[] _annotations, TypeParameter[] _typeParameters, DefinitionType _definitionType) {
        return new ClassLikeDef(_name, _access, _modifiers, _annotations, _typeParameters, _definitionType);
    }
    public static ClassLikeDef of(String _name, Access _access, Modifiers _modifiers, Annotation[] _annotations, TypeParameter[] _typeParameters, DefinitionType _definitionType) {
        return new ClassLikeDef(_name, _access, _modifiers, _annotations, _typeParameters, _definitionType);
    }
    
    private DefinitionType definitionType;
    protected ClassLikeDef(String _name, Access _access, Modifiers _modifiers, Annotation[] _annotations, TypeParameter[] _typeParameters, DefinitionType _definitionType) {
        super(_name, _access, _modifiers, _annotations, _typeParameters);
        definitionType = _definitionType;
    }
    public DefinitionType definitionType() {
        return this.definitionType;
    }
    public ClassLikeDef withName(String name) {
        return new ClassLikeDef(name, access(), modifiers(), annotations(), typeParameters(), definitionType);
    }
    public ClassLikeDef withAccess(Access access) {
        return new ClassLikeDef(name(), access, modifiers(), annotations(), typeParameters(), definitionType);
    }
    public ClassLikeDef withModifiers(Modifiers modifiers) {
        return new ClassLikeDef(name(), access(), modifiers, annotations(), typeParameters(), definitionType);
    }
    public ClassLikeDef withAnnotations(Annotation[] annotations) {
        return new ClassLikeDef(name(), access(), modifiers(), annotations, typeParameters(), definitionType);
    }
    public ClassLikeDef withTypeParameters(TypeParameter[] typeParameters) {
        return new ClassLikeDef(name(), access(), modifiers(), annotations(), typeParameters, definitionType);
    }
    public ClassLikeDef withDefinitionType(DefinitionType definitionType) {
        return new ClassLikeDef(name(), access(), modifiers(), annotations(), typeParameters(), definitionType);
    }
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (!(obj instanceof ClassLikeDef)) {
            return false;
        } else {
            ClassLikeDef o = (ClassLikeDef)obj;
            return name().equals(o.name()) && access().equals(o.access()) && modifiers().equals(o.modifiers()) && java.util.Arrays.deepEquals(annotations(), o.annotations()) && java.util.Arrays.deepEquals(typeParameters(), o.typeParameters()) && definitionType().equals(o.definitionType());
        }
    }
    public int hashCode() {
        return 37 * (37 * (37 * (37 * (37 * (37 * (37 * (17 + "xsbti.api.ClassLikeDef".hashCode()) + name().hashCode()) + access().hashCode()) + modifiers().hashCode()) + annotations().hashCode()) + typeParameters().hashCode()) + definitionType().hashCode());
    }
    public String toString() {
        return "ClassLikeDef("  + "name: " + name() + ", " + "access: " + access() + ", " + "modifiers: " + modifiers() + ", " + "annotations: " + annotations() + ", " + "typeParameters: " + typeParameters() + ", " + "definitionType: " + definitionType() + ")";
    }
}
