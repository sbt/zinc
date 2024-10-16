/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package xsbti.api;
public final class Singleton extends xsbti.api.Type implements java.io.Serializable {
    
    public static Singleton create(Path _path) {
        return new Singleton(_path);
    }
    public static Singleton of(Path _path) {
        return new Singleton(_path);
    }
    private Path path;
    protected Singleton(Path _path) {
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
            return this.path().equals(o.path());
        }
    }
    public int hashCode() {
        return 37 * (37 * (17 + "xsbti.api.Singleton".hashCode()) + path().hashCode());
    }
    public String toString() {
        return "Singleton("  + "path: " + path() + ")";
    }
}
