/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package xsbti.compile;
/**
 * Represents all configuration options for the incremental compiler itself and
 * not the underlying Java/Scala compiler.
 */
public final class IncOptions implements java.io.Serializable {
    public static int defaultTransitiveStep() {
        return 3;
    }
    public static double defaultRecompileAllFraction() {
        return 0.5;
    }
    public static boolean defaultRelationsDebug() {
        return false;
    }
    public static boolean defaultApiDebug() {
        return false;
    }
    public static int defaultApiDiffContextSize() {
        return 5;
    }
    public static java.util.Optional<java.io.File> defaultApiDumpDirectory() {
        return java.util.Optional.empty();
    }
    public static java.util.Optional<ClassFileManagerType> defaultClassFileManagerType() {
        return java.util.Optional.empty();
    }
    public static java.util.Optional<Boolean> defaultRecompileOnMacroDef() {
        return java.util.Optional.empty();
    }
    public static boolean defaultUseOptimizedSealed() {
        return false;
    }
    public static boolean defaultRecompileOnMacroDefImpl() {
        return true;
    }
    public static boolean getRecompileOnMacroDef(IncOptions options) {
        if (options.recompileOnMacroDef().isPresent()) {
            return options.recompileOnMacroDef().get();
        } else {
            return defaultRecompileOnMacroDefImpl();
        }
    }
    public static boolean defaultUseCustomizedFileManager() {
        return false;
    }
    public static boolean defaultStoreApis() {
        return true;
    }
    public static boolean defaultEnabled() {
        return true;
    }
    public static java.util.Map<String, String> defaultExtra() {
        return new java.util.HashMap<String, String>();
    }
    public static ExternalHooks defaultExternal() {
        return new DefaultExternalHooks(java.util.Optional.empty(), java.util.Optional.empty());
    }
    public static boolean defaultLogRecompileOnMacro() {
        return true;
    }
    public static IncOptions create() {
        return new IncOptions();
    }
    public static IncOptions of() {
        return new IncOptions();
    }
    public static IncOptions create(int _transitiveStep, double _recompileAllFraction, boolean _relationsDebug, boolean _apiDebug, int _apiDiffContextSize, java.util.Optional<java.io.File> _apiDumpDirectory, java.util.Optional<ClassFileManagerType> _classfileManagerType, boolean _useCustomizedFileManager, java.util.Optional<Boolean> _recompileOnMacroDef, boolean _useOptimizedSealed, boolean _storeApis, boolean _enabled, java.util.Map<String,String> _extra, boolean _logRecompileOnMacro, xsbti.compile.ExternalHooks _externalHooks) {
        return new IncOptions(_transitiveStep, _recompileAllFraction, _relationsDebug, _apiDebug, _apiDiffContextSize, _apiDumpDirectory, _classfileManagerType, _useCustomizedFileManager, _recompileOnMacroDef, _useOptimizedSealed, _storeApis, _enabled, _extra, _logRecompileOnMacro, _externalHooks);
    }
    public static IncOptions of(int _transitiveStep, double _recompileAllFraction, boolean _relationsDebug, boolean _apiDebug, int _apiDiffContextSize, java.util.Optional<java.io.File> _apiDumpDirectory, java.util.Optional<ClassFileManagerType> _classfileManagerType, boolean _useCustomizedFileManager, java.util.Optional<Boolean> _recompileOnMacroDef, boolean _useOptimizedSealed, boolean _storeApis, boolean _enabled, java.util.Map<String,String> _extra, boolean _logRecompileOnMacro, xsbti.compile.ExternalHooks _externalHooks) {
        return new IncOptions(_transitiveStep, _recompileAllFraction, _relationsDebug, _apiDebug, _apiDiffContextSize, _apiDumpDirectory, _classfileManagerType, _useCustomizedFileManager, _recompileOnMacroDef, _useOptimizedSealed, _storeApis, _enabled, _extra, _logRecompileOnMacro, _externalHooks);
    }
    /** After which step include whole transitive closure of invalidated source files. */
    private int transitiveStep;
    /**
     * What's the fraction of invalidated source files when we switch to recompiling
     * all files and giving up incremental compilation altogether. That's useful in
     * cases when probability that we end up recompiling most of source files but
     * in multiple steps is high. Multi-step incremental recompilation is slower
     * than recompiling everything in one step.
     */
    private double recompileAllFraction;
    /** Print very detailed information about relations, such as dependencies between source files. */
    private boolean relationsDebug;
    /** Enable tools for debugging API changes. */
    private boolean apiDebug;
    /**
     * Controls context size (in lines) displayed when diffs are produced for textual API
     * representation.
     * 
     * This option is used only when `apiDebug == true`.
     */
    private int apiDiffContextSize;
    /**
     * The directory where we dump textual representation of APIs. This method might be called
     * only if apiDebug returns true. This is unused option at the moment as the needed functionality
     * is not implemented yet.
     */
    private java.util.Optional<java.io.File> apiDumpDirectory;
    /** ClassfileManager that will handle class file deletion and addition during a single incremental compilation run. */
    private java.util.Optional<ClassFileManagerType> classfileManagerType;
    /**
     * Option to turn on customized file manager that tracks generated class files for transactional rollbacks.
     * Using customized file manager may conflict with some libraries, this option allows user to decide
     * whether to use.
     */
    private boolean useCustomizedFileManager;
    /**
     * Determines whether incremental compiler should recompile all dependencies of a file
     * that contains a macro definition.
     */
    private java.util.Optional<Boolean> recompileOnMacroDef;
    /**
     * Determines whether optimized approach for invalidating sealed classes/trait is used.
     * Turning this on may cause undercompilation in case of macros that are based sealed
     * trait/class children enumeration.
     */
    private boolean useOptimizedSealed;
    /** Determines whether incremental compiler stores apis alongside analysis. */
    private boolean storeApis;
    /** Determines whether incremental compilation is enabled. */
    private boolean enabled;
    /** Extra options */
    private java.util.Map<String,String> extra;
    /** Determines whether to log information on file recompiled due to a transitive macro change */
    private boolean logRecompileOnMacro;
    /** External hooks that allows clients e.g. IDEs to interacts with compilation internals */
    private xsbti.compile.ExternalHooks externalHooks;
    protected IncOptions() {
        super();
        transitiveStep = xsbti.compile.IncOptions.defaultTransitiveStep();
        recompileAllFraction = xsbti.compile.IncOptions.defaultRecompileAllFraction();
        relationsDebug = xsbti.compile.IncOptions.defaultRelationsDebug();
        apiDebug = xsbti.compile.IncOptions.defaultApiDebug();
        apiDiffContextSize = xsbti.compile.IncOptions.defaultApiDiffContextSize();
        apiDumpDirectory = xsbti.compile.IncOptions.defaultApiDumpDirectory();
        classfileManagerType = xsbti.compile.IncOptions.defaultClassFileManagerType();
        useCustomizedFileManager = xsbti.compile.IncOptions.defaultUseOptimizedSealed();
        recompileOnMacroDef = xsbti.compile.IncOptions.defaultRecompileOnMacroDef();
        useOptimizedSealed = xsbti.compile.IncOptions.defaultUseOptimizedSealed();
        storeApis = xsbti.compile.IncOptions.defaultStoreApis();
        enabled = xsbti.compile.IncOptions.defaultEnabled();
        extra = xsbti.compile.IncOptions.defaultExtra();
        logRecompileOnMacro = xsbti.compile.IncOptions.defaultLogRecompileOnMacro();
        externalHooks = xsbti.compile.IncOptions.defaultExternal();
    }
    protected IncOptions(int _transitiveStep, double _recompileAllFraction, boolean _relationsDebug, boolean _apiDebug, int _apiDiffContextSize, java.util.Optional<java.io.File> _apiDumpDirectory, java.util.Optional<ClassFileManagerType> _classfileManagerType, boolean _useCustomizedFileManager, java.util.Optional<Boolean> _recompileOnMacroDef, boolean _useOptimizedSealed, boolean _storeApis, boolean _enabled, java.util.Map<String,String> _extra, boolean _logRecompileOnMacro, xsbti.compile.ExternalHooks _externalHooks) {
        super();
        transitiveStep = _transitiveStep;
        recompileAllFraction = _recompileAllFraction;
        relationsDebug = _relationsDebug;
        apiDebug = _apiDebug;
        apiDiffContextSize = _apiDiffContextSize;
        apiDumpDirectory = _apiDumpDirectory;
        classfileManagerType = _classfileManagerType;
        useCustomizedFileManager = _useCustomizedFileManager;
        recompileOnMacroDef = _recompileOnMacroDef;
        useOptimizedSealed = _useOptimizedSealed;
        storeApis = _storeApis;
        enabled = _enabled;
        extra = _extra;
        logRecompileOnMacro = _logRecompileOnMacro;
        externalHooks = _externalHooks;
    }
    public int transitiveStep() {
        return this.transitiveStep;
    }
    public double recompileAllFraction() {
        return this.recompileAllFraction;
    }
    public boolean relationsDebug() {
        return this.relationsDebug;
    }
    public boolean apiDebug() {
        return this.apiDebug;
    }
    public int apiDiffContextSize() {
        return this.apiDiffContextSize;
    }
    public java.util.Optional<java.io.File> apiDumpDirectory() {
        return this.apiDumpDirectory;
    }
    public java.util.Optional<ClassFileManagerType> classfileManagerType() {
        return this.classfileManagerType;
    }
    public boolean useCustomizedFileManager() {
        return this.useCustomizedFileManager;
    }
    public java.util.Optional<Boolean> recompileOnMacroDef() {
        return this.recompileOnMacroDef;
    }
    public boolean useOptimizedSealed() {
        return this.useOptimizedSealed;
    }
    public boolean storeApis() {
        return this.storeApis;
    }
    public boolean enabled() {
        return this.enabled;
    }
    public java.util.Map<String,String> extra() {
        return this.extra;
    }
    public boolean logRecompileOnMacro() {
        return this.logRecompileOnMacro;
    }
    public xsbti.compile.ExternalHooks externalHooks() {
        return this.externalHooks;
    }
    public IncOptions withTransitiveStep(int transitiveStep) {
        return new IncOptions(transitiveStep, recompileAllFraction, relationsDebug, apiDebug, apiDiffContextSize, apiDumpDirectory, classfileManagerType, useCustomizedFileManager, recompileOnMacroDef, useOptimizedSealed, storeApis, enabled, extra, logRecompileOnMacro, externalHooks);
    }
    public IncOptions withRecompileAllFraction(double recompileAllFraction) {
        return new IncOptions(transitiveStep, recompileAllFraction, relationsDebug, apiDebug, apiDiffContextSize, apiDumpDirectory, classfileManagerType, useCustomizedFileManager, recompileOnMacroDef, useOptimizedSealed, storeApis, enabled, extra, logRecompileOnMacro, externalHooks);
    }
    public IncOptions withRelationsDebug(boolean relationsDebug) {
        return new IncOptions(transitiveStep, recompileAllFraction, relationsDebug, apiDebug, apiDiffContextSize, apiDumpDirectory, classfileManagerType, useCustomizedFileManager, recompileOnMacroDef, useOptimizedSealed, storeApis, enabled, extra, logRecompileOnMacro, externalHooks);
    }
    public IncOptions withApiDebug(boolean apiDebug) {
        return new IncOptions(transitiveStep, recompileAllFraction, relationsDebug, apiDebug, apiDiffContextSize, apiDumpDirectory, classfileManagerType, useCustomizedFileManager, recompileOnMacroDef, useOptimizedSealed, storeApis, enabled, extra, logRecompileOnMacro, externalHooks);
    }
    public IncOptions withApiDiffContextSize(int apiDiffContextSize) {
        return new IncOptions(transitiveStep, recompileAllFraction, relationsDebug, apiDebug, apiDiffContextSize, apiDumpDirectory, classfileManagerType, useCustomizedFileManager, recompileOnMacroDef, useOptimizedSealed, storeApis, enabled, extra, logRecompileOnMacro, externalHooks);
    }
    public IncOptions withApiDumpDirectory(java.util.Optional<java.io.File> apiDumpDirectory) {
        return new IncOptions(transitiveStep, recompileAllFraction, relationsDebug, apiDebug, apiDiffContextSize, apiDumpDirectory, classfileManagerType, useCustomizedFileManager, recompileOnMacroDef, useOptimizedSealed, storeApis, enabled, extra, logRecompileOnMacro, externalHooks);
    }
    public IncOptions withClassfileManagerType(java.util.Optional<ClassFileManagerType> classfileManagerType) {
        return new IncOptions(transitiveStep, recompileAllFraction, relationsDebug, apiDebug, apiDiffContextSize, apiDumpDirectory, classfileManagerType, useCustomizedFileManager, recompileOnMacroDef, useOptimizedSealed, storeApis, enabled, extra, logRecompileOnMacro, externalHooks);
    }
    public IncOptions withUseCustomizedFileManager(boolean useCustomizedFileManager) {
        return new IncOptions(transitiveStep, recompileAllFraction, relationsDebug, apiDebug, apiDiffContextSize, apiDumpDirectory, classfileManagerType, useCustomizedFileManager, recompileOnMacroDef, useOptimizedSealed, storeApis, enabled, extra, logRecompileOnMacro, externalHooks);
    }
    public IncOptions withRecompileOnMacroDef(java.util.Optional<Boolean> recompileOnMacroDef) {
        return new IncOptions(transitiveStep, recompileAllFraction, relationsDebug, apiDebug, apiDiffContextSize, apiDumpDirectory, classfileManagerType, useCustomizedFileManager, recompileOnMacroDef, useOptimizedSealed, storeApis, enabled, extra, logRecompileOnMacro, externalHooks);
    }
    public IncOptions withUseOptimizedSealed(boolean useOptimizedSealed) {
        return new IncOptions(transitiveStep, recompileAllFraction, relationsDebug, apiDebug, apiDiffContextSize, apiDumpDirectory, classfileManagerType, useCustomizedFileManager, recompileOnMacroDef, useOptimizedSealed, storeApis, enabled, extra, logRecompileOnMacro, externalHooks);
    }
    public IncOptions withStoreApis(boolean storeApis) {
        return new IncOptions(transitiveStep, recompileAllFraction, relationsDebug, apiDebug, apiDiffContextSize, apiDumpDirectory, classfileManagerType, useCustomizedFileManager, recompileOnMacroDef, useOptimizedSealed, storeApis, enabled, extra, logRecompileOnMacro, externalHooks);
    }
    public IncOptions withEnabled(boolean enabled) {
        return new IncOptions(transitiveStep, recompileAllFraction, relationsDebug, apiDebug, apiDiffContextSize, apiDumpDirectory, classfileManagerType, useCustomizedFileManager, recompileOnMacroDef, useOptimizedSealed, storeApis, enabled, extra, logRecompileOnMacro, externalHooks);
    }
    public IncOptions withExtra(java.util.Map<String,String> extra) {
        return new IncOptions(transitiveStep, recompileAllFraction, relationsDebug, apiDebug, apiDiffContextSize, apiDumpDirectory, classfileManagerType, useCustomizedFileManager, recompileOnMacroDef, useOptimizedSealed, storeApis, enabled, extra, logRecompileOnMacro, externalHooks);
    }
    public IncOptions withLogRecompileOnMacro(boolean logRecompileOnMacro) {
        return new IncOptions(transitiveStep, recompileAllFraction, relationsDebug, apiDebug, apiDiffContextSize, apiDumpDirectory, classfileManagerType, useCustomizedFileManager, recompileOnMacroDef, useOptimizedSealed, storeApis, enabled, extra, logRecompileOnMacro, externalHooks);
    }
    public IncOptions withExternalHooks(xsbti.compile.ExternalHooks externalHooks) {
        return new IncOptions(transitiveStep, recompileAllFraction, relationsDebug, apiDebug, apiDiffContextSize, apiDumpDirectory, classfileManagerType, useCustomizedFileManager, recompileOnMacroDef, useOptimizedSealed, storeApis, enabled, extra, logRecompileOnMacro, externalHooks);
    }
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (!(obj instanceof IncOptions)) {
            return false;
        } else {
            IncOptions o = (IncOptions)obj;
            return (transitiveStep() == o.transitiveStep()) && (recompileAllFraction() == o.recompileAllFraction()) && (relationsDebug() == o.relationsDebug()) && (apiDebug() == o.apiDebug()) && (apiDiffContextSize() == o.apiDiffContextSize()) && apiDumpDirectory().equals(o.apiDumpDirectory()) && classfileManagerType().equals(o.classfileManagerType()) && (useCustomizedFileManager() == o.useCustomizedFileManager()) && recompileOnMacroDef().equals(o.recompileOnMacroDef()) && (useOptimizedSealed() == o.useOptimizedSealed()) && (storeApis() == o.storeApis()) && (enabled() == o.enabled()) && extra().equals(o.extra()) && (logRecompileOnMacro() == o.logRecompileOnMacro()) && externalHooks().equals(o.externalHooks());
        }
    }
    public int hashCode() {
        return 37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (17 + "xsbti.compile.IncOptions".hashCode()) + (new Integer(transitiveStep())).hashCode()) + (new Double(recompileAllFraction())).hashCode()) + (new Boolean(relationsDebug())).hashCode()) + (new Boolean(apiDebug())).hashCode()) + (new Integer(apiDiffContextSize())).hashCode()) + apiDumpDirectory().hashCode()) + classfileManagerType().hashCode()) + (new Boolean(useCustomizedFileManager())).hashCode()) + recompileOnMacroDef().hashCode()) + (new Boolean(useOptimizedSealed())).hashCode()) + (new Boolean(storeApis())).hashCode()) + (new Boolean(enabled())).hashCode()) + extra().hashCode()) + (new Boolean(logRecompileOnMacro())).hashCode()) + externalHooks().hashCode());
    }
    public String toString() {
        return "IncOptions("  + "transitiveStep: " + transitiveStep() + ", " + "recompileAllFraction: " + recompileAllFraction() + ", " + "relationsDebug: " + relationsDebug() + ", " + "apiDebug: " + apiDebug() + ", " + "apiDiffContextSize: " + apiDiffContextSize() + ", " + "apiDumpDirectory: " + apiDumpDirectory() + ", " + "classfileManagerType: " + classfileManagerType() + ", " + "useCustomizedFileManager: " + useCustomizedFileManager() + ", " + "recompileOnMacroDef: " + recompileOnMacroDef() + ", " + "useOptimizedSealed: " + useOptimizedSealed() + ", " + "storeApis: " + storeApis() + ", " + "enabled: " + enabled() + ", " + "extra: " + extra() + ", " + "logRecompileOnMacro: " + logRecompileOnMacro() + ", " + "externalHooks: " + externalHooks() + ")";
    }
}
