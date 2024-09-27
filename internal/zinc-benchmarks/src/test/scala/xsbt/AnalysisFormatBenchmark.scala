package xsbt

import java.io.File
import java.util.concurrent.TimeUnit
import scala.collection.mutable

import org.openjdk.jmh.annotations._
import org.openjdk.jmh.infra.Blackhole
import sbt.internal.inc.consistent._
import sbt.internal.inc.{ Analysis, FileAnalysisStore }
import sbt.io.IO
import xsbti.compile.analysis.ReadWriteMappers
import xsbti.compile.{ AnalysisContents, AnalysisStore }

@BenchmarkMode(Array(Mode.AverageTime))
@Fork(1)
@Threads(1)
@Warmup(iterations = 5)
@Measurement(iterations = 5)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
class AnalysisFormatBenchmark {

  var temp: File = _
  val sets = IndexedSeq("compiler", "reflect", "library")
  var cached: Map[String, AnalysisContents] = _

  @Setup
  def setup(): Unit = {
    this.temp = IO.createTemporaryDirectory
    sets.foreach { s =>
      val f = new File("../../../test-data", s"${s}.zip")
      assert(f.exists())
      val f2 = new File(temp, f.getName)
      IO.copyFile(f, f2)
      assert(f2.exists())
    }
    this.cached = readAll("", FileAnalysisStore.binary(_))
    writeAll("-ref-text", FileAnalysisStore.text(_), cached)
    // writeAll("-ref-ctext", ConsistentFileAnalysisStore.text(_, ReadWriteMappers.getEmptyMappers), cached)
    writeAll(
      "-ref-cbin",
      ConsistentFileAnalysisStore.binary(_, ReadWriteMappers.getEmptyMappers),
      cached
    )
    writeAll(
      "-ref-cbin-nosort",
      ConsistentFileAnalysisStore.binary(_, ReadWriteMappers.getEmptyMappers, sort = false),
      cached
    )
    println("Sizes:")
    temp.listFiles().foreach { p => println(s"$p: ${p.length()}") }
    val cbinTotal = temp.listFiles().filter(_.getName.endsWith("-cbin.zip")).map(_.length()).sum
    println(s"cbin total = $cbinTotal, ${cbinTotal / 1024}k")
    val cbinNoSortTotal =
      temp.listFiles().filter(_.getName.endsWith("-cbin-nosort.zip")).map(_.length()).sum
    println(s"cbin-nosort total = $cbinNoSortTotal, ${cbinNoSortTotal / 1024}k")
  }

  @TearDown
  def tearDown(): Unit = {
    if (temp != null) IO.delete(temp)
  }

  @Benchmark
  def readConsistentBinary(bh: Blackhole): Unit =
    bh.consume(
      readAll("-ref-cbin", ConsistentFileAnalysisStore.binary(_, ReadWriteMappers.getEmptyMappers))
    )

  @Benchmark
  def writeConsistentBinary(bh: Blackhole): Unit =
    bh.consume(
      writeAll(
        "-test-cbin",
        ConsistentFileAnalysisStore.binary(_, ReadWriteMappers.getEmptyMappers),
        cached
      )
    )

  @Benchmark
  def writeConsistentBinaryNoSort(bh: Blackhole): Unit =
    bh.consume(
      writeAll(
        "-test-cbin-nosort",
        ConsistentFileAnalysisStore.binary(_, ReadWriteMappers.getEmptyMappers, sort = false),
        cached
      )
    )

  @Benchmark
  def writeNull(bh: Blackhole): Unit = {
    cached.foreach {
      case (s, a) =>
        val ser = new NullSerializer
        val af = new ConsistentAnalysisFormat(ReadWriteMappers.getEmptyMappers, sort = true)
        af.write(ser, a.getAnalysis, a.getMiniSetup)
        bh.consume(ser.count)
    }
  }

  @Benchmark
  def writeNullNoSort(bh: Blackhole): Unit = {
    cached.foreach {
      case (s, a) =>
        val ser = new NullSerializer
        val af = new ConsistentAnalysisFormat(ReadWriteMappers.getEmptyMappers, sort = false)
        af.write(ser, a.getAnalysis, a.getMiniSetup)
        bh.consume(ser.count)
    }
  }

  def readAll(suffix: String, store: File => AnalysisStore): Map[String, AnalysisContents] =
    sets.iterator.map(s => (s, read(s, suffix, store))).toMap

  def writeAll(
      suffix: String,
      store: File => AnalysisStore,
      map: Map[String, AnalysisContents]
  ): Unit =
    map.foreach { case (s, a) => write(s, suffix, store, a) }

  def read(set: String, suffix: String, store: File => AnalysisStore): AnalysisContents = {
    val api = store((new File(temp, s"${set}${suffix}.zip"))).unsafeGet()
    assert(api.getAnalysis.asInstanceOf[Analysis].apis.internal.head._2.api() != null)
    api
  }

  def write(
      set: String,
      suffix: String,
      store: File => AnalysisStore,
      analysis: AnalysisContents
  ): Unit = {
    assert(analysis.getMiniSetup.storeApis())
    val f = new File(temp, s"${set}${suffix}.zip")
    IO.delete(f)
    store(f).set(analysis)
    assert(f.exists())
  }
}

class NullSerializer extends Serializer {
  private[this] val strings = mutable.HashMap.empty[String, String]
  private[this] var _count = 0
  def count: Int = _count
  def startBlock(name: String): Unit = _count += 1
  def startArray(name: String, length: Int): Unit = _count += 1
  def endBlock(): Unit = _count += 1
  def endArray(): Unit = _count += 1
  def string(s: String): Unit = {
    if (!strings.contains(s)) {
      strings.put(s, s)
      _count += 1
    }
  }
  def bool(b: Boolean): Unit = _count += 1
  def int(i: Int): Unit = _count += 1
  def byte(b: Byte): Unit = _count += 1
  def long(l: Long): Unit = _count += 1
  def end(): Unit = _count += 1
}
