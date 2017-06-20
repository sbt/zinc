package xsbti;

import sbt.internal.inc.ReporterManager;

import java.io.PrintStream;
import java.io.PrintWriter;

public interface ReporterUtil {
    public static ReporterConfig getDefaultReporterConfig() {
        return ReporterManager.getDefaultReporterConfig();
    }

    public static Reporter getDefault(ReporterConfig config) {
        return ReporterManager.getReporter(System.out, config);
    }

    public static Reporter getReporter(PrintWriter writer, ReporterConfig config) {
        return ReporterManager.getReporter(writer, config);
    }

    public static Reporter getReporter(PrintStream stream, ReporterConfig config) {
        return ReporterManager.getReporter(stream, config);
    }
}
