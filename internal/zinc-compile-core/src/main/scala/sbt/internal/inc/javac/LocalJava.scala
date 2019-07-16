/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package sbt

package internal
package inc
package javac

import java.io.{ File, InputStream, OutputStream, PrintWriter, Writer }
import java.nio.charset.Charset
import java.util.Locale

import javax.tools.JavaFileManager.Location
import javax.tools.JavaFileObject.Kind
import javax.tools.{
  FileObject,
  ForwardingJavaFileManager,
  ForwardingJavaFileObject,
  JavaFileManager,
  JavaFileObject
}
import sbt.internal.util.LoggerWriter
import sbt.util.{ Level, Logger }

import scala.util.control.NonFatal
import xsbti.{ Reporter, Logger => XLogger }
import xsbti.compile.{
  ClassFileManager,
  IncToolOptions,
  JavaCompiler => XJavaCompiler,
  Javadoc => XJavadoc
}

import scala.tools.nsc.interpreter.WriterOutputStream

/**
 * Define helper methods that will try to instantiate the Java toolchain
 * in our current class loaders. This operation may fail because different
 * JDK versions will include different Java tool chains.
 */
object LocalJava {

  /** True if we can call a forked Javadoc. */
  def hasLocalJavadoc: Boolean = javadocTool.isDefined

  private[this] val javadocClass = "com.sun.tools.javadoc.Main"

  /** Get the javadoc tool. */
  private[this] def javadocTool: Option[javax.tools.DocumentationTool] = {
    try {
      Option(javax.tools.ToolProvider.getSystemDocumentationTool)
    } catch {
      case NonFatal(_) => None
    }
  }

  /** Get the javadoc execute method reflectively from current class loader. */
  private[this] def javadocMethod = {
    try {
      // Get the class from current class loader
      val javadocClz = Class.forName(javadocClass)
      val (str, pw) = (classOf[String], classOf[PrintWriter])
      val arrStr = classOf[Array[String]]
      Option(
        // Invoke the `execute` method to run Javadoc generation
        javadocClz.getDeclaredMethod("execute", str, pw, pw, pw, str, arrStr)
      )
    } catch {
      case _ @(_: ClassNotFoundException | _: NoSuchMethodException) => None
    }
  }

  private val JavadocFailure: String =
    "Unable to reflectively invoke javadoc, class not present on the current class loader."

  private[javac] def javadoc(
      args: Array[String],
      in: InputStream,
      out: OutputStream,
      err: OutputStream
  ): Int = {
    javadocTool match {
      case Some(m) =>
        m.run(in, out, err, args: _*)
      case _ =>
        System.err.println(JavadocFailure)
        -1
    }
  }

  /** A mechanism to call the javadoc tool via reflection. */
  @deprecated("use javadoc instead", "")
  private[javac] def unsafeJavadoc(
      args: Array[String],
      err: PrintWriter,
      warn: PrintWriter,
      notice: PrintWriter
  ): Int = {
    javadocMethod match {
      case Some(m) =>
        val stdClass = "com.sun.tools.doclets.standard.Standard"
        val run = m.invoke(null, "javadoc", err, warn, notice, stdClass, args)
        run.asInstanceOf[java.lang.Integer].intValue
      case _ =>
        System.err.println(JavadocFailure)
        -1
    }
  }
}

/** Implementation of javadoc tool which attempts to run it locally (in-class). */
final class LocalJavadoc() extends XJavadoc {
  override def run(sources: Array[File],
                   options: Array[String],
                   incToolOptions: IncToolOptions,
                   reporter: Reporter,
                   log: XLogger): Boolean = {
    val cwd = new File(new File(".").getAbsolutePath).getCanonicalFile
    val nonJArgs = options.filterNot(_.startsWith("-J"))
    val allArguments = nonJArgs ++ sources.map(_.getAbsolutePath)
    val javacLogger = new JavacLogger(log, reporter, cwd)
    val errorWriter = new WriterOutputStream(new ProcessLoggerWriter(javacLogger, Level.Error))
    val infoWriter = new WriterOutputStream(new ProcessLoggerWriter(javacLogger, Level.Info))
    var exitCode = -1
    try {
      exitCode = LocalJava.javadoc(allArguments, null, infoWriter, errorWriter)
    } finally {
      errorWriter.close()
      infoWriter.close()
      javacLogger.flush("javadoc", exitCode)
    }
    // We return true or false, depending on success.
    exitCode == 0
  }
}

/**
 * Define the implementation of a Java compiler which delegates to the JVM
 * resident Java compiler.
 */
final class LocalJavaCompiler(compiler: javax.tools.JavaCompiler) extends XJavaCompiler {
  override def run(sources: Array[File],
                   options: Array[String],
                   incToolOptions: IncToolOptions,
                   reporter: Reporter,
                   log0: XLogger): Boolean = {
    val log: Logger = log0
    import collection.JavaConverters._
    val logger = new LoggerWriter(log)
    val logWriter = new PrintWriter(logger)
    log.debug("Attempting to call " + compiler + " directly...")
    val diagnostics = new DiagnosticsReporter(reporter)
    val fileManager = compiler.getStandardFileManager(diagnostics, null, null)
    val jfiles = fileManager.getJavaFileObjectsFromFiles(sources.toList.asJava)

    /* Local Java compiler doesn't accept `-J<flag>` options, strip them. */
    val (invalidOptions, cleanedOptions) = options partition (_ startsWith "-J")
    if (invalidOptions.nonEmpty) {
      log.warn("Javac is running in 'local' mode. These flags have been removed:")
      log.warn(invalidOptions.mkString("\t", ", ", ""))
    }

    val customizedFileManager = {
      val maybeClassFileManager = incToolOptions.classFileManager()
      if (incToolOptions.useCustomizedFileManager && maybeClassFileManager.isPresent)
        new WriteReportingFileManager(fileManager, maybeClassFileManager.get)
      else fileManager
    }

    var compileSuccess = false
    try {
      val success = compiler
        .getTask(logWriter,
                 customizedFileManager,
                 diagnostics,
                 cleanedOptions.toList.asJava,
                 null,
                 jfiles)
        .call()

      /* Double check success variables for the Java compiler.
       * The local compiler may report successful compilations even though
       * there have been errors (e.g. encoding problems in sources). To stick
       * to javac's behaviour, we report fail compilation from diagnostics. */
      compileSuccess = success && !diagnostics.hasErrors
    } finally {
      logger.flushLines(if (compileSuccess) Level.Warn else Level.Error)
    }
    compileSuccess
  }
}

/**
 * Track write calls through customized file manager.
 *
 * @param fileManager A manager for Java files.
 * @param classFileManager The instance that manages generated class files.
 */
final class WriteReportingFileManager(
    fileManager: JavaFileManager,
    var classFileManager: ClassFileManager
) extends ForwardingJavaFileManager[JavaFileManager](fileManager) {
  override def getJavaFileForOutput(
      location: Location,
      className: String,
      kind: Kind,
      sibling: FileObject
  ): JavaFileObject = {
    val output = super.getJavaFileForOutput(location, className, kind, sibling)
    new WriteReportingJavaFileObject(output, classFileManager)
  }

  // Fixes #185, #684
  override def isSameFile(a: FileObject, b: FileObject): Boolean = {
    def unwrap(fo: FileObject): FileObject = {
      fo match {
        case wrapper: WriteReportingJavaFileObject => wrapper.javaFileObject
        case notWrapped                            => notWrapped
      }
    }
    super.isSameFile(unwrap(a), unwrap(b))
  }
}

/**
 * Track write calls through customized file manager.
 *
 * @param javaFileObject The Java File object where output should be stored.
 * @param classFileManager The instance that manages generated class files.
 */
final class WriteReportingJavaFileObject(
    val javaFileObject: JavaFileObject,
    var classFileManager: ClassFileManager
) extends ForwardingJavaFileObject[JavaFileObject](javaFileObject) {
  override def openWriter(): Writer = {
    classFileManager.generated(Array(new File(javaFileObject.toUri)))
    super.openWriter()
  }

  override def openOutputStream(): OutputStream = {
    classFileManager.generated(Array(new File(javaFileObject.toUri)))
    super.openOutputStream()
  }
}
