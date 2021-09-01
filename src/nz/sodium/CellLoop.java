package nz.sodium;

/**
 * A forward reference for a {@link Cell} equivalent to the Cell that is referenced.
 */
public final class CellLoop<A> extends LazyCell<A> {

  public CellLoop() {
    super(new StreamLoop<A>(), null);
  }

  /**
   * Resolve the loop to specify what the CellLoop was a forward reference to.
   * It must be invoked inside the same transaction as the place where the CellLoop is used.
   * This requires you to create an explicit transaction with {@link Transaction#run(Lambda0)}
   * or {@link Transaction#runVoid(Runnable)}.
   */
  public void loop(Cell<A> out) {
    var self = this;
    Transaction.apply(trans -> {
      ((StreamLoop<A>) self.str).loop(out.updates());
      self.lazyInitValue = out.sampleLazy(trans);
      return Unit.UNIT;
    });
  }

  @Override
  A sampleNoTrans() {
    if (!((StreamLoop<A>) str).assigned) {
      throw new RuntimeException("CellLoop sampled before it was looped");
    }
    return super.sampleNoTrans();
  }

}
