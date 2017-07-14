/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package xsbti.compile;
public abstract class ClassFileManagerType implements java.io.Serializable {
    
    
    protected ClassFileManagerType() {
        super();
        
    }
    
    
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (!(obj instanceof ClassFileManagerType)) {
            return false;
        } else {
            ClassFileManagerType o = (ClassFileManagerType)obj;
            return true;
        }
    }
    public int hashCode() {
        return 37 * (17 + "xsbti.compile.ClassFileManagerType".hashCode());
    }
    public String toString() {
        return "ClassFileManagerType("  + ")";
    }
}
