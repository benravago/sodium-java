package nz.sodium;

import java.util.LinkedList;

/**
 * Operational primitives that must be used with care.
 */
public class Operational {
  /**
   * A stream that gives the updates/steps for a {@link Cell}.
   * This is an OPERATIONAL primitive, which is not part of the main Sodium API.
   * It breaks the property of non-detectability of cell steps/updates.
   * The rule with this primitive is that you should only use it in functions that do not allow the caller to detect the cell updates.
   */
  public static <A> Stream<A> updates(Cell<A> c) {
    return c.updates();
  }

  /**
   * A stream that is guaranteed to fire once in the transaction where value() is invoked, giving the current value of the cell, and thereafter behaves like {@link #updates(Cell)}, firing for each update/step of the cell's value.
   * This is an OPERATIONAL primitive, which is not part of the main Sodium API.
   * It breaks the property of non-detectability of cell steps/updates.
   * The rule with this primitive is that you should only use it in functions that do not allow the caller to detect the cell updates.
   */
  public static <A> Stream<A> value(Cell<A> c) {
    return Transaction.apply(c::value);
  }

  /**
   * Push each event onto a new transaction guaranteed to come before the next externally initiated transaction.
   * Same as {@link #split(Stream)} but it works on a single value.
   */
  public static <A> Stream<A> defer(Stream<A> s) {
    return split(s.map(a -> {
      var l = new LinkedList<A>();
      l.add(a);
      return l;
    }));
  }

  /**
   * Push each event in the list onto a newly created transaction guaranteed to come before the next externally initiated transaction.
   * Note that the semantics are such that two different invocations of split() can put events into the same new transaction, so the resulting stream's events could be simultaneous with events output by split() or {@link #defer(Stream)} invoked elsewhere in the code.
   */
  public static <A, C extends Iterable<A>> Stream<A> split(Stream<C> s) {
    @SuppressWarnings("resource")
    var out = new StreamWithSend<A>();
    var l1 = s.listen_(out.node, (trans, as) -> {
      var childIx = 0;
      for (var a : as) {
        trans.post_(childIx, trans1 -> out.send(trans1, a));
        childIx++;
      }
    });
    return out.unsafeAddCleanup(l1);
  }

}
