package widget;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.scene.control.TextArea;

import nz.sodium.Cell;
import nz.sodium.Listener;
import nz.sodium.Operational;
import nz.sodium.Stream;
import nz.sodium.StreamSink;
import nz.sodium.Transaction;

public class STextArea extends TextArea implements Disposable {

  public Cell<String> text;

  public STextArea(String initText, int rows, int columns) {
    this(new Stream<String>(), initText, rows, columns);
  }

  public STextArea(String initText) {
    this(new Stream<String>(), initText);
  }

  public STextArea(Stream<String> sText, String initText, int rows, int columns) {
    this(sText, initText, rows, columns, new Cell<Boolean>(true));
  }

  public STextArea(Stream<String> sText, String initText) {
    this(sText, initText, new Cell<Boolean>(true));
  }

  public STextArea(String initText, int rows, int columns, Cell<Boolean> enabled) {
    this(new Stream<String>(), initText, rows, columns, enabled);
  }

  public STextArea(String initText, Cell<Boolean> enabled) {
    this(new Stream<String>(), initText, enabled);
  }

  public STextArea(Stream<String> sText, String initText, Cell<Boolean> enabled) {
    super(initText);
    setup(sText, initText, enabled);
  }

  public STextArea(Stream<String> sText, String initText, int rows, int columns, Cell<Boolean> enabled) {
    super(initText);
    setPrefRowCount(rows);
    setPrefColumnCount(columns);
    setup(sText, initText, enabled);
  }

  // Non-editable text area with text defined by a cell.
  public STextArea(Cell<String> text) {
    this(Operational.updates(text), text.sample());
    setEditable(false);
  }

  // Non-editable text area with text defined by a cell.
  public STextArea(Cell<String> text, int rows, int columns) {
    this(Operational.updates(text), text.sample(), rows, columns);
    setEditable(false);
  }

  // Non-editable text area with text defined by a cell.
  public STextArea(Cell<String> text, Cell<Boolean> enabled) {
    this(Operational.updates(text), text.sample(), enabled);
    setEditable(false);
  }

  // Non-editable text area with text defined by a cell.
  public STextArea(Cell<String> text, int rows, int columns, Cell<Boolean> enabled) {
    this(Operational.updates(text), text.sample(), rows, columns, enabled);
    setEditable(false);
  }

  private void setup(Stream<String> sText, String initText, Cell<Boolean> enabled) {

    var sUserText = new StreamSink<String>();
    allow = sText
      .map(u -> 1)
      .orElse(sDecrement)
      .accum(0, (d, b) -> b + d)
      .map(b -> b == 0);
    text = sUserText
      .gate(allow)
      .orElse(sText)
      .hold(initText);
    ChangeListener<String> dl = (observable, oldValue, newValue) -> {
      if (newValue != null) {
        sUserText.send(getText());
      }
    };
    textProperty().addListener(dl);

    // Do it at the end of the transaction so it works with looped cells
    Transaction.post(() -> setEnabled(enabled.sample()));
    l = sText
      .listen(t -> {
        Platform.runLater(() -> {
          setText(t);
          sDecrement.send(-1);
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

  private Listener l;

  @Override
  public void onDispose() {
    l.unlisten();
  }

}
