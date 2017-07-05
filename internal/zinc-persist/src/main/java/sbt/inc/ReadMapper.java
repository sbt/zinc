package sbt.inc;

/**
 * Defines a reader-only mapper interface that is used by Zinc before read
 * the contents of the analysis files from the persistent storage.
 *
 * This interface is useful to make the analysis file machine-independent and
 * allow third parties to distribute these files around.
 */
public interface ReadMapper extends GenericMapper {}
