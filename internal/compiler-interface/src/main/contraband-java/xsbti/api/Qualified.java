/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package xsbti.api;
public abstract class Qualified extends xsbti.api.Access implements java.io.Serializable {
    
    private Qualifier qualifier;
    protected Qualified(Qualifier _qualifier) {
        super();
        qualifier = _qualifier;
    }
    
    public Qualifier qualifier() {
        return this.qualifier;
    }
    
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (!(obj instanceof Qualified)) {
            return false;
        } else {
            Qualified o = (Qualified)obj;
            return this.qualifier().equals(o.qualifier());
        }
    }
    public int hashCode() {
        return 37 * (37 * (17 + "xsbti.api.Qualified".hashCode()) + qualifier().hashCode());
    }
    public String toString() {
        return "Qualified("  + "qualifier: " + qualifier() + ")";
    }
}
