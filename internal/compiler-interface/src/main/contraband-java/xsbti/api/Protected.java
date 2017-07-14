/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package xsbti.api;
public final class Protected extends xsbti.api.Qualified {
    
    public static Protected create(Qualifier _qualifier) {
        return new Protected(_qualifier);
    }
    public static Protected of(Qualifier _qualifier) {
        return new Protected(_qualifier);
    }
    
    protected Protected(Qualifier _qualifier) {
        super(_qualifier);
        
    }
    
    public Protected withQualifier(Qualifier qualifier) {
        return new Protected(qualifier);
    }
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (!(obj instanceof Protected)) {
            return false;
        } else {
            Protected o = (Protected)obj;
            return qualifier().equals(o.qualifier());
        }
    }
    public int hashCode() {
        return 37 * (37 * (17 + "xsbti.api.Protected".hashCode()) + qualifier().hashCode());
    }
    public String toString() {
        return "Protected("  + "qualifier: " + qualifier() + ")";
    }
}
