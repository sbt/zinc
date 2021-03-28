package xsbti.compile;

/**
 * Can be added to `IncOptions.auxiliaryClassFiles` so that the nir files
 * produced by the Scala Native compiler are managed by the ClassFileManager.
 */
public class ScalaNativeFiles extends AuxiliaryClassFileExtension {
    private static final ScalaNativeFiles _instance = new ScalaNativeFiles();

    public static ScalaNativeFiles instance() {
        return _instance;
    }

    private ScalaNativeFiles() {
        super("nir");
    }
}