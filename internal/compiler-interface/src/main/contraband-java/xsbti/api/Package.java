/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package xsbti.api;
public final class Package implements java.io.Serializable {
    
    public static Package create(String _name) {
        return new Package(_name);
    }
    public static Package of(String _name) {
        return new Package(_name);
    }
    
    private String name;
    protected Package(String _name) {
        super();
        name = _name;
    }
    public String name() {
        return this.name;
    }
    public Package withName(String name) {
        return new Package(name);
    }
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (!(obj instanceof Package)) {
            return false;
        } else {
            Package o = (Package)obj;
            return name().equals(o.name());
        }
    }
    public int hashCode() {
        return 37 * (37 * (17 + "xsbti.api.Package".hashCode()) + name().hashCode());
    }
    public String toString() {
        return "Package("  + "name: " + name() + ")";
    }
}
