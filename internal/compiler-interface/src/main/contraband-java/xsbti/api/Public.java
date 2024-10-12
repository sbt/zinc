/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package xsbti.api;
public final class Public extends xsbti.api.Access implements java.io.Serializable {
    
    public static Public create() {
        return new Public();
    }
    public static Public of() {
        return new Public();
    }
    
    protected Public() {
        super();
        
    }
    
    
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (!(obj instanceof Public)) {
            return false;
        } else {
            Public o = (Public)obj;
            return true;
        }
    }
    public int hashCode() {
        return 37 * (17 + "xsbti.api.Public".hashCode());
    }
    public String toString() {
        return "Public("  + ")";
    }
}
