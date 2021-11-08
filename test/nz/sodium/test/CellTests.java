package nz.sodium.test;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Optional;

import nz.sodium.Cell;
import nz.sodium.CellLoop;
import nz.sodium.CellSink;
import nz.sodium.Lambda1;
import nz.sodium.Operational;
import nz.sodium.Stream;
import nz.sodium.StreamSink;
import nz.sodium.Transaction;
import nz.sodium.Unit;

class CellTests {

  @AfterEach
  void tearDown() throws Exception {
    System.gc();
    Thread.sleep(100);
  }

  @Test
  void testHold() {
    try (var e = new StreamSink<Integer>()) {
      var b = e.hold(0);
      var out = new ArrayList<Integer>();
      var l = Operational.updates(b).listen(out::add);
      e.send(2);
      e.send(9);
      l.unlisten();
      assertEquals(Arrays.asList(2, 9), out);
    }
  }

  @Test
  void testSnapshot() {
    try (var trigger = new StreamSink<Long>()) {
      var b = new CellSink<Integer>(0);
      var out = new ArrayList<String>();
      var l = trigger.snapshot(b, (x, y) -> x + " " + y).listen(out::add);
      trigger.send(100L);
      b.send(2);
      trigger.send(200L);
      b.send(9);
      b.send(1);
      trigger.send(300L);
      l.unlisten();
      assertEquals(Arrays.asList("100 0", "200 2", "300 1"), out);
    }
  }

  @Test
  void testValues() {
    try (var b = new CellSink<Integer>(9)) {
      var out = new ArrayList<Integer>();
      var l = b.listen(out::add);
      b.send(2);
      b.send(7);
      l.unlisten();
      assertEquals(Arrays.asList(9, 2, 7), out);
    }
  }

  @Test
  void testConstantBehavior() {
    try (var b = new Cell<Integer>(12)) {
      var out = new ArrayList<Integer>();
      var l = b.listen(out::add);
      l.unlisten();
      assertEquals(Arrays.asList(12), out);
    }
  }

  @Test
  void testMapC() {
    try (var b = new CellSink<Integer>(6)) {
      var out = new ArrayList<String>();
      var l = b.map(x -> x.toString()).listen(out::add);
      b.send(8);
      l.unlisten();
      assertEquals(Arrays.asList("6", "8"), out);
    }
  }

  @Test
  void testMapCLateListen() {
    try (var b = new CellSink<Integer>(6)) {
      var out = new ArrayList<String>();
      var bm = b.map(x -> x.toString());
      b.send(2);
      var l = bm.listen(out::add);
      b.send(8);
      l.unlisten();
      assertEquals(Arrays.asList("2", "8"), out);
    }
  }

  @Test
  void testApply() {
    var bf = new CellSink<Lambda1<Long, String>>((Long b) -> "1 " + b);
    var ba = new CellSink<Long>(5L);
    var out = new ArrayList<String>();
    var l = Cell.apply(bf, ba).listen(out::add);
    bf.send((Long b) -> "12 " + b);
    ba.send(6L);
    l.unlisten();
    assertEquals(Arrays.asList("1 5", "12 5", "12 6"), out);
  }

  @Test
  void testLift() {
    try (var a = new CellSink<Integer>(1)) {
      var b = new CellSink<Long>(5L);
      var out = new ArrayList<String>();
      var l = a.lift(b, (x, y) -> x + " " + y).listen(out::add);
      a.send(12);
      b.send(6L);
      l.unlisten();
      assertEquals(Arrays.asList("1 5", "12 5", "12 6"), out);
    }
  }

  @Test
  void testLiftGlitch() {
    try (var a = new CellSink<Integer>(1)) {
      var a3 = a.map((Integer x) -> x * 3);
      var a5 = a.map((Integer x) -> x * 5);
      var b = a3.lift(a5, (x, y) -> x + " " + y);
      var out = new ArrayList<String>();
      var l = b.listen(out::add);
      a.send(2);
      l.unlisten();
      assertEquals(Arrays.asList("3 5", "6 10"), out);
    }
  }

  @Test
  void testLiftFromSimultaneous() {
    var t = Transaction.run(() -> {
      var b1 = new CellSink<>(3);
      var b2 = new CellSink<>(5);
      b2.send(7);
      return new Stream.State<>(b1, b2);
    });
    var b1 = t.state();
    var b2 = t.value();
    var out = new ArrayList<Integer>();
    var l = b1.lift(b2, (x, y) -> x + y).listen(out::add);
    l.unlisten();
    assertEquals(Arrays.asList(10), out);
  }

  @Test
  void testHoldIsDelayed() {
    try (var e = new StreamSink<Integer>()) {
      var h = e.hold(0);
      var pair = e.snapshot(h, (a, b) -> a + " " + b);
      var out = new ArrayList<String>();
      var l = pair.listen(out::add);
      e.send(2);
      e.send(3);
      l.unlisten();
      assertEquals(Arrays.asList("2 0", "3 2"), out);
    }
  }

  static class SB {
    SB(Optional<Character> a, Optional<Character> b, Optional<Cell<Character>> sw) {
      this.a = a;
      this.b = b;
      this.sw = sw;
    }
    Optional<Character> a;
    Optional<Character> b;
    Optional<Cell<Character>> sw;
  }

  @Test
  void testSwitchC() {
    try (var esb = new StreamSink<SB>()) {
      // Split each field out of SB so we can update multiple behaviours in a single transaction.
      var ba = Stream.filterOptional(esb.map(s -> s.a)).hold('A');
      var bb = Stream.filterOptional(esb.map(s -> s.b)).hold('a');
      var bsw = Stream.filterOptional(esb.map(s -> s.sw)).hold(ba);
      var bo = Cell.switchC(bsw);
      var out = new ArrayList<Character>();
      var l = bo.listen(out::add);
      esb.send(new SB(Optional.of('B'), Optional.of('b'), Optional.empty()));
      esb.send(new SB(Optional.of('C'), Optional.of('c'), Optional.of(bb)));
      esb.send(new SB(Optional.of('D'), Optional.of('d'), Optional.empty()));
      esb.send(new SB(Optional.of('E'), Optional.of('e'), Optional.of(ba)));
      esb.send(new SB(Optional.of('F'), Optional.of('f'), Optional.empty()));
      esb.send(new SB(Optional.empty(), Optional.empty(), Optional.of(bb)));
      esb.send(new SB(Optional.empty(), Optional.empty(), Optional.of(ba)));
      esb.send(new SB(Optional.of('G'), Optional.of('g'), Optional.of(bb)));
      esb.send(new SB(Optional.of('H'), Optional.of('h'), Optional.of(ba)));
      esb.send(new SB(Optional.of('I'), Optional.of('i'), Optional.of(ba)));
      l.unlisten();
      assertEquals(Arrays.asList('A', 'B', 'c', 'd', 'E', 'F', 'f', 'F', 'g', 'H', 'I'), out);
    }
  }

  static class SE {
    SE(Character a, Character b, Optional<Stream<Character>> sw) {
      this.a = a;
      this.b = b;
      this.sw = sw;
    }
    Character a;
    Character b;
    Optional<Stream<Character>> sw;
  }

  @Test
  void testSwitchS() {
    try (var ese = new StreamSink<SE>()) {
      var ea = ese.map(s -> s.a);
      var eb = ese.map(s -> s.b);
      var bsw = Stream.filterOptional(ese.map(s -> s.sw)).hold(ea);
      var out = new ArrayList<Character>();
      var eo = Cell.switchS(bsw);
      var l = eo.listen(out::add);
      ese.send(new SE('A', 'a', Optional.empty()));
      ese.send(new SE('B', 'b', Optional.empty()));
      ese.send(new SE('C', 'c', Optional.of(eb)));
      ese.send(new SE('D', 'd', Optional.empty()));
      ese.send(new SE('E', 'e', Optional.of(ea)));
      ese.send(new SE('F', 'f', Optional.empty()));
      ese.send(new SE('G', 'g', Optional.of(eb)));
      ese.send(new SE('H', 'h', Optional.of(ea)));
      ese.send(new SE('I', 'i', Optional.of(ea)));
      l.unlisten();
      assertEquals(Arrays.asList('A', 'B', 'C', 'd', 'e', 'F', 'G', 'h', 'I'), out);
    }
  }

  static class SS2 {
    final StreamSink<Integer> s = new StreamSink<>();
  }

  @Test
  void testSwitchSSimultaneous() {
    var ss1 = new SS2();
    try (var css = new CellSink<SS2>(ss1)) {
      var so = Cell.switchS(css.<Stream<Integer>>map(b -> b.s));
      var out = new ArrayList<Integer>();
      var l = so.listen(out::add);
      var ss3 = new SS2();
      var ss4 = new SS2();
      var ss2 = new SS2();
      ss1.s.send(0);
      ss1.s.send(1);
      ss1.s.send(2);
      css.send(ss2);
      ss1.s.send(7);
      ss2.s.send(3);
      ss2.s.send(4);
      ss3.s.send(2);
      css.send(ss3);
      ss3.s.send(5);
      ss3.s.send(6);
      ss3.s.send(7);
      Transaction.runVoid(() -> {
        ss3.s.send(8);
        css.send(ss4);
        ss4.s.send(2);
      });
      ss4.s.send(9);
      l.unlisten();
      assertEquals(Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7, 8, 9), out);
    }
  }

  @Test
  void testLoopCell() {
    try (var sa = new StreamSink<Integer>()) {
      var sum_out = Transaction.run(() -> {
        var sum = new CellLoop<Integer>();
        var snap = sa.snapshot(sum, (x, y) -> x + y).hold(0);
        sum.loop(snap);
        return snap;
      });
      var out = new ArrayList<Integer>();
      var l = sum_out.listen(out::add);
      sa.send(2);
      sa.send(3);
      sa.send(1);
      l.unlisten();
      assertEquals(Arrays.asList(0, 2, 5, 6), out);
      assertEquals((int) 6, (int) sum_out.sample());
    }
  }

  @Test
  void testAccum() {
    try (var sa = new StreamSink<Integer>()) {
      var out = new ArrayList<Integer>();
      var sum = sa.accum(100, (a, s) -> a + s);
      var l = sum.listen(out::add);
      sa.send(5);
      sa.send(7);
      sa.send(1);
      sa.send(2);
      sa.send(3);
      l.unlisten();
      assertEquals(Arrays.asList(100, 105, 112, 113, 115, 118), out);
    }
  }

  @Test
  void testLoopValueSnapshot() {
    var out = new ArrayList<String>();
    var l = Transaction.run(() -> {
      var a = new Cell<String>("lettuce");
      var b = new CellLoop<String>();
      var eSnap = Operational.value(a).snapshot(b, (aa, bb) -> aa + " " + bb);
      b.loop(new Cell<>("cheese"));
      return eSnap.listen(out::add);
    });
    l.unlisten();
    assertEquals(Arrays.asList("lettuce cheese"), out);
  }

  @Test
  void testLoopValueHold() {
    var out = new ArrayList<String>();
    var value = Transaction.run(() -> {
      var a = new CellLoop<String>();
      var v = Operational.value(a).hold("onion");
      a.loop(new Cell<>("cheese"));
      return v;
    });
    try (var eTick = new StreamSink<Unit>()) {
      var l = eTick.snapshot(value).listen(out::add);
      eTick.send(Unit.UNIT);
      l.unlisten();
      assertEquals(Arrays.asList("cheese"), out);
    }
  }

  @Test
  void testLiftLoop() {
    var out = new ArrayList<String>();
    var b = new CellSink<String>("kettle");
    var c = Transaction.run(() -> {
      try (var a = new CellLoop<String>()) {
        var v = a.lift(b, (aa, bb) -> aa + " " + bb);
        a.loop(new Cell<>("tea"));
        return v;
      }
    });
    var l = c.listen(out::add);
    b.send("caddy");
    l.unlisten();
    assertEquals(Arrays.asList("tea kettle", "tea caddy"), out);
  }

  @Test
  void testSwitchAndDefer() {
    try (var si = new StreamSink<Integer>()) {
      var out = new ArrayList<String>();
      var l = Cell
        .switchS(si
          .map(i -> {
             var c = new Cell<String>("A" + i);
             return Operational.defer(Operational.value(c));
           })
          .hold(new Stream<>())
         )
        .listen(out::add);
      si.send(2);
      si.send(4);
      l.unlisten();
      assertEquals(Arrays.asList("A2", "A4"), out);
    }
  }

}
