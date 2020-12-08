package xsbti.compile;

/**
 * Can be added to `IncOptions.auxiliaryClassFiles` so that the sjsir files
 * produced by the Scala.js compiler are managed by the ClassFileManager.
 */
public class ScalaJSFiles extends AuxiliaryClassFileExtension {
    private static final ScalaJSFiles _instance = new ScalaJSFiles();

    public static ScalaJSFiles instance() {
        return _instance;
    }

    private ScalaJSFiles() {
        super("sjsir");
    }
}