package xsbti.compile;

public final class FileHash implements java.io.Serializable {

    /**
     * @deprecated The use of this method is strongly discouraged. Please use {@link #hash64()}'s long variant.
     */
    @Deprecated public static FileHash create(java.io.File _file, int _hash) {
        return new FileHash(_file, (long) _hash);
    }

    /**
     * @deprecated The use of this method is strongly discouraged. Please use {@link #hash64()}'s long variant.
     */
    @Deprecated public static FileHash of(java.io.File _file, int _hash) {
        return new FileHash(_file, (long) _hash);
    }

    public static FileHash create(java.io.File _file, long _hash) {
        return new FileHash(_file, _hash);
    }

    public static FileHash of(java.io.File _file, long _hash) {
        return new FileHash(_file, _hash);
    }

    private java.io.File file;
    private long hash;

    protected FileHash(java.io.File _file, long _hash) {
        super();
        file = _file;
        hash = _hash;
    }

    public java.io.File file() {
        return this.file;
    }

    /**
     * @deprecated The use of this method is strongly discouraged. Please use {@link #hash64()}.
     * @return A truncated 32-byte hash from a 64-byte hash.
     */
    @Deprecated public int hash() {
        return (int) this.hash;
    }

    public long hash64() {
        return this.hash;
    }

    public FileHash withFile(java.io.File file) {
        return new FileHash(file, hash);
    }

    /**
     * @deprecated The use of this method is strongly discouraged. Please use {@link #hash64()}.
     */
    @Deprecated public FileHash withHash(int hash) {
        return new FileHash(file, (long) hash);
    }

    public FileHash withHash(long hash) {
        return new FileHash(file, hash);
    }

    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (!(obj instanceof FileHash)) {
            return false;
        } else {
            FileHash o = (FileHash)obj;
            return file().equals(o.file()) && (hash64() == o.hash64());
        }
    }

    public int hashCode() {
        return 37 * (37 * (37 * (17 + "xsbti.compile.FileHash".hashCode()) + file().hashCode()) + (new Long(hash64())).hashCode());
    }

    public String toString() {
        return "FileHash("  + "file: " + file() + ", " + "hash: " + hash64() + ")";
    }
}
