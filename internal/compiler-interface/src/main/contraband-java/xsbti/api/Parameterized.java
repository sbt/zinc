/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package xsbti.api;
public final class Parameterized extends xsbti.api.Type {
    
    public static Parameterized create(Type _baseType, Type[] _typeArguments) {
        return new Parameterized(_baseType, _typeArguments);
    }
    public static Parameterized of(Type _baseType, Type[] _typeArguments) {
        return new Parameterized(_baseType, _typeArguments);
    }
    
    private Type baseType;
    private Type[] typeArguments;
    protected Parameterized(Type _baseType, Type[] _typeArguments) {
        super();
        baseType = _baseType;
        typeArguments = _typeArguments;
    }
    public Type baseType() {
        return this.baseType;
    }
    public Type[] typeArguments() {
        return this.typeArguments;
    }
    public Parameterized withBaseType(Type baseType) {
        return new Parameterized(baseType, typeArguments);
    }
    public Parameterized withTypeArguments(Type[] typeArguments) {
        return new Parameterized(baseType, typeArguments);
    }
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (!(obj instanceof Parameterized)) {
            return false;
        } else {
            Parameterized o = (Parameterized)obj;
            return baseType().equals(o.baseType()) && java.util.Arrays.deepEquals(typeArguments(), o.typeArguments());
        }
    }
    public int hashCode() {
        return 37 * (37 * (37 * (17 + "xsbti.api.Parameterized".hashCode()) + baseType().hashCode()) + typeArguments().hashCode());
    }
    public String toString() {
        return "Parameterized("  + "baseType: " + baseType() + ", " + "typeArguments: " + typeArguments() + ")";
    }
}
