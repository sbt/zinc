/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package xsbti.api;
public final class ThisQualifier extends xsbti.api.Qualifier implements java.io.Serializable {
    
    public static ThisQualifier create() {
        return new ThisQualifier();
    }
    public static ThisQualifier of() {
        return new ThisQualifier();
    }
    
    protected ThisQualifier() {
        super();
        
    }
    
    
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (!(obj instanceof ThisQualifier)) {
            return false;
        } else {
            ThisQualifier o = (ThisQualifier)obj;
            return true;
        }
    }
    public int hashCode() {
        return 37 * (17 + "xsbti.api.ThisQualifier".hashCode());
    }
    public String toString() {
        return "ThisQualifier("  + ")";
    }
}
