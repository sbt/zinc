/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package xsbti.api;
public final class MethodParameter implements java.io.Serializable {
    
    public static MethodParameter create(String _name, Type _tpe, boolean _hasDefault, ParameterModifier _modifier) {
        return new MethodParameter(_name, _tpe, _hasDefault, _modifier);
    }
    public static MethodParameter of(String _name, Type _tpe, boolean _hasDefault, ParameterModifier _modifier) {
        return new MethodParameter(_name, _tpe, _hasDefault, _modifier);
    }
    
    private String name;
    private Type tpe;
    private boolean hasDefault;
    private ParameterModifier modifier;
    protected MethodParameter(String _name, Type _tpe, boolean _hasDefault, ParameterModifier _modifier) {
        super();
        name = _name;
        tpe = _tpe;
        hasDefault = _hasDefault;
        modifier = _modifier;
    }
    public String name() {
        return this.name;
    }
    public Type tpe() {
        return this.tpe;
    }
    public boolean hasDefault() {
        return this.hasDefault;
    }
    public ParameterModifier modifier() {
        return this.modifier;
    }
    public MethodParameter withName(String name) {
        return new MethodParameter(name, tpe, hasDefault, modifier);
    }
    public MethodParameter withTpe(Type tpe) {
        return new MethodParameter(name, tpe, hasDefault, modifier);
    }
    public MethodParameter withHasDefault(boolean hasDefault) {
        return new MethodParameter(name, tpe, hasDefault, modifier);
    }
    public MethodParameter withModifier(ParameterModifier modifier) {
        return new MethodParameter(name, tpe, hasDefault, modifier);
    }
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (!(obj instanceof MethodParameter)) {
            return false;
        } else {
            MethodParameter o = (MethodParameter)obj;
            return name().equals(o.name()) && tpe().equals(o.tpe()) && (hasDefault() == o.hasDefault()) && modifier().equals(o.modifier());
        }
    }
    public int hashCode() {
        return 37 * (37 * (37 * (37 * (37 * (17 + "xsbti.api.MethodParameter".hashCode()) + name().hashCode()) + tpe().hashCode()) + (new Boolean(hasDefault())).hashCode()) + modifier().hashCode());
    }
    public String toString() {
        return "MethodParameter("  + "name: " + name() + ", " + "tpe: " + tpe() + ", " + "hasDefault: " + hasDefault() + ", " + "modifier: " + modifier() + ")";
    }
}
