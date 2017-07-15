/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package xsbti.api;
public final class NameHash implements java.io.Serializable {
    
    public static NameHash create(String _name, xsbti.UseScope _scope, int _hash) {
        return new NameHash(_name, _scope, _hash);
    }
    public static NameHash of(String _name, xsbti.UseScope _scope, int _hash) {
        return new NameHash(_name, _scope, _hash);
    }
    
    private String name;
    private xsbti.UseScope scope;
    private int hash;
    protected NameHash(String _name, xsbti.UseScope _scope, int _hash) {
        super();
        name = _name;
        scope = _scope;
        hash = _hash;
    }
    public String name() {
        return this.name;
    }
    public xsbti.UseScope scope() {
        return this.scope;
    }
    public int hash() {
        return this.hash;
    }
    public NameHash withName(String name) {
        return new NameHash(name, scope, hash);
    }
    public NameHash withScope(xsbti.UseScope scope) {
        return new NameHash(name, scope, hash);
    }
    public NameHash withHash(int hash) {
        return new NameHash(name, scope, hash);
    }
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (!(obj instanceof NameHash)) {
            return false;
        } else {
            NameHash o = (NameHash)obj;
            return name().equals(o.name()) && scope().equals(o.scope()) && (hash() == o.hash());
        }
    }
    public int hashCode() {
        return 37 * (37 * (37 * (37 * (17 + "xsbti.api.NameHash".hashCode()) + name().hashCode()) + scope().hashCode()) + (new Integer(hash())).hashCode());
    }
    public String toString() {
        return "NameHash("  + "name: " + name() + ", " + "scope: " + scope() + ", " + "hash: " + hash() + ")";
    }
}
