package nz.sodium;

interface TransactionHandler<A> {

  void run(Transaction t, A a);
}
