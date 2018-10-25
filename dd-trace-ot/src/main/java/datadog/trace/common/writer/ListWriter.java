// Modified by SignalFx
package datadog.trace.common.writer;

import io.opentracing.mock.MockSpan;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** List writer used by tests mostly */
public class ListWriter extends CopyOnWriteArrayList<List<MockSpan>> implements Writer<MockSpan> {
  private final List<CountDownLatch> latches = new ArrayList<>();

  public List<MockSpan> firstTrace() {
    return get(0);
  }

  @Override
  public void write(final List<MockSpan> trace) {
    synchronized (latches) {
      add(trace);
      for (final CountDownLatch latch : latches) {
        if (size() >= latch.getCount()) {
          while (latch.getCount() > 0) {
            latch.countDown();
          }
        }
      }
    }
  }

  public void waitForTraces(final int number) throws InterruptedException, TimeoutException {
    final CountDownLatch latch = new CountDownLatch(number);
    synchronized (latches) {
      if (size() >= number) {
        return;
      }
      latches.add(latch);
    }
    if (!latch.await(20, TimeUnit.SECONDS)) {
      throw new TimeoutException(
          "Timeout waiting for " + number + " trace(s). ListWriter.size() == " + size());
    }
  }

  @Override
  public void start() {
    close();
  }

  @Override
  public void close() {
    clear();
    synchronized (latches) {
      for (final CountDownLatch latch : latches) {
        while (latch.getCount() > 0) {
          latch.countDown();
        }
      }
      latches.clear();
    }
  }

  @Override
  public String toString() {
    return "ListWriter { size=" + this.size() + " }";
  }
}
