/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package xsbti.api;
public final class Annotated extends xsbti.api.Type {
    
    
    private Type baseType;
    private Annotation[] annotations;
    public Annotated(Type _baseType, Annotation[] _annotations) {
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
        return 37 * (37 * (37 * (17 + "Annotated".hashCode()) + baseType().hashCode()) + annotations().hashCode());
    }
    public String toString() {
        return "Annotated("  + "baseType: " + baseType() + ", " + "annotations: " + annotations() + ")";
    }
}
