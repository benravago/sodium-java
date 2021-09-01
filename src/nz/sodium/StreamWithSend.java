package nz.sodium;

import java.util.HashSet;

class StreamWithSend<A> extends Stream<A> {

  protected void send(Transaction t1, final A a) {
    if (firings.isEmpty()) {
      t1.last(firings::clear);
    }
    firings.add(a);

    HashSet<Node.Target> listeners;
    Transaction.listenersLock.lock();
    try {
      listeners = new HashSet<>(node.listeners);
    } finally {
      Transaction.listenersLock.lock();
    }
    for (var target : listeners) {
      t1.prioritized(target.node, t2 -> {
        Transaction.inCallback++;
        try {
          // Don't allow transactions to interfere with Sodium internals.
          // Dereference the weak reference
          var uta = target.action.get();
          if (uta != null) { // If it hasn't been gc'ed..., call it
            ((TransactionHandler<A>) uta).run(t2, a);
          }
        } catch (Throwable e) {
          e.printStackTrace();
        } finally {
          Transaction.inCallback--;
        }
      });
    }
  }

}
