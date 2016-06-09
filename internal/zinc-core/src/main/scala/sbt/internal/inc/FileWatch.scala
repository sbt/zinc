package sbt
package internal
package inc

import scala.util.Try
import java.io.File
import sbt.util.Logger
import Logger.o2m
import xsbti.Maybe
import xsbti.compile.{ FileWatch, FileChanges }
import com.barbarysoftware.watchservice.{ WatchService, WatchKey, WatchEvent, WatchableFile, StandardWatchEventKind }
import com.barbarysoftware.watchservice.StandardWatchEventKind._
import scala.collection.JavaConverters._
import scala.collection.mutable

private[sbt] object NoFileWatch extends FileWatch {
  override def productChanges(since: Long): Maybe[FileChanges] = o2m(None)
  override def sourceChanges(since: Long): Maybe[FileChanges] = o2m(None)
  override def binaryChanges(since: Long): Maybe[FileChanges] = o2m(None)
}

class BarbaryFileWatch(sourcesToWatch: List[File], log: Logger) extends FileWatch with AutoCloseable {
  override def productChanges(since: Long): Maybe[FileChanges] = o2m(None)
  override def sourceChanges(since: Long): Maybe[FileChanges] =
    synchronized {
      log.debug(s"[filewatch] sourceChanges - since: $since")
      val t = System.currentTimeMillis
      val result = if (since < trackTime) None
      else {
        val xs = sourceEvents.toList filter { case (_, t, _) => t >= since }
        Some(new FileChanges(since, t,
          (xs collect { case (f, _, ENTRY_CREATE) => f }).toSet.asJava,
          (xs collect { case (f, _, ENTRY_DELETE) => f }).toSet.asJava,
          (xs collect { case (f, _, ENTRY_MODIFY) => f }).toSet.asJava))
      }
      sourceEvents.clear()
      trackTime = t
      o2m(result)
    }

  override def binaryChanges(since: Long): Maybe[FileChanges] = o2m(None)
  private val sourceEvents: mutable.ListBuffer[(File, Long, WatchEvent.Kind[_])] = mutable.ListBuffer.empty
  private def addSourceEvent(file0: File, t: Long, kind: WatchEvent.Kind[_]): Unit =
    synchronized {
      // On Mac /var/folders are reported out to /private/var/folders/
      val file =
        if (file0.toString startsWith "/private/var/folders/") new File(file0.toString.replaceAll("""^\/private\/""", "/"))
        else file0
      sourceEvents.append((file, t, kind))
    }
  private val watcher = WatchService.newWatchService
  sourcesToWatch foreach { x =>
    if (x.isDirectory) {
      val f = new WatchableFile(x)
      f.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY)
    }
  }
  private var trackTime: Long = System.currentTimeMillis
  private val consumer = new Thread(makeRunnable)
  consumer.start()
  def makeRunnable: Runnable = new Runnable {
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
                    addSourceEvent(ev.context, t, ev.kind)
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

  def close: Unit = {
    watcher.close
  }
}
