def resurcesDir = (file("..")/ "src" /  "test" / "resources" / "bin").getAbsoluteFile


val genTestResTask = TaskKey[Unit]("gen-test-resources")

def projectSettings(ext: String) = Seq(
  (scalaSource in Compile) := baseDirectory.value / "src",
  genTestResTask:= {
    IO.copyFile((packageBin in Compile).value, resurcesDir /  s"${name.value}.$ext")
  }
)


val jar1 = project settings(projectSettings("jar"):_*)
val jar2 = project settings(projectSettings("jar"):_*)
val classesDep1 = project settings(projectSettings("zip"):_*)


val root = project aggregate(jar1, jar2, classesDep1) settings(genTestResTask := {} )