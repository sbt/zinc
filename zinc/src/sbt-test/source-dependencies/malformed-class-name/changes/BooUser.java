package repro;

import repro.Boo;

public class BooUser {
  private static Boo getOwnBuf() {
    return new Boo() {};
  }

  public static void main(String[] args) {
    Boo boo = getOwnBuf();
    System.out.println("Made a buf: " + boo);
  }
}
