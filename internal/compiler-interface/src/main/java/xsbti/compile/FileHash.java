package xsbti.compile;

import java.nio.ByteBuffer;

public final class FileHash implements java.io.Serializable {

    /**
     * @deprecated The use of this method is strongly discouraged. Please use {@link #hash64()}'s long variant.
     */
    @Deprecated public static FileHash create(java.io.File _file, int _hash) {
        return of(_file, _hash);
    }

    private static byte[] toByteArray(int _hash) {
        ByteBuffer buffer = ByteBuffer.allocate(1);
        buffer.putInt(_hash);
        return buffer.array();
    }

    /**
     * @deprecated The use of this method is strongly discouraged. Please use {@link #hash64()}'s long variant.
     */
    @Deprecated public static FileHash of(java.io.File _file, int _hash) {
        return new FileHash(_file, toByteArray(_hash));
    }

    public static FileHash create(java.io.File _file, byte[] _hash) {
        return of(_file, _hash);
    }

    public static FileHash of(java.io.File _file, byte[] _hash) {
        return new FileHash(_file, _hash);
    }

    private java.io.File file;
    private byte[] hash;

    protected FileHash(java.io.File _file, byte[] _hash) {
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
        return this.hash[0];
    }

    public byte[] hash64() {
        return this.hash;
    }

    public FileHash withFile(java.io.File file) {
        return new FileHash(file, hash);
    }

    /**
     * @deprecated The use of this method is strongly discouraged. Please use {@link #hash64()}.
     */
    @Deprecated public FileHash withHash(int hash) {
        return new FileHash(file, toByteArray(hash));
    }

    public FileHash withHash(byte[] hash) {
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
        return 37 * (37 * (37 * (17 + "xsbti.compile.FileHash".hashCode()) + file().hashCode()) + hash64().hashCode());
    }

    public String toString() {
        return "FileHash("  + "file: " + file() + ", " + "hash: " + hash64() + ")";
    }
}
