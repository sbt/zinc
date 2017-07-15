/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package xsbti.api;
public final class Path implements java.io.Serializable {
    
    public static Path create(PathComponent[] _components) {
        return new Path(_components);
    }
    public static Path of(PathComponent[] _components) {
        return new Path(_components);
    }
    
    private PathComponent[] components;
    protected Path(PathComponent[] _components) {
        super();
        components = _components;
    }
    public PathComponent[] components() {
        return this.components;
    }
    public Path withComponents(PathComponent[] components) {
        return new Path(components);
    }
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (!(obj instanceof Path)) {
            return false;
        } else {
            Path o = (Path)obj;
            return java.util.Arrays.deepEquals(components(), o.components());
        }
    }
    public int hashCode() {
        return 37 * (37 * (17 + "xsbti.api.Path".hashCode()) + components().hashCode());
    }
    public String toString() {
        return "Path("  + "components: " + components() + ")";
    }
}
