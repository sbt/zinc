/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package xsbti.compile;
/**
 * Constructs a minimal ClassfileManager that immediately deletes class files when requested.
 * This is the default classfile manager if no type is provided in incremental options.
 */
public final class DeleteImmediatelyManagerType extends xsbti.compile.ClassFileManagerType {
    
    public static DeleteImmediatelyManagerType create() {
        return new DeleteImmediatelyManagerType();
    }
    public static DeleteImmediatelyManagerType of() {
        return new DeleteImmediatelyManagerType();
    }
    
    protected DeleteImmediatelyManagerType() {
        super();
        
    }
    
    
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (!(obj instanceof DeleteImmediatelyManagerType)) {
            return false;
        } else {
            DeleteImmediatelyManagerType o = (DeleteImmediatelyManagerType)obj;
            return true;
        }
    }
    public int hashCode() {
        return 37 * (17 + "xsbti.compile.DeleteImmediatelyManagerType".hashCode());
    }
    public String toString() {
        return "DeleteImmediatelyManagerType("  + ")";
    }
}
