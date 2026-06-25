public class B {
  // Changing MAX's type from int to String makes A's `case B.MAX` fail to compile *iff* A is
  // recompiled, which happens only when the cross-module A->B constant dependency was recorded.
  public static final String MAX = "3";
}
