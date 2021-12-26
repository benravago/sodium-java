package c2.s2;

import widget.SApplication;
import widget.SLabel;
import widget.STextField;

public class Label extends SApplication {

  @Override
  public void setup(Frame frame) {
    frame.setTitle("label");
    var msg = new STextField("Hello");
    var lbl = new SLabel(msg.text);
    frame.addChildren(msg,lbl);
    frame.setSize(400, 160);    
  }
}
