/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package xsbti.compile;
/**
 * Constructs a transactional ClassFileManager implementation that restores class
 * files to the way they were before compilation if there is an error. Otherwise, it
 * keeps the successfully generated class files from the new compilation.
 */
public final class TransactionalManagerType extends xsbti.compile.ClassFileManagerType implements java.io.Serializable {
    
    public static TransactionalManagerType create(java.io.File _backupDirectory, xsbti.Logger _logger) {
        return new TransactionalManagerType(_backupDirectory, _logger);
    }
    public static TransactionalManagerType of(java.io.File _backupDirectory, xsbti.Logger _logger) {
        return new TransactionalManagerType(_backupDirectory, _logger);
    }
    private java.io.File backupDirectory;
    private xsbti.Logger logger;
    protected TransactionalManagerType(java.io.File _backupDirectory, xsbti.Logger _logger) {
        super();
        backupDirectory = _backupDirectory;
        logger = _logger;
    }
    
    public java.io.File backupDirectory() {
        return this.backupDirectory;
    }
    public xsbti.Logger logger() {
        return this.logger;
    }
    public TransactionalManagerType withBackupDirectory(java.io.File backupDirectory) {
        return new TransactionalManagerType(backupDirectory, logger);
    }
    public TransactionalManagerType withLogger(xsbti.Logger logger) {
        return new TransactionalManagerType(backupDirectory, logger);
    }
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (!(obj instanceof TransactionalManagerType)) {
            return false;
        } else {
            TransactionalManagerType o = (TransactionalManagerType)obj;
            return this.backupDirectory().equals(o.backupDirectory()) && this.logger().equals(o.logger());
        }
    }
    public int hashCode() {
        return 37 * (37 * (37 * (17 + "xsbti.compile.TransactionalManagerType".hashCode()) + backupDirectory().hashCode()) + logger().hashCode());
    }
    public String toString() {
        return "TransactionalManagerType("  + "backupDirectory: " + backupDirectory() + ", " + "logger: " + logger() + ")";
    }
}
