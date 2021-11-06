package nz.sodium.memory;

import nz.sodium.Cell;
import nz.sodium.StreamSink;

public class Memory3 extends Timed {
  public static void main(String[] args) {
    new Memory3().run();
  }
  
  int[] n = {0};

  void bg(long elapsed) { bg(elapsed,n[0]); }
  
  void fg() {
    var et = new StreamSink<Integer>();
    var t = et.hold(0);
    var eChange = new StreamSink<Integer>();
    var oout = eChange.map(x -> t).hold(t);
    var out = Cell.switchC(oout);
    var l = out.listen(tt -> {}); // System.out.println(tt)
    var i = 0;
    while (i < 1_000_000) { // 1_000_000_000
      eChange.send(i);
      n[0] = i++;
    }
    l.unlisten();
  }
}
