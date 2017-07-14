/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package xsbti.compile;
public final class FileHash implements java.io.Serializable {
    
    
    private java.io.File file;
    private int hash;
    public FileHash(java.io.File _file, int _hash) {
        super();
        file = _file;
        hash = _hash;
    }
    public java.io.File file() {
        return this.file;
    }
    public int hash() {
        return this.hash;
    }
    public FileHash withFile(java.io.File file) {
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
            return file().equals(o.file()) && (hash() == o.hash());
        }
    }
    public int hashCode() {
        return 37 * (37 * (37 * (17 + "FileHash".hashCode()) + file().hashCode()) + (new Integer(hash())).hashCode());
    }
    public String toString() {
        return "FileHash("  + "file: " + file() + ", " + "hash: " + hash() + ")";
    }
}
