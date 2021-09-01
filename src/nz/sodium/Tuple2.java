package nz.sodium;

/**
 * A generalized 2-tuple.
 */
public final class Tuple2<A, B> {

  public Tuple2(A a, B b) {
    this.a = a;
    this.b = b;
  }
  public A a;
  public B b;

  @Override
  public boolean equals(Object o) {
    var other = (Tuple2<A, B>) o;
    return a.equals(other.a) && b.equals(other.b);
  }

  @Override
  public int hashCode() {
    return a.hashCode() + b.hashCode();
  }

}
