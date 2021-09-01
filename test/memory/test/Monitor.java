package memory.test;

import java.util.Timer;
import java.util.TimerTask;

class Monitor extends TimerTask {

  int iteration = 1;
  int done = 0;
  long then = System.currentTimeMillis();
  static final double mb = 1_000_000;

  Timer timer;

  Monitor(long period) {
    timer = new Timer(); // if daemon, then timer will end when main() ends
    timer.scheduleAtFixedRate(this,10,period);
  }

  @Override
  public void run() {
    if (iteration == done) {
      System.out.println("done");
      timer.cancel();
      return;
    }
    var now = System.currentTimeMillis();
    var elapsed = now - then; then = now;
    var increment = iteration - done; done = iteration;
    var rate = elapsed / (double)increment;
    var rt = Runtime.getRuntime();
    var total = rt.totalMemory() / mb;
    var max = rt.maxMemory() / mb;
    var free = rt.freeMemory() / mb;
    System.out.format(
      "#%d  inc %d et %dms  %.3fms/send  mem %.3f %.3f %.3f \n",
      iteration, increment, elapsed, rate, max, total, free);
  }
}
