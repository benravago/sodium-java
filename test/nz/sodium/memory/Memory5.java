package nz.sodium.memory;

import nz.sodium.StreamSink;

public class Memory5 extends Timed {
  public static void main(String[] args) {
    new Memory5().run();
  }
  
  int[] n = {0};
  
  void bg(long elapsed) {
    bg(elapsed,n[0]);
  }
  
  void fg() {
    var eChange = new StreamSink<Integer>();
    var out = eChange.hold(0);
    var l = out.listen(tt -> {}); // System.out.println(tt)
    var i = 0;
    while (i < 50_000_000) { // 1_000_000_000
      eChange.send(i);
      n[0] = i++;
    }
    l.unlisten();
  }
}
