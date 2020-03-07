/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package xsbti.compile;
/** A wrapper around PickleBuffer https://github.com/scala/scala/blob/v2.13.1/src/reflect/scala/reflect/internal/pickling/PickleBuffer.scala */
public final class PickleData implements java.io.Serializable {
    
    public static PickleData create(Object _underlying, String _fqcn, byte[] _data, int _writeIndex, java.nio.file.Path _path) {
        return new PickleData(_underlying, _fqcn, _data, _writeIndex, _path);
    }
    public static PickleData of(Object _underlying, String _fqcn, byte[] _data, int _writeIndex, java.nio.file.Path _path) {
        return new PickleData(_underlying, _fqcn, _data, _writeIndex, _path);
    }
    private Object underlying;
    private String fqcn;
    private byte[] data;
    private int writeIndex;
    private java.nio.file.Path path;
    protected PickleData(Object _underlying, String _fqcn, byte[] _data, int _writeIndex, java.nio.file.Path _path) {
        super();
        underlying = _underlying;
        fqcn = _fqcn;
        data = _data;
        writeIndex = _writeIndex;
        path = _path;
    }
    
    public Object underlying() {
        return this.underlying;
    }
    public String fqcn() {
        return this.fqcn;
    }
    public byte[] data() {
        return this.data;
    }
    public int writeIndex() {
        return this.writeIndex;
    }
    public java.nio.file.Path path() {
        return this.path;
    }
    public PickleData withUnderlying(Object underlying) {
        return new PickleData(underlying, fqcn, data, writeIndex, path);
    }
    public PickleData withFqcn(String fqcn) {
        return new PickleData(underlying, fqcn, data, writeIndex, path);
    }
    public PickleData withData(byte[] data) {
        return new PickleData(underlying, fqcn, data, writeIndex, path);
    }
    public PickleData withWriteIndex(int writeIndex) {
        return new PickleData(underlying, fqcn, data, writeIndex, path);
    }
    public PickleData withPath(java.nio.file.Path path) {
        return new PickleData(underlying, fqcn, data, writeIndex, path);
    }
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (!(obj instanceof PickleData)) {
            return false;
        } else {
            PickleData o = (PickleData)obj;
            return this.underlying().equals(o.underlying()) && this.fqcn().equals(o.fqcn()) && java.util.Arrays.equals(this.data(), o.data()) && (this.writeIndex() == o.writeIndex()) && this.path().equals(o.path());
        }
    }
    public int hashCode() {
        return 37 * (37 * (37 * (37 * (37 * (37 * (17 + "xsbti.compile.PickleData".hashCode()) + underlying().hashCode()) + fqcn().hashCode()) + java.util.Arrays.hashCode(data())) + Integer.valueOf(writeIndex()).hashCode()) + path().hashCode());
    }
    public String toString() {
        return "PickleData("  + "underlying: " + underlying() + ", " + "fqcn: " + fqcn() + ", " + "data: " + data() + ", " + "writeIndex: " + writeIndex() + ", " + "path: " + path() + ")";
    }
}
