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

import java.io.File
import java.net.URLClassLoader
import sbt.internal.inc.classfile.{ ClassFile, JavaCompilerForUnitTesting, Parser }
import sbt.io.IO
import sbt.util.Logger
import xsbt.api.HashAPI
import xsbti.api.ClassLike

/**
 * Phase 3 differential audit: for a corpus of loadable Java classes, compare the reflection-derived
 * API ([[ClassToAPI]], the current production path) with the classfile-derived API
 * ([[ClassfileToAPI]]). Catalogs the differences so we can decide, before de-reflecting loadable
 * classes, whether the classfile path is close enough — in particular how much the inherited-members
 * gap matters. Prints a report and asserts the core invariant that *declared* members agree.
 */
class DifferentialApiSpecification extends UnitSpec {

  // A corpus exercising the public-API surface ClassToAPI models (see ClassToAPI.scala): generics,
  // bounds, wildcards (extends/super/unbounded), nested generics, enums, interfaces (default/static
  // methods), annotations, varargs, throws, arrays, visibility levels, static vs instance, constants,
  // abstract methods, multiple interfaces, and inheritance from both JDK and user supertypes. Each
  // entry is a single .java file whose public type is `name` (helper types are package-private).
  private val corpus: Seq[(String, String)] = Seq(
    "Simple" -> "public class Simple { public int x; public String greet(int n) { return null; } }",
    "Box" -> "public class Box<T> { public T get() { return null; } public void set(T t) {} }",
    "Num" -> "public class Num<T extends Number> { public T value; }",
    "Wild" -> "import java.util.List; public class Wild { public List<? extends Number> f; public List<?> g; }",
    "Sup" -> "import java.util.List; public class Sup { public List<? super Integer> g; }",
    "Deep" -> "import java.util.List; import java.util.Map; public class Deep { public Map<String, List<Integer>> m; }",
    "Color" -> "public enum Color { RED, GREEN, BLUE }",
    "Iface" -> "public interface Iface { int compute(int x); default int twice(int x) { return x * 2; } }",
    "IfaceStatic" -> "public interface IfaceStatic { int inst(); static int helper() { return 0; } default int def() { return 1; } }",
    "Anno" -> "public class Anno { @Deprecated public void old() {} }",
    "Vararg" -> "public class Vararg { public void m(String... xs) {} }",
    "ArraysC" -> "public class ArraysC { public int[] a; public String[][] b; public int[] m(long[] xs) { return null; } }",
    "Thrower" -> "import java.io.IOException; public class Thrower { public void run() throws IOException {} }",
    "Visibility" -> "public class Visibility { public int pub; protected int prot; private int priv; public void pubM() {} protected void protM() {} private void privM() {} }",
    "Statics" -> "public class Statics { public static int s; public int i; public static void sm() {} public void im() {} }",
    "Constants" -> "public class Constants { public static final int I = 1; public static final String S = \"x\"; public static final long L = 2L; }",
    "GenericMethod" -> "import java.util.Map; public class GenericMethod { public <T, U> Map<T, U> pair(T t, U u) { return null; } }",
    "Abstr" -> "public abstract class Abstr { public abstract int compute(int x); public int done() { return 0; } }",
    "Child" -> "public class Child extends java.util.ArrayList<String> {}",
    "Derived" -> """|class Base<T> { public T value; public T get() { return null; } }
                    |public class Derived extends Base<String> { public int extra() { return 0; } }
                    |""".stripMargin,
    "MultiIface" -> """|interface I1 { void a(); }
                       |interface I2 { void b(); }
                       |public class MultiIface implements I1, I2 { public void a() {} public void b() {} }
                       |""".stripMargin,
    // A class implementing an interface that has a static method: the static method must NOT be
    // inherited by either path (Java does not inherit static interface methods).
    "ImplStatic" -> """|interface WithStatic { static int helper() { return 0; } int inst(); }
                       |public class ImplStatic implements WithStatic { public int inst() { return 0; } }
                       |""".stripMargin,
    // Member classes (static nested, instance inner, nested interface) must appear among the outer's
    // declared members, like ClassToAPI: static ones on the module, instance ones on the class.
    "Nesting" -> ("public class Nesting { public static class StaticInner { public int s() { return 0; } } " +
      "public class InstanceInner { public int i() { return 0; } } public interface NestedIface { void f(); } }"),
    // A public nested class of a supertype is inherited by subclasses — both paths must list it.
    "InheritNested" -> ("class HasNested { public static class Shared { public int v() { return 0; } } } " +
      "public class InheritNested extends HasNested {}"),
    // ...but a *parameterized* inner supertype (Outer<String>.Inner) is a ParameterizedType that
    // reflection's inner-class walk filters out, so Inner.Member must NOT be inherited by either path
    // — even though the erased superclass name (Outer$Inner) looks non-generic.
    "GenericInnerSuper" -> ("class Outer<T> { public class Inner { public class Member {} } } " +
      "public class GenericInnerSuper extends Outer<String>.Inner { " +
      "GenericInnerSuper(Outer<String> o) { o.super(); } }")
  )

  private def declared(apis: Seq[ClassLike]): Set[String] =
    apis.flatMap(_.structure.declared.map(_.name)).toSet
  private def inherited(apis: Seq[ClassLike]): Set[String] =
    apis.flatMap(_.structure.inherited.map(_.name)).toSet
  private def kind(d: xsbti.api.ClassDefinition): String = d match {
    case _: xsbti.api.Def       => "method"
    case _: xsbti.api.FieldLike => "field"
    case other                  => other.getClass.getSimpleName
  }
  // (member name, method-vs-field) — catches a member being modelled as the wrong kind.
  private def declaredKinds(apis: Seq[ClassLike]): Set[(String, String)] =
    apis.flatMap(_.structure.declared.map(d => d.name -> kind(d))).toSet

  private def accessStr(a: xsbti.api.Access): String = a match {
    case _: xsbti.api.Public    => "public"
    case p: xsbti.api.Protected => "protected:" + qualifierStr(p.qualifier)
    case p: xsbti.api.Private   => "private:" + qualifierStr(p.qualifier)
  }
  private def qualifierStr(q: xsbti.api.Qualifier): String = q match {
    case i: xsbti.api.IdQualifier   => i.value
    case _: xsbti.api.ThisQualifier => "this"
    case _                          => "unqualified"
  }
  // Per-declared-method facts that MUST match exactly: name, access, parameter count, varargs
  // (a parameter modifier), and type-parameter count. Type *strings* are intentionally excluded —
  // they differ by design (unqualified vs qualified type-variable names, wildcard-bound collapse).
  private def methodFacts(apis: Seq[ClassLike]): Set[(String, String, Int, Boolean, Int)] =
    apis.flatMap(_.structure.declared.collect {
      case d: xsbti.api.Def =>
        val params = d.valueParameters.flatMap(_.parameters.toSeq)
        val varargs = params.lastOption.exists(_.modifier == xsbti.api.ParameterModifier.Repeated)
        (d.name, accessStr(d.access), params.length, varargs, d.typeParameters.length)
    }).toSet
  // Per-declared-field facts that must match: name + access.
  private def fieldFacts(apis: Seq[ClassLike]): Set[(String, String)] =
    apis.flatMap(_.structure.declared.collect {
      case f: xsbti.api.FieldLike => (f.name, accessStr(f.access))
    }).toSet

  // Builds both the reflection-derived (ClassToAPI) and classfile-derived (ClassfileToAPI) APIs for
  // the public type `name`, after compiling `source` into a fresh dir. The classfile path resolves
  // parents by reading bytes (never loading), exactly as production does under classfileApiOnly.
  private def buildApis(temp: File, idx: Int, name: String, source: String) = {
    val dir = new File(temp, s"d$idx")
    dir.mkdir()
    val src = new File(dir, s"$name.java")
    IO.write(src, source)
    JavaCompilerForUnitTesting.compileJava(Seq(src), dir, Seq.empty)

    val loader = new URLClassLoader(Array(dir.toURI.toURL), null)
    val resolve: String => Option[ClassFile] = bn => {
      val res = bn.replace('.', '/') + ".class"
      Option(loader.getResource(res))
        .orElse(Option(ClassLoader.getSystemResource(res)))
        .flatMap(u =>
          try Some(Parser(u, Logger.Null))
          catch { case _: Throwable => None }
        )
    }
    val reflect = ClassToAPI.process(Seq(loader.loadClass(name)))._1.filter(_.name == name)
    val cf = Parser(new File(dir, s"$name.class").toPath, Logger.Null)
    val classfile = ClassfileToAPI.process(Seq(name -> cf), resolve)._1.filter(_.name == name)
    (reflect.toSeq, classfile.toSeq)
  }

  "ClassfileToAPI vs ClassToAPI" should "agree on declared and inherited members across the corpus" in {
    IO.withTemporaryDirectory { temp =>
      println("\n=== Phase 3 differential audit: ClassfileToAPI vs ClassToAPI (reflection) ===")
      val failures = scala.collection.mutable.ListBuffer.empty[String]
      corpus.zipWithIndex.foreach {
        case ((name, source), idx) =>
          val (reflect, classfile) = buildApis(temp, idx, name, source)
          val (dR, dC) = (declared(reflect), declared(classfile))
          val (iR, iC) = (inherited(reflect), inherited(classfile))
          val (kR, kC) = (declaredKinds(reflect), declaredKinds(classfile))
          val (mR, mC) = (methodFacts(reflect), methodFacts(classfile))
          val (fR, fC) = (fieldFacts(reflect), fieldFacts(classfile))
          val hashEq = reflect.map(HashAPI(_)).sum == classfile.map(HashAPI(_)).sum
          println(
            f"$name%-13s declared==:${dR == dC}%-6s inherited==:${iR == iC}%-6s " +
              f"kinds==:${kR == kC}%-6s methods==:${mR == mC}%-6s fields==:${fR == fC}%-6s hashEq:$hashEq"
          )
          if (dR != dC)
            failures += s"$name declared: onlyReflect=${dR -- dC} onlyClassfile=${dC -- dR}"
          if (iR != iC)
            failures += s"$name inherited: onlyReflect=${iR -- iC} onlyClassfile=${iC -- iR}"
          if (kR != kC)
            failures += s"$name kinds: onlyReflect=${kR -- kC} onlyClassfile=${kC -- kR}"
          if (mR != mC)
            failures += s"$name methods: onlyReflect=${mR -- mC} onlyClassfile=${mC -- mR}"
          if (fR != fC)
            failures += s"$name fields: onlyReflect=${fR -- fC} onlyClassfile=${fC -- fR}"
      }
      println("=== end audit ===\n")
      assert(failures.isEmpty, "structural divergences:\n" + failures.mkString("\n"))
    }
  }

  // Previously a known divergence, now closed: ClassToAPI lists a public nested class among the
  // outer class's declared members (ClassToAPI.scala:256); ClassfileToAPI now mirrors this from the
  // InnerClasses attribute, so the outer's declared set matches.
  it should "list a nested class in the outer's declared members, matching reflection" in {
    IO.withTemporaryDirectory { temp =>
      val src =
        "public class Outer { public class Inner { public int v() { return 0; } } public int x; }"
      val (reflect, classfile) = buildApis(temp, 0, "Outer", src)
      assert(
        declared(classfile).contains("Outer.Inner"),
        s"classfile path should list the nested class: ${declared(classfile)}"
      )
      assert(
        declared(reflect) == declared(classfile),
        s"declared differ — reflect=${declared(reflect)} classfile=${declared(classfile)}"
      )
    }
  }

  // Adversarial-hunt candidate: a non-static inner class's constructor carries a synthetic
  // enclosing-instance parameter (this$0) in its descriptor. Verify the two paths agree on the
  // constructor's parameter count — if they don't, ClassfileToAPI must drop the synthetic leading
  // parameter to match ClassToAPI.
  it should "agree on a non-static inner class constructor's parameter count" in {
    IO.withTemporaryDirectory { temp =>
      val dir = new File(temp, "d")
      dir.mkdir()
      val src = new File(dir, "Outer.java")
      IO.write(src, "public class Outer { public class Inner { public Inner() {} } }")
      JavaCompilerForUnitTesting.compileJava(Seq(src), dir, Seq.empty)

      val loader = new URLClassLoader(Array(dir.toURI.toURL), null)
      val resolve: String => Option[ClassFile] = bn => {
        val res = bn.replace('.', '/') + ".class"
        Option(loader.getResource(res))
          .orElse(Option(ClassLoader.getSystemResource(res)))
          .flatMap(u =>
            try Some(Parser(u, Logger.Null))
            catch { case _: Throwable => None }
          )
      }
      val innerName = "Outer.Inner"
      val reflect =
        ClassToAPI.process(Seq(loader.loadClass("Outer$Inner")))._1.filter(_.name == innerName).toSeq
      val innerCf = Parser(new File(dir, "Outer$Inner.class").toPath, Logger.Null)
      val classfile =
        ClassfileToAPI.process(Seq(innerName -> innerCf), resolve)._1.filter(_.name == innerName).toSeq

      def ctorParamCounts(apis: Seq[ClassLike]): Seq[Int] =
        apis.flatMap(_.structure.declared.collect {
          case d: xsbti.api.Def if d.name.contains("init") =>
            d.valueParameters.flatMap(_.parameters.toSeq).length
        })

      val r = ctorParamCounts(reflect)
      val c = ctorParamCounts(classfile)
      println(s"[inner-ctor] reflect ctor params=$r  classfile ctor params=$c")
      assert(r == c, s"inner-class ctor param count differs — reflect=$r classfile=$c")
    }
  }

  // The simple (Projection) name of a parent type reference, e.g. "Marker"/"Object". ClassToAPI and
  // ClassfileToAPI both build parent references via ClassToAPI.reference, which yields a Projection.
  private def parentSimpleName(t: xsbti.api.Type): Option[String] = t match {
    case p: xsbti.api.Projection    => Some(p.id)
    case p: xsbti.api.Parameterized => parentSimpleName(p.baseType)
    case _                          => None
  }
  private def parentNames(apis: Seq[ClassLike]): Set[String] =
    apis.flatMap(_.structure.parents.toSeq.flatMap(parentSimpleName)).toSet

  // Deviation #1 (fixed): ClassfileToAPI now lists the *transitive* supertype closure in `parents`
  // (ClassfileToAPI.collectParents), matching ClassToAPI.allSuperTypes — not just the direct
  // supertypes. So a subtyping relationship `C` holds only through an intermediate `B` (here a
  // member-less marker interface that `B` stops implementing) changes C's API on both paths, and the
  // classfile path no longer under-invalidates C's dependents. This was a green known-bug witness
  // before the fix; it now asserts agreement with reflection.
  it should "model the transitive supertype closure in parents, matching reflection" in {
    IO.withTemporaryDirectory { temp =>
      // v1: C -> B -> Marker (C is transitively a Marker).  v2: B no longer implements Marker.
      val v1 = "interface Marker {} class B implements Marker {} public class C extends B {}"
      val v2 = "interface Marker {} class B {} public class C extends B {}"
      val (reflect1, classfile1) = buildApis(temp, 100, "C", v1)
      val (reflect2, classfile2) = buildApis(temp, 101, "C", v2)

      // Structural: both paths list the transitive Marker among C's parents, and agree.
      val (pR, pC) = (parentNames(reflect1), parentNames(classfile1))
      println(s"[parents] reflect C parents=$pR  classfile C parents=$pC")
      assert(pC.contains("Marker"), s"classfile should now list transitive Marker: $pC")
      assert(pR == pC, s"parents should match reflection — reflect=$pR classfile=$pC")

      // Consequence: both paths invalidate (hash changes when B drops `implements Marker`).
      val (hR1, hR2) = (reflect1.map(HashAPI(_)).sum, reflect2.map(HashAPI(_)).sum)
      val (hC1, hC2) = (classfile1.map(HashAPI(_)).sum, classfile2.map(HashAPI(_)).sum)
      println(s"[hash] reflect $hR1 -> $hR2 (changed=${hR1 != hR2}); " +
        s"classfile $hC1 -> $hC2 (changed=${hC1 != hC2})")
      assert(hR1 != hR2, "reflection hash must change when B drops `implements Marker`")
      assert(
        hC1 != hC2,
        s"classfile hash must now change on the transitive-supertype change — got $hC1 vs $hC2"
      )
    }
  }

  // Deviation #2 (fixed): ClassfileToAPI now reads RuntimeVisible/InvisibleParameterAnnotations and
  // folds them into the method's synthetic annotation, so a parameter-annotation-only change changes
  // its hash — matching reflection (which carries them on the parameter type via api.Annotated). Was
  // a green known-bug witness before the fix; now asserts both paths invalidate.
  it should "detect a parameter-annotation change, matching reflection" in {
    IO.withTemporaryDirectory { temp =>
      val pa =
        "import java.lang.annotation.*;\n" +
          "@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.PARAMETER) @interface PA {}\n"
      val v1 = pa + "public class C { public void m(@PA String x) {} }"
      val v2 = pa + "public class C { public void m(String x) {} }"
      val (reflect1, classfile1) = buildApis(temp, 110, "C", v1)
      val (reflect2, classfile2) = buildApis(temp, 111, "C", v2)
      val (hR1, hR2) = (reflect1.map(HashAPI(_)).sum, reflect2.map(HashAPI(_)).sum)
      val (hC1, hC2) = (classfile1.map(HashAPI(_)).sum, classfile2.map(HashAPI(_)).sum)
      println(s"[param-anno hash] reflect changed=${hR1 != hR2}; classfile changed=${hC1 != hC2}")
      assert(hR1 != hR2, "reflection hash must change when the parameter annotation is removed")
      assert(
        hC1 != hC2,
        s"classfile hash must now change on a param-annotation change — $hC1 vs $hC2"
      )
    }
  }

  // The names of classes `process` reports as having a `main`, from each path.
  private def mainClasses(temp: File, idx: Int, names: Seq[(String, String)], query: String) = {
    val dir = new File(temp, s"m$idx")
    dir.mkdir()
    val srcs = names.map {
      case (n, src) =>
        val f = new File(dir, s"$n.java"); IO.write(f, src); f
    }
    JavaCompilerForUnitTesting.compileJava(srcs, dir, Seq.empty)
    val loader = new URLClassLoader(Array(dir.toURI.toURL), null)
    val resolve: String => Option[ClassFile] = bn => {
      val res = bn.replace('.', '/') + ".class"
      Option(loader.getResource(res))
        .orElse(Option(ClassLoader.getSystemResource(res)))
        .flatMap(u =>
          try Some(Parser(u, Logger.Null))
          catch { case _: Throwable => None }
        )
    }
    val reflectMains = ClassToAPI.process(Seq(loader.loadClass(query)))._2
    val cf = Parser(new File(dir, s"$query.class").toPath, Logger.Null)
    val classfileMains = ClassfileToAPI.process(Seq(query -> cf), resolve)._2
    (reflectMains, classfileMains)
  }

  // Deviation #3 (fixed): ClassfileToAPI.hasMain now also checks the inherited (superclass-chain)
  // public static main, mirroring ClassToAPI's use of c.getMethods, so a subclass that inherits
  // `main` is reported as a main class. Was a green known-bug witness before the fix; now asserts
  // agreement with reflection.
  it should "report an inherited main, matching reflection" in {
    IO.withTemporaryDirectory { temp =>
      val (reflectMains, classfileMains) = mainClasses(
        temp,
        0,
        Seq(
          "A" -> "public class A { public static void main(String[] a) {} }",
          "B" -> "public class B extends A {}"
        ),
        query = "B"
      )
      println(s"[main] reflect=$reflectMains classfile=$classfileMains")
      assert(
        reflectMains.contains("B"),
        s"reflection should report inherited main on B: $reflectMains"
      )
      assert(
        classfileMains.contains("B"),
        s"classfile should now report inherited main on B: $classfileMains"
      )
    }
  }

  // Deviation #4 (fixed): reflection's topLevel is `getEnclosingClass == null`, false for member,
  // local, AND anonymous classes. ClassfileToAPI now marks a class non-top-level on the mere presence
  // of an InnerClasses self entry (no longer requiring a non-empty outer), so anonymous/local classes
  // — whose self entry has outer index 0 — agree with reflection. Was a green known-bug witness
  // before the fix.
  it should "mark an anonymous class as non-top-level, matching reflection" in {
    IO.withTemporaryDirectory { temp =>
      val dir = new File(temp, "d4")
      dir.mkdir()
      val src = new File(dir, "Outer.java")
      IO.write(src, "public class Outer { Runnable r = new Runnable() { public void run() {} }; }")
      JavaCompilerForUnitTesting.compileJava(Seq(src), dir, Seq.empty)
      val loader = new URLClassLoader(Array(dir.toURI.toURL), null)
      val resolve: String => Option[ClassFile] = bn => {
        val res = bn.replace('.', '/') + ".class"
        Option(loader.getResource(res))
          .orElse(Option(ClassLoader.getSystemResource(res)))
          .flatMap(u =>
            try Some(Parser(u, Logger.Null))
            catch { case _: Throwable => None }
          )
      }
      val binary = "Outer$1" // the anonymous Runnable
      val reflect = ClassToAPI.process(Seq(loader.loadClass(binary)))._1.filter(_.name == binary)
      val cf = Parser(new File(dir, binary + ".class").toPath, Logger.Null)
      val classfile = ClassfileToAPI.process(Seq(binary -> cf), resolve)._1.filter(_.name == binary)
      val (rTop, cTop) = (reflect.map(_.topLevel).toSet, classfile.map(_.topLevel).toSet)
      println(s"[topLevel] reflect=$rTop classfile=$cTop")
      assert(rTop == Set(false), s"reflection: anon class is not top-level: $rTop")
      assert(
        cTop == rTop,
        s"classfile topLevel must now match reflection — reflect=$rTop classfile=$cTop"
      )
    }
  }
}
