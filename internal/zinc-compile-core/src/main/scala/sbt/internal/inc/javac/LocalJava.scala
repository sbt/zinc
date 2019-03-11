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
import java.util.Locale
import java.nio.{ ByteBuffer, CharBuffer }
import java.nio.charset.{ Charset, CodingErrorAction }

import javax.tools.JavaFileManager.Location
import javax.tools.JavaFileObject.Kind
import javax.tools.{
  FileObject,
  ForwardingJavaFileManager,
  ForwardingJavaFileObject,
  JavaFileManager,
  JavaFileObject,
  StandardJavaFileManager,
  DiagnosticListener
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
    val errorWriter = new WriterOutputStream(
      new PrintWriter(new ProcessLoggerWriter(javacLogger, Level.Error)))
    val infoWriter = new WriterOutputStream(
      new PrintWriter(new ProcessLoggerWriter(javacLogger, Level.Info)))
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

    /* Local Java compiler doesn't accept `-J<flag>` options, strip them. */
    val (invalidOptions, cleanedOptions) = options partition (_ startsWith "-J")
    if (invalidOptions.nonEmpty) {
      log.warn("Javac is running in 'local' mode. These flags have been removed:")
      log.warn(invalidOptions.mkString("\t", ", ", ""))
    }

    val fileManager = {
      if (cleanedOptions.contains("-XDuseOptimizedZip=false")) {
        fileManagerWithoutOptimizedZips(diagnostics)
      } else {
        compiler.getStandardFileManager(diagnostics, null, null)
      }
    }

    val jfiles = fileManager.getJavaFileObjectsFromFiles(sources.toList.asJava)
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
      customizedFileManager.close()
      logger.flushLines(if (compileSuccess) Level.Warn else Level.Error)
    }
    compileSuccess
  }

  /**
   * Rewrite of [[javax.tools.JavaCompiler.getStandardFileManager]] method that also sets
   * useOptimizedZip=false flag. With forked javac adding this option to arguments just works.
   * Here, as `FileManager` is created before `CompilationTask` options do not get passed
   * properly. Also there is no access to `com.sun.tools.javac` classes, hence the reflection...
   */
  private def fileManagerWithoutOptimizedZips(
      diagnostics: DiagnosticsReporter): StandardJavaFileManager = {
    val classLoader = compiler.getClass.getClassLoader
    val contextClass = Class.forName("com.sun.tools.javac.util.Context", true, classLoader)
    val optionsClass = Class.forName("com.sun.tools.javac.util.Options", true, classLoader)
    val javacFileManagerClass =
      Class.forName("com.sun.tools.javac.file.JavacFileManager", true, classLoader)

    val `Options.instance` = optionsClass.getMethod("instance", contextClass)
    val `context.put` = contextClass.getMethod("put", classOf[Class[_]], classOf[Object])
    val `options.put` = optionsClass.getMethod("put", classOf[String], classOf[String])
    val `new JavacFileManager` =
      javacFileManagerClass.getConstructor(contextClass, classOf[Boolean], classOf[Charset])

    val context = contextClass.getDeclaredConstructor().newInstance().asInstanceOf[AnyRef]
    `context.put`.invoke(context, classOf[Locale], null)
    `context.put`.invoke(context, classOf[DiagnosticListener[_]], diagnostics)
    val options = `Options.instance`.invoke(null, context)
    `options.put`.invoke(options, "useOptimizedZip", "false")

    `new JavacFileManager`
      .newInstance(context, Boolean.box(true), null)
      .asInstanceOf[StandardJavaFileManager]
  }
}

/**
 * Track write calls through customized file manager.
 *
 * @param fileManager A manager for Java files.
 * @param classFileManager The instance that manages generated class files.
 *
 * @note `getFileForOutput` used by the annotation process for writing
 *       resources cannot be overridden because of a Javac SDK check.
 *       JDK8 has a hard-coded check against it that impedes wrapping
 *       `RegularFileObject` with other instances, e.g. `ForwardingFileObject`.
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
}

/**
 * Track write calls through customized file manager.
 *
 * @param javaFileObject The Java File object where output should be stored.
 * @param classFileManager The instance that manages generated class files.
 */
final class WriteReportingJavaFileObject(
    javaFileObject: JavaFileObject,
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

/**
 * Converts Writer to OutputStream
 * Code adapted from scala.tools.nsc.interpreter.WriterOutputStream
 */
class WriterOutputStream(writer: Writer) extends OutputStream {
  private val decoder = Charset.defaultCharset.newDecoder
  decoder.onMalformedInput(CodingErrorAction.REPLACE)
  decoder.onUnmappableCharacter(CodingErrorAction.REPLACE)

  private val byteBuffer = ByteBuffer.allocate(64)
  private val charBuffer = CharBuffer.allocate(64)

  override def write(b: Int): Unit = {
    byteBuffer.put(b.toByte)
    byteBuffer.flip()
    decoder.decode(byteBuffer, charBuffer, /*endOfInput=*/ false)
    if (byteBuffer.remaining == 0) byteBuffer.clear()
    if (charBuffer.position() > 0) {
      charBuffer.flip()
      writer.write(charBuffer.toString)
      charBuffer.clear()
    }
    ()
  }
  override def close(): Unit = {
    decoder.decode(byteBuffer, charBuffer, /*endOfInput=*/ true)
    decoder.flush(charBuffer)
    ()
  }
  override def toString: String = charBuffer.toString
}
