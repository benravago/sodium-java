package nz.sodium;

class CoalesceHandler<A> implements TransactionHandler<A> {

  CoalesceHandler(Lambda2<A, A, A> f, StreamWithSend<A> out) {
    this.f = f;
    this.out = out;
  }

  final Lambda2<A, A, A> f;
  final StreamWithSend<A> out;
  boolean accumValid = false;
  A accum;

  @Override
  public void run(Transaction trans1, A a) {
    if (accumValid) {
      accum = f.apply(accum, a);
    } else {
      @SuppressWarnings("resource")
      var self = this;
      trans1.prioritized(out.node, trans2 -> {
        out.send(trans2, self.accum);
        self.accumValid = false;
        self.accum = null;
      });
      accum = a;
      accumValid = true;
    }
  }

}
