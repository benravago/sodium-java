package nz.sodium;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

class TestCommon {

  @AfterEach
  void tearDown() throws Exception {
    System.gc();
    Thread.sleep(100);
  }

  @Test
  void test_Base_send1() {
    var s = Transaction.run(() -> {
      var s_ = new StreamSink<String>();
      return s_;
    });
    var out = new ArrayList<String>();
    var l = Transaction.run(() -> {
      var l_ = s.listen(a -> out.add(a));
      return l_;
    });
    Transaction.runVoid(() -> {
      s.send("a");
    });
    Transaction.runVoid(() -> {
      s.send("b");
    });
    l.unlisten();
    assertEquals(Arrays.<String>asList("a", "b"), out);
  }

  @Test
  void test_Operational_split() {
    var a = Transaction.run(() -> {
      var a_ = new StreamSink<List<String>>();
      return a_;
    });
    var b = Transaction.run(() -> {
      var b_ = Operational.split(a);
      return b_;
    });
    var b_0 = new ArrayList<String>();
    var b_0_l = Transaction.run(() -> {
      var b_0_l_ = b.listen(val -> b_0.add(val));
      return b_0_l_;
    });
    Transaction.runVoid(() -> {
      a.send(Arrays.<String>asList("a", "b"));
    });
    b_0_l.unlisten();
    assertEquals(Arrays.<String>asList("a", "b"), b_0);
  }

  @Test
  void test_Operational_defer1() {
    var a = Transaction.run(() -> {
      var a_ = new StreamSink<String>();
      return a_;
    });
    var b = Transaction.run(() -> {
      var b_ = Operational.defer(a);
      return b_;
    });
    var b_0 = new ArrayList<String>();
    var b_0_l = Transaction.run(() -> {
      var b_0_l_ = b.listen(val -> b_0.add(val));
      return b_0_l_;
    });
    Transaction.runVoid(() -> {
      a.send("a");
    });
    b_0_l.unlisten();
    assertEquals(Arrays.<String>asList("a"), b_0);
    var b_1 = new ArrayList<String>();
    var b_1_l = Transaction.run(() -> {
      var b_1_l_ = b.listen(val -> b_1.add(val));
      return b_1_l_;
    });
    Transaction.runVoid(() -> {
      a.send("b");
    });
    b_1_l.unlisten();
    assertEquals(Arrays.<String>asList("b"), b_1);
  }

  @Test
  void test_Operational_defer2() {
    var a = Transaction.run(() -> {
      var a_ = new StreamSink<String>();
      return a_;
    });
    var b = Transaction.run(() -> {
      var b_ = new StreamSink<String>();
      return b_;
    });
    var c = Transaction.run(() -> {
      var c_ = Operational.defer(a).orElse(b);
      return c_;
    });
    var c_0 = new ArrayList<String>();
    var c_0_l = Transaction.run(() -> {
      var c_0_l_ = c.listen(val -> c_0.add(val));
      return c_0_l_;
    });
    Transaction.runVoid(() -> {
      a.send("a");
    });
    c_0_l.unlisten();
    assertEquals(Arrays.<String>asList("a"), c_0);
    var c_1 = new ArrayList<String>();
    var c_1_l = Transaction.run(() -> {
      var c_1_l_ = c.listen(val -> c_1.add(val));
      return c_1_l_;
    });
    Transaction.runVoid(() -> {
      a.send("b");
      b.send("B");
    });
    c_1_l.unlisten();
    assertEquals(Arrays.<String>asList("B", "b"), c_1);
  }

  @Test
  void test_Stream_orElse1() {
    var a = Transaction.run(() -> {
      var a_ = new StreamSink<Integer>();
      return a_;
    });
    var b = Transaction.run(() -> {
      var b_ = new StreamSink<Integer>();
      return b_;
    });
    var c = Transaction.run(() -> {
      var c_ = a.orElse(b);
      return c_;
    });
    var c_0 = new ArrayList<Integer>();
    var c_0_l = Transaction.run(() -> {
      var c_0_l_ = c.listen(val -> c_0.add(val));
      return c_0_l_;
    });
    Transaction.runVoid(() -> {
      a.send(0);
    });
    c_0_l.unlisten();
    assertEquals(Arrays.<Integer>asList(0), c_0);
    var c_1 = new ArrayList<Integer>();
    var c_1_l = Transaction.run(() -> {
      var c_1_l_ = c.listen(val -> c_1.add(val));
      return c_1_l_;
    });
    Transaction.runVoid(() -> {
      b.send(10);
    });
    c_1_l.unlisten();
    assertEquals(Arrays.<Integer>asList(10), c_1);
    var c_2 = new ArrayList<Integer>();
    var c_2_l = Transaction.run(() -> {
      var c_2_l_ = c.listen(val -> c_2.add(val));
      return c_2_l_;
    });
    Transaction.runVoid(() -> {
      a.send(2);
      b.send(20);
    });
    c_2_l.unlisten();
    assertEquals(Arrays.<Integer>asList(2), c_2);
    var c_3 = new ArrayList<Integer>();
    var c_3_l = Transaction.run(() -> {
      var c_3_l_ = c.listen(val -> c_3.add(val));
      return c_3_l_;
    });
    Transaction.runVoid(() -> {
      b.send(30);
    });
    c_3_l.unlisten();
    assertEquals(Arrays.<Integer>asList(30), c_3);
  }

  @Test
  void test_Operational_deferSimultaneous() {
    var a = Transaction.run(() -> {
      var a_ = new StreamSink<String>();
      return a_;
    });
    var b = Transaction.run(() -> {
      var b_ = new StreamSink<String>();
      return b_;
    });
    var c = Transaction.run(() -> {
      var c_ = Operational.defer(a).orElse(Operational.defer(b));
      return c_;
    });
    var c_0 = new ArrayList<String>();
    var c_0_l = Transaction.run(() -> {
      var c_0_l_ = c.listen(val -> c_0.add(val));
      return c_0_l_;
    });
    Transaction.runVoid(() -> {
      b.send("A");
    });
    c_0_l.unlisten();
    assertEquals(Arrays.<String>asList("A"), c_0);
    var c_1 = new ArrayList<String>();
    var c_1_l = Transaction.run(() -> {
      var c_1_l_ = c.listen(val -> c_1.add(val));
      return c_1_l_;
    });
    Transaction.runVoid(() -> {
      a.send("b");
      b.send("B");
    });
    c_1_l.unlisten();
    assertEquals(Arrays.<String>asList("b"), c_1);
  }

}
