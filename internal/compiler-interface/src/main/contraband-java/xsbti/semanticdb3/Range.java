/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package xsbti.semanticdb3;
/**
 * <code>Range</Code> in SemanticDB directly corresponds to <code>Range</code> in LSP.
 * @see <a href='https://github.com/scalameta/scalameta/blob/master/semanticdb/semanticdb3/semanticdb3.md#range'>scalameta/semanticdb3#Range</a>
 * @see <a href='https://microsoft.github.io/language-server-protocol/'>LSP</a>
 */
public final class Range implements java.io.Serializable {
    
    public static Range create(int _startLine, int _startCharacter, int _endLine, int _endCharacter) {
        return new Range(_startLine, _startCharacter, _endLine, _endCharacter);
    }
    public static Range of(int _startLine, int _startCharacter, int _endLine, int _endCharacter) {
        return new Range(_startLine, _startCharacter, _endLine, _endCharacter);
    }
    
    private int startLine;
    private int startCharacter;
    private int endLine;
    private int endCharacter;
    protected Range(int _startLine, int _startCharacter, int _endLine, int _endCharacter) {
        super();
        startLine = _startLine;
        startCharacter = _startCharacter;
        endLine = _endLine;
        endCharacter = _endCharacter;
    }
    public int startLine() {
        return this.startLine;
    }
    public int startCharacter() {
        return this.startCharacter;
    }
    public int endLine() {
        return this.endLine;
    }
    public int endCharacter() {
        return this.endCharacter;
    }
    public Range withStartLine(int startLine) {
        return new Range(startLine, startCharacter, endLine, endCharacter);
    }
    public Range withStartCharacter(int startCharacter) {
        return new Range(startLine, startCharacter, endLine, endCharacter);
    }
    public Range withEndLine(int endLine) {
        return new Range(startLine, startCharacter, endLine, endCharacter);
    }
    public Range withEndCharacter(int endCharacter) {
        return new Range(startLine, startCharacter, endLine, endCharacter);
    }
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (!(obj instanceof Range)) {
            return false;
        } else {
            Range o = (Range)obj;
            return (this.startLine() == o.startLine()) && (this.startCharacter() == o.startCharacter()) && (this.endLine() == o.endLine()) && (this.endCharacter() == o.endCharacter());
        }
    }
    public int hashCode() {
        return 37 * (37 * (37 * (37 * (37 * (17 + "xsbti.semanticdb3.Range".hashCode()) + (new Integer(startLine())).hashCode()) + (new Integer(startCharacter())).hashCode()) + (new Integer(endLine())).hashCode()) + (new Integer(endCharacter())).hashCode());
    }
    public String toString() {
        return "Range("  + "startLine: " + startLine() + ", " + "startCharacter: " + startCharacter() + ", " + "endLine: " + endLine() + ", " + "endCharacter: " + endCharacter() + ")";
    }
}
