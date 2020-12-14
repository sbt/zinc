package xsbti.compile;

/**
 * Can be added to `IncOptions.auxiliaryClassFiles` so that the TASTy files are
 * managed by the ClassFileManager.
 */
public class TastyFiles extends AuxiliaryClassFileExtension {
    private static final TastyFiles _instance = new TastyFiles();

    public static TastyFiles instance() {
        return _instance;
    }

    private TastyFiles() {
        super("tasty");
    }
}
