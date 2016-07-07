package sbt
package internal
package inc

import scala.util.Try
import java.io.File
import sbt.util.Logger
import Logger.o2m
import xsbti.Maybe
import xsbti.compile.{ FileWatch, FileChanges }

import scala.collection.JavaConverters._
import scala.collection.mutable

private[sbt] object NoFileWatch extends FileWatch {
  override def productChanges(since: Long): Maybe[FileChanges] = o2m(None)
  override def sourceChanges(since: Long): Maybe[FileChanges] = o2m(None)
  override def binaryChanges(since: Long): Maybe[FileChanges] = o2m(None)
}

abstract class NativeFileWatch(sourcesToWatch: List[File], log: Logger) extends FileWatch with AutoCloseable {
  override def productChanges(since: Long): Maybe[FileChanges] = o2m(None)
  override def sourceChanges(since: Long): Maybe[FileChanges] =
    synchronized {
      log.debug(s"[filewatch] sourceChanges - since: $since")
      val t = System.currentTimeMillis
      val result = if (since < trackTime) None
      else {
        val xs = sourceEvents.toList filter { case (_, t, _) => t >= since }
        Some(new FileChanges(since, t,
          (xs collect { case (f, _, FileChangeType.Create) => f }).toSet.asJava,
          (xs collect { case (f, _, FileChangeType.Delete) => f }).toSet.asJava,
          (xs collect { case (f, _, FileChangeType.Modify) => f }).toSet.asJava))
      }

      setTrackTime(t)
      o2m(result)
    }

  override def binaryChanges(since: Long): Maybe[FileChanges] = o2m(None)
  private var trackTime: Long = System.currentTimeMillis
  protected def setTrackTime(value: Long): Unit =
    {
      sourceEvents.clear()
      trackTime = value
    }
  private val sourceEvents: mutable.ListBuffer[(File, Long, FileChangeType)] = mutable.ListBuffer.empty
  protected def addSourceEvent(file0: File, t: Long, kind: FileChangeType): Unit =
    synchronized {
      // On Mac /var/folders are reported out to /private/var/folders/
      val file =
        if (file0.toString startsWith "/private/var/folders/") new File(file0.toString.replaceAll("""^\/private\/""", "/"))
        else file0
      sourceEvents.append((file, t, kind))
    }
}

/**
 * File watcher using JDK7. For Linux and Windows, there's a native implemation.
 * For OS X it will use polling.
 */
class JDK7FileWatch(sourcesToWatch: List[File], log: Logger) extends NativeFileWatch(sourcesToWatch, log) {
  import java.nio.file._
  import StandardWatchEventKinds._

  private val watcher = FileSystems.getDefault.newWatchService()
  sourcesToWatch foreach { x =>
    if (x.isDirectory) {
      x.toPath.register(watcher, Array[WatchEvent.Kind[_]](ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY),
        // This custom modifier exists just for polling implementations of the watch service, and means poll every 2 seconds.
        // For non polling event based watchers, it has no effect.
        com.sun.nio.file.SensitivityWatchEventModifier.HIGH)
    }
  }
  def init(): Unit = {
    val consumer = new Thread(makeRunnable)
    consumer.start()
  }
  private def makeRunnable: Runnable = new Runnable {
    def run: Unit = {
      var done = false
      while (!done) {
        Try { watcher.take() } map { key =>
          key.pollEvents.asScala foreach { event0 =>
            val t = System.currentTimeMillis
            event0.kind match {
              case OVERFLOW => ()
              case _ =>
                event0 match {
                  case ev: WatchEvent[Path] @unchecked =>
                    addSourceEvent(ev.context.toFile, t, FileChangeType(ev.kind))
                    log.debug("[filewatch] detected file system event: " + ev.context() + " " + ev.kind)
                }
            }
          }
          done = !key.reset
        } recover {
          case e => done = true
        }
      }
    }
  }
  init()
  override def close: Unit = {
    watcher.close
  }
}

/** File watcher for OS X. */
class BarbaryFileWatch(sourcesToWatch: List[File], log: Logger) extends NativeFileWatch(sourcesToWatch, log) {
  import com.barbarysoftware.watchservice.{ WatchService, WatchKey, WatchEvent, WatchableFile, StandardWatchEventKind }
  import com.barbarysoftware.watchservice.StandardWatchEventKind._

  private val watcher = WatchService.newWatchService
  sourcesToWatch foreach { x =>
    if (x.isDirectory) {
      val f = new WatchableFile(x)
      f.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY)
    }
  }
  def init(): Unit = {
    val consumer = new Thread(makeBarbaryRunnable)
    consumer.start()
  }
  private def makeBarbaryRunnable: Runnable = new Runnable {
    def run: Unit = {
      var done = false
      while (!done) {
        Try { watcher.take() } map { key =>
          key.pollEvents.asScala foreach { event0 =>
            val t = System.currentTimeMillis
            event0.kind match {
              case OVERFLOW => ()
              case _ =>
                event0 match {
                  case ev: WatchEvent[File] @unchecked =>
                    addSourceEvent(ev.context, t, FileChangeType(ev.kind))
                    log.debug("[filewatch] detected file system event: " + ev.context() + " " + ev.kind)
                }
            }
          }
          done = !key.reset
        } recover {
          case e => done = true
        }
      }
    }
  }
  init()
  override def close: Unit = {
    watcher.close
  }
}

sealed trait FileChangeType
object FileChangeType {
  import com.barbarysoftware.watchservice.{ WatchEvent => BWatchEvent }
  import com.barbarysoftware.watchservice.{ StandardWatchEventKind => BStandard }
  import java.nio.file.WatchEvent
  import com.barbarysoftware.watchservice.{ StandardWatchEventKind => Standard }
  def apply(value: BWatchEvent.Kind[_]): FileChangeType =
    value match {
      case BStandard.ENTRY_CREATE => Create
      case BStandard.ENTRY_DELETE => Delete
      case BStandard.ENTRY_MODIFY => Modify
    }
  def apply(value: WatchEvent.Kind[_]): FileChangeType =
    value match {
      case Standard.ENTRY_CREATE => Create
      case Standard.ENTRY_DELETE => Delete
      case Standard.ENTRY_MODIFY => Modify
    }
  case object Create extends FileChangeType
  case object Delete extends FileChangeType
  case object Modify extends FileChangeType
}
