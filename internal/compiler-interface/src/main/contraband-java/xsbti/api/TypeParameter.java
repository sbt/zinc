/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package xsbti.api;
public final class TypeParameter implements java.io.Serializable {
    
    public static TypeParameter create(String _id, Annotation[] _annotations, TypeParameter[] _typeParameters, Variance _variance, Type _lowerBound, Type _upperBound) {
        return new TypeParameter(_id, _annotations, _typeParameters, _variance, _lowerBound, _upperBound);
    }
    public static TypeParameter of(String _id, Annotation[] _annotations, TypeParameter[] _typeParameters, Variance _variance, Type _lowerBound, Type _upperBound) {
        return new TypeParameter(_id, _annotations, _typeParameters, _variance, _lowerBound, _upperBound);
    }
    
    private String id;
    private Annotation[] annotations;
    private TypeParameter[] typeParameters;
    private Variance variance;
    private Type lowerBound;
    private Type upperBound;
    protected TypeParameter(String _id, Annotation[] _annotations, TypeParameter[] _typeParameters, Variance _variance, Type _lowerBound, Type _upperBound) {
        super();
        id = _id;
        annotations = _annotations;
        typeParameters = _typeParameters;
        variance = _variance;
        lowerBound = _lowerBound;
        upperBound = _upperBound;
    }
    public String id() {
        return this.id;
    }
    public Annotation[] annotations() {
        return this.annotations;
    }
    public TypeParameter[] typeParameters() {
        return this.typeParameters;
    }
    public Variance variance() {
        return this.variance;
    }
    public Type lowerBound() {
        return this.lowerBound;
    }
    public Type upperBound() {
        return this.upperBound;
    }
    public TypeParameter withId(String id) {
        return new TypeParameter(id, annotations, typeParameters, variance, lowerBound, upperBound);
    }
    public TypeParameter withAnnotations(Annotation[] annotations) {
        return new TypeParameter(id, annotations, typeParameters, variance, lowerBound, upperBound);
    }
    public TypeParameter withTypeParameters(TypeParameter[] typeParameters) {
        return new TypeParameter(id, annotations, typeParameters, variance, lowerBound, upperBound);
    }
    public TypeParameter withVariance(Variance variance) {
        return new TypeParameter(id, annotations, typeParameters, variance, lowerBound, upperBound);
    }
    public TypeParameter withLowerBound(Type lowerBound) {
        return new TypeParameter(id, annotations, typeParameters, variance, lowerBound, upperBound);
    }
    public TypeParameter withUpperBound(Type upperBound) {
        return new TypeParameter(id, annotations, typeParameters, variance, lowerBound, upperBound);
    }
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (!(obj instanceof TypeParameter)) {
            return false;
        } else {
            TypeParameter o = (TypeParameter)obj;
            return id().equals(o.id()) && java.util.Arrays.deepEquals(annotations(), o.annotations()) && java.util.Arrays.deepEquals(typeParameters(), o.typeParameters()) && variance().equals(o.variance()) && lowerBound().equals(o.lowerBound()) && upperBound().equals(o.upperBound());
        }
    }
    public int hashCode() {
        return 37 * (37 * (37 * (37 * (37 * (37 * (37 * (17 + "xsbti.api.TypeParameter".hashCode()) + id().hashCode()) + annotations().hashCode()) + typeParameters().hashCode()) + variance().hashCode()) + lowerBound().hashCode()) + upperBound().hashCode());
    }
    public String toString() {
        return "TypeParameter("  + "id: " + id() + ", " + "annotations: " + annotations() + ", " + "typeParameters: " + typeParameters() + ", " + "variance: " + variance() + ", " + "lowerBound: " + lowerBound() + ", " + "upperBound: " + upperBound() + ")";
    }
}
