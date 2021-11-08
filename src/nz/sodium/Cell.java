package nz.sodium;

/**
 * Represents a value of type A that changes over time.
 */
public class Cell<A> implements AutoCloseable {

  final Stream<A> str;
  A value;
  A valueUpdate;
  Listener cleanup;
  Lazy<A> lazyInitValue; // Used by LazyCell

  /**
   * A cell with a constant value.
   */
  public Cell(A value) {
    this.str = new Stream<>();
    this.value = value;
  }

  Cell(Stream<A> str, A initValue) {
    this.str = str;
    this.value = initValue;
    Transaction.run(trans1 -> {
      Cell.this.cleanup = str.listen(Node.NULL, trans1, (trans2, a) -> {
        if (Cell.this.valueUpdate == null) {
          trans2.last(() -> {
            Cell.this.value = Cell.this.valueUpdate;
            Cell.this.lazyInitValue = null;
            Cell.this.valueUpdate = null;
          });
        }
        Cell.this.valueUpdate = a;
      }, false);
    });
  }

  /**
   * @return The value including any updates that have happened in this transaction.
   */
  final A newValue() {
    return (valueUpdate == null) ? sampleNoTrans() : valueUpdate;
  }

  /**
   * Sample the cell's current value.
   * It may be used inside the functions passed to primitives that apply them to {@link Stream}s, including {@link Stream#map(Lambda1)} in which case it is equivalent to snapshotting the cell, {@link Stream#snapshot(Cell, Lambda2)}, {@link Stream#filter(Lambda1)} and {@link Stream#merge(Stream, Lambda2)}.
   * It should generally be avoided in favour of {@link #listen(Handler)} so you don't miss any updates, but in many circumstances it makes sense.
   */
  public final A sample() {
    return Transaction.apply(trans -> sampleNoTrans());
  }

  static class LazySample<A> {
    LazySample(Cell<A> cell) {
      this.cell = cell;
    }
    Cell<A> cell;
    boolean hasValue;
    A value;
  }

  /**
   * A variant of {@link #sample()} that works with {@link CellLoop}s when they haven't been looped yet.
   * It should be used in any code that's general enough that it could be passed a {@link CellLoop}.
   * @see Stream#holdLazy(Lazy) Stream.holdLazy()
   */
  public final Lazy<A> sampleLazy() {
    @SuppressWarnings("resource")
    var self = this;
    return Transaction.apply(self::sampleLazy);
  }

  final Lazy<A> sampleLazy(Transaction trans) {
    var self = this;
    var s = new LazySample<A>(self);
    trans.last(() -> {
      s.value = self.valueUpdate != null ? self.valueUpdate : self.sampleNoTrans();
      s.hasValue = true;
      s.cell = null;
    });
    return new Lazy<>(() -> s.hasValue ? s.value : s.cell.sample());
  }

  A sampleNoTrans() {
    return value;
  }

  final Stream<A> updates() {
    return str;
  }

  final Stream<A> value(Transaction trans1) {
    @SuppressWarnings("resource")
    var sSpark = new StreamWithSend<>();
    trans1.prioritized(sSpark.node, trans2 -> sSpark.send(trans2, Unit.UNIT));
    var sInitial = sSpark.<A>snapshot(this);
    return sInitial.merge(updates(), (left, right) -> right);
  }

  /**
   * Transform the cell's value according to the supplied function, so the returned Cell always reflects the value of the function applied to the input Cell's value.
   * @param f Function to apply to convert the values. It must be <em>referentially transparent</em>.
   */
  public final <B> Cell<B> map(Lambda1<A, B> f) {
    return Transaction.apply(trans -> updates().map(f).holdLazy(trans, sampleLazy(trans).map(f)));
  }

  /**
   * Lift a binary function into cells, so the returned Cell always reflects the specified function applied to the input cells' values.
   * @param fn Function to apply. It must be <em>referentially transparent</em>.
   */
  public final <B, C> Cell<C> lift(Cell<B> bb, Lambda2<A,B,C> fn) {
    Lambda1<A, Lambda1<B, C>> l = a->b->fn.apply(a,b);
    return apply(map(l), bb);
  }

  /**
   * Lift a ternary function into cells, so the returned Cell always reflects the specified function applied to the input cells' values.
   * @param fn Function to apply. It must be <em>referentially transparent</em>.
   */
  public final <B, C, D> Cell<D> lift(Cell<B> bb, Cell<C> cc, Lambda3<A,B,C,D> fn) {
    Lambda1<A, Lambda1<B, Lambda1<C, D>>> l = a->b->c->fn.apply(a,b,c);
    return apply(apply(map(l), bb), cc);
  }

  /**
   * Lift a quaternary function into cells, so the returned Cell always reflects the specified function applied to the input cells' values.
   * @param fn Function to apply. It must be <em>referentially transparent</em>.
   */
  public final <B, C, D, E> Cell<E> lift(Cell<B> bb, Cell<C> cc, Cell<D> dd, Lambda4<A,B,C,D,E> fn) {
    Lambda1<A, Lambda1<B, Lambda1<C, Lambda1<D, E>>>> l = a->b->c->d->fn.apply(a,b,c,d);
    return apply(apply(apply(map(l), bb), cc), dd);
  }

  /**
   * Lift a 5-argument function into cells, so the returned Cell always reflects the specified function applied to the input cells' values.
   * @param fn Function to apply. It must be <em>referentially transparent</em>.
   */
  public final <B, C, D, E, F> Cell<F> lift(Cell<B> bb, Cell<C> cc, Cell<D> dd, Cell<E> ee, Lambda5<A,B,C,D,E,F> fn) {
    Lambda1<A, Lambda1<B, Lambda1<C, Lambda1<D, Lambda1<E, F>>>>> l = a->b->c->d->e->fn.apply(a,b,c,d,e);
    return apply(apply(apply(apply(map(l), bb), cc), dd), ee);
  }

  /**
   * Lift a 6-argument function into cells, so the returned Cell always reflects the specified function applied to the input cells' values.
   * @param fn Function to apply. It must be <em>referentially transparent</em>.
   */
  public final <B, C, D, E, F, G> Cell<G> lift(Cell<B> bb, Cell<C> cc, Cell<D> dd, Cell<E> ee, Cell<F> ff, Lambda6<A, B, C, D, E, F, G> fn) {
    Lambda1<A, Lambda1<B, Lambda1<C, Lambda1<D, Lambda1<E, Lambda1<F, G>>>>>> l = a->b->c->d->e->f->fn.apply(a,b,c,d,e, f);
    return apply(apply(apply(apply(apply(map(l), bb), cc), dd), ee), ff);
  }

  /**
   * Apply a value inside a cell to a function inside a cell.
   * This is the primitive for all function lifting.
   */
  public static <A, B> Cell<B> apply(Cell<Lambda1<A, B>> bf, Cell<A> ba) {
    return Transaction.apply(trans0 -> {
      @SuppressWarnings("resource")
      var out = new StreamWithSend<B>();

      class ApplyHandler implements Handler<Transaction> {
        @Override
        public void run(Transaction trans1) {
          trans1.prioritized(out.node, trans2 -> out.send(trans2, f.apply(a)));
        }
        Lambda1<A, B> f = null;
        boolean f_present = false;
        A a = null;
        boolean a_present = false;
      }
      
      var out_target = out.node;
      var in_target = new Node(0);
      var nt = new Node.Target[1];
      in_target.linkTo(null, out_target, nt);
      var node_target = nt[0];
      var h = new ApplyHandler();
      var l1 = bf.value(trans0).listen_(in_target, (trans1, f) -> {
        h.f = f;
        h.f_present = true;
        if (h.a_present) {
          h.run(trans1);
        }
      });
      var l2 = ba.value(trans0).listen_(in_target, (trans1, a) -> {
        h.a = a;
        h.a_present = true;
        if (h.f_present) {
          h.run(trans1);
        }
      });
      return out
        .lastFiringOnly(trans0)
        .unsafeAddCleanup(l1)
        .unsafeAddCleanup(l2)
        .unsafeAddCleanup(() -> in_target.unlinkTo(node_target))
        .holdLazy(new Lazy<>(() -> bf.sampleNoTrans().apply(ba.sampleNoTrans())));
    });
  }

  /**
   * Unwrap a cell inside another cell to give a time-varying cell implementation.
   */
  public static <A> Cell<A> switchC(Cell<Cell<A>> b_a) {
    return Transaction.apply(trans0 -> {
      var za = b_a.sampleLazy().map(Cell::sample);
      @SuppressWarnings("resource")
      var out = new StreamWithSend<A>();
      var h = new TransactionHandler<Cell<A>>() {
        Listener currentListener;
        @Override
        public void run(Transaction trans2, Cell<A> ba) {
          // Note: If any switch takes place during a transaction, then the lastFiringOnly() below will always cause a sample to be fetched from the one we just switched to.
          // So anything from the old input cell that might have happened during this transaction will be suppressed.
          if (currentListener != null) {
            currentListener.unlisten();
          }
          currentListener = ba.value(trans2).listen(out.node, trans2, out::send, false);
        }
        @Override
        public void close() {
          if (currentListener != null) {
            currentListener.unlisten();
            currentListener = null;
          }
        }

        // void finalize() { close(); }
      };
      var l1 = b_a.value(trans0).listen_(out.node, h);
      return out.lastFiringOnly(trans0).unsafeAddCleanup(l1).holdLazy(za);
    });
  }

  /**
   * Unwrap a stream inside a cell to give a time-varying stream implementation.
   */
  public static <A> Stream<A> switchS(Cell<Stream<A>> bea) {
    return Transaction.apply(trans -> switchS(trans, bea));
  }

  static <A> Stream<A> switchS(Transaction trans1, Cell<Stream<A>> bea) {
    @SuppressWarnings("resource")
    var out = new StreamWithSend<A>();
    TransactionHandler<A> h2 = out::send;
    var h1 = new TransactionHandler<Stream<A>>() {
      Listener currentListener = bea.sampleNoTrans().listen(out.node, trans1, h2, false);
      @Override
      public void run(Transaction trans2, Stream<A> ea) {
        trans2.last(() -> {
          if (currentListener != null) {
            currentListener.unlisten();
          }
          currentListener = ea.listen(out.node, trans2, h2, true);
        });
      }
      @Override
      public void close() {
        if (currentListener != null) {
          currentListener.unlisten();
          currentListener = null;
        }
      }

      // void finalize() { close(); }
    };
    var l1 = bea.updates().listen(out.node, trans1, h1, false);
    return out.unsafeAddCleanup(l1);
  }

  @Override
  public void close() {
    if (cleanup != null) {
      cleanup.unlisten();
      cleanup = null;
    }
  }

  // void finalize() { close(); }

  /**
   * Listen for updates to the value of this cell.
   * This is the observer pattern.
   * The returned {@link Listener} has a {@link Listener#unlisten()} method to cause the listener to be removed.
   * This is an OPERATIONAL mechanism is for interfacing between the world of I/O and for FRP.
   * @param action The handler to execute when there's a new value. You should make no assumptions about what thread you are called on, and the handler should not block. You are not allowed to use {@link CellSink#send(Object)} or {@link StreamSink#send(Object)} in the handler. An exception will be thrown, because you are not meant to use this to create your own primitives.
   */
  public final Listener listen(Handler<A> action) {
    return Transaction.apply(trans -> value(trans).listen(action));
  }

  /**
   * A variant of {@link #listen(Handler)} that will deregister the listener automatically if the listener is garbage collected.
   * With {@link #listen(Handler)}, the listener is only deregistered if {@link Listener#unlisten()} is called explicitly.
   */
  public final Listener listenWeak(Handler<A> action) {
    return Transaction.apply(trans -> value(trans).listenWeak(action));
  }

}
