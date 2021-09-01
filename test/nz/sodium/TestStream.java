package nz.sodium;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Optional;

class TestStream {

  @AfterEach
  void tearDown() throws Exception {
    System.gc();
    Thread.sleep(100);
  }

  @Test
  void testSendStream() {
    var e = new StreamSink<Integer>();
    var out = new ArrayList<Integer>();
    var l = e.listen(x -> out.add(x));
    e.send(5);
    l.unlisten();
    assertEquals(Arrays.asList(5), out);
    e.send(6);
    assertEquals(Arrays.asList(5), out);
  }

  @Test
  void testMap() {
    var e = new StreamSink<Integer>();
    var m = e.map(x -> Integer.toString(x));
    var out = new ArrayList<String>();
    var l = m.listen(x -> out.add(x));
    e.send(5);
    l.unlisten();
    assertEquals(Arrays.asList("5"), out);
  }

  @Test
  void testMapTo() {
    var e = new StreamSink<Integer>();
    var m = e.mapTo("fusebox");
    var out = new ArrayList<String>();
    var l = m.listen(x -> out.add(x));
    e.send(5);
    e.send(6);
    l.unlisten();
    assertEquals(Arrays.asList("fusebox", "fusebox"), out);
  }

  @Test
  void testMergeNonSimultaneous() {
    var e1 = new StreamSink<Integer>();
    var e2 = new StreamSink<Integer>();
    var out = new ArrayList<Integer>();
    var l = e2.orElse(e1).listen(x -> out.add(x));
    e1.send(7);
    e2.send(9);
    e1.send(8);
    l.unlisten();
    assertEquals(Arrays.asList(7, 9, 8), out);
  }

  @Test
  void testMergeSimultaneous() {
    var s1 = new StreamSink<Integer>((l, r) -> r);
    var s2 = new StreamSink<Integer>((l, r) -> r);
    var out = new ArrayList<Integer>();
    var l = s2.orElse(s1).listen(x -> out.add(x));
    Transaction.runVoid(() -> {
      s1.send(7);
      s2.send(60);
    });
    Transaction.runVoid(() -> {
      s1.send(9);
    });
    Transaction.runVoid(() -> {
      s1.send(7);
      s1.send(60);
      s2.send(8);
      s2.send(90);
    });
    Transaction.runVoid(() -> {
      s2.send(8);
      s2.send(90);
      s1.send(7);
      s1.send(60);
    });
    Transaction.runVoid(() -> {
      s2.send(8);
      s1.send(7);
      s2.send(90);
      s1.send(60);
    });
    l.unlisten();
    assertEquals(Arrays.asList(60, 9, 90, 90, 90), out);
  }

  @Test
  void testCoalesce() {
    var s = new StreamSink<Integer>((Integer a, Integer b) -> a + b);
    var out = new ArrayList<Integer>();
    var l = s.listen(x -> out.add(x));
    Transaction.runVoid(() -> {
      s.send(2);
    });
    Transaction.runVoid(() -> {
      s.send(8);
      s.send(40);
    });
    l.unlisten();
    assertEquals(Arrays.asList(2, 48), out);
  }

  @Test
  void testFilter() {
    var e = new StreamSink<Character>();
    var out = new ArrayList<Character>();
    var l = e.filter(c -> Character.isUpperCase(c)).listen(c -> out.add(c));
    e.send('H');
    e.send('o');
    e.send('I');
    l.unlisten();
    assertEquals(Arrays.asList('H', 'I'), out);
  }

  @Test
  void testFilterOptional() {
    var e = new StreamSink<Optional<String>>();
    var out = new ArrayList<String>();
    var l = Stream.filterOptional(e).listen(s -> out.add(s));
    e.send(Optional.of("tomato"));
    e.send(Optional.empty());
    e.send(Optional.of("peach"));
    l.unlisten();
    assertEquals(Arrays.asList("tomato", "peach"), out);
  }

  @Test
  void testLoopStream() {
    var ea = new StreamSink<Integer>();
    var ec = Transaction.<Stream<Integer>>run(() -> {
      var eb = new StreamLoop<Integer>();
      var ec_ = ea.map(x -> x % 10).merge(eb, (x, y) -> x + y);
      var eb_out = ea.map(x -> x / 10).filter(x -> x != 0);
      eb.loop(eb_out);
      return ec_;
    });
    var out = new ArrayList<Integer>();
    var l = ec.listen(x -> out.add(x));
    ea.send(2);
    ea.send(52);
    l.unlisten();
    assertEquals(Arrays.asList(2, 7), out);
  }

  @Test
  void testGate() {
    var ec = new StreamSink<Character>();
    var epred = new CellSink<Boolean>(true);
    var out = new ArrayList<Character>();
    var l = ec.gate(epred).listen(x -> out.add(x));
    ec.send('H');
    epred.send(false);
    ec.send('O');
    epred.send(true);
    ec.send('I');
    l.unlisten();
    assertEquals(Arrays.asList('H', 'I'), out);
  }

  @Test
  void testCollect() {
    var ea = new StreamSink<Integer>();
    var out = new ArrayList<Integer>();
    var sum = ea.collect(0, (a, s) -> new Tuple2<>(a + s + 100, a + s));
    var l = sum.listen(x -> out.add(x));
    ea.send(5);
    ea.send(7);
    ea.send(1);
    ea.send(2);
    ea.send(3);
    l.unlisten();
    assertEquals(Arrays.asList(105, 112, 113, 115, 118), out);
  }

  @Test
  void testAccum() {
    var ea = new StreamSink<Integer>();
    var out = new ArrayList<Integer>();
    var sum = ea.accum(100, (a, s) -> a + s);
    var l = sum.listen(x -> out.add(x));
    ea.send(5);
    ea.send(7);
    ea.send(1);
    ea.send(2);
    ea.send(3);
    l.unlisten();
    assertEquals(Arrays.asList(100, 105, 112, 113, 115, 118), out);
  }

  @Test
  void testOnce() {
    var e = new StreamSink<Character>();
    var out = new ArrayList<Character>();
    var l = e.once().listen(x -> out.add(x));
    e.send('A');
    e.send('B');
    e.send('C');
    l.unlisten();
    assertEquals(Arrays.asList('A'), out);
  }

  @Test
  void testDefer() {
    var e = new StreamSink<Character>();
    var b = e.hold(' ');
    var out = new ArrayList<Character>();
    var l = Operational.defer(e).snapshot(b).listen(x -> out.add(x));
    e.send('C');
    e.send('B');
    e.send('A');
    l.unlisten();
    assertEquals(Arrays.asList('C', 'B', 'A'), out);
  }

}
