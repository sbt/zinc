/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package xsbti.api;
public abstract class Qualified extends xsbti.api.Access {
    
    
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
            return qualifier().equals(o.qualifier());
        }
    }
    public int hashCode() {
        return 37 * (37 * (17 + "xsbti.api.Qualified".hashCode()) + qualifier().hashCode());
    }
    public String toString() {
        return "Qualified("  + "qualifier: " + qualifier() + ")";
    }
}
