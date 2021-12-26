package widget;

import javafx.application.Platform;
import javafx.scene.control.Label;

import nz.sodium.Cell;
import nz.sodium.Listener;
import nz.sodium.Operational;
import nz.sodium.Transaction;

public class SLabel extends Label implements Disposable {

  public SLabel(Cell<String> text) {
    super("");

    l = Operational.updates(text)
      .listen(t -> { // set label text via sodium api
        if (Platform.isFxApplicationThread()) {
          setText(t);
        } else {
          Platform.runLater(() -> setText(t));
        }
      });

    // set the text at the end of the transaction so SLabel works with CellLoops.
    Transaction.post(() -> Platform.runLater(() -> setText(text.sample())) );
  }

  Listener l;

  @Override
  public void onDispose() {
    l.unlisten();
  }

}
