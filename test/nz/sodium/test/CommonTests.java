package nz.sodium.test;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import nz.sodium.Operational;
import nz.sodium.StreamSink;
import nz.sodium.Transaction;

class CommonTests {

  @Test
  void test_Base_send1() {
    var s = Transaction.run(() -> new StreamSink<String>());
    var out = new ArrayList<String>();
    var l = Transaction.run(() -> s.listen(out::add));
    Transaction.runVoid(() -> s.send("a"));
    Transaction.runVoid(() -> s.send("b"));
    l.unlisten();
    assertEquals(Arrays.asList("a", "b"), out);
  }

  @Test
  void test_Operational_split() {
    var a = Transaction.run(() -> new StreamSink<List<String>>());
    var b = Transaction.run(() -> Operational.split(a));
    var b_0 = new ArrayList<String>();
    var b_0_l = Transaction.run(() -> b.listen(b_0::add));
    Transaction.runVoid(() -> a.send(Arrays.asList("a", "b")));
    b_0_l.unlisten();
    assertEquals(Arrays.asList("a", "b"), b_0);
  }

  @Test
  void test_Operational_defer1() {
    var a = Transaction.run(() -> new StreamSink<String>());
    var b = Transaction.run(() -> Operational.defer(a));
    var b_0 = new ArrayList<String>();
    var b_0_l = Transaction.run(() -> b.listen(b_0::add));
    Transaction.runVoid(() -> a.send("a"));
    b_0_l.unlisten();
    assertEquals(Arrays.asList("a"), b_0);
    var b_1 = new ArrayList<String>();
    var b_1_l = Transaction.run(() -> b.listen(b_1::add));
    Transaction.runVoid(() -> a.send("b"));
    b_1_l.unlisten();
    assertEquals(Arrays.asList("b"), b_1);
  }

  @Test
  void test_Operational_defer2() {
    var a = Transaction.run(() -> new StreamSink<String>());
    var b = Transaction.run(() -> new StreamSink<String>());
    var c = Transaction.run(() -> Operational.defer(a).orElse(b));
    var c_0 = new ArrayList<String>();
    var c_0_l = Transaction.run(() -> c.listen(c_0::add));
    Transaction.runVoid(() -> a.send("a"));
    c_0_l.unlisten();
    assertEquals(Arrays.asList("a"), c_0);
    var c_1 = new ArrayList<String>();
    var c_1_l = Transaction.run(() -> c.listen(c_1::add));
    Transaction.runVoid(() -> { a.send("b"); b.send("B"); });
    c_1_l.unlisten();
    assertEquals(Arrays.asList("B", "b"), c_1);
  }

  @Test
  void test_Stream_orElse1() {
    var a = Transaction.run(() -> new StreamSink<Integer>());
    var b = Transaction.run(() -> new StreamSink<Integer>());
    var c = Transaction.run(() -> a.orElse(b));
    var c_0 = new ArrayList<Integer>();
    var c_0_l = Transaction.run(() -> c.listen(c_0::add));
    Transaction.runVoid(() -> a.send(0));
    c_0_l.unlisten();
    assertEquals(Arrays.asList(0), c_0);
    var c_1 = new ArrayList<Integer>();
    var c_1_l = Transaction.run(() -> c.listen(c_1::add));
    Transaction.runVoid(() -> b.send(10));
    c_1_l.unlisten();
    assertEquals(Arrays.asList(10), c_1);
    var c_2 = new ArrayList<Integer>();
    var c_2_l = Transaction.run(() -> c.listen(c_2::add));
    Transaction.runVoid(() -> { a.send(2); b.send(20); });
    c_2_l.unlisten();
    assertEquals(Arrays.asList(2), c_2);
    var c_3 = new ArrayList<Integer>();
    var c_3_l = Transaction.run(() -> c.listen(c_3::add));
    Transaction.runVoid(() -> b.send(30));
    c_3_l.unlisten();
    assertEquals(Arrays.asList(30), c_3);
  }

  @Test
  void test_Operational_deferSimultaneous() {
    var a = Transaction.run(() -> new StreamSink<String>());
    var b = Transaction.run(() -> new StreamSink<String>());
    var c = Transaction.run(() -> Operational.defer(a).orElse(Operational.defer(b)));
    var c_0 = new ArrayList<String>();
    var c_0_l = Transaction.run(() -> c.listen(c_0::add));
    Transaction.runVoid(() -> b.send("A"));
    c_0_l.unlisten();
    assertEquals(Arrays.asList("A"), c_0);
    var c_1 = new ArrayList<String>();
    var c_1_l = Transaction.run(() -> c.listen(c_1::add));
    Transaction.runVoid(() -> { a.send("b"); b.send("B"); });
    c_1_l.unlisten();
    assertEquals(Arrays.asList("b"), c_1);
  }

}
