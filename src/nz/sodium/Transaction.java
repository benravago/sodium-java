package nz.sodium;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Functions for controlling transactions.
 */
public final class Transaction {

  // Coarse-grained lock that's held during the whole transaction.
  static final Lock transactionLock = new ReentrantLock();

  // Fine-grained lock that protects listeners and nodes.
  static final Lock listenersLock = new ReentrantLock();

  // True if we need to re-generate the priority queue.
  boolean toRegen = false;

  private static class Entry implements Comparable<Entry> {
    private static long nextSeq;

    private final Node rank;
    private final Handler<Transaction> action;
    private final long seq;

    public Entry(Node rank, Handler<Transaction> action) {
      this.rank = rank;
      this.action = action;
      this.seq = nextSeq++;
    }

    @Override
    public int compareTo(Entry o) {
      var answer = rank.compareTo(o.rank);
      if (answer == 0) {  // Same rank: preserve chronological sequence.
        if (seq < o.seq) {
          answer = -1;
        } else if (seq > o.seq) {
          answer = 1;
        }
      }
      return answer;
    }
  }

  private final PriorityQueue<Entry> prioritizedQ = new PriorityQueue<>();
  private final Set<Entry> entries = new HashSet<>();
  private final List<Runnable> lastQ = new ArrayList<>();
  private Map<Integer, Handler<Transaction>> postQ;

  static int inCallback;

  private static Transaction currentTransaction;
  private static final List<Runnable> onStartHooks = new ArrayList<Runnable>();
  private static boolean runningOnStartHooks = false;

  /**
   * Return the current transaction, or null if there isn't one.
   */
  static Transaction getCurrentTransaction() {
    transactionLock.lock();
    try {
      return currentTransaction;
    } finally {
      transactionLock.unlock();
    }
  }

  /**
   * Run the specified code inside a single transaction.
   *
   * In most cases this is not needed, because the primitives always create
   * their own transaction automatically, but it is needed in some circumstances.
   */
  public static void runVoid(Runnable code) {
    transactionLock.lock();
    try {
      // If we are already inside a transaction (which must be on the same thread otherwise we wouldn't have acquired transactionLock),
      // then keep using that same transaction.
      var transWas = currentTransaction;
      try {
        startIfNecessary();
        code.run();
      } finally {
        try {
          if (transWas == null) {
            currentTransaction.close();
          }
        } finally {
          currentTransaction = transWas;
        }
      }
    } finally {
      transactionLock.unlock();
    }
  }

  /**
   * Run the specified code inside a single transaction,
   * with the contained code returning a value of the parameter type A.
   * In most cases this is not needed, because the primitives always create
   * their own transaction automatically, but it is needed in some circumstances.
   */
  public static <A> A run(Lambda0<A> code) {
    transactionLock.lock();
    try {
      // If we are already inside a transaction (which must be on the same thread otherwise we wouldn't have acquired transactionLock),
      // then keep using that same transaction.
      var transWas = currentTransaction;
      try {
        startIfNecessary();
        return code.apply();
      } finally {
        try {
          if (transWas == null) {
            currentTransaction.close();
          }
        } finally {
          currentTransaction = transWas;
        }
      }
    } finally {
      transactionLock.unlock();
    }
  }

  static void run(Handler<Transaction> code) {
    transactionLock.lock();
    try {
      // If we are already inside a transaction (which must be on the same thread otherwise we wouldn't have acquired transactionLock),
      // then keep using that same transaction.
      var transWas = currentTransaction;
      try {
        startIfNecessary();
        code.run(currentTransaction);
      } finally {
        try {
          if (transWas == null) {
            currentTransaction.close();
          }
        } finally {
          currentTransaction = transWas;
        }
      }
    } finally {
      transactionLock.unlock();
    }
  }

  /**
   * Add a runnable that will be executed whenever a transaction is started.
   * That runnable may start transactions itself, which will not cause the hooks to be run recursively.
   * The main use case of this is the implementation of a time/alarm system.
   */
  public static void onStart(Runnable r) {
    transactionLock.lock();
    try {
      onStartHooks.add(r);
    } finally {
      transactionLock.unlock();
    }
  }

  static <A> A apply(Lambda1<Transaction, A> code) {
    transactionLock.lock();
    try {
      // If we are already inside a transaction (which must be on the same thread otherwise we wouldn't have acquired transactionLock),
      // then keep using that same transaction.
      var transWas = currentTransaction;
      try {
        startIfNecessary();
        return code.apply(currentTransaction);
      } finally {
        try {
          if (transWas == null) {
            currentTransaction.close();
          }
        } finally {
          currentTransaction = transWas;
        }
      }
    } finally {
      transactionLock.unlock();
    }
  }

  private static void startIfNecessary() {
    if (currentTransaction == null) {
      if (!runningOnStartHooks) {
        runningOnStartHooks = true;
        try {
          for (var r : onStartHooks) {
            r.run();
          }
        } finally {
          runningOnStartHooks = false;
        }
      }
      currentTransaction = new Transaction();
    }
  }

  void prioritized(Node rank, Handler<Transaction> action) {
    var e = new Entry(rank, action);
    prioritizedQ.add(e);
    entries.add(e);
  }

  /**
   * Add an action to run after all prioritized() actions.
   */
  void last(Runnable action) {
    lastQ.add(action);
  }

  /**
   * Add an action to run after all last() actions.
   */
  void post_(int childIx, Handler<Transaction> action) {
    if (postQ == null) {
      postQ = new HashMap<>();
    }
    // If an entry exists already, combine the old one with the new one.
    var existing = postQ.get(childIx);
    var actionIx = existing == null
      ? action
      : new Handler<Transaction>() {
          @Override
          public void run(Transaction trans) {
            existing.run(trans);
            action.run(trans);
          }
        };
    postQ.put(childIx, actionIx);
  }

  /**
   * Execute the specified code after the current transaction is closed,
   * or immediately if there is no current transaction.
   */
  public static void post(Runnable action) {
    Transaction.run(t1 -> {
      // -1 will mean it runs before anything split/deferred, and will run outside a transaction context.
      t1.post_(-1, t2 -> action.run());
    });
  }

  /**
   * If the priority queue has entries in it when we modify any of the nodes' ranks,
   * then we need to re-generate it to make sure it's up-to-date.
   */
  private void checkRegen() {
    if (toRegen) {
      toRegen = false;
      prioritizedQ.clear();
      for (var e : entries) {
        prioritizedQ.add(e);
      }
    }
  }

  void close() {
    for (;;) {
      checkRegen();
      if (prioritizedQ.isEmpty()) {
        break;
      }
      var e = prioritizedQ.remove();
      entries.remove(e);
      e.action.run(this);
    }
    for (var action : lastQ) {
      action.run();
    }
    lastQ.clear();
    if (postQ != null) {
      while (!postQ.isEmpty()) {
        var iter = postQ.entrySet().iterator();
        if (iter.hasNext()) {
          var e = iter.next();
          int ix = e.getKey();
          var h = e.getValue();
          iter.remove();
          var parent = currentTransaction;
          try {
            if (ix >= 0) {
              var trans = new Transaction();
              currentTransaction = trans;
              try {
                h.run(trans);
              } finally {
                trans.close();
              }
            } else {
              currentTransaction = null;
              h.run(null);
            }
          } finally {
            currentTransaction = parent;
          }
        }
      }
    }
  }

}
