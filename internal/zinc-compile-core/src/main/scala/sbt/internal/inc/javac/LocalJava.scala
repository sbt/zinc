/*
 * Zinc - The incremental compiler for Scala.
 * Copyright Scala Center, Lightbend, and Mark Harrah
 *
 * Licensed under Apache License 2.0
 * SPDX-License-Identifier: Apache-2.0
 *
 * See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.
 */

package sbt

package internal
package inc
package javac

import java.net.{ URI, URLClassLoader }
import java.io.{ InputStream, OutputStream, OutputStreamWriter, PrintWriter, Reader, Writer }
import java.util.Locale
import java.nio.{ ByteBuffer, CharBuffer }
import java.nio.charset.{ Charset, CodingErrorAction }
import java.nio.file.{ Files, FileSystems, Path, Paths }
import javax.lang.model.element.{ Modifier, NestingKind, TypeElement, VariableElement }
import javax.lang.model.util.Elements
import com.sun.source.tree.{
  ClassTree,
  CompilationUnitTree,
  IdentifierTree,
  MemberSelectTree,
  Tree
}
import com.sun.source.util.{ JavacTask, TaskEvent, TaskListener, TreePath, TreePathScanner, Trees }
import javax.tools.JavaFileManager.Location
import javax.tools.JavaFileObject.Kind
import javax.tools.{
  FileObject,
  ForwardingJavaFileManager,
  ForwardingJavaFileObject,
  JavaFileManager,
  JavaFileObject,
  SimpleJavaFileObject,
  StandardJavaFileManager,
  StandardLocation,
  DiagnosticListener
}
import sbt.internal.util.LoggerWriter
import sbt.util.{ Level, Logger }

import scala.jdk.CollectionConverters._
import scala.util.control.NonFatal
import xsbti.{ Reporter, Logger => XLogger, PathBasedFile, VirtualFile, VirtualFileRef }
import xsbti.compile.{
  ClassFileManager,
  IncToolOptions,
  JavaCompiler => XJavaCompiler,
  Javadoc => XJavadoc,
  Output
}

/**
 * Define helper methods that will try to instantiate the Java toolchain
 * in our current class loaders. This operation may fail because different
 * JDK versions will include different Java tool chains.
 */
object LocalJava {

  /** True if we can call a forked Javadoc. */
  def hasLocalJavadoc: Boolean = javadocTool.isDefined

  private val javadocClass = "com.sun.tools.javadoc.Main"
  private val sunStandard = "com.sun.tools.doclets.standard.Standard"
  private val standardDoclet = "jdk.javadoc.doclet.StandardDoclet"

  /** Get the javadoc tool. */
  private[javac] def javadocTool: Option[javax.tools.DocumentationTool] = {
    try {
      Option(javax.tools.ToolProvider.getSystemDocumentationTool)
    } catch {
      case NonFatal(_) => None
    }
  }

  private lazy val toolsJar: Option[Path] = {
    val javaHome: Path = Paths.get(sys.props("java.home"))
    val tools0 = javaHome.resolve("lib").resolve("tools.jar")
    val tools1 = javaHome.getParent.resolve("lib").resolve("tools.jar")
    val tools2 = javaHome.resolve("jmods").resolve("jdk.javadoc.jmod")
    javaHome match {
      case _ if Files.exists(tools0) => Some(tools0)
      case _ if Files.exists(tools1) => Some(tools1)
      case _ if Files.exists(tools2) => Some(tools2)
      case _                         => None
    }
  }
  private lazy val toolsJarClassLoader: Option[URLClassLoader] =
    toolsJar map { jar =>
      new URLClassLoader(Array(jar.toUri.toURL))
    }
  private[javac] lazy val standardDocletClass: Option[Class[?]] =
    try {
      toolsJarClassLoader flatMap { cl =>
        Option(cl.loadClass(standardDoclet)): Option[Class[?]]
      }
    } catch {
      case NonFatal(_) => None
    }
  private[javac] def javadocViaTask(
      compilationUnits: Array[JavaFileObject],
      options: Array[String],
      out: PrintWriter,
      diagnosticLister: DiagnosticListener[JavaFileObject]
  ): Int = {
    (javadocTool, standardDocletClass) match {
      case (Some(m), Some(clz)) =>
        val task = m.getTask(
          out,
          null,
          diagnosticLister,
          clz,
          options.toList.asJava,
          compilationUnits.toList.asJava
        )
        if (task.call) 0
        else -1
      case _ =>
        System.err.println(JavadocFailure)
        -1
    }
  }

  private[javac] def javadocViaRun(
      args: Array[String],
      in: InputStream,
      out: OutputStream,
      err: OutputStream
  ): Int =
    javadocTool match {
      case Some(m) =>
        m.run(in, out, err, args*)
      case _ =>
        System.err.println(JavadocFailure)
        -1
    }

  /** Get the javadoc execute method reflectively from current class loader. */
  private def javadocMethod = {
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

  private[javac] val JavadocFailure: String =
    "Unable to reflectively invoke javadoc, class not present on the current class loader."

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
        val run = m.invoke(null, "javadoc", err, warn, notice, sunStandard, args)
        run.asInstanceOf[java.lang.Integer].intValue
      case _ =>
        System.err.println(JavadocFailure)
        -1
    }
  }

  private[javac] def toFileObject(vf: VirtualFile): JavaFileObject =
    new VJavaFileObject(vf, toUri(vf))
  private[javac] class VJavaFileObject(val underlying: VirtualFile, uri: URI)
      extends SimpleJavaFileObject(uri, JavaFileObject.Kind.SOURCE) {
    // println(uri.toString)
    override def openInputStream: InputStream = underlying.input
    override def getName: String = underlying.name
    override def toString: String = underlying.id
    override def getCharContent(ignoreEncodingErrors: Boolean): CharSequence = {
      val in = underlying.input
      try {
        sbt.io.IO.readStream(in)
      } finally {
        in.close()
      }
    }
  }

  private[sbt] def toUri(vf: VirtualFile): URI = new URI("vf", "tmp", s"/${vf.id}", null)

  private[sbt] def fromUri(uri: URI): VirtualFileRef =
    if (uri.getScheme != "vf") sys.error(s"invalid URI for VirtualFileRef: $uri")
    else VirtualFileRef.of(uri.getPath.stripPrefix("/"))

  // Fixes sbt/zinc#185, sbt/zinc#684, sbt/zinc#832
  private[sbt] def isSameFile(a: FileObject, b: FileObject): Boolean = {
    assert(a != null && b != null)
    def unwrap(fo: FileObject): AnyRef = {
      fo match {
        case wrapper: VJavaFileObject              => wrapper.underlying
        case wrapper: WriteReportingJavaFileObject => wrapper.javaFileObject
        case notWrapped                            => notWrapped
      }
    }
    unwrap(a) == unwrap(b)
  }
}

/** Implementation of javadoc tool which attempts to run it locally (in-class). */
final class LocalJavadoc() extends XJavadoc {
  override def run(
      sources: Array[VirtualFile],
      options: Array[String],
      output: Output,
      incToolOptions: IncToolOptions,
      reporter: Reporter,
      log: XLogger
  ): Boolean = {
    val nonJArgs = options.filterNot(_.startsWith("-J"))
    val outputOption = CompilerArguments.outputOption(output)
    val allOptions = outputOption.toArray ++ nonJArgs
    val diagnostics = new DiagnosticsReporter(reporter)
    val logger = new LoggerWriter(log)
    val logWriter = new PrintWriter(logger)
    var compileSuccess = false
    val useTask = LocalJava.standardDocletClass.isDefined
    if (useTask) {
      val jfiles = sources.toList.map(LocalJava.toFileObject)
      try {
        val exitCode = LocalJava.javadocViaTask(
          jfiles.toArray,
          allOptions,
          logWriter,
          diagnostics
        )
        compileSuccess = exitCode == 0
      } finally {
        logWriter.close()
        logger.flushLines(if (compileSuccess) Level.Warn else Level.Error)
      }
    } else {
      val cwd = Paths.get(".").toAbsolutePath
      val pathSources = sources map {
        case x: PathBasedFile => x.toPath.toAbsolutePath.toString
        case _ =>
          sys.error(
            s"falling back to javax.tools.DocumentationTool#run, which does not support virtual files but found " + sources
              .mkString(", ")
          )
      }
      val allArguments = allOptions ++ pathSources
      val javacLogger = new JavacLogger(log, reporter, cwd.toFile)
      val errorWriter = new WriterOutputStream(
        new PrintWriter(new ProcessLoggerWriter(javacLogger, Level.Error))
      )
      val infoWriter = new WriterOutputStream(
        new PrintWriter(new ProcessLoggerWriter(javacLogger, Level.Info))
      )
      var exitCode: Int = -1
      try {
        exitCode = LocalJava.javadocViaRun(allArguments, null, infoWriter, errorWriter)
      } finally {
        errorWriter.close()
        infoWriter.close()
        javacLogger.flush("javadoc", exitCode)
      }
      compileSuccess = exitCode == 0
    }
    compileSuccess
  }
}

/**
 * Define the implementation of a Java compiler which delegates to the JVM
 * resident Java compiler.
 */
final class LocalJavaCompiler(compiler: javax.tools.JavaCompiler) extends XJavaCompiler {
  override def supportsDirectToJar: Boolean = true

  override def run(
      sources: Array[VirtualFile],
      options: Array[String],
      output: Output,
      incToolOptions: IncToolOptions,
      reporter: Reporter,
      log0: XLogger
  ): Boolean =
    runWithConstantDeps(sources, options, output, incToolOptions, reporter, log0)._1

  /**
   * Like [[run]], but also returns the Java->Java dependencies on inlined `static final` constants
   * recovered from javac's attributed AST (sbt/zinc#145). javac inlines such constants and erases
   * the reference to the declaring class from the using class's bytecode, so they can only be
   * observed here. The map is keyed `usingClassBinaryName -> ownerBinaryNames`. Returned (rather
   * than stashed on the instance) so a shared compiler can serve concurrent compiles safely.
   */
  private[sbt] def runWithConstantDeps(
      sources: Array[VirtualFile],
      options: Array[String],
      output: Output,
      incToolOptions: IncToolOptions,
      reporter: Reporter,
      log0: XLogger
  ): (Boolean, Map[String, Set[String]]) = {
    val log: Logger = log0
    val logger = new LoggerWriter(log)
    val logWriter = new PrintWriter(logger)
    log.debug("Attempting to call " + compiler + " directly...")
    val diagnostics = new DiagnosticsReporter(reporter)

    /* Local Java compiler doesn't accept `-J<flag>` options, strip them. */
    val (invalidOptions, cleanedOptions) = options partition (_.startsWith("-J"))
    if (invalidOptions.nonEmpty) {
      log.warn("Javac is running in 'local' mode. These flags have been removed:")
      log.warn(invalidOptions.mkString("\t", ", ", ""))
    }

    def standardFileManager = compiler.getStandardFileManager(diagnostics, null, null)

    val (fileManager, javacOptions) = JarUtils.getOutputJar(output) match {
      case Some(outputJar) =>
        (new DirectToJarFileManager(outputJar, standardFileManager), cleanedOptions.toSeq)
      case None =>
        output.getSingleOutputAsPath match {
          case p if p.isPresent => Files.createDirectories(p.get)
          case _                =>
        }
        val fileManager = {
          if (cleanedOptions.contains("-XDuseOptimizedZip=false")) {
            fileManagerWithoutOptimizedZips(diagnostics)
          } else {
            standardFileManager
          }
        }
        val outputOption = CompilerArguments.outputOption(output)
        (fileManager, outputOption ++ cleanedOptions)
    }

    val jfiles = sources.toList.map(LocalJava.toFileObject)
    val customizedFileManager = {
      val maybeClassFileManager = incToolOptions.classFileManager()
      if (incToolOptions.useCustomizedFileManager && maybeClassFileManager.isPresent)
        new WriteReportingFileManager(fileManager, maybeClassFileManager.get)
      else
        new SameFileFixFileManager(fileManager)
    }

    var compileSuccess = false
    var constantDeps: Map[String, Set[String]] = Map.empty
    try {
      // sbt/zinc#145: collect Java->Java dependencies on inlined `static final` constants from
      // javac's attributed AST, which retains the reference that the emitted bytecode erases.
      val deps = new JavaConstantDeps
      val task = compiler.getTask(
        logWriter,
        customizedFileManager,
        diagnostics,
        javacOptions.toList.asJava,
        null,
        jfiles.asJava
      )
      try task match {
          case jt: JavacTask => jt.addTaskListener(new ConstantDepListener(jt, deps))
          case _             => () // not the system javac; constant deps are simply not tracked
        }
      catch {
        case NonFatal(e) => log.debug("Could not install constant-dependency listener: " + e)
      }
      val success = task.call()
      constantDeps = deps.result

      /* Double check success variables for the Java compiler.
       * The local compiler may report successful compilations even though
       * there have been errors (e.g. encoding problems in sources). To stick
       * to javac's behaviour, we report fail compilation from diagnostics. */
      compileSuccess = success && !diagnostics.hasErrors
    } finally {
      customizedFileManager.close()
      logger.flushLines(if (compileSuccess) Level.Warn else Level.Error)
    }
    (compileSuccess, constantDeps)
  }

  /**
   * Rewrite of [[javax.tools.JavaCompiler.getStandardFileManager]] method that also sets
   * useOptimizedZip=false flag. With forked javac adding this option to arguments just works.
   * Here, as `FileManager` is created before `CompilationTask` options do not get passed
   * properly. Also there is no access to `com.sun.tools.javac` classes, hence the reflection...
   */
  private[sbt] def fileManagerWithoutOptimizedZips(
      diagnostics: DiagnosticsReporter
  ): StandardJavaFileManager = {
    val classLoader = compiler.getClass.getClassLoader
    val contextClass = Class.forName("com.sun.tools.javac.util.Context", true, classLoader)
    val optionsClass = Class.forName("com.sun.tools.javac.util.Options", true, classLoader)
    val javacFileManagerClass =
      Class.forName("com.sun.tools.javac.file.JavacFileManager", true, classLoader)

    val `Options.instance` = optionsClass.getMethod("instance", contextClass)
    val `context.put` = contextClass.getMethod("put", classOf[Class[?]], classOf[Object])
    val `options.put` = optionsClass.getMethod("put", classOf[String], classOf[String])
    val `new JavacFileManager` =
      javacFileManagerClass.getConstructor(contextClass, classOf[Boolean], classOf[Charset])

    val context = contextClass.getDeclaredConstructor().newInstance().asInstanceOf[AnyRef]
    `context.put`.invoke(context, classOf[Locale], null)
    `context.put`.invoke(context, classOf[DiagnosticListener[?]], diagnostics)
    val options = `Options.instance`.invoke(null, context)
    `options.put`.invoke(options, "useOptimizedZip", "false")

    `new JavacFileManager`
      .newInstance(context, Boolean.box(true), null)
      .asInstanceOf[StandardJavaFileManager]
  }
}

final class SameFileFixFileManager(underlying: JavaFileManager)
    extends ForwardingJavaFileManager[JavaFileManager](underlying) {
  override def isSameFile(a: FileObject, b: FileObject): Boolean = LocalJava.isSameFile(a, b)
}

/**
 * Mutable accumulator for Java->Java dependencies on inlined `static final` constants (sbt/zinc#145).
 * Keys and values are JVM binary names (e.g. `pkg.Outer$Inner`), matching what `JavaAnalyze` records.
 */
private[sbt] final class JavaConstantDeps {
  private val deps =
    scala.collection.mutable.Map.empty[String, scala.collection.mutable.Set[String]]
  def add(from: String, on: String): Unit =
    deps.getOrElseUpdate(from, scala.collection.mutable.Set.empty[String]) += on
  def result: Map[String, Set[String]] =
    deps.iterator.map { case (k, v) => k -> v.toSet }.toMap
}

/**
 * Registers [[ConstantDepScanner]] on each top-level class once it has been attributed (the
 * `ANALYZE` task event), when resolved symbols — including references to inlined constants — are
 * available. Failures are swallowed so analysis never fails the compile.
 */
private[sbt] final class ConstantDepListener(task: JavacTask, sink: JavaConstantDeps)
    extends TaskListener {
  private val trees = Trees.instance(task)
  private val elements = task.getElements

  override def started(e: TaskEvent): Unit = ()

  override def finished(e: TaskEvent): Unit =
    if (e.getKind == TaskEvent.Kind.ANALYZE) {
      val te = e.getTypeElement
      if (te != null)
        try {
          val path = trees.getPath(te)
          if (path != null) new ConstantDepScanner(trees, elements, sink).scan(path, null)
        } catch { case NonFatal(_) => () }
    }
}

/**
 * Walks an attributed Java AST and records, for every reference that resolves to a compile-time
 * constant field (`VariableElement.getConstantValue != null`), an edge from the enclosing class to
 * the class that declares the constant (the value source, taken from the resolved element so
 * `import static` references are captured too) and, for a qualified reference, to the qualifier type
 * when it differs from the declaring class.
 */
private[sbt] final class ConstantDepScanner(
    trees: Trees,
    elements: Elements,
    sink: JavaConstantDeps
) extends TreePathScanner[Void, Void] {

  private def record(qualifier: Option[Tree]): Unit =
    try {
      val path = getCurrentPath
      trees.getElement(path) match {
        case v: VariableElement if v.getConstantValue != null =>
          enclosingTypeBinaryName(path).foreach { from =>
            // the class that declares the constant — a change to its value must recompile `from`
            v.getEnclosingElement match {
              case owner: TypeElement => addEdge(from, owner)
              case _                  => ()
            }
            // sbt/zinc#145: when the constant is named through a subtype rather than its declaring
            // class (`Sub.K`, or a bare `K` brought in by `import static p.Sub.K` / `p.Sub.*`, where
            // K is inherited from Base), javac erases both Base and Sub from the bytecode. Record the
            // named (qualifier) type(s) too, so a change to one (it gains its own K, or changes its
            // inheritance) recompiles `from` as well.
            namedTypes(path, qualifier, v).foreach(addEdge(from, _))
          }
        case _ => ()
      }
    } catch { case NonFatal(_) => () }

  /** The type(s) named at the reference site: a member-select qualifier, or static-import types. */
  private def namedTypes(
      path: TreePath,
      qualifier: Option[Tree],
      field: VariableElement
  ): List[TypeElement] =
    qualifier match {
      case Some(q) =>
        trees.getElement(new TreePath(path, q)) match {
          case te: TypeElement => te :: Nil
          case _               => Nil
        }
      case None => staticImportTypes(path.getCompilationUnit, field)
    }

  /**
   * Every statically-imported type that actually contributes `field` — covering both explicit
   * (`import static p.Sub.K`) and on-demand (`import static p.Sub.*`) imports. The import must name
   * the field (or be a wildcard) AND the type must actually have it (`getAllMembers`, which includes
   * inherited members): the name guard rejects an unrelated `import static p.Sub.M` (which would
   * otherwise match just because `Sub` also inherits `K`), and the membership guard rejects a
   * same-named import of a *different* constant.
   */
  private def staticImportTypes(
      cu: CompilationUnitTree,
      field: VariableElement
  ): List[TypeElement] = {
    val cuPath = new TreePath(cu)
    cu.getImports.asScala.iterator
      .filter(_.isStatic)
      .flatMap { imp =>
        // both `import static p.Sub.K` and `import static p.Sub.*` name the type in `getExpression`
        imp.getQualifiedIdentifier match {
          case ms: MemberSelectTree
              if ms.getIdentifier.contentEquals("*") ||
                ms.getIdentifier.contentEquals(field.getSimpleName) =>
            val typePath =
              new TreePath(new TreePath(new TreePath(cuPath, imp), ms), ms.getExpression)
            trees.getElement(typePath) match {
              case te: TypeElement if elements.getAllMembers(te).contains(field) => Some(te)
              case _                                                             => None
            }
          case _ => None
        }
      }
      .toList
  }

  private def addEdge(from: String, to: TypeElement): Unit = {
    val on = elements.getBinaryName(to).toString
    if (from != on) sink.add(from, on)
  }

  private def enclosingTypeBinaryName(path: TreePath): Option[String] = {
    var p = path
    while (p != null) {
      p.getLeaf match {
        case _: ClassTree =>
          return trees.getElement(p) match {
            case te: TypeElement => Some(elements.getBinaryName(te).toString)
            case _               => None
          }
        case _ => ()
      }
      p = p.getParentPath
    }
    None
  }

  override def visitIdentifier(node: IdentifierTree, p: Void): Void = {
    record(None)
    super.visitIdentifier(node, p)
  }

  override def visitMemberSelect(node: MemberSelectTree, p: Void): Void = {
    record(Some(node.getExpression))
    super.visitMemberSelect(node, p)
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

  override def isSameFile(a: FileObject, b: FileObject): Boolean = LocalJava.isSameFile(a, b)
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
    classFileManager.generated(
      Array[VirtualFile](PlainVirtualFile(Paths.get(javaFileObject.toUri)))
    )
    super.openWriter()
  }

  override def openOutputStream(): OutputStream = {
    classFileManager.generated(
      Array[VirtualFile](PlainVirtualFile(Paths.get(javaFileObject.toUri)))
    )
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

final class DirectToJarFileManager(
    outputJar: Path,
    delegate: JavaFileManager
) extends ForwardingJavaFileManager[JavaFileManager](delegate) {
  private val jarFs = {
    val uri = URI.create("jar:file:" + outputJar.toUri.getPath)
    def newFs() = FileSystems.newFileSystem(uri, Map("create" -> "true").asJava)
    // work around java 8 bug which results in ZipFileSystem being initially registered with a null key on the provider
    newFs().close()
    newFs()
  }
  private val jarRoot = jarFs.getRootDirectories.iterator.next()

  override def getFileForOutput(
      location: JavaFileManager.Location,
      packageName: String,
      relativeName: String,
      sibling: FileObject
  ): FileObject =
    if (location == StandardLocation.CLASS_OUTPUT) {
      val packagePath = packageName.replace('.', '/')
      val filePath = jarRoot.resolve(packagePath).resolve(relativeName)
      new DirectToJarFileObject(filePath, filePath.toUri)
    } else super.getFileForOutput(location, packageName, relativeName, sibling)

  override def getJavaFileForOutput(
      location: JavaFileManager.Location,
      className: String,
      kind: JavaFileObject.Kind,
      sibling: FileObject
  ): JavaFileObject =
    if (location == StandardLocation.CLASS_OUTPUT) {
      val relativeFilePath = className.replace('.', '/') + kind.extension
      val filePath = jarRoot.resolve(relativeFilePath)
      new DirectToJarJavaFileObject(filePath, filePath.toUri, kind)
    } else super.getJavaFileForOutput(location, className, kind, sibling)

  override def isSameFile(a: FileObject, b: FileObject): Boolean = a == b

  override def close(): Unit = {
    // super also holds a reference to outputJar, so we need to close it before closing jarFs
    super.close()
    jarFs.close()
  }
}

class DirectToJarFileObject(path: Path, uri: URI) extends FileObject {
  override def getName: String = path.toString
  override def toUri: URI = uri

  override def getLastModified: Long = 0L

  override def getCharContent(ignoreEncodingErrors: Boolean): CharSequence =
    throw new UnsupportedOperationException
  override def openInputStream(): InputStream =
    throw new UnsupportedOperationException
  override def openReader(ignoreEncodingErrors: Boolean): Reader =
    throw new UnsupportedOperationException

  override def openOutputStream(): OutputStream = {
    Files.createDirectories(path.getParent)
    Files.newOutputStream(path)
  }
  override def openWriter(): Writer = new OutputStreamWriter(openOutputStream())

  override def delete(): Boolean = false
}

final class DirectToJarJavaFileObject(path: Path, uri: URI, kind: JavaFileObject.Kind)
    extends DirectToJarFileObject(path, uri)
    with JavaFileObject {

  override def getKind: JavaFileObject.Kind = kind

  override def isNameCompatible(simpleName: String, kind: JavaFileObject.Kind): Boolean = {
    val baseName = s"$simpleName${kind.extension}"
    kind == this.kind && (path.toString == baseName || path.endsWith(baseName))
  }

  override def getNestingKind: NestingKind = null
  override def getAccessLevel: Modifier = null
}
