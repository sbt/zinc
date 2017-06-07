// Circular declarations
class E {
  type T <: F
}

class F {
  type T <: E
}

// Circular declaration with inheritance
class G {
  type T <: H
}

class H extends G
