package nz.sodium;

interface TransactionHandler<A> extends AutoCloseable {
  void run(Transaction trans, A a);
  default void close() {}
}
