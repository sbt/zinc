/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package xsbti.api;
public abstract class Access implements java.io.Serializable {
    
    
    protected Access() {
        super();
        
    }
    
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (!(obj instanceof Access)) {
            return false;
        } else {
            Access o = (Access)obj;
            return true;
        }
    }
    @Override
    public int hashCode() {
        return 37 * (17 + "xsbti.api.Access".hashCode());
    }
    @Override
    public String toString() {
        return "Access("  + ")";
    }
}
