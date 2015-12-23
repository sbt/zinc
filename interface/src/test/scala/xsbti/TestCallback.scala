package xsbti

import java.io.File
import scala.collection.mutable.ArrayBuffer
import xsbti.api.SourceAPI
import xsbti.DependencyContext._

class TestCallback(override val nameHashing: Boolean = false) extends AnalysisCallback
{
	val sourceDependencies = new ArrayBuffer[(String, String, DependencyContext)]
	val binaryDependencies = new ArrayBuffer[(File, String, String, DependencyContext)]
	val products = new ArrayBuffer[(File, File, String)]
	val usedNames = scala.collection.mutable.Map.empty[File, Set[String]].withDefaultValue(Set.empty)
	val declaredClasses = scala.collection.mutable.Map.empty[File, Set[String]].withDefaultValue(Set.empty)
	val apis: scala.collection.mutable.Map[File, SourceAPI] = scala.collection.mutable.Map.empty

	def classDependency(dependsOn: String, sourceClassName: String, context: DependencyContext): Unit = {
		sourceDependencies += ((dependsOn, sourceClassName, context))
	}
	def binaryDependency(targetBinary: File, targetProductName: String, sourceClassName: String, sourceFile: File, context: DependencyContext): Unit = {
		binaryDependencies += ((targetBinary, targetProductName, sourceClassName, context))
	}
	def generatedClass(source: File, module: File, name: String): Unit = { products += ((source, module, name)) }

	def usedName(source: File, name: String): Unit = { usedNames(source) += name }
	override def declaredClass(sourceFile: File, className: String): Unit =
		declaredClasses(sourceFile) += className

	def api(source: File, sourceAPI: SourceAPI): Unit = {
		assert(!apis.contains(source), s"The `api` method should be called once per source file: $source")
		apis(source) = sourceAPI
	}
	def problem(category: String, pos: xsbti.Position, message: String, severity: xsbti.Severity, reported: Boolean): Unit = ()
}
