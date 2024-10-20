/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package xsbti.api;
public final class Existential extends xsbti.api.Type implements java.io.Serializable {
    
    public static Existential create(Type _baseType, TypeParameter[] _clause) {
        return new Existential(_baseType, _clause);
    }
    public static Existential of(Type _baseType, TypeParameter[] _clause) {
        return new Existential(_baseType, _clause);
    }
    private Type baseType;
    private TypeParameter[] clause;
    protected Existential(Type _baseType, TypeParameter[] _clause) {
        super();
        baseType = _baseType;
        clause = _clause;
    }
    
    public Type baseType() {
        return this.baseType;
    }
    public TypeParameter[] clause() {
        return this.clause;
    }
    public Existential withBaseType(Type baseType) {
        return new Existential(baseType, clause);
    }
    public Existential withClause(TypeParameter[] clause) {
        return new Existential(baseType, clause);
    }
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (!(obj instanceof Existential)) {
            return false;
        } else {
            Existential o = (Existential)obj;
            return this.baseType().equals(o.baseType()) && java.util.Arrays.deepEquals(this.clause(), o.clause());
        }
    }
    public int hashCode() {
        return 37 * (37 * (37 * (17 + "xsbti.api.Existential".hashCode()) + baseType().hashCode()) + java.util.Arrays.deepHashCode(clause()));
    }
    public String toString() {
        return "Existential("  + "baseType: " + baseType() + ", " + "clause: " + clause() + ")";
    }
}
