/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package xsbti.api;
public abstract class ParameterizedDefinition extends xsbti.api.ClassDefinition {
    
    
    private TypeParameter[] typeParameters;
    protected ParameterizedDefinition(String _name, Access _access, Modifiers _modifiers, Annotation[] _annotations, TypeParameter[] _typeParameters) {
        super(_name, _access, _modifiers, _annotations);
        typeParameters = _typeParameters;
    }
    public TypeParameter[] typeParameters() {
        return this.typeParameters;
    }
    
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (!(obj instanceof ParameterizedDefinition)) {
            return false;
        } else {
            ParameterizedDefinition o = (ParameterizedDefinition)obj;
            return name().equals(o.name()) && access().equals(o.access()) && modifiers().equals(o.modifiers()) && java.util.Arrays.deepEquals(annotations(), o.annotations()) && java.util.Arrays.deepEquals(typeParameters(), o.typeParameters());
        }
    }
    public int hashCode() {
        return 37 * (37 * (37 * (37 * (37 * (37 * (17 + "xsbti.api.ParameterizedDefinition".hashCode()) + name().hashCode()) + access().hashCode()) + modifiers().hashCode()) + annotations().hashCode()) + typeParameters().hashCode());
    }
    public String toString() {
        return "ParameterizedDefinition("  + "name: " + name() + ", " + "access: " + access() + ", " + "modifiers: " + modifiers() + ", " + "annotations: " + annotations() + ", " + "typeParameters: " + typeParameters() + ")";
    }
}
