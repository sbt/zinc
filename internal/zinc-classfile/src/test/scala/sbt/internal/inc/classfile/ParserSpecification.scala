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
package classfile

import sbt.internal.util.ConsoleLogger

class ParserSpecification extends UnitSpec {

  val sampleClasses = List[Class[?]](
    this.getClass,
    classOf[java.lang.Integer],
    classOf[java.util.AbstractMap.SimpleEntry[String, String]],
    classOf[String],
    classOf[Thread],
    classOf[org.scalacheck.Properties],
    // exercises meta-annotation parsing
    classOf[java.lang.annotation.Retention]
    // I thought it would be nice to throw in a nested annotation example here,
    // but I couldn't find one that we could use without having to add another
    // JAR to the test classpath. it's fine, we have nested annotation testing
    // over in AnalyzeSpecification
  )

  for (c <- sampleClasses)
    "classfile.Parser" should s"not crash when parsing $c" in {
      val logger = ConsoleLogger()
      // logger.setLevel(sbt.util.Level.Debug)
      val classfile = Parser(sbt.io.IO.classfileLocation(c), logger)
      assert(classfile ne null)
      assert(classfile.types.nonEmpty)
    }

  it should "parse InnerClasses attribute for AbstractMap.SimpleEntry" in {
    val logger = ConsoleLogger()
    val c = classOf[java.util.AbstractMap.SimpleEntry[String, String]]
    val cf = Parser(sbt.io.IO.classfileLocation(c), logger)
    val innerClasses = cf.innerClasses
    assert(innerClasses.nonEmpty)
    val self = innerClasses.find(_.innerClassName == "java.util.AbstractMap$SimpleEntry")
    assert(self.isDefined)
    assert(self.get.outerClassName == "java.util.AbstractMap")
  }

  it should "parse InnerClasses attribute for AbstractMap" in {
    val logger = ConsoleLogger()
    val c = classOf[java.util.AbstractMap[?, ?]]
    val cf = Parser(sbt.io.IO.classfileLocation(c), logger)
    val innerClasses = cf.innerClasses
    val entry = innerClasses.find(_.innerClassName == "java.util.AbstractMap$SimpleEntry")
    assert(entry.isDefined)
    assert(entry.get.outerClassName == "java.util.AbstractMap")
    assert(entry.get.isPublic)
  }

  // --- FieldInfo / MethodInfo predicate coverage ---
  //
  // We compile a single inline fixture rather than reaching for JDK classes, so the
  // predicates aren't subject to JDK-version-dependent shapes (e.g. method finality on
  // java.lang.Object changes between versions).

  private val PredicateFixtureSrc =
    """|public abstract class PredicateFixture {
       |  static int initialized;
       |  static { initialized = compute(); }   // forces <clinit>
       |  private static int compute() { return 42; }
       |
       |  private String secretField;           // ACC_PRIVATE
       |  protected int protectedField;         // ACC_PROTECTED
       |  public final String finalField = "x"; // ACC_FINAL
       |  public static final int CONSTANT = 7; // ACC_STATIC | ACC_FINAL | ACC_PUBLIC
       |
       |  public PredicateFixture() {}          // <init>
       |
       |  public abstract int abstractMethod(); // ACC_ABSTRACT
       |  public final void finalMethod() {}    // ACC_FINAL
       |  public void varargsMethod(String... args) {}  // ACC_VARARGS
       |  private void privateMethod() {}       // ACC_PRIVATE
       |  protected void protectedMethod() {}   // ACC_PROTECTED
       |}
       |""".stripMargin

  private val BridgeFixtureSrc =
    """|abstract class GenericBase<T> {
       |  public T value() { return null; }
       |}
       |""".stripMargin

  private val BridgeSubclassSrc =
    """|public class GenericSub extends GenericBase<String> {
       |  @Override public String value() { return "x"; }  // bridge + synthetic generated
       |}
       |""".stripMargin

  private def withFixture[T](
      srcs: (String, String)*
  )(f: Map[String, ClassFile] => T): T = {
    sbt.io.IO.withTemporaryDirectory { temp =>
      val files = srcs.map { case (name, src) =>
        val file = new java.io.File(temp, name)
        sbt.io.IO.write(file, src)
        file
      }
      val outDir = new java.io.File(temp, "out")
      outDir.mkdir()
      JavaCompilerForUnitTesting.compileJava(files, outDir, Seq.empty)
      val logger = ConsoleLogger()
      val byClass = (sbt.io.PathFinder(outDir) ** "*.class").get().iterator.map { f =>
        val cf = Parser(f.toPath, logger)
        cf.className -> cf
      }.toMap
      f(byClass)
    }
  }

  it should "detect ACC_PRIVATE on a private method and field" in {
    withFixture("PredicateFixture.java" -> PredicateFixtureSrc) { cfs =>
      val cf = cfs("PredicateFixture")
      val priv =
        cf.methods.find(_.name.contains("privateMethod")).getOrElse(fail("no privateMethod"))
      assert(priv.isPrivate)
      assert(!priv.isPublic && !priv.isProtected)
      val secret = cf.fields.find(_.name.contains("secretField")).getOrElse(fail("no secretField"))
      assert(secret.isPrivate)
    }
  }

  it should "detect ACC_PROTECTED on a protected method and field" in {
    withFixture("PredicateFixture.java" -> PredicateFixtureSrc) { cfs =>
      val cf = cfs("PredicateFixture")
      val prot = cf.methods
        .find(_.name.contains("protectedMethod"))
        .getOrElse(fail("no protectedMethod"))
      assert(prot.isProtected)
      assert(!prot.isPublic && !prot.isPrivate)
      val field = cf.fields
        .find(_.name.contains("protectedField"))
        .getOrElse(fail("no protectedField"))
      assert(field.isProtected)
    }
  }

  it should "detect ACC_FINAL on a final method and field" in {
    withFixture("PredicateFixture.java" -> PredicateFixtureSrc) { cfs =>
      val cf = cfs("PredicateFixture")
      val fm = cf.methods.find(_.name.contains("finalMethod")).getOrElse(fail("no finalMethod"))
      assert(fm.isFinal)
      val ff = cf.fields.find(_.name.contains("finalField")).getOrElse(fail("no finalField"))
      assert(ff.isFinal)
    }
  }

  it should "detect ACC_ABSTRACT on an abstract method" in {
    withFixture("PredicateFixture.java" -> PredicateFixtureSrc) { cfs =>
      val cf = cfs("PredicateFixture")
      val am = cf.methods
        .find(_.name.contains("abstractMethod"))
        .getOrElse(fail("no abstractMethod"))
      assert(am.isAbstract)
    }
  }

  it should "detect ACC_VARARGS on a varargs method" in {
    withFixture("PredicateFixture.java" -> PredicateFixtureSrc) { cfs =>
      val cf = cfs("PredicateFixture")
      val vm = cf.methods
        .find(_.name.contains("varargsMethod"))
        .getOrElse(fail("no varargsMethod"))
      assert(vm.isVarArgs)
      // Descriptor still encodes the last param as an array; ACC_VARARGS is the
      // only signal that this is a Java varargs.
      assert(vm.descriptor.exists(_.endsWith(")V")))
    }
  }

  it should "detect isConstructor on <init>" in {
    withFixture("PredicateFixture.java" -> PredicateFixtureSrc) { cfs =>
      val cf = cfs("PredicateFixture")
      val ctor = cf.methods.find(_.isConstructor).getOrElse(fail("no <init>"))
      assert(ctor.name.contains("<init>"))
      assert(!ctor.isStaticInit)
    }
  }

  it should "detect isStaticInit on <clinit>" in {
    withFixture("PredicateFixture.java" -> PredicateFixtureSrc) { cfs =>
      val cf = cfs("PredicateFixture")
      val clinit = cf.methods.find(_.isStaticInit).getOrElse {
        fail("no <clinit> — static block didn't produce one?")
      }
      assert(clinit.name.contains("<clinit>"))
      assert(clinit.isStatic)
      assert(!clinit.isConstructor)
    }
  }

  it should "detect ACC_BRIDGE and ACC_SYNTHETIC on a generic-erasure bridge" in {
    withFixture(
      "GenericBase.java" -> BridgeFixtureSrc,
      "GenericSub.java" -> BridgeSubclassSrc
    ) { cfs =>
      val sub = cfs("GenericSub")
      // The real override returns String; javac generates a bridge that returns Object
      // and forwards to it. Both are named `value`.
      val values = sub.methods.filter(_.name.contains("value"))
      assert(values.length === 2, s"expected 2 `value` methods (real + bridge), got $values")
      val bridge = values.find(_.isBridge).getOrElse(fail(s"no bridge in $values"))
      assert(bridge.isSynthetic)
      assert(
        bridge.descriptor.exists(_.endsWith("Ljava/lang/Object;")),
        s"bridge return should be erased to Object: ${bridge.descriptor}"
      )
      val real = values.find(!_.isBridge).getOrElse(fail("no non-bridge"))
      assert(!real.isSynthetic)
      assert(
        real.descriptor.exists(_.endsWith("Ljava/lang/String;")),
        s"real method return should be String: ${real.descriptor}"
      )
    }
  }

  it should "report combined modifiers on a public static final field" in {
    withFixture("PredicateFixture.java" -> PredicateFixtureSrc) { cfs =>
      val cf = cfs("PredicateFixture")
      val c = cf.fields.find(_.name.contains("CONSTANT")).getOrElse(fail("no CONSTANT"))
      assert(c.isPublic && c.isStatic && c.isFinal)
      assert(!c.isPrivate && !c.isProtected)
    }
  }

  // --- FieldInfo-only predicates (FieldAccessFlags) ---
  //
  // These predicates only exist on FieldInfo, not on MethodInfo, so the bits 0x0040
  // (ACC_VOLATILE / ACC_BRIDGE) and 0x0080 (ACC_TRANSIENT / ACC_VARARGS) can never be
  // misread by calling the wrong predicate.

  private val FieldOnlyFixtureSrc =
    """|public class FieldOnlyFixture {
       |  public volatile int volatileField;
       |  public transient int transientField;
       |}
       |""".stripMargin

  private val EnumFixtureSrc =
    """|public enum EnumFixture { ONE, TWO }
       |""".stripMargin

  it should "detect ACC_VOLATILE on a volatile field" in {
    withFixture("FieldOnlyFixture.java" -> FieldOnlyFixtureSrc) { cfs =>
      val cf = cfs("FieldOnlyFixture")
      val vol =
        cf.fields.find(_.name.contains("volatileField")).getOrElse(fail("no volatileField"))
      assert(vol.isVolatile)
      assert(!vol.isTransient && !vol.isEnum)
    }
  }

  it should "detect ACC_TRANSIENT on a transient field" in {
    withFixture("FieldOnlyFixture.java" -> FieldOnlyFixtureSrc) { cfs =>
      val cf = cfs("FieldOnlyFixture")
      val tr =
        cf.fields.find(_.name.contains("transientField")).getOrElse(fail("no transientField"))
      assert(tr.isTransient)
      assert(!tr.isVolatile && !tr.isEnum)
    }
  }

  it should "detect ACC_ENUM on enum constant fields" in {
    withFixture("EnumFixture.java" -> EnumFixtureSrc) { cfs =>
      val cf = cfs("EnumFixture")
      val one = cf.fields.find(_.name.contains("ONE")).getOrElse(fail("no ONE"))
      assert(one.isEnum)
      assert(one.isStatic && one.isFinal && one.isPublic)
      assert(!one.isVolatile && !one.isTransient)
    }
  }

  // --- MethodInfo-only predicates (MethodAccessFlags) ---

  private val SynchronizedFixtureSrc =
    """|public class SynchronizedFixture {
       |  public synchronized void sync() {}
       |  public native void nativeMethod();
       |}
       |""".stripMargin

  it should "detect ACC_SYNCHRONIZED on a synchronized method" in {
    withFixture("SynchronizedFixture.java" -> SynchronizedFixtureSrc) { cfs =>
      val cf = cfs("SynchronizedFixture")
      val sync = cf.methods.find(_.name.contains("sync")).getOrElse(fail("no sync"))
      assert(sync.isSynchronized)
    }
  }

  it should "detect ACC_NATIVE on a native method" in {
    withFixture("SynchronizedFixture.java" -> SynchronizedFixtureSrc) { cfs =>
      val cf = cfs("SynchronizedFixture")
      val nat = cf.methods.find(_.name.contains("nativeMethod")).getOrElse(fail("no nativeMethod"))
      assert(nat.isNative)
    }
  }

  // --- ClassFile class-level predicates (ClassAccessFlags) ---

  it should "detect class-level ACC_PUBLIC, ACC_ABSTRACT on an abstract class" in {
    withFixture("PredicateFixture.java" -> PredicateFixtureSrc) { cfs =>
      val cf = cfs("PredicateFixture")
      assert(cf.isPublic)
      assert(cf.isAbstract)
      assert(!cf.isInterface)
      assert(!cf.isEnum)
      assert(!cf.isAnnotation)
    }
  }

  it should "detect class-level ACC_INTERFACE on an interface" in {
    val src = "public interface InterfaceFixture { void m(); }"
    withFixture("InterfaceFixture.java" -> src) { cfs =>
      val cf = cfs("InterfaceFixture")
      assert(cf.isInterface)
      assert(cf.isAbstract) // interfaces are implicitly abstract in the classfile
      assert(!cf.isAnnotation)
      assert(!cf.isEnum)
    }
  }

  it should "detect class-level ACC_ANNOTATION on an annotation type" in {
    val src =
      """|import java.lang.annotation.*;
         |@Retention(RetentionPolicy.RUNTIME)
         |public @interface AnnoFixture { String value() default ""; }
         |""".stripMargin
    withFixture("AnnoFixture.java" -> src) { cfs =>
      val cf = cfs("AnnoFixture")
      assert(cf.isAnnotation)
      assert(cf.isInterface) // annotations are interfaces at the classfile level
    }
  }

  it should "detect class-level ACC_ENUM on an enum class" in {
    withFixture("EnumFixture.java" -> EnumFixtureSrc) { cfs =>
      val cf = cfs("EnumFixture")
      assert(cf.isEnum)
      assert(cf.isPublic)
      assert(cf.isFinal) // simple enums (no per-constant body) get ACC_FINAL
    }
  }

  // --- InnerClassInfo extended predicates (InnerClassAccessFlags) ---

  private val NestedFixtureSrc =
    """|public class NestedFixture {
       |  public static class StaticNested {}
       |  private class PrivateInner {}
       |  protected final class ProtectedFinalInner {}
       |  public interface NestedInterface {}
       |}
       |""".stripMargin

  it should "expose extended InnerClassInfo predicates" in {
    withFixture("NestedFixture.java" -> NestedFixtureSrc) { cfs =>
      val cf = cfs("NestedFixture")
      val byName = cf.innerClasses.iterator.map(ic => ic.innerName.getOrElse("") -> ic).toMap

      val staticNested = byName.getOrElse("StaticNested", fail("StaticNested not in InnerClasses"))
      assert(staticNested.isStatic && staticNested.isPublic)

      val privateInner = byName.getOrElse("PrivateInner", fail("PrivateInner not in InnerClasses"))
      assert(privateInner.isPrivate)
      assert(!privateInner.isStatic && !privateInner.isPublic && !privateInner.isProtected)

      val protFinal =
        byName.getOrElse("ProtectedFinalInner", fail("ProtectedFinalInner not in InnerClasses"))
      assert(protFinal.isProtected && protFinal.isFinal)

      val nestedIface =
        byName.getOrElse("NestedInterface", fail("NestedInterface not in InnerClasses"))
      assert(nestedIface.isInterface && nestedIface.isAbstract && nestedIface.isStatic)
    }
  }

  // --- isMain (name + descriptor based) ---

  it should "detect a main method via isMain" in {
    // Three classes to exercise positive + negative paths together: the canonical main,
    // a same-name overload with the wrong descriptor, and a method named main but on an
    // instance (no static) — all separated into distinct classes since Java forbids
    // overloads that differ only in `static` modifier or only in name/return-type combos.
    val src =
      """|public class MainFixture {
         |  public static void main(String[] args) {}
         |}
         |class WrongDescriptor {
         |  public static void main(int x) {}
         |}
         |class NotStatic {
         |  public void main(String[] args) {}
         |}
         |""".stripMargin
    withFixture("MainFixture.java" -> src) { cfs =>
      val mainCf = cfs.getOrElse("MainFixture", fail(s"no MainFixture in ${cfs.keys}"))
      val mains = mainCf.methods.filter(_.isMain)
      assert(mains.length === 1, s"expected exactly one isMain match, got $mains")
      val m = mains.head
      assert(m.name.contains("main"))
      assert(m.descriptor.contains("([Ljava/lang/String;)V"))
      assert(m.isPublic && m.isStatic)

      // Negative: a method named `main` with a different descriptor is not isMain.
      val wrongDesc = cfs("WrongDescriptor")
      val wrongDescMain = wrongDesc.methods.find(_.name.contains("main")).get
      assert(!wrongDescMain.isMain)

      // Negative: a method with the right name+descriptor but instance-level is not isMain.
      val notStatic = cfs("NotStatic")
      val instanceMain = notStatic.methods.find(_.name.contains("main")).get
      assert(!instanceMain.isMain)
    }
  }

  // --- InnerClassInfo: isAnnotation, isEnum ---

  it should "detect ACC_ANNOTATION on a nested annotation type's InnerClasses entry" in {
    val src =
      """|public class NestedAnnoHost {
         |  public @interface Nested { String value() default ""; }
         |}
         |""".stripMargin
    withFixture("NestedAnnoHost.java" -> src) { cfs =>
      val host = cfs("NestedAnnoHost")
      val nested = host.innerClasses
        .find(_.innerName.contains("Nested"))
        .getOrElse(fail("Nested not in InnerClasses"))
      assert(nested.isAnnotation)
      assert(nested.isInterface) // annotations are interfaces
      assert(nested.isStatic) // nested annotations are implicitly static
    }
  }

  it should "detect ACC_ENUM on a nested enum's InnerClasses entry" in {
    val src =
      """|public class NestedEnumHost {
         |  public enum Nested { A, B }
         |}
         |""".stripMargin
    withFixture("NestedEnumHost.java" -> src) { cfs =>
      val host = cfs("NestedEnumHost")
      val nested = host.innerClasses
        .find(_.innerName.contains("Nested"))
        .getOrElse(fail("Nested not in InnerClasses"))
      assert(nested.isEnum)
      assert(nested.isStatic) // nested enums are implicitly static
    }
  }

  // --- Synthetic markers ---
  //
  // For FieldInfo / cf (class-level), we have reliable javac triggers:
  //   - Enum's auto-generated `$VALUES` static-final array is ACC_SYNTHETIC.
  //   - `package-info.class` (compiled from package-info.java with a package
  //     annotation) is ACC_INTERFACE | ACC_ABSTRACT | ACC_SYNTHETIC.
  //
  // For InnerClassInfo.isSynthetic and MethodInfo.isStrict, modern javac no
  // longer emits these bits in source-compiled output — lambda capture moved to
  // invokedynamic (no synthetic inner-class entries from javac), and JEP 306
  // (Java 17) made strictfp implicit, so the bit is never set. Both still occur
  // in bytecode produced by older javac or by bytecode rewriters (ASM, ProGuard,
  // Mockito, etc.), so we still expose the predicates. The parser stores the
  // raw access-flag int verbatim — the predicate's bit-mask is the only logic
  // worth checking — so we exercise those via direct case-class construction.

  it should "detect ACC_SYNTHETIC on a FieldInfo via enum's $VALUES" in {
    val src = "public enum SyntheticFieldFixture { A, B }"
    withFixture("SyntheticFieldFixture.java" -> src) { cfs =>
      val cf = cfs("SyntheticFieldFixture")
      val values = cf.fields.find(_.name.contains("$VALUES")).getOrElse {
        fail(s"no $$VALUES in ${cf.fields.map(_.name).toSeq}")
      }
      assert(values.isSynthetic)
      assert(values.isPrivate && values.isStatic && values.isFinal)
    }
  }

  it should "detect class-level ACC_SYNTHETIC on a package-info classfile" in {
    sbt.io.IO.withTemporaryDirectory { temp =>
      val pkgDir = new java.io.File(temp, "annotated_pkg")
      pkgDir.mkdir()
      val src = new java.io.File(pkgDir, "package-info.java")
      sbt.io.IO.write(src, "@Deprecated\npackage annotated_pkg;\n")
      val outDir = new java.io.File(temp, "out")
      outDir.mkdir()
      JavaCompilerForUnitTesting.compileJava(Seq(src), outDir, Seq.empty)
      val classFile =
        (sbt.io.PathFinder(outDir) ** "package-info.class").get().headOption.getOrElse {
          fail(s"package-info.class not produced under ${outDir.getAbsolutePath}")
        }
      val cf = Parser(classFile.toPath, ConsoleLogger())
      assert(cf.isSynthetic, "package-info should be synthetic")
      assert(cf.isInterface)
      assert(cf.isAbstract)
    }
  }

  it should "detect ACC_SYNTHETIC on an InnerClassInfo via the bit mask" in {
    val info = InnerClassInfo(
      accessFlags = InnerClassAccessFlags.ACC_SYNTHETIC,
      innerName = Some("Synth"),
      innerClassName = "Outer$Synth",
      outerClassName = "Outer"
    )
    assert(info.isSynthetic)
  }

  it should "detect ACC_STRICT on a MethodInfo via the bit mask" in {
    val m = MethodInfo(
      accessFlags = MethodAccessFlags.ACC_STRICT,
      name = Some("legacyStrict"),
      descriptor = Some("()V"),
      attributes = IndexedSeq.empty
    )
    assert(m.isStrict)
  }
}
