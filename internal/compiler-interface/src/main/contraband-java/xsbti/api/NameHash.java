/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
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
    public static NameHash create(String _name, xsbti.UseScope _scope, int _hash, xsbti.NameKind _ownerKind) {
        return new NameHash(_name, _scope, _hash, _ownerKind);
    }
    public static NameHash of(String _name, xsbti.UseScope _scope, int _hash, xsbti.NameKind _ownerKind) {
        return new NameHash(_name, _scope, _hash, _ownerKind);
    }
    private String name;
    private xsbti.UseScope scope;
    private int hash;
    private xsbti.NameKind ownerKind;
    protected NameHash(String _name, xsbti.UseScope _scope, int _hash) {
        super();
        name = _name;
        scope = _scope;
        hash = _hash;
        ownerKind = xsbti.NameKind.Type;
    }
    protected NameHash(String _name, xsbti.UseScope _scope, int _hash, xsbti.NameKind _ownerKind) {
        super();
        name = _name;
        scope = _scope;
        hash = _hash;
        ownerKind = _ownerKind;
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
    /**
     * Whether the name is defined in the class (Type) or in the object (Term) of the
     * hashed class name, which a class and its companion object share.
     */
    public xsbti.NameKind ownerKind() {
        return this.ownerKind;
    }
    public NameHash withName(String name) {
        return new NameHash(name, scope, hash, ownerKind);
    }
    public NameHash withScope(xsbti.UseScope scope) {
        return new NameHash(name, scope, hash, ownerKind);
    }
    public NameHash withHash(int hash) {
        return new NameHash(name, scope, hash, ownerKind);
    }
    public NameHash withOwnerKind(xsbti.NameKind ownerKind) {
        return new NameHash(name, scope, hash, ownerKind);
    }
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (!(obj instanceof NameHash)) {
            return false;
        } else {
            NameHash o = (NameHash)obj;
            return this.name().equals(o.name()) && this.scope().equals(o.scope()) && (this.hash() == o.hash()) && this.ownerKind().equals(o.ownerKind());
        }
    }
    @Override
    public int hashCode() {
        return 37 * (37 * (37 * (37 * (37 * (17 + "xsbti.api.NameHash".hashCode()) + name().hashCode()) + scope().hashCode()) + Integer.hashCode(hash())) + ownerKind().hashCode());
    }
    @Override
    public String toString() {
        return "NameHash("  + "name: " + name() + ", " + "scope: " + scope() + ", " + "hash: " + hash() + ", " + "ownerKind: " + ownerKind() + ")";
    }
}
