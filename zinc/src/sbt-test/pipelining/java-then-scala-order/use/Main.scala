object Main {
  def value: Int = ByteArrayAccess.getInt(Array[Byte](1, 2, 3), 0)
}
