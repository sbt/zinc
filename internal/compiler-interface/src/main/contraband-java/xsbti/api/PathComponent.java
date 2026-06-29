/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package xsbti.api;
public abstract class PathComponent implements java.io.Serializable {
    
    
    protected PathComponent() {
        super();
        
    }
    
    
    @Override
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
    @Override
    public int hashCode() {
        return 37 * (17 + "xsbti.api.PathComponent".hashCode());
    }
    @Override
    public String toString() {
        return "PathComponent("  + ")";
    }
}
