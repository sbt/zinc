/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package xsbti.api;
public final class Companions implements java.io.Serializable {
    
    public static Companions create(ClassLike _classApi, ClassLike _objectApi) {
        return new Companions(_classApi, _objectApi);
    }
    public static Companions of(ClassLike _classApi, ClassLike _objectApi) {
        return new Companions(_classApi, _objectApi);
    }
    
    private ClassLike classApi;
    private ClassLike objectApi;
    protected Companions(ClassLike _classApi, ClassLike _objectApi) {
        super();
        classApi = _classApi;
        objectApi = _objectApi;
    }
    public ClassLike classApi() {
        return this.classApi;
    }
    public ClassLike objectApi() {
        return this.objectApi;
    }
    public Companions withClassApi(ClassLike classApi) {
        return new Companions(classApi, objectApi);
    }
    public Companions withObjectApi(ClassLike objectApi) {
        return new Companions(classApi, objectApi);
    }
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (!(obj instanceof Companions)) {
            return false;
        } else {
            Companions o = (Companions)obj;
            return classApi().equals(o.classApi()) && objectApi().equals(o.objectApi());
        }
    }
    public int hashCode() {
        return 37 * (37 * (37 * (17 + "xsbti.api.Companions".hashCode()) + classApi().hashCode()) + objectApi().hashCode());
    }
    public String toString() {
        return "Companions("  + "classApi: " + classApi() + ", " + "objectApi: " + objectApi() + ")";
    }
}
