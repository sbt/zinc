/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

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

    public static Reporter getReporter(xsbti.Logger logger, ReporterConfig config) {
        return ReporterManager.getReporter(logger, config);
    }
}
