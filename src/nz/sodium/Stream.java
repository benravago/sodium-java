package nz.sodium;

import java.lang.ref.Cleaner;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Optional;

/**
 * Represents a stream of discrete events/firings containing values of type A.
 */
public class Stream<A> {

  private static final class ListenerImplementation<A> implements Listener {

    /** It's essential that we keep the listener alive while the caller holds the Listener, so that the finalizer doesn't get triggered. */
    private Stream<A> event;

    /** It's also essential that we keep the action alive, since the node uses a weak reference. */
    private TransactionHandler<A> action;

    private Node.Target target;

    private ListenerImplementation(Stream<A> event, TransactionHandler<A> action, Node.Target target) {
      this.event = event;
      this.action = action;
      this.target = target;
    }

    @Override
    public void unlisten() {
      Transaction.listenersLock.lock();
      try {
        if (this.event != null) {
          event.node.unlinkTo(target);
          this.event = null;
          this.action = null;
          this.target = null;
        }
      } finally {
        Transaction.listenersLock.unlock();
      }
    }
  }

  final Node node;

  final List<A> firings;

  /**
   * A stream that never fires.
   */
  public Stream() {
    this.node = new Node(0L);
    this.firings = new ArrayList<>();
  }

  static final Map<Listener,Object> keepListenersAlive = new ConcurrentHashMap<>();

  /**
   * Listen for events/firings on this stream. This is the observer pattern.
   * The returned {@link Listener} has a {@link Listener#run()} method to cause the listener to be removed.
   * This is an OPERATIONAL mechanism is for interfacing between the world of I/O and for FRP.
   * @param handler The handler to execute when there's a new value.
   *   You should make no assumptions about what thread you are called on, and the handler should not block.
   *   You are not allowed to use {@link CellSink#send(Object)} or {@link StreamSink#send(Object)} in the handler.
   *   An exception will be thrown, because you are not meant to use this to create your own primitives.
   */
  public final Listener listen(Handler<A> handler) {
    var l0 = listenWeak(handler);
    var l1 = new Listener() {
      @Override
      public void unlisten() {
        l0.unlisten();
        keepListenersAlive.remove(this);
      }
    };
    keepListenersAlive.put(l1,l1);
    return l1;
  }

  /**
   * A variant of {@link #listen(Handler)} that handles the first event and then automatically deregisters itself.
   * This is useful for implementing things that work like promises.
   */
  public final Listener listenOnce(Handler<A> handler) {
    var lRef = new Listener[1];
    lRef[0] = listen(a -> {
      lRef[0].unlisten();
      handler.run(a);
    });
    return lRef[0];
  }

  final Listener listen_(Node target, TransactionHandler<A> action) {
    return Transaction.apply(t -> listen(target, t, action, false));
  }

  /**
   * A variant of {@link #listen(Handler)} that will deregister the listener automatically if the listener is garbage collected.
   * With {@link #listen(Handler)}, the listener is only deregistered if {@link Listener#run()} is called explicitly.
   * <p>
   * This method should be used for listeners that are to be passed to {@link Stream#addCleanup(Listener)} to ensure that things don't get kept alive when they shouldn't.
   */
  public final Listener listenWeak(Handler<A> action) {
    return listen_(Node.NULL, (t, a) -> action.run(a));
  }

  @SuppressWarnings("unchecked")
  final Listener listen(Node target, Transaction trans, TransactionHandler<A> action, boolean suppressEarlierFirings) {
    var node_target_ = new Node.Target[1];
    Transaction.listenersLock.lock();
    try {
      if (node.linkTo((TransactionHandler<Unit>) action, target, node_target_)) {
        trans.toRegen = true;
      }
    } finally {
      Transaction.listenersLock.unlock();
    }
    var node_target = node_target_[0];
    var firings_ = new ArrayList<A>(this.firings);
    if (!suppressEarlierFirings && !firings_.isEmpty()) {
      trans.prioritized(target, t2 -> {
        // Anything sent already in this transaction must be sent now
        // so that there's no order dependency between send and listen.
        for (var a : firings_) {
          Transaction.inCallback++;
          try {  // Don't allow transactions to interfere with Sodium internals.
            action.run(t2, a);
          } catch (Throwable e) {
            e.printStackTrace();
          } finally {
            Transaction.inCallback--;
          }
        }
      });
    }
    return new ListenerImplementation<>(this, action, node_target);
  }

  /**
   * Transform the stream's event values according to the supplied function,
   * so the returned Stream's event values reflect the value of the function applied to the input Stream's event values.
   * @param f Function to apply to convert the values.
   *    It may construct FRP logic or use {@link Cell#sample()} in which case it is equivalent to {@link Stream#snapshot(Cell)}ing the cell.
   *    Apart from this the function must be <em>referentially transparent</em>.
   */
  public final <B> Stream<B> map(Lambda1<A, B> f) {
    // var self = this;
    var out = new StreamWithSend<B>();
    var l = listen_(out.node, (t, a) -> out.send(t, f.apply(a)));
    return out.cleanup(l);
  }

  /**
   * Transform the stream's event values into the specified constant value.
   * @param b Constant value.
   */
  public final <B> Stream<B> mapTo(B b) {
    return this.map(a -> b);
  }

  /**
   * Create a {@link Cell} with the specified initial value, that is updated by this stream's event values.
   * <p>
   * There is an implicit delay:
   * State updates caused by event firings don't become visible as the cell's current value as viewed by {@link Stream#snapshot(Cell, Lambda2)} until the following transaction.
   * To put this another way, {@link Stream#snapshot(Cell, Lambda2)} always sees the value of a cell as it was before any state changes from the current transaction.
   */
  public final Cell<A> hold(A initValue) {
    return Transaction.apply(t -> new Cell<A>(Stream.this, initValue));
  }

  /**
   * A variant of {@link #hold(Object)} with an initial value captured by {@link Cell#sampleLazy()}.
   */
  public final Cell<A> holdLazy(Lazy<A> initValue) {
    return Transaction.apply(t -> holdLazy(t, initValue));
  }

  final Cell<A> holdLazy(Transaction trans, Lazy<A> initValue) {
    return new LazyCell<>(this, initValue);
  }

  /**
   * Variant of {@link #snapshot(Cell, Lambda2)} that captures the cell's value at the time of the event firing, ignoring the stream's value.
   */
  public final <B> Stream<B> snapshot(Cell<B> c) {
    return snapshot(c, (a, b) -> b);
  }

  /**
   * Return a stream whose events are the result of the combination using the specified function of the input stream's event value and the value of the cell at that time.
   * <p>
   * There is an implicit delay:
   * State updates caused by event firings being held with {@link Stream#hold(Object)} don't become visible as the cell's current value until the following transaction.
   * To put this another way, {@link Stream#snapshot(Cell, Lambda2)} always sees the value of a cell as it was before any state changes from the current transaction.
   */
  public final <B, C> Stream<C> snapshot(Cell<B> c, Lambda2<A, B, C> f) {
    var out = new StreamWithSend<C>();
    var l = listen_(out.node, (t, a) -> out.send(t, f.apply(a, c.sampleNoTrans())));
    return out.cleanup(l);
  }

  /**
   * Variant of {@link #snapshot(Cell, Lambda2)} that captures the values of
   * two cells.
   */
  public final <B, C, D> Stream<D> snapshot(Cell<B> cb, Cell<C> cc, Lambda3<A, B, C, D> fn) {
    return this.snapshot(cb, (a, b) -> fn.apply(a, b, cc.sample()));
  }

  /**
   * Variant of {@link #snapshot(Cell, Lambda2)} that captures the values of three cells.
   */
  public final <B, C, D, E> Stream<E> snapshot(Cell<B> cb, Cell<C> cc, Cell<D> cd, Lambda4<A, B, C, D, E> fn) {
    return this.snapshot(cb, (a, b) -> fn.apply(a, b, cc.sample(), cd.sample()));
  }

  /**
   * Variant of {@link #snapshot(Cell, Lambda2)} that captures the values of four cells.
   */
  public final <B, C, D, E, F> Stream<F> snapshot(Cell<B> cb, Cell<C> cc, Cell<D> cd, Cell<E> ce, Lambda5<A, B, C, D, E, F> fn) {
    return this.snapshot(cb, (a, b) -> fn.apply(a, b, cc.sample(), cd.sample(), ce.sample()));
  }

  /**
   * Variant of {@link #snapshot(Cell, Lambda2)} that captures the values of five cells.
   */
  public final <B, C, D, E, F, G> Stream<G> snapshot(Cell<B> cb, Cell<C> cc, Cell<D> cd, Cell<E> ce, Cell<F> cf, Lambda6<A, B, C, D, E, F, G> fn) {
    return this.snapshot(cb, (a, b) -> fn.apply(a, b, cc.sample(), cd.sample(), ce.sample(), cf.sample()));
  }

  /**
   * Variant of {@link Stream#merge(Stream, Lambda2)} that merges two streams and will drop an event in the simultaneous case.
   * <p>
   * In the case where two events are simultaneous (i.e. both within the same transaction), the event from <em>this</em> will take precedence, and the event from <em>s</em> will be dropped.
   * If you want to specify your own combining function, use {@link Stream#merge(Stream, Lambda2)}. s1.orElse(s2) is equivalent to s1.merge(s2, (l, r) -&gt; l).
   * <p>
   * The name orElse() is used instead of merge() to make it really clear that care should be taken, because events can be dropped.
   */
  public final Stream<A> orElse(Stream<A> s) {
    return merge(s, (left, right) -> left);
  }

  private static <A> Stream<A> merge(Stream<A> ea, Stream<A> eb) {
    var out = new StreamWithSend<A>();
    var left = new Node(0);
    var right = out.node;
    var node_target_ = new Node.Target[1];
    left.linkTo(null, right, node_target_);
    var node_target = node_target_[0];
    TransactionHandler<A> h = (t, a) -> out.send(t, a);
    var l1 = ea.listen_(left, h);
    var l2 = eb.listen_(right, h);
    return out
      .cleanup(l1)
      .cleanup(l2)
      .cleanup(() -> left.unlinkTo(node_target));
  }

  /**
   * Merge two streams of the same type into one, so that events on either input appear on the returned stream.
   * <p>
   * If the events are simultaneous (that is, one event from this and one from <em>s</em> occurring in the same transaction), combine them into one using the specified combining function so that the returned stream is guaranteed only ever to have one event per transaction.
   * The event from <em>this</em> will appear at the left input of the combining function, and the event from <em>s</em> will appear at the right.
   * @param f Function to combine the values.
   *   It may construct FRP logic or use {@link Cell#sample()}. Apart from this the function must be <em>referentially transparent</em>.
   */
  public final Stream<A> merge(Stream<A> s, Lambda2<A, A, A> f) {
    return Transaction.apply(t -> Stream.<A>merge(Stream.this, s).coalesce(t, f));
  }

  /**
   * Variant of {@link #orElse(Stream)} that merges a collection of streams.
   */
  public static <A> Stream<A> orElse(Iterable<Stream<A>> ss) {
    return Stream.<A>merge(ss, (left, right) -> left);
  }

  /**
   * Variant of {@link #merge(Stream,Lambda2)} that merges a collection of streams.
   */
  public static <A> Stream<A> merge(Iterable<Stream<A>> ss, Lambda2<A, A, A> f) {
    var v = new ArrayList<Stream<A>>();
    for (var s : ss) {
      v.add(s);
    }
    return merge(v, 0, v.size(), f);
  }

  private static <A> Stream<A> merge(List<Stream<A>> sas, int start, int end, Lambda2<A, A, A> f) {
    int len = end - start;
    return switch (len) {
      case 0 -> new Stream<>();
      case 1 -> sas.get(start);
      case 2 -> sas.get(start).merge(sas.get(start + 1), f);
      default -> merge_(sas,start,end,f);
    };
  }

  private static <A> Stream<A> merge_(List<Stream<A>> sas, int start, int end, Lambda2<A, A, A> f) {
    int mid = (start + end) / 2;
    return Stream.merge(sas, start, mid, f).merge(Stream.merge(sas, mid, end, f), f);
  }

  private Stream<A> coalesce(Transaction t, Lambda2<A, A, A> f) {
    var out = new StreamWithSend<A>();
    var h = new CoalesceHandler<A>(f, out);
    var l = listen(out.node, t, h, false);
    return out.cleanup(l);
  }

  /**
   * Clean up the output by discarding any firing other than the last one.
   */
  final Stream<A> lastFiringOnly(Transaction trans) {
    return coalesce(trans, (first, second) -> second);
  }

  /**
   * Return a stream that only outputs events for which the predicate returns true.
   */
  public final Stream<A> filter(final Lambda1<A, Boolean> predicate) {
    var out = new StreamWithSend<A>();
    var l = listen_(out.node, (t, a) -> {
      if (predicate.apply(a)) {
        out.send(t, a);
      }
    });
    return out.cleanup(l);
  }

  /**
   * Return a stream that only outputs events that have present values, removing the {@link java.util.Optional} wrapper, discarding empty values.
   */
  public static <A> Stream<A> filterOptional(Stream<Optional<A>> ev) {
    var out = new StreamWithSend<A>();
    var l = ev.listen_(out.node, (t, oa) -> {
      if (oa.isPresent()) {
        out.send(t, oa.get());
      }
    });
    return out.cleanup(l);
  }

  /**
   * Return a stream that only outputs events from the input stream when the specified cell's value is true.
   */
  public final Stream<A> gate(Cell<Boolean> c) {
    return Stream.filterOptional(snapshot(c, (a, pred) -> pred ? Optional.of(a) : Optional.<A>empty()));
  }

  /**
   * Transform an event with a generalized state loop (a Mealy machine).
   * The function is passed the input and the old state and returns the new state and output value.
   * @param f Function to apply to update the state.
   *   It may construct FRP logic or use {@link Cell#sample()} in which case it is equivalent to {@link Stream#snapshot(Cell)}ing the cell.
   *   Apart from this the function must be <em>referentially transparent</em>.
   */
  public final <B, S> Stream<B> collect(final S initState, final Lambda2<A, S, Tuple2<B, S>> f) {
    return collectLazy(new Lazy<>(initState), f);
  }

  /**
   * A variant of {@link #collect(Object, Lambda2)} that takes an initial state returned by {@link Cell#sampleLazy()}.
   */
  public final <B, S> Stream<B> collectLazy(Lazy<S> initState, Lambda2<A, S, Tuple2<B, S>> f) {
    return Transaction.run(() -> {
      var ea = Stream.this;
      var es = new StreamLoop<S>();
      var s = es.holdLazy(initState);
      var ebs = ea.snapshot(s, f);
      var eb = ebs.map(bs -> bs.a);
      var es_out = ebs.map(bs -> bs.b);
      es.loop(es_out);
      return eb;
    });
  }

  /**
   * Accumulate on input event, outputting the new state each time.
   * @param f Function to apply to update the state.
   *   It may construct FRP logic or use {@link Cell#sample()} in which case it is equivalent to {@link Stream#snapshot(Cell)}ing the cell.
   *   Apart from this the function must be <em>referentially transparent</em>.
   */
  public final <S> Cell<S> accum(S initState, Lambda2<A, S, S> f) {
    return accumLazy(new Lazy<>(initState), f);
  }

  /**
   * A variant of {@link #accum(Object, Lambda2)} that takes an initial state returned by {@link Cell#sampleLazy()}.
   */
  public final <S> Cell<S> accumLazy(final Lazy<S> initState, final Lambda2<A, S, S> f) {
    return Transaction.run(() -> {
      var ea = Stream.this;
      var es = new StreamLoop<S>();
      var s = es.holdLazy(initState);
      var es_out = ea.snapshot(s, f);
      es.loop(es_out);
      return es_out.holdLazy(initState);
    });
  }

  /**
   * Return a stream that outputs only one value: the next event of the input stream, starting from the transaction in which once() was invoked.
   */
  public final Stream<A> once() {
    // This is a bit long-winded but it's efficient because it deregisters the listener.
    var self = this;
    var la = new Listener[1];
    var out = new StreamWithSend<A>();
    la[0] = self.listen_(out.node, (t, a) -> {
      if (la[0] != null) {
        out.send(t, a);
        la[0].unlisten();
        la[0] = null;
      }
    });
    return out.cleanup(la[0]);
  }

  static final Cleaner cleaner = Cleaner.create();

  Stream<A> cleanup(Listener listener) {
    cleaner.register(this, listener::unlisten);
    return this;
  }

  /**
   * Attach a listener to this stream so that its {@link Listener#unlisten()} is invoked when this stream is garbage collected.
   * Useful for functions that initiate I/O, returning the result of it through a stream.
   * <p>
   * You must use this only with listeners returned by {@link #listenWeak(Handler)} so that things don't get kept alive when they shouldn't.
   */
  public Stream<A> addCleanup(Listener listener) {
    var self = this;
    return Transaction.run(() -> {
      self.cleanup(listener);
      return self;
    });
  }

}
