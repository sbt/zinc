/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package xsbti.api;
public final class Super extends xsbti.api.PathComponent {
    
    public static Super create(Path _qualifier) {
        return new Super(_qualifier);
    }
    public static Super of(Path _qualifier) {
        return new Super(_qualifier);
    }
    
    private Path qualifier;
    protected Super(Path _qualifier) {
        super();
        qualifier = _qualifier;
    }
    public Path qualifier() {
        return this.qualifier;
    }
    public Super withQualifier(Path qualifier) {
        return new Super(qualifier);
    }
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (!(obj instanceof Super)) {
            return false;
        } else {
            Super o = (Super)obj;
            return qualifier().equals(o.qualifier());
        }
    }
    public int hashCode() {
        return 37 * (37 * (17 + "xsbti.api.Super".hashCode()) + qualifier().hashCode());
    }
    public String toString() {
        return "Super("  + "qualifier: " + qualifier() + ")";
    }
}
