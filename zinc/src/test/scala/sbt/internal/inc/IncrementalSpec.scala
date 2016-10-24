package sbt.internal.inc

import java.io.File

import sbt.io.IO
import xsbti.api.{DependencyContext, ExternalDependency}
import xsbti.compile.DependencyChanges

class IncrementalSpec extends BaseIncCompilerSpec {

  type BinaryDependencies = Seq[(File, String, Stamp)]

  type ExternalDependencies = Seq[ExternalDependency]

  trait CreateEntryOnClasspath {
    // creates entry on classpath
    def create(entryName: String, dependencyName: String,
               setup: DirectorySetup): (BinaryDependencies, ExternalDependencies)
  }

  case object JarOnClasspath extends CreateEntryOnClasspath {
    def create(entryName: String,
               className: String,
               directorySetup: DirectorySetup
              ): (BinaryDependencies, ExternalDependencies) = {
      val jar = VirtualJar(s"${entryName}Jar", Seq("A1", "A2", className), directorySetup)
      val binaryJarDependency = (jar.classpathEntry, className, Stamp.lastModified(jar.classpathEntry))
      (Seq(binaryJarDependency), Seq.empty)
    }
  }

  case object ClassDirectoryOnClasspath extends CreateEntryOnClasspath {
    def create(entryName: String,
               className: String,
               directorySetup: DirectorySetup
              ): (BinaryDependencies, ExternalDependencies) = {
      val dir = new VirtualDir(s"${entryName}Directory", Seq("C1", "C2", className), directorySetup)
      val concreteFile = dir.classpathEntry.toPath.resolve(s"$className.class").toFile
      val binaryClassPathDependency = (concreteFile, className, Stamp.lastModified(dir.classpathEntry))
      (Seq(binaryClassPathDependency), Seq.empty)
    }
  }

  case object ProjectOnClasspath extends CreateEntryOnClasspath {
    def create(entryName: String,
               className: String,
               directorySetup: DirectorySetup
              ): (BinaryDependencies, ExternalDependencies) = {
      val project = new VirtualProject(s"${entryName}Project", directorySetup) {
        val F1 = VirtualSource("F1", this)

        val dependency = VirtualSource(className, this)
      }

      val analyzedClass = project.analysis.get.apis.internalAPI(className)

      val externalDep = new ExternalDependency(dependentOnFileName, className,
        analyzedClass, DependencyContext.DependencyByMemberRef)
      (Seq.empty, Seq(externalDep))
    }
  }

  class TestProjectSetup(in: File, withAnalysisOnlyExtDepLookup: Boolean) extends DirectorySetup(in) {

    def createMockConfig = {
      val configWithoutSetting = mockedConfig(classpath, entryLookup, in)
      val newIncOptions = configWithoutSetting.incOptions.withAnalysisOnlyExtDepLookup(withAnalysisOnlyExtDepLookup)
      configWithoutSetting.copy(incOptions = newIncOptions)
    }

    def createLookup = new LookupImpl(createMockConfig)

    val readStamps = Stamps.defaultInitial

    class MainProject(binaryDependencies: BinaryDependencies, extDependencies: ExternalDependencies)
      extends VirtualProject("mainProject", this) {

      val P1 = VirtualSource("P1", this)

      val DependentOn = VirtualSource(dependentOnFileName, this, extDependencies, binaryDependencies)
    }

  }

  object DependencyType {
    val project = "project"
    val classDir = "class dir"
    val jar = "jar"
  }

  private val dependencyFileName = "Dependency"
  private val dependentOnFileName = "DependentOn"

  private val possibleFlags = Seq(false, true)
  private val possibleEntriesOnClassPath: Seq[(String, CreateEntryOnClasspath)] = Seq(
    DependencyType.project -> ProjectOnClasspath,
    DependencyType.jar -> JarOnClasspath, DependencyType.classDir -> ClassDirectoryOnClasspath
  )

  private val ignoredTests = Set(
    (true, DependencyType.jar, DependencyType.project),
    (true, DependencyType.classDir, DependencyType.project)
  )

  for {
    flag <- possibleFlags
    (secondEntryType, secondEntryOnClasspath) <- possibleEntriesOnClassPath
  } {

    it should s"detect no changes for unchanged $secondEntryType " +
      s"with withAnalysisOnlyExtDepLookup set to $flag" in IO.withTemporaryDirectory {
      file =>
        // GIVEN: set up classpath with no changes according to the test requirements
        val setup = new TestProjectSetup(file, flag)
        import setup._

        val (binaryDependencies, externalDependencies) =
          secondEntryOnClasspath.create("zero", dependencyFileName, setup)

        var compiledFiles = Set.empty[File]
        val doCompile = createDoCompile(compiledFiles = _) _
        val (_, mainProjectSources, oldAnalysis, mockConfig, lookup) =
          setupEnvForCompilation(setup, binaryDependencies, externalDependencies)

        // WHEN: Compiling the main project
        Incremental.compile(mainProjectSources, lookup, oldAnalysis,
          readStamps, doCompile, log, mockConfig.incOptions)

        // THEN: No files should be recompiled
        compiledFiles shouldEqual Set.empty
    }

    for ((firstEntryType, firstEntryOnClassPath) <- possibleEntriesOnClassPath) {

      def testBody(secondDependencyFileName: String): Unit =
        IO.withTemporaryDirectory { tmpDir =>
          //  GIVEN: set up classpath with new elements according to the test requirements
          val setup = new TestProjectSetup(tmpDir, flag)

          firstEntryOnClassPath.create("first", dependencyFileName, setup)
          val (binaryDependencies, externalDependencies) =
            secondEntryOnClasspath.create("second", secondDependencyFileName, setup)

          var compiledFiles = Set.empty[File]
          val doCompile = createDoCompile(compiledFiles = _) _
          val (mainProject, mainProjectSources, oldAnalysis, mockConfig, lookup) =
            setupEnvForCompilation(setup, binaryDependencies, externalDependencies)

          // WHEN: Compiling the main project
          Incremental.compile(mainProjectSources, lookup, oldAnalysis,
            setup.readStamps, doCompile, log, mockConfig.incOptions)

          // THEN: The right file should be recompiled if an identical dependency was added earlier
          if (dependencyFileName != secondDependencyFileName)
            compiledFiles shouldEqual Set()
          else
            compiledFiles shouldEqual Set(mainProject.DependentOn.sourceFile)
        }

      val noChangeTestMessage = "does not recompile files after adding " +
        s"$secondEntryType with no dependency before $firstEntryType with withAnalysisOnlyExtDepLookup set to $flag"

      it should noChangeTestMessage in {
        testBody("notTheRightDependency")
      }

      val shouldRecompileTestMessage = "recompiles correct files after adding " +
        s"$secondEntryType before $firstEntryType with withAnalysisOnlyExtDepLookup set to $flag"

      if (ignoredTests((flag, firstEntryType, secondEntryType)))
        it should shouldRecompileTestMessage in pendingUntilFixed {
          testBody(dependencyFileName)
        }
      else
        it should shouldRecompileTestMessage in {
          testBody(dependencyFileName)
        }

    }
  }

  private def setupEnvForCompilation(setup: TestProjectSetup,
                                     binaryDependencies: BinaryDependencies,
                                     externalDependencies: ExternalDependencies) = {
    val mainProject = new setup.MainProject(binaryDependencies, externalDependencies)
    val mainProjectSources = Set(mainProject.P1.sourceFile, mainProject.DependentOn.sourceFile)
    val oldAnalysis = mainProject.analysis.get
    val mockConfig = setup.createMockConfig
    val lookup = setup.createLookup
    (mainProject, mainProjectSources, oldAnalysis, mockConfig, lookup)
  }

  private def createDoCompile(filesFunc: Set[File] => Unit)(files: Set[File], changes: DependencyChanges): Analysis = {
    filesFunc(files)
    Analysis.empty(true)
  }
}