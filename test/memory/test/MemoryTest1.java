package memory.test;

import java.util.Optional;

import nz.sodium.Cell;
import nz.sodium.Stream;
import nz.sodium.StreamSink;
import nz.sodium.Tuple2;

public class MemoryTest1 {
  public static void main(String[] args) {
    
    var m = new Monitor(5_000);
    var et = new StreamSink<Integer>();
    var t = et.hold(0);
    var etens = et.map(x -> x / 10);
    var changeTens = Stream.filterOptional(et.snapshot(t, (neu, old) -> neu.equals(old) ? Optional.empty() : Optional.of(neu)));
    var oout = changeTens.map(tens -> t.map(tt -> new Tuple2<>(tens, tt)))
                         .hold(t.map(tt -> new Tuple2<>(0, tt)));
    var out = Cell.switchC(oout);
    var l = out.listen(tu -> {
      // System.out.println(tu.a+","+tu.b);
    });
    var i = 0;
    while (i < 5_000) { // 1_000_000_000
      et.send(i);
      m.iteration = i++;
    }
    l.unlisten();
  }
}
