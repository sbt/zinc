/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package xsbti.api;
public abstract class ClassDefinition extends xsbti.api.Definition {
    
    
    protected ClassDefinition(String _name, Access _access, Modifiers _modifiers, Annotation[] _annotations) {
        super(_name, _access, _modifiers, _annotations);
        
    }
    
    
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (!(obj instanceof ClassDefinition)) {
            return false;
        } else {
            ClassDefinition o = (ClassDefinition)obj;
            return name().equals(o.name()) && access().equals(o.access()) && modifiers().equals(o.modifiers()) && java.util.Arrays.deepEquals(annotations(), o.annotations());
        }
    }
    public int hashCode() {
        return 37 * (37 * (37 * (37 * (37 * (17 + "xsbti.api.ClassDefinition".hashCode()) + name().hashCode()) + access().hashCode()) + modifiers().hashCode()) + annotations().hashCode());
    }
    public String toString() {
        return "ClassDefinition("  + "name: " + name() + ", " + "access: " + access() + ", " + "modifiers: " + modifiers() + ", " + "annotations: " + annotations() + ")";
    }
}
