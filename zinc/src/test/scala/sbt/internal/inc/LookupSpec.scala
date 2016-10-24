package sbt.internal.inc

import java.io.File

import sbt.io.IO

class LookupSpec extends BaseIncCompilerSpec {
  behavior of "LookupImpl"

  protected case class ProjectSetup(in: File) extends DirectorySetup(in) {

    val jar1 = VirtualJar("jar1", Seq("J1J2", "J1P1", "J1C1"), this)
    val classesDir1 = new VirtualDir("classes1", Seq("J1C1", "C1C2", "C1P1", "C1J2"), this)

    object project1 extends VirtualProject("project1", this) {
      val P1P2 = VirtualSource("P1P2", this)
      val J1P1 = VirtualSource("J1P1", this)
      val C1P1 = VirtualSource("C1P1", this)
      val P1C2 = VirtualSource("P1C2", this)
      val P1J2 = VirtualSource("P1J2", this)
    }

    project1

    val jar2 = VirtualJar("jar2", Seq("J1J2", "C1J2", "P1J2", "J2"), this)
    val classesDir2 = new VirtualDir("classes2", Seq("C1C2", "P1C2", "C2"), this)

    object project2 extends VirtualProject("project2", this) {
      val P1P2 = VirtualSource("P1P2", this)
      val P2 = VirtualSource("P2", this)
    }

    project2

    val config = mockedConfig(classpath, entryLookup, in)

    val lookup = new LookupImpl(config)
  }

  private def mockedProjectContext(op: ProjectSetup => Unit) = IO.withTemporaryDirectory(tempDir => op(new ProjectSetup(tempDir)))

  private def withAnalysisOnlyExtDepLookup(value: Boolean, from: ProjectSetup): LookupImpl = {
    import from._
    new LookupImpl(config.copy(incOptions = config.incOptions.withAnalysisOnlyExtDepLookup(value)))
  }

  it should "find correct analysis for binary name - fast lookup on" in mockedProjectContext { setup =>
    import setup._

    val lookup = withAnalysisOnlyExtDepLookup(true, setup)

    lookup.lookupAnalysis("P1P2") shouldEqual project1.analysis
    lookup.lookupAnalysis("P1J2") shouldEqual project1.analysis
    lookup.lookupAnalysis("P1C2") shouldEqual project1.analysis
    lookup.lookupAnalysis("J1P1") shouldEqual project1.analysis
    lookup.lookupAnalysis("C1P1") shouldEqual project1.analysis

    lookup.lookupAnalysis("P2") shouldEqual project2.analysis

    lookup.lookupAnalysis("J1J2") shouldEqual None
    lookup.lookupAnalysis("C1C2") shouldEqual None

    lookup.lookupAnalysis("P1.P2") shouldEqual None
    lookup.lookupAnalysis("P1P2$") shouldEqual None
    lookup.lookupAnalysis("P1$P2") shouldEqual None
  }

  it should "find correct analysis for binary name - fast lookup off" in mockedProjectContext { setup =>
    import setup._

    val lookup = withAnalysisOnlyExtDepLookup(false, setup)

    lookup.lookupAnalysis("P1P2") shouldEqual project1.analysis
    lookup.lookupAnalysis("P1J2") shouldEqual project1.analysis
    lookup.lookupAnalysis("P1C2") shouldEqual project1.analysis

    lookup.lookupAnalysis("P2") shouldEqual project2.analysis

    lookup.lookupAnalysis("J1P1") shouldEqual None
    lookup.lookupAnalysis("C1P1") shouldEqual None

    lookup.lookupAnalysis("J1J2") shouldEqual None
    lookup.lookupAnalysis("C1C2") shouldEqual None

    lookup.lookupAnalysis("P1.P2") shouldEqual None
    lookup.lookupAnalysis("P1P2$") shouldEqual None
    lookup.lookupAnalysis("P1$P2") shouldEqual None
  }

  it should "find correct analysis for class file" in mockedProjectContext { setup =>
    import setup._

    lookup.lookupAnalysis(project1.P1P2.classFile) shouldEqual project1.analysis
    lookup.lookupAnalysis(project2.P1P2.classFile) shouldEqual project2.analysis
    lookup.lookupAnalysis(project2.P2.classFile) shouldEqual project2.analysis
    lookup.lookupAnalysis(project1.P1J2.classFile) shouldEqual project1.analysis
    lookup.lookupAnalysis(project1.P1C2.classFile) shouldEqual project1.analysis

    lookup.lookupAnalysis(classesDir1.classFile("C1C2")) shouldEqual None
  }

  it should "find correct analysis for class file and binary name" in mockedProjectContext { setup =>
    import setup._

    lookup.lookupAnalysis(project1.P1P2.classFile, "P1P2") shouldEqual project1.analysis
    lookup.lookupAnalysis(project1.P1J2.classFile, "P1J2") shouldEqual project1.analysis
    lookup.lookupAnalysis(project1.P1C2.classFile, "P1C2") shouldEqual project1.analysis

    lookup.lookupAnalysis(project2.P1P2.classFile, "P1P2") shouldEqual None
    lookup.lookupAnalysis(project2.P2.classFile, "P2") shouldEqual project2.analysis

    lookup.lookupAnalysis(project1.J1P1.classFile, "J1P1") shouldEqual None

    lookup.lookupAnalysis(project1.C1P1.classFile, "C1P1") shouldEqual None
  }

  it should "find correct class file for binary name" in mockedProjectContext { setup =>
    import setup._

    lookup.lookupOnClasspath("J1J2") shouldEqual Some(jar1.location)
    lookup.lookupOnClasspath("J1C1") shouldEqual Some(jar1.location)
    lookup.lookupOnClasspath("J1P1") shouldEqual Some(jar1.location)

    lookup.lookupOnClasspath("C1P1") shouldEqual Some(classesDir1.dir)
    lookup.lookupOnClasspath("C1C2") shouldEqual Some(classesDir1.dir)
    lookup.lookupOnClasspath("C1J2") shouldEqual Some(classesDir1.dir)

    lookup.lookupOnClasspath("P1P2") shouldEqual Some(project1.out)
    lookup.lookupOnClasspath("P1J2") shouldEqual Some(project1.out)
    lookup.lookupOnClasspath("P1C2") shouldEqual Some(project1.out)

    lookup.lookupOnClasspath("J2") shouldEqual Some(jar2.location)

    lookup.lookupOnClasspath("C2") shouldEqual Some(classesDir2.dir)

    lookup.lookupOnClasspath("P2") shouldEqual Some(project2.out)

    lookup.lookupOnClasspath("Ala") shouldEqual None
    lookup.lookupOnClasspath("C1P1$") shouldEqual None
    lookup.lookupOnClasspath("C1.P1") shouldEqual None
  }

}
