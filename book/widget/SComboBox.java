package widget;

import java.util.List;
import java.util.Optional;

import javafx.scene.control.ComboBox;

import nz.sodium.Cell;
import nz.sodium.CellSink;

public class SComboBox<E> extends ComboBox<E> {

  public Cell<Optional<E>> selectedItem;

  @SuppressWarnings("unchecked")
  public SComboBox(List<E> items) {
    this((E[])items.toArray());
  }

  @SafeVarargs
  public SComboBox(E...items) {
    super();
    getItems().addAll(items);

    var sel = getValue();
    var selected = new CellSink<Optional<E>>(sel == null ? Optional.<E>empty() : Optional.of(sel));

    getSelectionModel()
      .selectedItemProperty()
      .addListener((options, oldValue, newValue) -> selected.send(Optional.of(newValue)));

    selectedItem = selected;
  }

}
