class A { self: A => }

class B extends A
class C extends B { self: A => }

class D { self: A => }
