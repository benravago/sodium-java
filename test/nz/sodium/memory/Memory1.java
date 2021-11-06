package nz.sodium.memory;

import java.util.Optional;

import nz.sodium.StreamSink;
import nz.sodium.Cell;
import nz.sodium.Stream;
import nz.sodium.Stream.State;

public class Memory1 extends Timed{
  public static void main(String[] args) {
    new Memory1().run();
  }
  
  int[] n = {0};

  void bg(long elapsed) { bg(elapsed,n[0]); }
  
  void fg() {
    var et = new StreamSink<Integer>();
    var t = et.hold(0);
    // var etens = et.map(x -> x / 10);
    var changeTens = Stream
      .filterOptional(et.snapshot(t, (neu, old) -> neu.equals(old) ? Optional.empty() : Optional.of(neu)));
    var oout = changeTens
      .map(tens -> t.map(tt -> new State<>(tens, tt)))
      .hold(t.map(tt -> new State<>(0, tt)));
    var out = Cell.switchC(oout);
    var l = out.listen(tu -> {}); // System.out.println(tu.a+","+tu.b);
    var i = 0;
    while (i < 50_000) { // 1_000_000_000
      et.send(i);
      n[0] = i++;
    }
    l.unlisten();
  }
}
