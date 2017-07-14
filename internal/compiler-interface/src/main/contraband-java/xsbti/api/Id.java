/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package xsbti.api;
public final class Id extends xsbti.api.PathComponent {
    
    
    private String id;
    public Id(String _id) {
        super();
        id = _id;
    }
    public String id() {
        return this.id;
    }
    public Id withId(String id) {
        return new Id(id);
    }
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (!(obj instanceof Id)) {
            return false;
        } else {
            Id o = (Id)obj;
            return id().equals(o.id());
        }
    }
    public int hashCode() {
        return 37 * (37 * (17 + "Id".hashCode()) + id().hashCode());
    }
    public String toString() {
        return "Id("  + "id: " + id() + ")";
    }
}
