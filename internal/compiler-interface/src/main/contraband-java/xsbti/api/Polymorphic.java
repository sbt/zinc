/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package xsbti.api;
public final class Polymorphic extends xsbti.api.Type {
    
    public static Polymorphic create(Type _baseType, TypeParameter[] _parameters) {
        return new Polymorphic(_baseType, _parameters);
    }
    public static Polymorphic of(Type _baseType, TypeParameter[] _parameters) {
        return new Polymorphic(_baseType, _parameters);
    }
    
    private Type baseType;
    private TypeParameter[] parameters;
    protected Polymorphic(Type _baseType, TypeParameter[] _parameters) {
        super();
        baseType = _baseType;
        parameters = _parameters;
    }
    public Type baseType() {
        return this.baseType;
    }
    public TypeParameter[] parameters() {
        return this.parameters;
    }
    public Polymorphic withBaseType(Type baseType) {
        return new Polymorphic(baseType, parameters);
    }
    public Polymorphic withParameters(TypeParameter[] parameters) {
        return new Polymorphic(baseType, parameters);
    }
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (!(obj instanceof Polymorphic)) {
            return false;
        } else {
            Polymorphic o = (Polymorphic)obj;
            return baseType().equals(o.baseType()) && java.util.Arrays.deepEquals(parameters(), o.parameters());
        }
    }
    public int hashCode() {
        return 37 * (37 * (37 * (17 + "xsbti.api.Polymorphic".hashCode()) + baseType().hashCode()) + parameters().hashCode());
    }
    public String toString() {
        return "Polymorphic("  + "baseType: " + baseType() + ", " + "parameters: " + parameters() + ")";
    }
}
