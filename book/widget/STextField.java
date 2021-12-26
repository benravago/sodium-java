package widget;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.scene.control.TextField;

import nz.sodium.Cell;
import nz.sodium.Listener;
import nz.sodium.Operational;
import nz.sodium.Stream;
import nz.sodium.StreamSink;
import nz.sodium.Transaction;

public class STextField extends TextField implements Disposable {

  public Cell<String> text;
  public Stream<String> sUserChanges;

  public STextField(String initText) {
    this(new Stream<String>(), initText, 15);
  }

  public STextField(String initText, int width) {
    this(new Stream<String>(), initText, width);
  }

  public STextField(Stream<String> sText, String initText) {
    this(sText, initText, 15);
  }

  public STextField(Stream<String> sText, String initText, int width) {
    this(sText, initText, width, new Cell<Boolean>(true));
  }

  public STextField(String initText, int width, Cell<Boolean> enabled) {
    this(new Stream<String>(), initText, width, enabled);
  }

  public STextField(Stream<String> sText, String initText, int width, Cell<Boolean> enabled) {
    super(initText);
    setWidth(width);
    setup(sText, initText, enabled);
  }

  private void setup(Stream<String> sText, String initText, Cell<Boolean> enabled) {

    var sUserChangesSnk = new StreamSink<String>();
    sUserChanges = sUserChangesSnk;
    allow = sText
      .map(u -> 1) // Block local changes until remote change has been completed in the GUI
      .orElse(sDecrement)
      .accum(0, (d, b) -> b + d)
      .map(b -> b == 0);
    text = sUserChangesSnk
      .gate(allow)
      .orElse(sText)
      .hold(initText);
    ChangeListener<String> dl = (observable, oldValue, newValue) -> {
      if (newValue != null) {
        sUserChangesSnk.send(newValue);
      }
    };
    textProperty().addListener(dl);

    // Do it at the end of the transaction so it works with looped cells
    Transaction.post(() -> setEnabled(enabled.sample()));
    l = sText
      .listen(t -> { // set field text via sodium api
        Platform.runLater(() -> {
          textProperty().removeListener(dl);
          setText(t);
          textProperty().addListener(dl);
          sDecrement.send(-1); // Re-allow blocked remote changes
        });
      })
      .append(Operational.updates(enabled)
        .listen(e -> {
          if (Platform.isFxApplicationThread()) {
            setEnabled(e);
          } else {
            Platform.runLater(() -> setEnabled(e));
          }
        })
      );
  }

  void setEnabled(boolean enabled) {
    setDisabled(!enabled);
  }

  StreamSink<Integer> sDecrement = new StreamSink<>();
  Cell<Boolean> allow;

  Listener l;

  @Override
  public void onDispose() {
    l.unlisten();
  }

}
