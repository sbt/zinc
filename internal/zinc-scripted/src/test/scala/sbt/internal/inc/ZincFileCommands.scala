package sbt.internal.inc

import java.io.File
import java.nio.file.Paths

import sbt.internal.scripted.FileCommands
import sbt.io.IO

class ZincFileCommands(baseDirectory: File) extends FileCommands(baseDirectory) {
  override def commandMap: Map[String, List[String] => Unit] = {
    super.commandMap + {
      "pause" noArg {
        // Redefine pause not to use `System.console`, which is too restrictive
        println(s"Pausing in $baseDirectory (press enter to continue)")
        scala.io.StdIn.readLine()
        println("Restarting the execution.")
      }
    }
  }

  override def absent(paths: List[String]): Unit = {
    val present = paths.filter(exists)
    if (present.nonEmpty)
      scriptError("File(s) existed: " + present.mkString("[ ", " , ", " ]"))
  }

  override def newer(a: String, b: String): Unit = {
    val isNewer = exists(a) && (!exists(b) || getModifiedTimeOrZero(a) > getModifiedTimeOrZero(b))
    if (!isNewer) {
      scriptError(s"$a is not newer than $b")
    }
  }

  override def exists(paths: List[String]): Unit = {
    val notPresent = paths.filterNot(exists)
    if (notPresent.nonEmpty) {
      scriptError("File(s) did not exist: " + notPresent.mkString("[ ", " , ", " ]"))
    }
  }

  private def exists(path: String): Boolean = {
    pathFold(path)(_.exists(), JarUtils.exists)(_ || _)
  }

  private def getModifiedTimeOrZero(path: String): Long = {
    pathFold(path)(IO.getModifiedTimeOrZero, JarUtils.readModifiedTime)(_ max _)
  }

  /**
   * Folds over representations of path (analogously to Either#fold).
   * Path can be a path to regular file or a jared class.
   *
   * This method is pretty hacky but makes scripted tests work with
   * Straight to Jar compilation and without changing assertions there.
   * The path will always point to actual file, but this method will
   * attempt to run a function for both plain file and jared class
   * and then decide which result is relevant with `combine` function.
   *
   * As an example, it will convert path "target/classes/C.class" to
   *   "/tmp/sbt_535fddcd/target/classes/a/b/c/C.class"
   * as well as to
   *   "/tmp/sbt_535fddcd/target/classes/output.jar!a/b/c/C.class"
   * and run functions on them, e.g. checking if one of those files exists.
   */
  private def pathFold[A](path: String)(
      transformPlain: File => A,
      transformJared: JarUtils.ClassInJar => A
  )(combine: (A, A) => A): A = {
    val jaredRes = {
      val relBasePath = "target/classes"
      IO.relativize(new File(relBasePath), new File(path)).map { relClass =>
        val jar = Paths.get(baseDirectory.toString, relBasePath, "output.jar").toFile
        transformJared(JarUtils.ClassInJar(jar, relClass))
      }
    }
    val regularRes = transformPlain(fromString(path))
    jaredRes.map(combine(_, regularRes)).getOrElse(regularRes)
  }

}
