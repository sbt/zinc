/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package xsbti.api;
public final class Private extends xsbti.api.Qualified implements java.io.Serializable {
    
    public static Private create(Qualifier _qualifier) {
        return new Private(_qualifier);
    }
    public static Private of(Qualifier _qualifier) {
        return new Private(_qualifier);
    }
    
    protected Private(Qualifier _qualifier) {
        super(_qualifier);
        
    }
    
    public Private withQualifier(Qualifier qualifier) {
        return new Private(qualifier);
    }
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (!(obj instanceof Private)) {
            return false;
        } else {
            Private o = (Private)obj;
            return this.qualifier().equals(o.qualifier());
        }
    }
    @Override
    public int hashCode() {
        return 37 * (37 * (17 + "xsbti.api.Private".hashCode()) + qualifier().hashCode());
    }
    @Override
    public String toString() {
        return "Private("  + "qualifier: " + qualifier() + ")";
    }
}
