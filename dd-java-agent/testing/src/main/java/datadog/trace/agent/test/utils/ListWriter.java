// Modified by SignalFx
package datadog.trace.agent.test.utils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/** List writer used by tests mostly */
public class ListWriter extends CopyOnWriteArrayList<List<TestSpan>> implements Writer<TestSpan> {
  private final List<CountDownLatch> latches = new ArrayList<>();

  public List<TestSpan> firstTrace() {
    return get(0);
  }

  @Override
  public void write(final List<TestSpan> trace) {
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

  public void sort() {
    forEach(
        new Consumer<List<TestSpan>>() {
          @Override
          public void accept(List<TestSpan> testSpans) {
            testSpans.sort(
                new Comparator<TestSpan>() {
                  @Override
                  public int compare(TestSpan o1, TestSpan o2) {
                    return Long.compare(o1.spanId, o2.spanId);
                  }
                });
          }
        });
  }
}
