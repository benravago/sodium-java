package nz.sodium;

/**
 * A handle for a listener that was registered with {@link Cell#listen(Handler)} or {@link Stream#listen(Handler)}.
 */
public interface Listener {

  /**
   * Deregister the listener that was registered so it will no longer be called back,
   * allowing associated resources to be garbage collected.
   */
  void unlisten();

  /**
   * Combine listeners into one so that invoking {@link #run()}
   * on the returned listener will unlisten both the inputs.
   */
  default Listener append(Listener other) {
    var self = this;
    return () -> {
      self.unlisten();
      other.unlisten();
    };
  }

}
