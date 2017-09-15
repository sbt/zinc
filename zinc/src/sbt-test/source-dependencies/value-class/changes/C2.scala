object C extends App {
  val res = new B().bar("")("").x
  res match {
    case 0 => println(Console.GREEN + "OK: " + res.toString + Console.RESET)
    case _ => println(Console.RED + "FAIL: " + res.toString + Console.RESET)
  }
}
