package nz.sodium;

import java.util.HashSet;

class StreamWithSend<A> extends Stream<A> {

  @SuppressWarnings("unchecked")
  protected void send(Transaction trans, A a) {
    if (firings.isEmpty()) {
      trans.last(firings::clear);
    }
    firings.add(a);

    HashSet<Node.Target> listeners;
    Transaction.listenersLock.lock();
    try {
      listeners = new HashSet<>(node.listeners);
    } finally {
      Transaction.listenersLock.unlock();
    }
    for (var target : listeners) {
      trans.prioritized(target.node, trans2 -> {
        Transaction.inCallback++;
        try {
          // Don't allow transactions to interfere with Sodium internals.
          // Dereference the weak reference
          var uta = target.action.get();
          if (uta != null) { // If it hasn't been gc'ed..., call it
            ((TransactionHandler<A>) uta).run(trans2, a);
          }
        } catch (Throwable t) {
          t.printStackTrace();
        } finally {
          Transaction.inCallback--;
        }
      });
    }
  }

}
