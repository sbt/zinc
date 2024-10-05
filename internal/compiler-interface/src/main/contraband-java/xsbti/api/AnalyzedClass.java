/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
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
    public static AnalyzedClass create(long _compilationTimestamp, String _name, xsbti.api.Lazy<Companions> _api, int _apiHash, NameHash[] _nameHashes, boolean _hasMacro, int _extraHash, String _provenance) {
        return new AnalyzedClass(_compilationTimestamp, _name, _api, _apiHash, _nameHashes, _hasMacro, _extraHash, _provenance);
    }
    public static AnalyzedClass of(long _compilationTimestamp, String _name, xsbti.api.Lazy<Companions> _api, int _apiHash, NameHash[] _nameHashes, boolean _hasMacro, int _extraHash, String _provenance) {
        return new AnalyzedClass(_compilationTimestamp, _name, _api, _apiHash, _nameHashes, _hasMacro, _extraHash, _provenance);
    }
    public static AnalyzedClass create(long _compilationTimestamp, String _name, xsbti.api.Lazy<Companions> _api, int _apiHash, NameHash[] _nameHashes, boolean _hasMacro, int _extraHash, String _provenance, int _bytecodeHash, int _extraBytecodeHash) {
        return new AnalyzedClass(_compilationTimestamp, _name, _api, _apiHash, _nameHashes, _hasMacro, _extraHash, _provenance, _bytecodeHash, _extraBytecodeHash);
    }
    public static AnalyzedClass of(long _compilationTimestamp, String _name, xsbti.api.Lazy<Companions> _api, int _apiHash, NameHash[] _nameHashes, boolean _hasMacro, int _extraHash, String _provenance, int _bytecodeHash, int _extraBytecodeHash) {
        return new AnalyzedClass(_compilationTimestamp, _name, _api, _apiHash, _nameHashes, _hasMacro, _extraHash, _provenance, _bytecodeHash, _extraBytecodeHash);
    }
    private long compilationTimestamp;
    private String name;
    private xsbti.api.Lazy<Companions> api;
    private int apiHash;
    private NameHash[] nameHashes;
    private boolean hasMacro;
    private int extraHash;
    private String provenance;
    private int bytecodeHash;
    private int extraBytecodeHash;
    protected AnalyzedClass(long _compilationTimestamp, String _name, xsbti.api.Lazy<Companions> _api, int _apiHash, NameHash[] _nameHashes, boolean _hasMacro) {
        super();
        compilationTimestamp = _compilationTimestamp;
        name = _name;
        api = _api;
        apiHash = _apiHash;
        nameHashes = _nameHashes;
        hasMacro = _hasMacro;
        extraHash = apiHash;
        provenance = "";
        bytecodeHash = 0;
        extraBytecodeHash = 0;
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
        provenance = "";
        bytecodeHash = 0;
        extraBytecodeHash = 0;
    }
    protected AnalyzedClass(long _compilationTimestamp, String _name, xsbti.api.Lazy<Companions> _api, int _apiHash, NameHash[] _nameHashes, boolean _hasMacro, int _extraHash, String _provenance) {
        super();
        compilationTimestamp = _compilationTimestamp;
        name = _name;
        api = _api;
        apiHash = _apiHash;
        nameHashes = _nameHashes;
        hasMacro = _hasMacro;
        extraHash = _extraHash;
        provenance = _provenance;
        bytecodeHash = 0;
        extraBytecodeHash = 0;
    }
    protected AnalyzedClass(long _compilationTimestamp, String _name, xsbti.api.Lazy<Companions> _api, int _apiHash, NameHash[] _nameHashes, boolean _hasMacro, int _extraHash, String _provenance, int _bytecodeHash, int _extraBytecodeHash) {
        super();
        compilationTimestamp = _compilationTimestamp;
        name = _name;
        api = _api;
        apiHash = _apiHash;
        nameHashes = _nameHashes;
        hasMacro = _hasMacro;
        extraHash = _extraHash;
        provenance = _provenance;
        bytecodeHash = _bytecodeHash;
        extraBytecodeHash = _extraBytecodeHash;
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
    /**
     * An identifier of the 'provenance' of a class, such as the jar that contained it.
     * Combined with a way to tell if the provenance has changed,
     * it can be used to short-circuit the 'lookupAnalyzedClass' operation.
     */
    public String provenance() {
        return this.provenance;
    }
    /** A hash of generated bytecode of source file hosting the class */
    public int bytecodeHash() {
        return this.bytecodeHash;
    }
    /** A hash of generated bytecode of all upstream dependencies */
    public int extraBytecodeHash() {
        return this.extraBytecodeHash;
    }
    public AnalyzedClass withCompilationTimestamp(long compilationTimestamp) {
        return new AnalyzedClass(compilationTimestamp, name, api, apiHash, nameHashes, hasMacro, extraHash, provenance, bytecodeHash, extraBytecodeHash);
    }
    public AnalyzedClass withName(String name) {
        return new AnalyzedClass(compilationTimestamp, name, api, apiHash, nameHashes, hasMacro, extraHash, provenance, bytecodeHash, extraBytecodeHash);
    }
    public AnalyzedClass withApi(xsbti.api.Lazy<Companions> api) {
        return new AnalyzedClass(compilationTimestamp, name, api, apiHash, nameHashes, hasMacro, extraHash, provenance, bytecodeHash, extraBytecodeHash);
    }
    public AnalyzedClass withApiHash(int apiHash) {
        return new AnalyzedClass(compilationTimestamp, name, api, apiHash, nameHashes, hasMacro, extraHash, provenance, bytecodeHash, extraBytecodeHash);
    }
    public AnalyzedClass withNameHashes(NameHash[] nameHashes) {
        return new AnalyzedClass(compilationTimestamp, name, api, apiHash, nameHashes, hasMacro, extraHash, provenance, bytecodeHash, extraBytecodeHash);
    }
    public AnalyzedClass withHasMacro(boolean hasMacro) {
        return new AnalyzedClass(compilationTimestamp, name, api, apiHash, nameHashes, hasMacro, extraHash, provenance, bytecodeHash, extraBytecodeHash);
    }
    public AnalyzedClass withExtraHash(int extraHash) {
        return new AnalyzedClass(compilationTimestamp, name, api, apiHash, nameHashes, hasMacro, extraHash, provenance, bytecodeHash, extraBytecodeHash);
    }
    public AnalyzedClass withProvenance(String provenance) {
        return new AnalyzedClass(compilationTimestamp, name, api, apiHash, nameHashes, hasMacro, extraHash, provenance, bytecodeHash, extraBytecodeHash);
    }
    public AnalyzedClass withBytecodeHash(int bytecodeHash) {
        return new AnalyzedClass(compilationTimestamp, name, api, apiHash, nameHashes, hasMacro, extraHash, provenance, bytecodeHash, extraBytecodeHash);
    }
    public AnalyzedClass withExtraBytecodeHash(int extraBytecodeHash) {
        return new AnalyzedClass(compilationTimestamp, name, api, apiHash, nameHashes, hasMacro, extraHash, provenance, bytecodeHash, extraBytecodeHash);
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
