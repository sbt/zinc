/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package xsbti.api;
public final class ClassLike extends xsbti.api.Definition {
    
    public static ClassLike create(String _name, Access _access, Modifiers _modifiers, Annotation[] _annotations, DefinitionType _definitionType, xsbti.api.Lazy<Type> _selfType, xsbti.api.Lazy<Structure> _structure, String[] _savedAnnotations, Type[] _childrenOfSealedClass, boolean _topLevel, TypeParameter[] _typeParameters) {
        return new ClassLike(_name, _access, _modifiers, _annotations, _definitionType, _selfType, _structure, _savedAnnotations, _childrenOfSealedClass, _topLevel, _typeParameters);
    }
    public static ClassLike of(String _name, Access _access, Modifiers _modifiers, Annotation[] _annotations, DefinitionType _definitionType, xsbti.api.Lazy<Type> _selfType, xsbti.api.Lazy<Structure> _structure, String[] _savedAnnotations, Type[] _childrenOfSealedClass, boolean _topLevel, TypeParameter[] _typeParameters) {
        return new ClassLike(_name, _access, _modifiers, _annotations, _definitionType, _selfType, _structure, _savedAnnotations, _childrenOfSealedClass, _topLevel, _typeParameters);
    }
    
    private DefinitionType definitionType;
    private xsbti.api.Lazy<Type> selfType;
    private xsbti.api.Lazy<Structure> structure;
    private String[] savedAnnotations;
    private Type[] childrenOfSealedClass;
    private boolean topLevel;
    private TypeParameter[] typeParameters;
    protected ClassLike(String _name, Access _access, Modifiers _modifiers, Annotation[] _annotations, DefinitionType _definitionType, xsbti.api.Lazy<Type> _selfType, xsbti.api.Lazy<Structure> _structure, String[] _savedAnnotations, Type[] _childrenOfSealedClass, boolean _topLevel, TypeParameter[] _typeParameters) {
        super(_name, _access, _modifiers, _annotations);
        definitionType = _definitionType;
        selfType = _selfType;
        structure = _structure;
        savedAnnotations = _savedAnnotations;
        childrenOfSealedClass = _childrenOfSealedClass;
        topLevel = _topLevel;
        typeParameters = _typeParameters;
    }
    public DefinitionType definitionType() {
        return this.definitionType;
    }
    public Type selfType() {
        return this.selfType.get();
    }
    public Structure structure() {
        return this.structure.get();
    }
    public String[] savedAnnotations() {
        return this.savedAnnotations;
    }
    public Type[] childrenOfSealedClass() {
        return this.childrenOfSealedClass;
    }
    public boolean topLevel() {
        return this.topLevel;
    }
    public TypeParameter[] typeParameters() {
        return this.typeParameters;
    }
    public ClassLike withName(String name) {
        return new ClassLike(name, access(), modifiers(), annotations(), definitionType, selfType, structure, savedAnnotations, childrenOfSealedClass, topLevel, typeParameters);
    }
    public ClassLike withAccess(Access access) {
        return new ClassLike(name(), access, modifiers(), annotations(), definitionType, selfType, structure, savedAnnotations, childrenOfSealedClass, topLevel, typeParameters);
    }
    public ClassLike withModifiers(Modifiers modifiers) {
        return new ClassLike(name(), access(), modifiers, annotations(), definitionType, selfType, structure, savedAnnotations, childrenOfSealedClass, topLevel, typeParameters);
    }
    public ClassLike withAnnotations(Annotation[] annotations) {
        return new ClassLike(name(), access(), modifiers(), annotations, definitionType, selfType, structure, savedAnnotations, childrenOfSealedClass, topLevel, typeParameters);
    }
    public ClassLike withDefinitionType(DefinitionType definitionType) {
        return new ClassLike(name(), access(), modifiers(), annotations(), definitionType, selfType, structure, savedAnnotations, childrenOfSealedClass, topLevel, typeParameters);
    }
    public ClassLike withSelfType(xsbti.api.Lazy<Type> selfType) {
        return new ClassLike(name(), access(), modifiers(), annotations(), definitionType, selfType, structure, savedAnnotations, childrenOfSealedClass, topLevel, typeParameters);
    }
    public ClassLike withStructure(xsbti.api.Lazy<Structure> structure) {
        return new ClassLike(name(), access(), modifiers(), annotations(), definitionType, selfType, structure, savedAnnotations, childrenOfSealedClass, topLevel, typeParameters);
    }
    public ClassLike withSavedAnnotations(String[] savedAnnotations) {
        return new ClassLike(name(), access(), modifiers(), annotations(), definitionType, selfType, structure, savedAnnotations, childrenOfSealedClass, topLevel, typeParameters);
    }
    public ClassLike withChildrenOfSealedClass(Type[] childrenOfSealedClass) {
        return new ClassLike(name(), access(), modifiers(), annotations(), definitionType, selfType, structure, savedAnnotations, childrenOfSealedClass, topLevel, typeParameters);
    }
    public ClassLike withTopLevel(boolean topLevel) {
        return new ClassLike(name(), access(), modifiers(), annotations(), definitionType, selfType, structure, savedAnnotations, childrenOfSealedClass, topLevel, typeParameters);
    }
    public ClassLike withTypeParameters(TypeParameter[] typeParameters) {
        return new ClassLike(name(), access(), modifiers(), annotations(), definitionType, selfType, structure, savedAnnotations, childrenOfSealedClass, topLevel, typeParameters);
    }
    public boolean equals(Object obj) {
        return this == obj; // We have lazy members, so use object identity to avoid circularity.
    }
    public int hashCode() {
        return super.hashCode(); // Avoid evaluating lazy members in hashCode to avoid circularity.
    }
    public String toString() {
        return super.toString(); // Avoid evaluating lazy members in toString to avoid circularity.
    }
}
