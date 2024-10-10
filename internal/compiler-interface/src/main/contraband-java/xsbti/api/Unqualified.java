/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package xsbti.api;
public final class Unqualified extends xsbti.api.Qualifier implements java.io.Serializable {
    
    public static Unqualified create() {
        return new Unqualified();
    }
    public static Unqualified of() {
        return new Unqualified();
    }
    
    protected Unqualified() {
        super();
        
    }
    
    
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (!(obj instanceof Unqualified)) {
            return false;
        } else {
            Unqualified o = (Unqualified)obj;
            return true;
        }
    }
    public int hashCode() {
        return 37 * (17 + "xsbti.api.Unqualified".hashCode());
    }
    public String toString() {
        return "Unqualified("  + ")";
    }
}
