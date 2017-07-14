/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package xsbti.api;
public final class Structure extends xsbti.api.Type {
    
    public static Structure create(xsbti.api.Lazy<Type[]> _parents, xsbti.api.Lazy<ClassDefinition[]> _declared, xsbti.api.Lazy<ClassDefinition[]> _inherited) {
        return new Structure(_parents, _declared, _inherited);
    }
    public static Structure of(xsbti.api.Lazy<Type[]> _parents, xsbti.api.Lazy<ClassDefinition[]> _declared, xsbti.api.Lazy<ClassDefinition[]> _inherited) {
        return new Structure(_parents, _declared, _inherited);
    }
    
    private xsbti.api.Lazy<Type[]> parents;
    private xsbti.api.Lazy<ClassDefinition[]> declared;
    private xsbti.api.Lazy<ClassDefinition[]> inherited;
    protected Structure(xsbti.api.Lazy<Type[]> _parents, xsbti.api.Lazy<ClassDefinition[]> _declared, xsbti.api.Lazy<ClassDefinition[]> _inherited) {
        super();
        parents = _parents;
        declared = _declared;
        inherited = _inherited;
    }
    public Type[] parents() {
        return this.parents.get();
    }
    public ClassDefinition[] declared() {
        return this.declared.get();
    }
    public ClassDefinition[] inherited() {
        return this.inherited.get();
    }
    public Structure withParents(xsbti.api.Lazy<Type[]> parents) {
        return new Structure(parents, declared, inherited);
    }
    public Structure withDeclared(xsbti.api.Lazy<ClassDefinition[]> declared) {
        return new Structure(parents, declared, inherited);
    }
    public Structure withInherited(xsbti.api.Lazy<ClassDefinition[]> inherited) {
        return new Structure(parents, declared, inherited);
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
