/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package xsbti.api;
public final class ParameterList implements java.io.Serializable {
    
    public static ParameterList create(MethodParameter[] _parameters, boolean _isImplicit) {
        return new ParameterList(_parameters, _isImplicit);
    }
    public static ParameterList of(MethodParameter[] _parameters, boolean _isImplicit) {
        return new ParameterList(_parameters, _isImplicit);
    }
    
    private MethodParameter[] parameters;
    private boolean isImplicit;
    protected ParameterList(MethodParameter[] _parameters, boolean _isImplicit) {
        super();
        parameters = _parameters;
        isImplicit = _isImplicit;
    }
    public MethodParameter[] parameters() {
        return this.parameters;
    }
    public boolean isImplicit() {
        return this.isImplicit;
    }
    public ParameterList withParameters(MethodParameter[] parameters) {
        return new ParameterList(parameters, isImplicit);
    }
    public ParameterList withIsImplicit(boolean isImplicit) {
        return new ParameterList(parameters, isImplicit);
    }
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (!(obj instanceof ParameterList)) {
            return false;
        } else {
            ParameterList o = (ParameterList)obj;
            return java.util.Arrays.deepEquals(parameters(), o.parameters()) && (isImplicit() == o.isImplicit());
        }
    }
    public int hashCode() {
        return 37 * (37 * (37 * (17 + "xsbti.api.ParameterList".hashCode()) + parameters().hashCode()) + (new Boolean(isImplicit())).hashCode());
    }
    public String toString() {
        return "ParameterList("  + "parameters: " + parameters() + ", " + "isImplicit: " + isImplicit() + ")";
    }
}
