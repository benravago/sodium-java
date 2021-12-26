package widget;

import java.util.List;

import javafx.application.Application;
import javafx.beans.Observable;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;

public abstract class SApplication extends Application {

  @Override
  public void start(Stage primaryStage) {
    var frame = new Frame();
    setup(frame);
    addNotify(frame.root.getChildren());
    primaryStage.setTitle(frame.title);
    primaryStage.setScene(new Scene(frame.root));
    primaryStage.show();
  }

  public class Frame {
    Pane root = new FlowPane();
    String title = "";

    public void setTitle(String title) {
      this.title = title;
    }
    public void setSize(int width, int height) {
      root.setPrefSize(width,height);
    }
    public void addChild(Node node) {
      root.getChildren().add(node);
    }
    public void addChildren(Node... nodes) {
      root.getChildren().addAll(nodes);
    }
  }

  public abstract void setup(Frame frame);

  void addNotify(List<Node> nodes) {
    for (var node:nodes) {
      if (node instanceof Disposable) {
        node.sceneProperty().addListener(this::disposer);
      }
    }
  }

  void disposer(Observable o) {
    if (o instanceof ReadOnlyObjectProperty<?> op && op.get() == null && op.getBean() instanceof Disposable d) {
      d.onDispose();
    }
  }

}
