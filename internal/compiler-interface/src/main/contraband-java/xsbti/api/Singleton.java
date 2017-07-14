/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package xsbti.api;
public final class Singleton extends xsbti.api.Type {
    
    
    private Path path;
    public Singleton(Path _path) {
        super();
        path = _path;
    }
    public Path path() {
        return this.path;
    }
    public Singleton withPath(Path path) {
        return new Singleton(path);
    }
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (!(obj instanceof Singleton)) {
            return false;
        } else {
            Singleton o = (Singleton)obj;
            return path().equals(o.path());
        }
    }
    public int hashCode() {
        return 37 * (37 * (17 + "Singleton".hashCode()) + path().hashCode());
    }
    public String toString() {
        return "Singleton("  + "path: " + path() + ")";
    }
}
