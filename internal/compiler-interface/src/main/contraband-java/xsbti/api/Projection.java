/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package xsbti.api;
public final class Projection extends xsbti.api.Type {
    
    public static Projection create(Type _prefix, String _id) {
        return new Projection(_prefix, _id);
    }
    public static Projection of(Type _prefix, String _id) {
        return new Projection(_prefix, _id);
    }
    
    private Type prefix;
    private String id;
    protected Projection(Type _prefix, String _id) {
        super();
        prefix = _prefix;
        id = _id;
    }
    public Type prefix() {
        return this.prefix;
    }
    public String id() {
        return this.id;
    }
    public Projection withPrefix(Type prefix) {
        return new Projection(prefix, id);
    }
    public Projection withId(String id) {
        return new Projection(prefix, id);
    }
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (!(obj instanceof Projection)) {
            return false;
        } else {
            Projection o = (Projection)obj;
            return prefix().equals(o.prefix()) && id().equals(o.id());
        }
    }
    public int hashCode() {
        return 37 * (37 * (37 * (17 + "xsbti.api.Projection".hashCode()) + prefix().hashCode()) + id().hashCode());
    }
    public String toString() {
        return "Projection("  + "prefix: " + prefix() + ", " + "id: " + id() + ")";
    }
}
