/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package xsbti.api;
public final class Constant extends xsbti.api.Type {
    
    public static Constant create(Type _baseType, String _value) {
        return new Constant(_baseType, _value);
    }
    public static Constant of(Type _baseType, String _value) {
        return new Constant(_baseType, _value);
    }
    
    private Type baseType;
    private String value;
    protected Constant(Type _baseType, String _value) {
        super();
        baseType = _baseType;
        value = _value;
    }
    public Type baseType() {
        return this.baseType;
    }
    public String value() {
        return this.value;
    }
    public Constant withBaseType(Type baseType) {
        return new Constant(baseType, value);
    }
    public Constant withValue(String value) {
        return new Constant(baseType, value);
    }
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (!(obj instanceof Constant)) {
            return false;
        } else {
            Constant o = (Constant)obj;
            return baseType().equals(o.baseType()) && value().equals(o.value());
        }
    }
    public int hashCode() {
        return 37 * (37 * (37 * (17 + "xsbti.api.Constant".hashCode()) + baseType().hashCode()) + value().hashCode());
    }
    public String toString() {
        return "Constant("  + "baseType: " + baseType() + ", " + "value: " + value() + ")";
    }
}
