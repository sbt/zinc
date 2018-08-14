/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package xsbti.api;
public final class AnalyzedClass implements java.io.Serializable {
    
    public static AnalyzedClass create(long _compilationTimestamp, String _name, xsbti.api.Lazy<Companions> _api, int _apiHash, NameHash[] _nameHashes, boolean _hasMacro) {
        return new AnalyzedClass(_compilationTimestamp, _name, _api, _apiHash, _nameHashes, _hasMacro);
    }
    public static AnalyzedClass of(long _compilationTimestamp, String _name, xsbti.api.Lazy<Companions> _api, int _apiHash, NameHash[] _nameHashes, boolean _hasMacro) {
        return new AnalyzedClass(_compilationTimestamp, _name, _api, _apiHash, _nameHashes, _hasMacro);
    }
    public static AnalyzedClass create(long _compilationTimestamp, String _name, xsbti.api.Lazy<Companions> _api, int _apiHash, NameHash[] _nameHashes, boolean _hasMacro, int _extraHash) {
        return new AnalyzedClass(_compilationTimestamp, _name, _api, _apiHash, _nameHashes, _hasMacro, _extraHash);
    }
    public static AnalyzedClass of(long _compilationTimestamp, String _name, xsbti.api.Lazy<Companions> _api, int _apiHash, NameHash[] _nameHashes, boolean _hasMacro, int _extraHash) {
        return new AnalyzedClass(_compilationTimestamp, _name, _api, _apiHash, _nameHashes, _hasMacro, _extraHash);
    }
    
    private long compilationTimestamp;
    private String name;
    private xsbti.api.Lazy<Companions> api;
    private int apiHash;
    private NameHash[] nameHashes;
    private boolean hasMacro;
    private int extraHash;
    protected AnalyzedClass(long _compilationTimestamp, String _name, xsbti.api.Lazy<Companions> _api, int _apiHash, NameHash[] _nameHashes, boolean _hasMacro) {
        super();
        compilationTimestamp = _compilationTimestamp;
        name = _name;
        api = _api;
        apiHash = _apiHash;
        nameHashes = _nameHashes;
        hasMacro = _hasMacro;
        extraHash = apiHash;
    }
    protected AnalyzedClass(long _compilationTimestamp, String _name, xsbti.api.Lazy<Companions> _api, int _apiHash, NameHash[] _nameHashes, boolean _hasMacro, int _extraHash) {
        super();
        compilationTimestamp = _compilationTimestamp;
        name = _name;
        api = _api;
        apiHash = _apiHash;
        nameHashes = _nameHashes;
        hasMacro = _hasMacro;
        extraHash = _extraHash;
    }
    public long compilationTimestamp() {
        return this.compilationTimestamp;
    }
    public String name() {
        return this.name;
    }
    public Companions api() {
        return this.api.get();
    }
    public int apiHash() {
        return this.apiHash;
    }
    public NameHash[] nameHashes() {
        return this.nameHashes;
    }
    public boolean hasMacro() {
        return this.hasMacro;
    }
    public int extraHash() {
        return this.extraHash;
    }
    public AnalyzedClass withCompilationTimestamp(long compilationTimestamp) {
        return new AnalyzedClass(compilationTimestamp, name, api, apiHash, nameHashes, hasMacro, extraHash);
    }
    public AnalyzedClass withName(String name) {
        return new AnalyzedClass(compilationTimestamp, name, api, apiHash, nameHashes, hasMacro, extraHash);
    }
    public AnalyzedClass withApi(xsbti.api.Lazy<Companions> api) {
        return new AnalyzedClass(compilationTimestamp, name, api, apiHash, nameHashes, hasMacro, extraHash);
    }
    public AnalyzedClass withApiHash(int apiHash) {
        return new AnalyzedClass(compilationTimestamp, name, api, apiHash, nameHashes, hasMacro, extraHash);
    }
    public AnalyzedClass withNameHashes(NameHash[] nameHashes) {
        return new AnalyzedClass(compilationTimestamp, name, api, apiHash, nameHashes, hasMacro, extraHash);
    }
    public AnalyzedClass withHasMacro(boolean hasMacro) {
        return new AnalyzedClass(compilationTimestamp, name, api, apiHash, nameHashes, hasMacro, extraHash);
    }
    public AnalyzedClass withExtraHash(int extraHash) {
        return new AnalyzedClass(compilationTimestamp, name, api, apiHash, nameHashes, hasMacro, extraHash);
    }
    public boolean equals(Object obj) {
        return this == obj; // We have lazy members, so use object identity to avoid circularity.
    }
    public int hashCode() {
        return super.hashCode(); // Avoid evaluating lazy members in hashCode to avoid circularity.
    }
    public String toString() {
        return super.toString(); // Avoid evaluating lazy members in toString to avoid circularity.
    }
}
