/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package xsbti.semanticdb3;
/**
 * "Occurrences" is a section of a TextDocument that represents the results of name resolution for identifiers in the underlying code snippet.
 * <code>SymbolOccurrence</code> refers to a <code>Range</code> in the TextDocument and has a symbol as explained in <code>Symbol</code>.
 * <code>role</code> is an enumeration that describes the semantic role that the identifier performs in the code.
 * @see <a href='https://github.com/scalameta/scalameta/blob/master/semanticdb/semanticdb3/semanticdb3.md#symboloccurrence'>scalameta/semanticdb3#SymbolOccurrence</a>
 */
public final class SymbolOccurrence implements java.io.Serializable {
    
    public static SymbolOccurrence create(xsbti.semanticdb3.Range _range, String _symbol, xsbti.semanticdb3.Role _role) {
        return new SymbolOccurrence(_range, _symbol, _role);
    }
    public static SymbolOccurrence of(xsbti.semanticdb3.Range _range, String _symbol, xsbti.semanticdb3.Role _role) {
        return new SymbolOccurrence(_range, _symbol, _role);
    }
    
    private xsbti.semanticdb3.Range range;
    /**
     * Symbols are tokens that are used to correlate references and definitions. In the SemanticDB model, symbols are represented as strings.
     * @see <a href='https://github.com/scalameta/scalameta/blob/master/semanticdb/semanticdb3/semanticdb3.md#symbol'>scalameta/semanticdb3#Symbol</a>
     */
    private String symbol;
    private xsbti.semanticdb3.Role role;
    protected SymbolOccurrence(xsbti.semanticdb3.Range _range, String _symbol, xsbti.semanticdb3.Role _role) {
        super();
        range = _range;
        symbol = _symbol;
        role = _role;
    }
    public xsbti.semanticdb3.Range range() {
        return this.range;
    }
    public String symbol() {
        return this.symbol;
    }
    public xsbti.semanticdb3.Role role() {
        return this.role;
    }
    public SymbolOccurrence withRange(xsbti.semanticdb3.Range range) {
        return new SymbolOccurrence(range, symbol, role);
    }
    public SymbolOccurrence withSymbol(String symbol) {
        return new SymbolOccurrence(range, symbol, role);
    }
    public SymbolOccurrence withRole(xsbti.semanticdb3.Role role) {
        return new SymbolOccurrence(range, symbol, role);
    }
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (!(obj instanceof SymbolOccurrence)) {
            return false;
        } else {
            SymbolOccurrence o = (SymbolOccurrence)obj;
            return this.range().equals(o.range()) && this.symbol().equals(o.symbol()) && this.role().equals(o.role());
        }
    }
    public int hashCode() {
        return 37 * (37 * (37 * (37 * (17 + "xsbti.semanticdb3.SymbolOccurrence".hashCode()) + range().hashCode()) + symbol().hashCode()) + role().hashCode());
    }
    public String toString() {
        return "SymbolOccurrence("  + "range: " + range() + ", " + "symbol: " + symbol() + ", " + "role: " + role() + ")";
    }
}
