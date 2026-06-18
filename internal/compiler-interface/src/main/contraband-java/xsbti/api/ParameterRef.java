/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package xsbti.api;
public final class ParameterRef extends xsbti.api.Type implements java.io.Serializable {
    
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
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (!(obj instanceof ParameterRef)) {
            return false;
        } else {
            ParameterRef o = (ParameterRef)obj;
            return this.id().equals(o.id());
        }
    }
    @Override
    public int hashCode() {
        return 37 * (37 * (17 + "xsbti.api.ParameterRef".hashCode()) + id().hashCode());
    }
    @Override
    public String toString() {
        return "ParameterRef("  + "id: " + id() + ")";
    }
}
