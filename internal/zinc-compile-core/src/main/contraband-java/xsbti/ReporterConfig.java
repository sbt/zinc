/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package xsbti;
public final class ReporterConfig implements java.io.Serializable {
    
    public static ReporterConfig create(String _loggerName, int _maximumErrors, boolean _useColor, java.util.function.Function<String, Boolean>[] _msgFilters, java.util.function.Function<java.nio.file.Path, Boolean>[] _fileFilters, java.util.logging.Level _logLevel, java.util.function.Function<Position, Position> _positionMapper) {
        return new ReporterConfig(_loggerName, _maximumErrors, _useColor, _msgFilters, _fileFilters, _logLevel, _positionMapper);
    }
    public static ReporterConfig of(String _loggerName, int _maximumErrors, boolean _useColor, java.util.function.Function<String, Boolean>[] _msgFilters, java.util.function.Function<java.nio.file.Path, Boolean>[] _fileFilters, java.util.logging.Level _logLevel, java.util.function.Function<Position, Position> _positionMapper) {
        return new ReporterConfig(_loggerName, _maximumErrors, _useColor, _msgFilters, _fileFilters, _logLevel, _positionMapper);
    }
    
    private String loggerName;
    private int maximumErrors;
    private boolean useColor;
    private java.util.function.Function<String, Boolean>[] msgFilters;
    private java.util.function.Function<java.nio.file.Path, Boolean>[] fileFilters;
    private java.util.logging.Level logLevel;
    private java.util.function.Function<Position, Position> positionMapper;
    protected ReporterConfig(String _loggerName, int _maximumErrors, boolean _useColor, java.util.function.Function<String, Boolean>[] _msgFilters, java.util.function.Function<java.nio.file.Path, Boolean>[] _fileFilters, java.util.logging.Level _logLevel, java.util.function.Function<Position, Position> _positionMapper) {
        super();
        loggerName = _loggerName;
        maximumErrors = _maximumErrors;
        useColor = _useColor;
        msgFilters = _msgFilters;
        fileFilters = _fileFilters;
        logLevel = _logLevel;
        positionMapper = _positionMapper;
    }
    public String loggerName() {
        return this.loggerName;
    }
    public int maximumErrors() {
        return this.maximumErrors;
    }
    public boolean useColor() {
        return this.useColor;
    }
    public java.util.function.Function<String, Boolean>[] msgFilters() {
        return this.msgFilters;
    }
    public java.util.function.Function<java.nio.file.Path, Boolean>[] fileFilters() {
        return this.fileFilters;
    }
    public java.util.logging.Level logLevel() {
        return this.logLevel;
    }
    public java.util.function.Function<Position, Position> positionMapper() {
        return this.positionMapper;
    }
    public ReporterConfig withLoggerName(String loggerName) {
        return new ReporterConfig(loggerName, maximumErrors, useColor, msgFilters, fileFilters, logLevel, positionMapper);
    }
    public ReporterConfig withMaximumErrors(int maximumErrors) {
        return new ReporterConfig(loggerName, maximumErrors, useColor, msgFilters, fileFilters, logLevel, positionMapper);
    }
    public ReporterConfig withUseColor(boolean useColor) {
        return new ReporterConfig(loggerName, maximumErrors, useColor, msgFilters, fileFilters, logLevel, positionMapper);
    }
    public ReporterConfig withMsgFilters(java.util.function.Function<String, Boolean>[] msgFilters) {
        return new ReporterConfig(loggerName, maximumErrors, useColor, msgFilters, fileFilters, logLevel, positionMapper);
    }
    public ReporterConfig withFileFilters(java.util.function.Function<java.nio.file.Path, Boolean>[] fileFilters) {
        return new ReporterConfig(loggerName, maximumErrors, useColor, msgFilters, fileFilters, logLevel, positionMapper);
    }
    public ReporterConfig withLogLevel(java.util.logging.Level logLevel) {
        return new ReporterConfig(loggerName, maximumErrors, useColor, msgFilters, fileFilters, logLevel, positionMapper);
    }
    public ReporterConfig withPositionMapper(java.util.function.Function<Position, Position> positionMapper) {
        return new ReporterConfig(loggerName, maximumErrors, useColor, msgFilters, fileFilters, logLevel, positionMapper);
    }
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (!(obj instanceof ReporterConfig)) {
            return false;
        } else {
            ReporterConfig o = (ReporterConfig)obj;
            return this.loggerName().equals(o.loggerName()) && (this.maximumErrors() == o.maximumErrors()) && (this.useColor() == o.useColor()) && java.util.Arrays.deepEquals(this.msgFilters(), o.msgFilters()) && java.util.Arrays.deepEquals(this.fileFilters(), o.fileFilters()) && this.logLevel().equals(o.logLevel()) && this.positionMapper().equals(o.positionMapper());
        }
    }
    public int hashCode() {
        return 37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (17 + "xsbti.ReporterConfig".hashCode()) + loggerName().hashCode()) + (new Integer(maximumErrors())).hashCode()) + (new Boolean(useColor())).hashCode()) + java.util.Arrays.deepHashCode(msgFilters())) + java.util.Arrays.deepHashCode(fileFilters())) + logLevel().hashCode()) + positionMapper().hashCode());
    }
    public String toString() {
        return "ReporterConfig("  + "loggerName: " + loggerName() + ", " + "maximumErrors: " + maximumErrors() + ", " + "useColor: " + useColor() + ", " + "msgFilters: " + msgFilters() + ", " + "fileFilters: " + fileFilters() + ", " + "logLevel: " + logLevel() + ", " + "positionMapper: " + positionMapper() + ")";
    }
}
