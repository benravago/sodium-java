package nz.sodium;

class CoalesceHandler<A> implements TransactionHandler<A> {

  CoalesceHandler(Lambda2<A, A, A> f, StreamWithSend<A> out) {
    this.f = f;
    this.out = out;
  }

  private final Lambda2<A, A, A> f;
  private final StreamWithSend<A> out;
  private boolean accumValid = false;
  private A accum;

  @Override
  public void run(Transaction t1, A a) {
    if (accumValid) {
      accum = f.apply(accum, a);
    } else {
      var self = this;
      t1.prioritized(out.node, (t2) -> {
        out.send(t2, self.accum);
        self.accumValid = false;
        self.accum = null;
      });
      accum = a;
      accumValid = true;
    }
  }

}
