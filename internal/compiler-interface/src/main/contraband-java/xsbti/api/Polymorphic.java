/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package xsbti.api;
public final class Polymorphic extends xsbti.api.Type implements java.io.Serializable {
    
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
            return this.baseType().equals(o.baseType()) && java.util.Arrays.deepEquals(this.parameters(), o.parameters());
        }
    }
    public int hashCode() {
        return 37 * (37 * (37 * (17 + "xsbti.api.Polymorphic".hashCode()) + baseType().hashCode()) + java.util.Arrays.deepHashCode(parameters()));
    }
    public String toString() {
        return "Polymorphic("  + "baseType: " + baseType() + ", " + "parameters: " + parameters() + ")";
    }
}
