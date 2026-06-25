// `use` depends on `dep` ONLY through dep's inlined static-final constant B.MAX, used as a
// switch-case label. javac inlines the value AND erases every reference to B from A.class (verified
// with javap), so the cross-module A->B dependency can be recovered only from javac's attributed
// AST (sbt/zinc#145). Without it, changing B.MAX would not recompile A and A would go stale.
public class A {
  public int g(int x) {
    switch (x) {
      case B.MAX:
        return 1;
      default:
        return 0;
    }
  }
}
