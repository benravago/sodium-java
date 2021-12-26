package nz.sodium.edgecase;

import nz.sodium.Cell;
import nz.sodium.CellLoop;
import nz.sodium.CellSink;
import nz.sodium.Operational;
import nz.sodium.Stream;
import nz.sodium.Transaction;

public class Loopback {
  public static void main(String[] args) {
    System.out.println("Running loopback-negative-feedback test");
    loopbackNegativeFeedbackTest1();
  }

  static <T> void addStdOutListener(Cell<T> cell, String comment) {
    cell.listen(e -> System.out.println("Cell listener: " + comment + " " + e.toString()));
  }

  static <T> void addStdOutListener(Stream<T> stream, String comment) {
    stream.listen(e -> System.out.println("Stream listener: " + comment + " " + e.toString()));
  }

  static void loopbackNegativeFeedbackTest1() {
    try (
      var currentLevel = new CellSink<>(0.0);
      var anotherCell = new CellSink<>(1.2345);
    ) {
      var thresholdDifference = 0.01;
      Transaction.runVoid(() -> {
        var feedbackLevel = new CellLoop<Double>();
        var levelDifference = currentLevel.lift(feedbackLevel, (a, b) -> a - b);

        // Stabilisation updates to be fed to some external system, so are required to be an Event Stream.
        var stabilisationUpdates = Operational
          .updates(levelDifference)
          .filter(d -> Math.abs(d) > thresholdDifference);

        feedbackLevel.loop(stabilisationUpdates 
          // .coalesce((d, s) -> (d + s)) // TODO: what should this be? coalesce() is private
          .accum(0.0, (d, s) -> (d + s))
        );

        addStdOutListener(stabilisationUpdates, "stabilisationUpdates");
        addStdOutListener(levelDifference, "levelDifference");
        addStdOutListener(feedbackLevel, "feedbackLevel");

        var levelDifference_map = levelDifference.map(a -> 2.0 * a);
        addStdOutListener(levelDifference_map, "levelDifference_map");
  
        var levelDifference_lift = anotherCell.lift(levelDifference, (a, b) -> a + b);
        addStdOutListener(levelDifference_lift, "levelDifference_lift");
      });

      System.out.println("About to send 20.0");
      currentLevel.send(20.0);

      System.out.println("About to send 15.0");
      currentLevel.send(15.0);
    }    
    System.out.println("done");
  }
}
