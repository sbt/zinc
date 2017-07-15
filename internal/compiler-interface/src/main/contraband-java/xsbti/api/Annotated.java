/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package xsbti.api;
public final class Annotated extends xsbti.api.Type {
    
    public static Annotated create(Type _baseType, Annotation[] _annotations) {
        return new Annotated(_baseType, _annotations);
    }
    public static Annotated of(Type _baseType, Annotation[] _annotations) {
        return new Annotated(_baseType, _annotations);
    }
    
    private Type baseType;
    private Annotation[] annotations;
    protected Annotated(Type _baseType, Annotation[] _annotations) {
        super();
        baseType = _baseType;
        annotations = _annotations;
    }
    public Type baseType() {
        return this.baseType;
    }
    public Annotation[] annotations() {
        return this.annotations;
    }
    public Annotated withBaseType(Type baseType) {
        return new Annotated(baseType, annotations);
    }
    public Annotated withAnnotations(Annotation[] annotations) {
        return new Annotated(baseType, annotations);
    }
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (!(obj instanceof Annotated)) {
            return false;
        } else {
            Annotated o = (Annotated)obj;
            return baseType().equals(o.baseType()) && java.util.Arrays.deepEquals(annotations(), o.annotations());
        }
    }
    public int hashCode() {
        return 37 * (37 * (37 * (17 + "xsbti.api.Annotated".hashCode()) + baseType().hashCode()) + annotations().hashCode());
    }
    public String toString() {
        return "Annotated("  + "baseType: " + baseType() + ", " + "annotations: " + annotations() + ")";
    }
}
