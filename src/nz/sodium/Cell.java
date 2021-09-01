package nz.sodium;

/**
 * Represents a value of type A that changes over time.
 */
public class Cell<A> {

  final Stream<A> str;
  A value;
  A valueUpdate;
  private Listener cleanup;
  Lazy<A> lazyInitValue;  // Used by LazyCell

  /**
   * A cell with a constant value.
   */
  public Cell(A value) {
    this.str = new Stream<>();
    this.value = value;
  }

  Cell(final Stream<A> str, A initValue) {
    this.str = str;
    this.value = initValue;
    Transaction.run(t1 -> {
      Cell.this.cleanup = str.listen(Node.NULL, t1,
        (t2,a) -> {
          if (Cell.this.valueUpdate == null) {
            t2.last(() -> {
              Cell.this.value = Cell.this.valueUpdate;
              Cell.this.lazyInitValue = null;
              Cell.this.valueUpdate = null;
            });
          }
          Cell.this.valueUpdate = a;
        },
        false);
    });
  }

  /**
   * @return The value including any updates that have happened in this transaction.
   */
  final A newValue() {
    return valueUpdate == null ? sampleNoTrans() : valueUpdate;
  }

  /**
   * Sample the cell's current value.
   * <p>
   * It may be used inside the functions passed to primitives that apply them to {@link Stream}s,
   * including {@link Stream#map(Lambda1)} in which case it is equivalent to snapshot-ing the cell,
   * {@link Stream#snapshot(Cell, Lambda2)}, {@link Stream#filter(Lambda1)} and
   * {@link Stream#merge(Stream, Lambda2)}.
   * It should generally be avoided in favor of {@link #listen(Handler)} so you don't
   * miss any updates, but in many circumstances it makes sense.
   */
  public final A sample() {
    return Transaction.apply(t -> sampleNoTrans());
  }

  private static class LazySample<A> {
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
    return Transaction.apply(this::sampleLazy);
  }

  final Lazy<A> sampleLazy(Transaction t) {
    var s = new LazySample<A>(this);
    t.last(() -> {
      var me = s.cell;
      s.value = me.valueUpdate != null ? me.valueUpdate : me.sampleNoTrans();
      s.hasValue = true;
      s.cell = null;
    });
    return new Lazy<>(() -> {
      if (s.hasValue) {
        return s.value;
      } else {
        return s.cell.sample();
      }
    });
  }

  A sampleNoTrans() {
    return value;
  }

  final Stream<A> updates() {
    return str;
  }

  final Stream<A> value(Transaction t1) {
    var sSpark = new StreamWithSend<Unit>();
    t1.prioritized(sSpark.node, t2 -> sSpark.send(t2, Unit.UNIT));
    var sInitial = sSpark.snapshot(this);
    return sInitial.merge(updates(), (left, right) -> right);
  }

  /**
   * Transform the cell's value according to the supplied function,
   * so the returned Cell always reflects the value of the function applied to the input Cell's value.
   * @param f Function to apply to convert the values. It must be <em>referentially transparent</em>.
   */
  public final <B> Cell<B> map(final Lambda1<A, B> f) {
    return Transaction.apply(t -> updates().map(f).holdLazy(t, sampleLazy(t).map(f)));
  }

  /**
   * Lift a binary function into cells,
   * so the returned Cell always reflects the specified function applied to the input cells' values.
   * @param fn Function to apply. It must be <em>referentially transparent</em>.
   */
  public final <B, C> Cell<C> lift(Cell<B> b, final Lambda2<A, B, C> fn) {
    Lambda1<A, Lambda1<B, C>> ffa = aa -> bb -> fn.apply(aa, bb);
    var bf = this.map(ffa);
    return Cell.apply(bf, b);
  }

  /**
   * Lift a ternary function into cells,
   * so the returned Cell always reflects the specified function applied to the input cells' values.
   * @param fn Function to apply. It must be <em>referentially transparent</em>.
   */
  public final <B, C, D> Cell<D> lift(Cell<B> b, Cell<C> c, final Lambda3<A, B, C, D> fn) {
    Lambda1<A, Lambda1<B, Lambda1<C, D>>> ffa = aa -> bb -> cc -> fn.apply(aa, bb, cc);
    var bf = this.map(ffa);
    return Cell.apply(apply(bf, b), c);
  }

  /**
   * Lift a quaternary function into cells,
   * so the returned Cell always reflects the specified function applied to the input cells' values.
   * @param fn Function to apply. It must be <em>referentially transparent</em>.
   */
  public final <B, C, D, E> Cell<E> lift(Cell<B> b, Cell<C> c, Cell<D> d, final Lambda4<A, B, C, D, E> fn) {
    Lambda1<A, Lambda1<B, Lambda1<C, Lambda1<D, E>>>> ffa = aa -> bb -> cc -> dd -> fn.apply(aa, bb, cc, dd);
    var bf = this.map(ffa);
    return Cell.apply(apply(apply(bf, b), c), d);
  }

  /**
   * Lift a 5-argument function into cells,
   * so the returned Cell always reflects the specified function applied to the input cells' values.
   * @param fn Function to apply. It must be <em>referentially transparent</em>.
   */
  public final <B, C, D, E, F> Cell<F> lift(Cell<B> b, Cell<C> c, Cell<D> d, Cell<E> e, final Lambda5<A, B, C, D, E, F> fn) {
    Lambda1<A, Lambda1<B, Lambda1<C, Lambda1<D, Lambda1<E, F>>>>> ffa = aa -> bb -> cc -> dd -> ee -> fn.apply(aa, bb, cc, dd, ee);
    var bf = this.map(ffa);
    return Cell.apply(apply(apply(apply(bf, b), c), d), e);
  }

  /**
   * Lift a 6-argument function into cells,
   * so the returned Cell always reflects the specified function applied to the input cells' values.
   * @param fn Function to apply. It must be <em>referentially transparent</em>.
   */
  public final <B, C, D, E, F, G> Cell<G> lift(Cell<B> b, Cell<C> c, Cell<D> d, Cell<E> e, Cell<F> f, final Lambda6<A, B, C, D, E, F, G> fn) {
    Lambda1<A, Lambda1<B, Lambda1<C, Lambda1<D, Lambda1<E, Lambda1<F, G>>>>>> ffa = (final A aa) -> (final B bb) -> (final C cc) -> (final D dd) -> (final E ee) -> (final F ff) -> fn.apply(aa, bb, cc, dd, ee, ff);
    var bf = this.map(ffa);
    return Cell.apply(apply(apply(apply(apply(bf, b), c), d), e), f);
  }

  /**
   * Apply a value inside a cell to a function inside a cell.
   * This is the primitive for all function lifting.
   */
  public static <A, B> Cell<B> apply(Cell<Lambda1<A, B>> bf, Cell<A> ba) {
    return Transaction.apply(t0 -> {
      var out = new StreamWithSend<B>();

      class ApplyHandler implements Handler<Transaction> {
        Lambda1<A, B> f = null;
        boolean f_present = false;
        A a = null;
        boolean a_present = false;

        @Override
        public void run(Transaction t1) {
          t1.prioritized(out.node, t2 -> out.send(t2, f.apply(a)));
        }
      }
      var h = new ApplyHandler();

      var out_target = out.node;
      var in_target = new Node(0);
      var node_target_ = new Node.Target[1];
      in_target.linkTo(null, out_target, node_target_);
      var node_target = node_target_[0];

      var l1 = bf.value(t0)
       .listen_(in_target, (t1, f) -> {
          h.f = f;
          h.f_present = true;
          if (h.a_present) {
            h.run(t1);
          }
        });
      var l2 = ba.value(t0)
       .listen_(in_target, (t2, a) -> {
          h.a = a;
          h.a_present = true;
          if (h.f_present) {
            h.run(t2);
          }
        });

      return out
        .lastFiringOnly(t0)
        .cleanup(l1)
        .cleanup(l2)
        .cleanup(() -> in_target.unlinkTo(node_target))
        .holdLazy(new Lazy<B>(() -> bf.sampleNoTrans().apply(ba.sampleNoTrans())));
    });
  }

  /**
   * Unwrap a cell inside another cell to give a time-varying cell implementation.
   */
  public static <A> Cell<A> switchC(Cell<Cell<A>> bba) {
    return Transaction.apply(t0 -> {
      var za = bba.sampleLazy().map(ba -> ba.sample());
      var out = new StreamWithSend<A>();
      var h = new TransactionHandler<Cell<A>>() {
        Listener currentListener;
        @Override
        public void run(Transaction t2, Cell<A> ba) {
          // Note: If any switch takes place during a transaction, then the lastFiringOnly() below will always cause a sample to be fetched from the one we just switched to.
          // So anything from the old input cell that might have happened during this transaction will be suppressed.
          if (currentListener != null) {
            currentListener.unlisten();
          }
          currentListener = ba.value(t2).listen(out.node, t2, (t3, a) -> out.send(t3, a), false);
        }
        @Override
        protected void finalize() throws Throwable {
          if (currentListener != null) {
            currentListener.unlisten();
          }
        }
      };
      var l1 = bba.value(t0).listen_(out.node, h);
      return out
        .lastFiringOnly(t0)
        .cleanup(l1)
        .holdLazy(za);
    });
  }

  /**
   * Unwrap a stream inside a cell to give a time-varying stream implementation.
   */
  public static <A> Stream<A> switchS(Cell<Stream<A>> bea) {
    return Transaction.apply(t -> switchS(t, bea));
  }

  private static <A> Stream<A> switchS(Transaction t1, Cell<Stream<A>> bea) {
    var out = new StreamWithSend<A>();
    TransactionHandler<A> h2 = (t2, a) -> out.send(t2, a);
    var h1 = new TransactionHandler<Stream<A>>() {
      Listener currentListener = bea.sampleNoTrans().listen(out.node, t1, h2, false);
      @Override
      public void run(Transaction t3, final Stream<A> ea) {
        t3.last(() -> {
          if (currentListener != null) {
            currentListener.unlisten();
          }
          currentListener = ea.listen(out.node, t3, h2, true);
        });
      }
      @Override
      protected void finalize() throws Throwable {
        if (currentListener != null) {
          currentListener.unlisten();
        }
      }
    };
    var l1 = bea.updates().listen(out.node, t1, h1, false);
    return out.cleanup(l1);
  }

  // TODO: maybe replace finalize with Cleaner.Cleanable

  @Override
  protected void finalize() throws Throwable {
    if (cleanup != null) {
      cleanup.unlisten();
    }
  }

  /**
   * Listen for updates to the value of this cell. This is the observer pattern.
   * The returned {@link Listener} has a {@link Listener#run()} method to cause the listener to be removed.
   * This is an OPERATIONAL mechanism is for interfacing between the world of I/O and for FRP.
   * @param action The handler to execute when there's a new value.
   *   You should make no assumptions about what thread you are called on, and the handler should not block.
   *   You are not allowed to use {@link CellSink#send(Object)} or {@link StreamSink#send(Object)} in the handler.
   *   An exception will be thrown, because you are not meant to use this to create your own primitives.
   */
  public final Listener listen(Handler<A> action) {
    return Transaction.apply(t -> value(t).listen(action));
  }

  /**
   * A variant of {@link #listen(Handler)} that will deregister the listener automatically if the listener is garbage collected.
   * With {@link #listen(Handler)}, the listener is only deregistered if {@link Listener#run()} is called explicitly.
   */
  public final Listener listenWeak(Handler<A> action) {
    return Transaction.apply(t -> value(t).listenWeak(action));
  }

}
