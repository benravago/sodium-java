package nz.sodium.edgecase;

import nz.sodium.StreamLoop;
import nz.sodium.StreamSink;
import nz.sodium.Transaction;

public class Repeated {

  public static void main(String[] args) {
    var sT = Transaction.run(() -> {
      var sA = new StreamSink<Integer>();
      var sB = new StreamLoop<Integer>();
      var sC = sA.orElse(sB);
      sB.loop(sC.map(x -> x + 1).filter(x -> x < 10));
      sC.listen(c -> System.out.println(c));
      return sA;
    });
    System.out.println("send 5");
    sT.send(5);
    System.out.println("sent");
    sT.close();
  }
}
