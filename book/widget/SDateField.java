package widget;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Optional;

import javafx.scene.layout.HBox;

import nz.sodium.Cell;

public class SDateField extends HBox {

  public final Cell<Calendar> date;

  public SDateField() {
    this(new GregorianCalendar());
  }

  static final String[] months = {
    "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
  };

  public SDateField(Calendar cal) {

    var years = new ArrayList<Integer>();
    var now = new GregorianCalendar().get(Calendar.YEAR);
    for (var y = now - 10; y <= now + 10; y++) {
      years.add(y);
    }

    var days = new ArrayList<Integer>();
    for (var d = 1; d <= 31; d++) {
      days.add(d);
    }

    var year = new SComboBox<>(years);
    year.getSelectionModel().select(cal.get(Calendar.YEAR));

    var month = new SComboBox<>(months);
    month.getSelectionModel().select(months[cal.get(Calendar.MONTH)]);

    var day = new SComboBox<>(days);
    day.getSelectionModel().select(cal.get(Calendar.DAY_OF_MONTH));

    getChildren().addAll(year,month,day);

    var monthIndex = month.selectedItem
      .map(ostr -> {
        if (ostr.isPresent()) {
          for (var i = 0; i < months.length; i++) {
            if (months[i].equals(ostr.get())) {
              return Optional.of(i);
            }
          }
        }
        return Optional.<Integer>empty();
      });

    date = year.selectedItem
      .lift(monthIndex, day.selectedItem, (oy, om, od) -> {
        return oy.isPresent() && om.isPresent() && od.isPresent()
          ? new GregorianCalendar(oy.get(), om.get(), od.get())
          : new GregorianCalendar();
      });
  }

}
