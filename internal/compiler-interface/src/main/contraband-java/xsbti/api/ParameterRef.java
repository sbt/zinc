/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package xsbti.api;
public final class ParameterRef extends xsbti.api.Type {
    
    public static ParameterRef create(String _id) {
        return new ParameterRef(_id);
    }
    public static ParameterRef of(String _id) {
        return new ParameterRef(_id);
    }
    
    private String id;
    protected ParameterRef(String _id) {
        super();
        id = _id;
    }
    public String id() {
        return this.id;
    }
    public ParameterRef withId(String id) {
        return new ParameterRef(id);
    }
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (!(obj instanceof ParameterRef)) {
            return false;
        } else {
            ParameterRef o = (ParameterRef)obj;
            return id().equals(o.id());
        }
    }
    public int hashCode() {
        return 37 * (37 * (17 + "xsbti.api.ParameterRef".hashCode()) + id().hashCode());
    }
    public String toString() {
        return "ParameterRef("  + "id: " + id() + ")";
    }
}
