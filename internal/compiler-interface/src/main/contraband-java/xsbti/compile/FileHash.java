/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package xsbti.compile;
public final class FileHash implements java.io.Serializable {
    
    public static FileHash create(java.nio.file.Path _file, int _hash) {
        return new FileHash(_file, _hash);
    }
    public static FileHash of(java.nio.file.Path _file, int _hash) {
        return new FileHash(_file, _hash);
    }
    private java.nio.file.Path file;
    private int hash;
    protected FileHash(java.nio.file.Path _file, int _hash) {
        super();
        file = _file;
        hash = _hash;
    }
    
    public java.nio.file.Path file() {
        return this.file;
    }
    public int hash() {
        return this.hash;
    }
    public FileHash withFile(java.nio.file.Path file) {
        return new FileHash(file, hash);
    }
    public FileHash withHash(int hash) {
        return new FileHash(file, hash);
    }
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (!(obj instanceof FileHash)) {
            return false;
        } else {
            FileHash o = (FileHash)obj;
            return this.file().equals(o.file()) && (this.hash() == o.hash());
        }
    }
    public int hashCode() {
        return 37 * (37 * (37 * (17 + "xsbti.compile.FileHash".hashCode()) + file().hashCode()) + Integer.valueOf(hash()).hashCode());
    }
    public String toString() {
        return "FileHash("  + "file: " + file() + ", " + "hash: " + hash() + ")";
    }
}
