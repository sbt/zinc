object C extends App {
  val res = new B().foo(null)
  res match {
    case 1 => println(Console.GREEN + "OK: " + res.toString + Console.RESET)
    case _ => println(Console.RED + "FAIL: " + res.toString + Console.RESET)
  }
}
