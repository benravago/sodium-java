package nz.sodium.memory;

import nz.sodium.Cell;
import nz.sodium.Stream;
import nz.sodium.StreamSink;

public class Memory4 extends Timed {
  public static void main(String[] args) {
    new Memory4().run();
  }
  
  int[] n = {0};
  
  void bg(long elapsed) { bg(elapsed,n[0]); }
  
  void fg() {
    var et = new StreamSink<Integer>();
    var eChange = new StreamSink<Integer>();
    var oout = eChange.map(x -> (Stream<Integer>) et).hold(et);
    var out = Cell.switchS(oout);
    var l = out.listen(tt -> {}); // System.out.println(tt);
    var i = 0;
    while (i < 50_000_000) { // 1_000_000_000
      eChange.send(i);
      n[0] = i++;
    }
    l.unlisten();
  }
}
