package memory.test;

import nz.sodium.Cell;
import nz.sodium.StreamSink;

public class MemoryTest3 {
  public static void main(String[] args) {

    var m = new Monitor(1_000);
    var et = new StreamSink<Integer>();
    var t = et.hold(0);
    var eChange = new StreamSink<Integer>();
    var oout = eChange.map(x -> t).hold(t);
    var out = Cell.switchC(oout);
    var l = out.listen(tt -> {
      // System.out.println(tt)
    });
    var i = 0;
    while (i < 1_000_000) { // 1_000_000_000
      eChange.send(i);
      m.iteration = i++;
    }
    l.unlisten();
  }
}
