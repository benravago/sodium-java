package nz.sodium.memory;

import java.util.Timer;
import java.util.TimerTask;

class Timed {
  
  void run() {
    var start = System.nanoTime();
    var t = new Timer(true);
    t.scheduleAtFixedRate(new TimerTask() {
      @Override
      public void run() {
        try {
          for (;;) {
            bg(System.nanoTime() - start);
            Thread.sleep(5000);
          }
        } catch (Throwable t) {
          t.printStackTrace();
        }
      }
    }, 0, 5_000);
    fg();
    t.cancel();
    bg(System.nanoTime() - start);
  }
  
  void fg() {}
  void bg(long elapsed) {}
  
  void bg(long elapsed, int count) {
    var r = count > 0 ? count / msec(elapsed) : 0;
    var m = Runtime.getRuntime().totalMemory() / 1.e+6;
    System.out.format("mem %1.2f mb   %d cyc  %1.3f c/ms \n", m, count, r);
        // null, null).println("mem " + Runtime.getRuntime().totalMemory() + ' ' + count + ' ' + r);
  }
  
  static double sec(long nano) { return nano/1e+9; }
  static double msec(long nano) { return nano/1e+6; }
}
