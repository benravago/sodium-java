package nz.sodium;

/**
 * A representation for a value that may not be available until the current transaction is closed.
 */
public class Lazy<A> {

  public Lazy(Lambda0<A> f) {
    this.f = f;
  }

  public Lazy(final A a) {
    this.f = () -> a;
  }

  private final Lambda0<A> f;

  /**
   * Get the value if available, throwing an exception if not.
   * In the general case this should only be used in subsequent transactions to when the Lazy was obtained.
   */
  public final A get() {
    return f.apply();
  }

  /**
   * Map the lazy value according to the specified function,
   * so the returned Lazy reflects the value of the function applied to the input Lazy's value.
   * @param f Function to apply to the contained value. It must be <em>referentially transparent</em>.
   */
  public final <B> Lazy<B> map(final Lambda1<A, B> f) {
    return new Lazy<>(() -> f.apply(get()));
  }

  /**
   * Lift a binary function into lazy values,
   * so the returned Lazy reflects the value of the function applied to the input Lazys' values.
   */
  public final <B, C> Lazy<C> lift(final Lazy<B> b, final Lambda2<A, B, C> f) {
    return new Lazy<>(() -> f.apply(Lazy.this.get(), b.get()));
  }

  /**
   * Lift a ternary function into lazy values,
   * so the returned Lazy reflects the value of the function applied to the input Lazys' values.
   */
  public final <B, C, D> Lazy<D> lift(final Lazy<B> b, final Lazy<C> c, final Lambda3<A, B, C, D> f) {
    return new Lazy<>(() -> f.apply(Lazy.this.get(), b.get(), c.get()));
  }

  /**
   * Lift a quaternary function into lazy values,
   * so the returned Lazy reflects the value of the function applied to the input Lazys' values.
   */
  public final <B, C, D, E> Lazy<E> lift(final Lazy<B> b, final Lazy<C> c, final Lazy<D> d, final Lambda4<A, B, C, D, E> f) {
    return new Lazy<>(() -> f.apply(Lazy.this.get(), b.get(), c.get(), d.get()));
  }

}
