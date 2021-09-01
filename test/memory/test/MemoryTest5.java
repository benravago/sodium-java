package memory.test;

import nz.sodium.StreamSink;

public class MemoryTest5 {
  public static void main(String[] args) {

    var m = new Monitor(1_000);
    var eChange = new StreamSink<Integer>();
    var out = eChange.hold(0);
    var l = out.listen(tt -> {
      // System.out.println(tt)
    });
    var i = 0;
    while (i < 1_000_000_000) {
      eChange.send(i);
      m.iteration = i++;
    }
    l.unlisten();
  }
}
