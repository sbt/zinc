/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package xsbti.api;
public final class InternalDependency implements java.io.Serializable {
    
    public static InternalDependency create(String _sourceClassName, String _targetClassName, xsbti.api.DependencyContext _context) {
        return new InternalDependency(_sourceClassName, _targetClassName, _context);
    }
    public static InternalDependency of(String _sourceClassName, String _targetClassName, xsbti.api.DependencyContext _context) {
        return new InternalDependency(_sourceClassName, _targetClassName, _context);
    }
    
    private String sourceClassName;
    private String targetClassName;
    private xsbti.api.DependencyContext context;
    protected InternalDependency(String _sourceClassName, String _targetClassName, xsbti.api.DependencyContext _context) {
        super();
        sourceClassName = _sourceClassName;
        targetClassName = _targetClassName;
        context = _context;
    }
    public String sourceClassName() {
        return this.sourceClassName;
    }
    public String targetClassName() {
        return this.targetClassName;
    }
    public xsbti.api.DependencyContext context() {
        return this.context;
    }
    public InternalDependency withSourceClassName(String sourceClassName) {
        return new InternalDependency(sourceClassName, targetClassName, context);
    }
    public InternalDependency withTargetClassName(String targetClassName) {
        return new InternalDependency(sourceClassName, targetClassName, context);
    }
    public InternalDependency withContext(xsbti.api.DependencyContext context) {
        return new InternalDependency(sourceClassName, targetClassName, context);
    }
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (!(obj instanceof InternalDependency)) {
            return false;
        } else {
            InternalDependency o = (InternalDependency)obj;
            return sourceClassName().equals(o.sourceClassName()) && targetClassName().equals(o.targetClassName()) && context().equals(o.context());
        }
    }
    public int hashCode() {
        return 37 * (37 * (37 * (37 * (17 + "xsbti.api.InternalDependency".hashCode()) + sourceClassName().hashCode()) + targetClassName().hashCode()) + context().hashCode());
    }
    public String toString() {
        return "InternalDependency("  + "sourceClassName: " + sourceClassName() + ", " + "targetClassName: " + targetClassName() + ", " + "context: " + context() + ")";
    }
}
