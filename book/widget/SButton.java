package widget;

import javafx.application.Platform;
import javafx.scene.control.Button;

import nz.sodium.Cell;
import nz.sodium.Listener;
import nz.sodium.Operational;
import nz.sodium.Stream;
import nz.sodium.StreamSink;
import nz.sodium.Transaction;
import nz.sodium.Unit;

public class SButton extends Button implements Disposable {

  public SButton(String label) {
    this(label, new Cell<Boolean>(true));
  }

  public SButton(String label, Cell<Boolean> enabled) {
    super(label);

    // monitor clicks
    var sClickedSink = new StreamSink<Unit>();
    sClicked = sClickedSink;
    setOnAction(e -> sClickedSink.send(Unit.UNIT));

    l = Operational.updates(enabled)
      .listen(e -> { // enable button via sodium api
        if (Platform.isFxApplicationThread()) {
          setEnabled(e);
        } else {
          Platform.runLater(() -> setEnabled(e));
        }
      });

    // do it at the end of the transaction so it works with looped cells
    Transaction.post(() -> setEnabled(enabled.sample()));
  }

  void setEnabled(boolean enabled) {
    setDisabled(!enabled);
  }

  Stream<Unit> sClicked;

  Listener l;

  @Override
  public void onDispose() {
    l.unlisten();
  }

}
