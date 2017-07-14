/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package xsbti.api;
public final class ThisQualifier extends xsbti.api.Qualifier {
    
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
