/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package xsbti.compile;
/** Configures incremental recompilation. */
public final class Setup implements java.io.Serializable {
    public java.io.File cacheFile() {
        return this.cachePath.toFile();
    }
    public Setup withCacheFile(java.io.File cacheFile) {
        return this.withCachePath(cacheFile.toPath());
    }
    public static Setup create(xsbti.compile.PerClasspathEntryLookup _perClasspathEntryLookup, boolean _skip, java.io.File _cacheFile, xsbti.compile.GlobalsCache _cache, xsbti.compile.IncOptions _incrementalCompilerOptions, xsbti.Reporter _reporter, java.util.Optional<xsbti.compile.CompileProgress> _progress, xsbti.T2<String, String>[] _extra) {
        return new Setup(_perClasspathEntryLookup, _skip, _cacheFile.toPath(), _cache, _incrementalCompilerOptions, _reporter, _progress, _extra);
    }
    public static Setup of(xsbti.compile.PerClasspathEntryLookup _perClasspathEntryLookup, boolean _skip, java.io.File _cacheFile, xsbti.compile.GlobalsCache _cache, xsbti.compile.IncOptions _incrementalCompilerOptions, xsbti.Reporter _reporter, java.util.Optional<xsbti.compile.CompileProgress> _progress, xsbti.T2<String, String>[] _extra) {
        return new Setup(_perClasspathEntryLookup, _skip, _cacheFile.toPath(), _cache, _incrementalCompilerOptions, _reporter, _progress, _extra);
    }
    public static Setup create(xsbti.compile.PerClasspathEntryLookup _perClasspathEntryLookup, boolean _skip, java.nio.file.Path _cachePath, xsbti.compile.GlobalsCache _cache, xsbti.compile.IncOptions _incrementalCompilerOptions, xsbti.Reporter _reporter, xsbti.T2<String, String>[] _extra) {
        return new Setup(_perClasspathEntryLookup, _skip, _cachePath, _cache, _incrementalCompilerOptions, _reporter, _extra);
    }
    public static Setup of(xsbti.compile.PerClasspathEntryLookup _perClasspathEntryLookup, boolean _skip, java.nio.file.Path _cachePath, xsbti.compile.GlobalsCache _cache, xsbti.compile.IncOptions _incrementalCompilerOptions, xsbti.Reporter _reporter, xsbti.T2<String, String>[] _extra) {
        return new Setup(_perClasspathEntryLookup, _skip, _cachePath, _cache, _incrementalCompilerOptions, _reporter, _extra);
    }
    public static Setup create(xsbti.compile.PerClasspathEntryLookup _perClasspathEntryLookup, boolean _skip, java.nio.file.Path _cachePath, xsbti.compile.GlobalsCache _cache, xsbti.compile.IncOptions _incrementalCompilerOptions, xsbti.Reporter _reporter, java.util.Optional<xsbti.compile.CompileProgress> _progress, xsbti.T2<String, String>[] _extra) {
        return new Setup(_perClasspathEntryLookup, _skip, _cachePath, _cache, _incrementalCompilerOptions, _reporter, _progress, _extra);
    }
    public static Setup of(xsbti.compile.PerClasspathEntryLookup _perClasspathEntryLookup, boolean _skip, java.nio.file.Path _cachePath, xsbti.compile.GlobalsCache _cache, xsbti.compile.IncOptions _incrementalCompilerOptions, xsbti.Reporter _reporter, java.util.Optional<xsbti.compile.CompileProgress> _progress, xsbti.T2<String, String>[] _extra) {
        return new Setup(_perClasspathEntryLookup, _skip, _cachePath, _cache, _incrementalCompilerOptions, _reporter, _progress, _extra);
    }
    public static Setup create(xsbti.compile.PerClasspathEntryLookup _perClasspathEntryLookup, boolean _skip, java.nio.file.Path _cachePath, xsbti.compile.GlobalsCache _cache, xsbti.compile.IncOptions _incrementalCompilerOptions, xsbti.Reporter _reporter, xsbti.compile.CompileProgress _progress, xsbti.T2<String, String>[] _extra) {
        return new Setup(_perClasspathEntryLookup, _skip, _cachePath, _cache, _incrementalCompilerOptions, _reporter, _progress, _extra);
    }
    public static Setup of(xsbti.compile.PerClasspathEntryLookup _perClasspathEntryLookup, boolean _skip, java.nio.file.Path _cachePath, xsbti.compile.GlobalsCache _cache, xsbti.compile.IncOptions _incrementalCompilerOptions, xsbti.Reporter _reporter, xsbti.compile.CompileProgress _progress, xsbti.T2<String, String>[] _extra) {
        return new Setup(_perClasspathEntryLookup, _skip, _cachePath, _cache, _incrementalCompilerOptions, _reporter, _progress, _extra);
    }
    public static Setup create(xsbti.compile.PerClasspathEntryLookup _perClasspathEntryLookup, boolean _skip, java.nio.file.Path _cachePath, xsbti.compile.GlobalsCache _cache, xsbti.compile.IncOptions _incrementalCompilerOptions, xsbti.Reporter _reporter, java.util.Optional<xsbti.compile.CompileProgress> _progress, java.util.Optional<xsbti.compile.AnalysisStore> _earlyAnalysisStore, xsbti.T2<String, String>[] _extra) {
        return new Setup(_perClasspathEntryLookup, _skip, _cachePath, _cache, _incrementalCompilerOptions, _reporter, _progress, _earlyAnalysisStore, _extra);
    }
    public static Setup of(xsbti.compile.PerClasspathEntryLookup _perClasspathEntryLookup, boolean _skip, java.nio.file.Path _cachePath, xsbti.compile.GlobalsCache _cache, xsbti.compile.IncOptions _incrementalCompilerOptions, xsbti.Reporter _reporter, java.util.Optional<xsbti.compile.CompileProgress> _progress, java.util.Optional<xsbti.compile.AnalysisStore> _earlyAnalysisStore, xsbti.T2<String, String>[] _extra) {
        return new Setup(_perClasspathEntryLookup, _skip, _cachePath, _cache, _incrementalCompilerOptions, _reporter, _progress, _earlyAnalysisStore, _extra);
    }
    public static Setup create(xsbti.compile.PerClasspathEntryLookup _perClasspathEntryLookup, boolean _skip, java.nio.file.Path _cachePath, xsbti.compile.GlobalsCache _cache, xsbti.compile.IncOptions _incrementalCompilerOptions, xsbti.Reporter _reporter, xsbti.compile.CompileProgress _progress, xsbti.compile.AnalysisStore _earlyAnalysisStore, xsbti.T2<String, String>[] _extra) {
        return new Setup(_perClasspathEntryLookup, _skip, _cachePath, _cache, _incrementalCompilerOptions, _reporter, _progress, _earlyAnalysisStore, _extra);
    }
    public static Setup of(xsbti.compile.PerClasspathEntryLookup _perClasspathEntryLookup, boolean _skip, java.nio.file.Path _cachePath, xsbti.compile.GlobalsCache _cache, xsbti.compile.IncOptions _incrementalCompilerOptions, xsbti.Reporter _reporter, xsbti.compile.CompileProgress _progress, xsbti.compile.AnalysisStore _earlyAnalysisStore, xsbti.T2<String, String>[] _extra) {
        return new Setup(_perClasspathEntryLookup, _skip, _cachePath, _cache, _incrementalCompilerOptions, _reporter, _progress, _earlyAnalysisStore, _extra);
    }
    private xsbti.compile.PerClasspathEntryLookup perClasspathEntryLookup;
    private boolean skip;
    private java.nio.file.Path cachePath;
    private xsbti.compile.GlobalsCache cache;
    private xsbti.compile.IncOptions incrementalCompilerOptions;
    private xsbti.Reporter reporter;
    private java.util.Optional<xsbti.compile.CompileProgress> progress;
    private java.util.Optional<xsbti.compile.AnalysisStore> earlyAnalysisStore;
    private xsbti.T2<String, String>[] extra;
    protected Setup(xsbti.compile.PerClasspathEntryLookup _perClasspathEntryLookup, boolean _skip, java.nio.file.Path _cachePath, xsbti.compile.GlobalsCache _cache, xsbti.compile.IncOptions _incrementalCompilerOptions, xsbti.Reporter _reporter, xsbti.T2<String, String>[] _extra) {
        super();
        perClasspathEntryLookup = _perClasspathEntryLookup;
        skip = _skip;
        cachePath = _cachePath;
        cache = _cache;
        incrementalCompilerOptions = _incrementalCompilerOptions;
        reporter = _reporter;
        progress = java.util.Optional.<xsbti.compile.CompileProgress>empty();
        earlyAnalysisStore = java.util.Optional.<xsbti.compile.AnalysisStore>empty();
        extra = _extra;
    }
    protected Setup(xsbti.compile.PerClasspathEntryLookup _perClasspathEntryLookup, boolean _skip, java.nio.file.Path _cachePath, xsbti.compile.GlobalsCache _cache, xsbti.compile.IncOptions _incrementalCompilerOptions, xsbti.Reporter _reporter, java.util.Optional<xsbti.compile.CompileProgress> _progress, xsbti.T2<String, String>[] _extra) {
        super();
        perClasspathEntryLookup = _perClasspathEntryLookup;
        skip = _skip;
        cachePath = _cachePath;
        cache = _cache;
        incrementalCompilerOptions = _incrementalCompilerOptions;
        reporter = _reporter;
        progress = _progress;
        earlyAnalysisStore = java.util.Optional.<xsbti.compile.AnalysisStore>empty();
        extra = _extra;
    }
    protected Setup(xsbti.compile.PerClasspathEntryLookup _perClasspathEntryLookup, boolean _skip, java.nio.file.Path _cachePath, xsbti.compile.GlobalsCache _cache, xsbti.compile.IncOptions _incrementalCompilerOptions, xsbti.Reporter _reporter, xsbti.compile.CompileProgress _progress, xsbti.T2<String, String>[] _extra) {
        super();
        perClasspathEntryLookup = _perClasspathEntryLookup;
        skip = _skip;
        cachePath = _cachePath;
        cache = _cache;
        incrementalCompilerOptions = _incrementalCompilerOptions;
        reporter = _reporter;
        progress = java.util.Optional.<xsbti.compile.CompileProgress>ofNullable(_progress);
        earlyAnalysisStore = java.util.Optional.<xsbti.compile.AnalysisStore>empty();
        extra = _extra;
    }
    protected Setup(xsbti.compile.PerClasspathEntryLookup _perClasspathEntryLookup, boolean _skip, java.nio.file.Path _cachePath, xsbti.compile.GlobalsCache _cache, xsbti.compile.IncOptions _incrementalCompilerOptions, xsbti.Reporter _reporter, java.util.Optional<xsbti.compile.CompileProgress> _progress, java.util.Optional<xsbti.compile.AnalysisStore> _earlyAnalysisStore, xsbti.T2<String, String>[] _extra) {
        super();
        perClasspathEntryLookup = _perClasspathEntryLookup;
        skip = _skip;
        cachePath = _cachePath;
        cache = _cache;
        incrementalCompilerOptions = _incrementalCompilerOptions;
        reporter = _reporter;
        progress = _progress;
        earlyAnalysisStore = _earlyAnalysisStore;
        extra = _extra;
    }
    protected Setup(xsbti.compile.PerClasspathEntryLookup _perClasspathEntryLookup, boolean _skip, java.nio.file.Path _cachePath, xsbti.compile.GlobalsCache _cache, xsbti.compile.IncOptions _incrementalCompilerOptions, xsbti.Reporter _reporter, xsbti.compile.CompileProgress _progress, xsbti.compile.AnalysisStore _earlyAnalysisStore, xsbti.T2<String, String>[] _extra) {
        super();
        perClasspathEntryLookup = _perClasspathEntryLookup;
        skip = _skip;
        cachePath = _cachePath;
        cache = _cache;
        incrementalCompilerOptions = _incrementalCompilerOptions;
        reporter = _reporter;
        progress = java.util.Optional.<xsbti.compile.CompileProgress>ofNullable(_progress);
        earlyAnalysisStore = java.util.Optional.<xsbti.compile.AnalysisStore>ofNullable(_earlyAnalysisStore);
        extra = _extra;
    }
    /** Provides a lookup of data structures and operations associated with a single classpath entry. */
    public xsbti.compile.PerClasspathEntryLookup perClasspathEntryLookup() {
        return this.perClasspathEntryLookup;
    }
    /** If true, no sources are actually compiled and the Analysis from the previous compilation is returned. */
    public boolean skip() {
        return this.skip;
    }
    /**
     * The file used to cache information across compilations.
     * This file can be removed to force a full recompilation.
     * The file should be unique and not shared between compilations.
     */
    public java.nio.file.Path cachePath() {
        return this.cachePath;
    }
    public xsbti.compile.GlobalsCache cache() {
        return this.cache;
    }
    public xsbti.compile.IncOptions incrementalCompilerOptions() {
        return this.incrementalCompilerOptions;
    }
    /** The reporter that should be used to report scala compilation to. */
    public xsbti.Reporter reporter() {
        return this.reporter;
    }
    /** Optionally provide progress indication. */
    public java.util.Optional<xsbti.compile.CompileProgress> progress() {
        return this.progress;
    }
    /** Store early Analysis during pipeline compilation. */
    public java.util.Optional<xsbti.compile.AnalysisStore> earlyAnalysisStore() {
        return this.earlyAnalysisStore;
    }
    public xsbti.T2<String, String>[] extra() {
        return this.extra;
    }
    public Setup withPerClasspathEntryLookup(xsbti.compile.PerClasspathEntryLookup perClasspathEntryLookup) {
        return new Setup(perClasspathEntryLookup, skip, cachePath, cache, incrementalCompilerOptions, reporter, progress, earlyAnalysisStore, extra);
    }
    public Setup withSkip(boolean skip) {
        return new Setup(perClasspathEntryLookup, skip, cachePath, cache, incrementalCompilerOptions, reporter, progress, earlyAnalysisStore, extra);
    }
    public Setup withCachePath(java.nio.file.Path cachePath) {
        return new Setup(perClasspathEntryLookup, skip, cachePath, cache, incrementalCompilerOptions, reporter, progress, earlyAnalysisStore, extra);
    }
    public Setup withCache(xsbti.compile.GlobalsCache cache) {
        return new Setup(perClasspathEntryLookup, skip, cachePath, cache, incrementalCompilerOptions, reporter, progress, earlyAnalysisStore, extra);
    }
    public Setup withIncrementalCompilerOptions(xsbti.compile.IncOptions incrementalCompilerOptions) {
        return new Setup(perClasspathEntryLookup, skip, cachePath, cache, incrementalCompilerOptions, reporter, progress, earlyAnalysisStore, extra);
    }
    public Setup withReporter(xsbti.Reporter reporter) {
        return new Setup(perClasspathEntryLookup, skip, cachePath, cache, incrementalCompilerOptions, reporter, progress, earlyAnalysisStore, extra);
    }
    public Setup withProgress(java.util.Optional<xsbti.compile.CompileProgress> progress) {
        return new Setup(perClasspathEntryLookup, skip, cachePath, cache, incrementalCompilerOptions, reporter, progress, earlyAnalysisStore, extra);
    }
    public Setup withProgress(xsbti.compile.CompileProgress progress) {
        return new Setup(perClasspathEntryLookup, skip, cachePath, cache, incrementalCompilerOptions, reporter, java.util.Optional.<xsbti.compile.CompileProgress>ofNullable(progress), earlyAnalysisStore, extra);
    }
    public Setup withEarlyAnalysisStore(java.util.Optional<xsbti.compile.AnalysisStore> earlyAnalysisStore) {
        return new Setup(perClasspathEntryLookup, skip, cachePath, cache, incrementalCompilerOptions, reporter, progress, earlyAnalysisStore, extra);
    }
    public Setup withEarlyAnalysisStore(xsbti.compile.AnalysisStore earlyAnalysisStore) {
        return new Setup(perClasspathEntryLookup, skip, cachePath, cache, incrementalCompilerOptions, reporter, progress, java.util.Optional.<xsbti.compile.AnalysisStore>ofNullable(earlyAnalysisStore), extra);
    }
    public Setup withExtra(xsbti.T2<String, String>[] extra) {
        return new Setup(perClasspathEntryLookup, skip, cachePath, cache, incrementalCompilerOptions, reporter, progress, earlyAnalysisStore, extra);
    }
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (!(obj instanceof Setup)) {
            return false;
        } else {
            Setup o = (Setup)obj;
            return this.perClasspathEntryLookup().equals(o.perClasspathEntryLookup()) && (this.skip() == o.skip()) && this.cachePath().equals(o.cachePath()) && this.cache().equals(o.cache()) && this.incrementalCompilerOptions().equals(o.incrementalCompilerOptions()) && this.reporter().equals(o.reporter()) && this.progress().equals(o.progress()) && this.earlyAnalysisStore().equals(o.earlyAnalysisStore()) && java.util.Arrays.deepEquals(this.extra(), o.extra());
        }
    }
    public int hashCode() {
        return 37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (17 + "xsbti.compile.Setup".hashCode()) + perClasspathEntryLookup().hashCode()) + Boolean.valueOf(skip()).hashCode()) + cachePath().hashCode()) + cache().hashCode()) + incrementalCompilerOptions().hashCode()) + reporter().hashCode()) + progress().hashCode()) + earlyAnalysisStore().hashCode()) + java.util.Arrays.deepHashCode(extra()));
    }
    public String toString() {
        return "Setup("  + "perClasspathEntryLookup: " + perClasspathEntryLookup() + ", " + "skip: " + skip() + ", " + "cachePath: " + cachePath() + ", " + "cache: " + cache() + ", " + "incrementalCompilerOptions: " + incrementalCompilerOptions() + ", " + "reporter: " + reporter() + ", " + "progress: " + progress() + ", " + "earlyAnalysisStore: " + earlyAnalysisStore() + ", " + "extra: " + extra() + ")";
    }
}
