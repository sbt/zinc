package qwe

object BuyOrderDataImplTest extends App {
  val b: BuyOrderDataImpl2 = new BuyOrderDataImpl2

  b.findOne(new BuyOrderData2.Filter())
}
