package xsbti.compile;

/**
 * Created by krzysztr on 02/08/2016.
 */
public interface ExternalHooks {
    public static interface Lookup {}

    Lookup externalLookup();

    ClassFileManager externalClassFileManager();
}
