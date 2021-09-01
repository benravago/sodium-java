package memory.test;

import nz.sodium.Cell;
import nz.sodium.Stream;
import nz.sodium.StreamSink;

public class MemoryTest4 {
  public static void main(String[] args) {

    var m = new Monitor(1_000);
    var et = new StreamSink<Integer>();
    var eChange = new StreamSink<Integer>();
    var oout = eChange.map(x -> (Stream<Integer>) et).hold(et);
    var out = Cell.switchS(oout);
    var l = out.listen(tt -> {
      // System.out.println(tt);
    });
    var i = 0;
    while (i < 1_000_000_000) { // 1_000_000_000
      eChange.send(i);
      m.iteration = i++;
    }
    l.unlisten();
  }
}
