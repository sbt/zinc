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

import java.io.Serializable;
import java.util.*;
import java.lang.annotation.*;

// ---------------------------------------------------------------------------
// Annotations used as metadata (stresses annotation-attribute parser)
// ---------------------------------------------------------------------------

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@interface Stress {
    Class<? extends Comparable<?>>[] comparators() default {};
    Class<?>[] witnesses()                          default {};
    String     note()                               default "";
}

// ---------------------------------------------------------------------------
// 1. Deep single-parameter F-bound chain
//    Each level adds one more layer to the Signature attribute.
// ---------------------------------------------------------------------------

interface L0<T extends L0<T>> {
    T self();

    // Signature cycle through method return types. `companion()` names Mirror,
    // and Mirror.origin() names L0 again — so following method signatures without
    // memoizing visited types loops forever:
    //   L0.companion -> Mirror<T> -> Mirror.origin -> L0<T> -> L0.companion -> ...
    Mirror<T> companion();

    default int depth() { return 0; }
}

// The other half of the cycle: every method here references back to L0 (the
// first/parent type of the chain), closing the loop a naive parser would chase.
interface Mirror<T extends L0<T>> {
    L0<T> origin();
    T     pivot();
}

interface L1<T extends L1<T>> extends L0<T> {
    @Override default int depth() { return 1; }
}

interface L2<T extends L2<T>> extends L1<T> {
    @Override default int depth() { return 2; }
}

interface L3<T extends L3<T>> extends L2<T> {
    @Override default int depth() { return 3; }
}

interface L4<T extends L4<T>> extends L3<T> {
    @Override default int depth() { return 4; }
}

interface L5<T extends L5<T>> extends L4<T> {
    @Override default int depth() { return 5; }
}

interface L6<T extends L6<T>> extends L5<T> {
    @Override default int depth() { return 6; }
}

interface L7<T extends L7<T>> extends L6<T> {
    @Override default int depth() { return 7; }
}

interface L8<T extends L8<T>> extends L7<T> {
    @Override default int depth() { return 8; }
}

// Concrete leaf — produces a Signature like:
//   Ljava/lang/Object;LL8<TLeaf;>;  (simplified)
// plus bridge methods from every covariant self() override.
@Stress(note = "deep single-param F-bound leaf")
final class Leaf implements L8<Leaf> {
    @Override public Leaf self() { return this; }

    // Closes the L0 <-> Mirror signature cycle for the concrete leaf. The
    // anonymous Mirror also adds another InnerClasses entry to chew on.
    @Override public Mirror<Leaf> companion() {
        return new Mirror<Leaf>() {
            @Override public L0<Leaf> origin() { return Leaf.this; }
            @Override public Leaf     pivot()  { return Leaf.this; }
        };
    }
}

// ---------------------------------------------------------------------------
// 2. Multi-parameter mutually-constrained F-bounds
//    A <-> B mutual reference via a shared carrier interface.
// ---------------------------------------------------------------------------

interface Carrier<A extends Carrier<A, B>, B extends Carrier<B, A>> {
    A left();
    B right();
}

@Stress(note = "mutual F-bound left")
final class LeftNode implements Carrier<LeftNode, RightNode> {
    @Override public LeftNode  left()  { return this; }
    @Override public RightNode right() { return new RightNode(); }
}

@Stress(note = "mutual F-bound right")
final class RightNode implements Carrier<RightNode, LeftNode> {
    @Override public RightNode left()  { return this; }
    @Override public LeftNode  right() { return new LeftNode(); }
}

// ---------------------------------------------------------------------------
// 2b. Mutually recursive type parameters across two type constructors.
//     Type *variables* can't cycle directly (`<A extends B, B extends A>` is
//     rejected), but they CAN be mutually recursive *through* generic types:
//     each interface's parameters are bounded by the other interface, and each
//     is additionally F-bounded on itself. The bound graph has a 2-cycle
//     (Yin <-> Yang) that a naive bound-resolver must break.
// ---------------------------------------------------------------------------

interface Yin<Y extends Yin<Y, Z>, Z extends Yang<Z, Y>> {
    Z yang();   // crosses to the other constructor
    Y self();   // F-bound on this one
}

interface Yang<Z extends Yang<Z, Y>, Y extends Yin<Y, Z>> {
    Y yin();
    Z self();
}

@Stress(note = "mutually recursive type params (yin)")
final class ConcYin implements Yin<ConcYin, ConcYang> {
    @Override public ConcYang yang() { return new ConcYang(); }
    @Override public ConcYin  self() { return this; }
}

@Stress(note = "mutually recursive type params (yang)")
final class ConcYang implements Yang<ConcYang, ConcYin> {
    @Override public ConcYin  yin()  { return new ConcYin(); }
    @Override public ConcYang self() { return this; }
}

// ---------------------------------------------------------------------------
// 3. Wildcard capture inside F-bound + Comparable
//    Signature explosion: <T:Ljava/lang/Comparable<-TT;>;>
// ---------------------------------------------------------------------------

@Stress(note = "wildcard in F-bound with Comparable")
abstract class WildStress<T extends Comparable<? super T>>
        implements Comparable<WildStress<T>> {

    abstract T value();

    @Override
    public int compareTo(WildStress<T> other) {
        return this.value().compareTo(other.value());
    }

    // Covariant return triggers a bridge method
    abstract WildStress<T> copy();
}

final class WildInt extends WildStress<Integer> {
    private final int v;
    WildInt(int v) { this.v = v; }
    @Override Integer value() { return v; }
    @Override WildInt copy()  { return new WildInt(v); }  // bridge for WildStress.copy()
}

// ---------------------------------------------------------------------------
// 4. Bridge-method proliferation via covariant overrides in a chain
//    Each class overrides a method with a covariant return type, producing
//    one synthetic bridge per level.
// ---------------------------------------------------------------------------

abstract class Base {
    abstract Base produce();
    Base consume(Base b) { return b; }
}

abstract class Mid extends Base {
    @Override abstract Mid produce();   // bridge: Base produce()
    @Override Mid consume(Base b) { return (Mid) b; }
}

abstract class Top extends Mid {
    @Override abstract Top produce();   // bridge: Mid produce(), Base produce()
    @Override Top consume(Base b) { return (Top) b; }
}

@Stress(note = "covariant bridge proliferation")
final class Tip extends Top {
    @Override public Tip produce() { return this; }  // 3 bridges total
    @Override public Tip consume(Base b) { return (Tip) b; }
}

// ---------------------------------------------------------------------------
// 5. Raw-type / generic mixed inheritance
//    SomeRaw extends a raw generic; javac emits unchecked-cast bridge logic.
// ---------------------------------------------------------------------------

abstract class GenericBase<T> {
    abstract T get();
    abstract void set(T value);
}

@SuppressWarnings({"rawtypes", "unchecked"})
final class SomeRaw extends GenericBase {  // raw supertype
    private Object val;
    @Override public Object get()         { return val; }
    @Override public void   set(Object v) { val = v; }
}

// ---------------------------------------------------------------------------
// 6. Deeply nested generic inner classes
//    Signature of the innermost class encodes all enclosing type parameters.
// ---------------------------------------------------------------------------

@Stress(note = "nested generic inner class signatures")
class Outer<A> {
    class Middle<B extends Comparable<B>> {
        class Inner<C extends L4<C>> {
            // Signature references A, B, C plus their bounds
            Map<A, Map<B, List<C>>> combined() { return Collections.emptyMap(); }

            // Stress: method return type references all three levels
            Inner<C> innerSelf() { return this; }
        }
    }
}

// ---------------------------------------------------------------------------
// 7. Default method diamond
//    Both Left7 and Right7 provide a default; concrete class must override.
//    Produces synthetic accessor / forwarder methods in the class file.
// ---------------------------------------------------------------------------

interface DiamondTop {
    default String name() { return "top"; }
}

interface DiamondLeft extends DiamondTop {
    @Override default String name() { return "left"; }
}

interface DiamondRight extends DiamondTop {
    @Override default String name() { return "right"; }
}

@Stress(note = "diamond default method forwarders")
final class DiamondBottom implements DiamondLeft, DiamondRight {
    @Override public String name() { return DiamondLeft.super.name() + "+" + DiamondRight.super.name(); }
}

// ---------------------------------------------------------------------------
// 8. Generic method with multiple independent F-bounds + throws clause
//    Stresses method-descriptor and exception-table parsing together.
// ---------------------------------------------------------------------------

@Stress(
    comparators = { String.class, Integer.class },
    witnesses   = { Leaf.class, WildInt.class },
    note        = "multi-bound generic method"
)
final class Utilities {

    /**
     * Mutually recursive type parameters within a single method signature:
     * A is bounded by Comparable&lt;B&gt; and B by Comparable&lt;A&gt;. Legal because the
     * recursion passes through Comparable rather than being a bare variable cycle.
     * The method Signature attribute encodes both bounds referencing each other.
     */
    public static <A extends Comparable<B>, B extends Comparable<A>>
    int mutualCompare(A a, B b) {
        return a.compareTo(b) - b.compareTo(a);
    }

    /**
     * A method whose type parameter has two independent upper bounds (produces &-separator
     * in the Signature attribute) plus a checked exception.
     */
    public static <T extends L8<T> & Serializable & Comparable<T>>
    T roundTrip(T value) throws CloneNotSupportedException {
        // The cast here is legal but forces the verifier to check bridge compatibility.
        @SuppressWarnings("unchecked")
        T copy = (T) value;
        return copy.self();
    }

    /**
     * Wildcard hell: nested wildcards in both parameter and return position.
     * Exercises signature parsing of `? extends List<? super T>`.
     */
    public static <T extends Comparable<? super T>>
    List<? extends T> sortedCopy(Collection<? extends T> src) {
        List<T> result = new ArrayList<>(src);
        Collections.sort(result);
        return result;
    }

    /**
     * Higher-kinded simulation via a Function interface chain.
     * The Signature on this method is notably long.
     */
    public static <A extends L4<A>, B extends WildStress<? extends Comparable<? super A>>>
    Map<A, B> crossProduct(List<A> as, List<B> bs) {
        Map<A, B> out = new LinkedHashMap<>();
        for (A a : as) for (B b : bs) out.put(a, b);
        return out;
    }
}

// ---------------------------------------------------------------------------
// 9. Enum with abstract method + generic interface
//    Each enum constant becomes an anonymous class in the class file.
//    Parsers iterating InnerClasses attributes will see all of them.
// ---------------------------------------------------------------------------

@Stress(note = "enum anonymous-class inner entries")
enum Transformer implements L0<Transformer> {
    IDENTITY {
        @Override public <T> T apply(T x) { return x; }
        @Override public Transformer self() { return IDENTITY; }
    },
    BOXED {
        @Override public <T> T apply(T x) { return x; }  // simplified
        @Override public Transformer self() { return BOXED; }
    };

    public abstract <T> T apply(T x);

    // Shared (non-abstract) — satisfies L0.companion() for every enum constant
    // and re-enters the L0 <-> Mirror signature cycle.
    @Override public Mirror<Transformer> companion() {
        return new Mirror<Transformer>() {
            @Override public L0<Transformer> origin() { return Transformer.this; }
            @Override public Transformer     pivot()  { return Transformer.this; }
        };
    }
}

// ---------------------------------------------------------------------------
// Main — just enough to force class loading and expose any parse errors.
// ---------------------------------------------------------------------------

public class FBoundedStress {
    public static void main(String[] args) throws Exception {
        // Force class initialization (also ensures the class files are all valid)
        System.out.println("Leaf depth      : " + new Leaf().depth());
        // Walk the L0 <-> Mirror signature cycle one full lap at runtime.
        System.out.println("Cycle lap       : " + new Leaf().companion().origin().companion().pivot().depth());
        System.out.println("Carrier mutual  : " + new LeftNode().right().left().getClass().getSimpleName());
        System.out.println("Yin/Yang        : " + new ConcYin().yang().yin().getClass().getSimpleName());
        System.out.println("mutualCompare   : " + Utilities.mutualCompare("a", "b"));
        System.out.println("WildInt cmp     : " + new WildInt(1).compareTo(new WildInt(2)));
        System.out.println("Tip produce     : " + new Tip().produce().getClass().getSimpleName());
        System.out.println("Diamond name    : " + new DiamondBottom().name());
        System.out.println("SomeRaw get     : " + ((java.util.function.Supplier<Object>) () -> { var r = new SomeRaw(); r.set("x"); return r.get(); }).get());
        System.out.println("Utilities sort  : " + Utilities.sortedCopy(List.of(3, 1, 2)));
        System.out.println("Transformer     : " + Transformer.IDENTITY.apply("ok"));

        // Nested inner — exercises Outer<A>.Middle<B>.Inner<C> signatures
        Outer<String>.Middle<Integer>.Inner<Leaf> inner =
            new Outer<String>().new Middle<Integer>().new Inner<Leaf>();
        System.out.println("Inner self      : " + (inner.innerSelf() == inner));
    }
}
