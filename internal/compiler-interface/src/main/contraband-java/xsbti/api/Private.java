/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package xsbti.api;
public final class Private extends xsbti.api.Qualified {
    
    
    public Private(Qualifier _qualifier) {
        super(_qualifier);
        
    }
    
    public Private withQualifier(Qualifier qualifier) {
        return new Private(qualifier);
    }
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (!(obj instanceof Private)) {
            return false;
        } else {
            Private o = (Private)obj;
            return qualifier().equals(o.qualifier());
        }
    }
    public int hashCode() {
        return 37 * (37 * (17 + "Private".hashCode()) + qualifier().hashCode());
    }
    public String toString() {
        return "Private("  + "qualifier: " + qualifier() + ")";
    }
}
