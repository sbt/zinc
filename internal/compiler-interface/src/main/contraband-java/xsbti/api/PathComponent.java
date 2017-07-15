/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package xsbti.api;
public abstract class PathComponent implements java.io.Serializable {
    
    
    protected PathComponent() {
        super();
        
    }
    
    
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (!(obj instanceof PathComponent)) {
            return false;
        } else {
            PathComponent o = (PathComponent)obj;
            return true;
        }
    }
    public int hashCode() {
        return 37 * (17 + "xsbti.api.PathComponent".hashCode());
    }
    public String toString() {
        return "PathComponent("  + ")";
    }
}
