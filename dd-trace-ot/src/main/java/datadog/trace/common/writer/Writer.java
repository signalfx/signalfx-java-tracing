// Modified by SignalFx
package datadog.trace.common.writer;

import io.opentracing.Span;
import java.util.List;

/** A writer is responsible to send collected spans to some place */
public interface Writer<T extends Span> {

  /**
   * Write a trace represented by the entire list of all the finished spans
   *
   * @param trace the list of spans to write
   */
  void write(List<T> trace);

  /** Start the writer */
  void start();

  /**
   * Indicates to the writer that no future writing will come and it should terminates all
   * connections and tasks
   */
  void close();
}
