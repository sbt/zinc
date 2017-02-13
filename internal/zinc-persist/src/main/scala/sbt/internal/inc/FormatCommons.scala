/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package sbt.internal.inc

import java.io.{ BufferedReader, File, Writer }
import java.nio.file.Paths

// Very simple timer for timing repeated code sections.
// TODO: Temporary. Remove once we've milked all available performance gains.
private[inc] object FormatTimer {
  private val timers = scala.collection.mutable.Map[String, Long]()
  private val printTimings = "true" == System.getProperty("sbt.analysis.debug.timing")

  def aggregate[T](key: String)(f: => T) = {
    val start = System.nanoTime()
    val ret = f
    val elapsed = System.nanoTime() - start
    timers.update(key, timers.getOrElseUpdate(key, 0) + elapsed)
    ret
  }

  def time[T](key: String)(f: => T) = {
    val ret = aggregate(key)(f)
    close(key)
    ret
  }

  def close(key: String): Unit = {
    if (printTimings) {
      println("[%s] %dms".format(key, timers.getOrElse(key, 0L) / 1000000))
    }
    timers.remove(key)
    ()
  }
}

class ReadException(s: String) extends Exception(s) {
  def this(expected: String, found: String) = this("Expected: %s. Found: %s.".format(expected, found))
}

class EOFException extends ReadException("Unexpected EOF.")

object FormatCommons extends FormatCommons

/** Various helper functions. */
trait FormatCommons {

  val fileToString: File => String =
    { f: File => f.toPath.toString }
  val stringToFile: String => File =
    { s: String =>
      try {
        Paths.get(s).toFile
      } catch {
        case e: Exception => sys.error(e.getMessage + ": " + s)
      }
    }

  protected def writeHeader(out: Writer, header: String): Unit = out.write(header + ":\n")

  protected def expectHeader(in: BufferedReader, expectedHeader: String): Unit = {
    val header = in.readLine()
    if (header != expectedHeader + ":") throw new ReadException(expectedHeader, if (header == null) "EOF" else header)
  }

  protected def writeSize(out: Writer, n: Int): Unit = out.write("%d items\n".format(n))

  private val itemsPattern = """(\d+) items""".r
  protected def readSize(in: BufferedReader): Int = {
    in.readLine() match {
      case itemsPattern(nStr) => Integer.parseInt(nStr)
      case s: String          => throw new ReadException("\"<n> items\"", s)
      case null               => throw new EOFException
    }
  }

  protected def writeSeq[T](out: Writer)(header: String, s: Seq[T], t2s: T => String): Unit = {
    // We write sequences as idx -> element maps, for uniformity with maps/relations.
    def n = s.length
    val numDigits = if (n < 2) 1 else math.log10(n.toDouble - 1).toInt + 1
    val fmtStr = "%%0%dd".format(numDigits)
    // We only use this for relatively short seqs, so creating this extra map won't be a performance hit.
    val m: Map[String, T] = s.zipWithIndex.map(x => fmtStr.format(x._2) -> x._1).toMap
    writeMap(out)(header, m, identity[String] _, t2s)
  }

  protected def writeMap[K, V](out: Writer)(header: String, m: Map[K, V],
    k2s: K => String,
    v2s: V => String,
    inlineVals: Boolean = true)(implicit ord: Ordering[K]): Unit =
    writePairs(out)(header, m.keys.toSeq.sorted map { k => (k, (m(k))) }, k2s, v2s, inlineVals)

  protected def writePairs[K, V](out: Writer)(header: String, s: Seq[(K, V)], k2s: K => String, v2s: V => String, inlineVals: Boolean = true): Unit = {
    writeHeader(out, header)
    writeSize(out, s.size)
    s foreach {
      case (k, v) =>
        out.write(k2s(k))
        out.write(" -> ")
        if (!inlineVals) out.write("\n") // Put large vals on their own line, to save string munging on read.
        out.write(v2s(v))
        out.write("\n")
    }
  }

  protected def readMap[K, V](in: BufferedReader)(expectedHeader: String, s2k: String => K, s2v: String => V): Map[K, V] = {
    readPairs(in)(expectedHeader, s2k, s2v).toMap
  }

  protected def readSeq[T](in: BufferedReader)(expectedHeader: String, s2t: String => T): Seq[T] =
    (readPairs(in)(expectedHeader, identity[String], s2t).toSeq.sortBy(_._1) map (_._2))

  protected def readPairs[K, V](in: BufferedReader)(expectedHeader: String, s2k: String => K, s2v: String => V) =
    readMappedPairs(in)(expectedHeader, s2k, (_: K, s) => s2v(s))

  protected def readMappedPairs[K, V](in: BufferedReader)(expectedHeader: String, s2k: String => K, s2v: (K, String) => V): Traversable[(K, V)] = {
    def toPair(s: String): (K, V) = {
      if (s == null) throw new EOFException
      val p = s.indexOf(" -> ")
      val k = s2k(s.substring(0, p))
      // Pair is either "a -> b" or "a -> \nb". This saves us a lot of substring munging when b is a large blob.
      val v = s2v(k, if (p == s.length - 4) in.readLine() else s.substring(p + 4))
      (k, v)
    }
    expectHeader(in, expectedHeader)
    val n = readSize(in)
    for (i <- 0 until n) yield toPair(in.readLine())
  }
}
