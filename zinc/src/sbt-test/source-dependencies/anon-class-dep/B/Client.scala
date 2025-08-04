package B

import A.Refined

class Client {
  def temp = new Refined().select().using(null)
}
