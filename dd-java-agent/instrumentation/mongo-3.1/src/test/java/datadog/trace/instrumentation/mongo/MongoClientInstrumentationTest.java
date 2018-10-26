// Modified by SignalFx
package datadog.trace.instrumentation.mongo;

import static org.assertj.core.api.Java6Assertions.assertThat;

import com.mongodb.ServerAddress;
import com.mongodb.connection.ClusterId;
import com.mongodb.connection.ConnectionDescription;
import com.mongodb.connection.ServerId;
import com.mongodb.event.CommandStartedEvent;
import datadog.trace.agent.test.utils.TestSpan;
import datadog.trace.agent.test.utils.TestTracer;
import java.util.Arrays;
import java.util.List;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.junit.Test;

public class MongoClientInstrumentationTest {

  private static ConnectionDescription makeConnection() {
    return new ConnectionDescription(new ServerId(new ClusterId(), new ServerAddress()));
  }

  @Test
  public void mongoSpan() {
    final CommandStartedEvent cmd =
        new CommandStartedEvent(1, makeConnection(), "databasename", "query", new BsonDocument());

    final TestSpan span = new TestSpan(new TestTracer().buildSpan("foo").start());
    DDTracingCommandListener.decorate(span.span, cmd);

    assertThat(span.getComponent()).isEqualTo("mongodb");
    assertThat(span.getDBStatement()).isNotEmpty();
    assertThat(span.getDBType()).isEqualTo("mongodb");
  }

  @Test
  public void queryScrubbing() {
    // all "secret" strings should be scrubbed out of these queries
    final BsonDocument query1 = new BsonDocument("find", new BsonString("show"));
    query1.put("stuff", new BsonString("secret"));
    final BsonDocument query2 = new BsonDocument("insert", new BsonString("table"));
    final BsonDocument query2_1 = new BsonDocument("count", new BsonString("show"));
    query2_1.put("id", new BsonString("secret"));
    query2.put("docs", new BsonArray(Arrays.asList(new BsonString("secret"), query2_1)));
    final List<BsonDocument> queries = Arrays.asList(query1, query2);
    for (final BsonDocument query : queries) {
      final CommandStartedEvent cmd =
          new CommandStartedEvent(1, makeConnection(), "databasename", "query", query);

      final TestSpan span = new TestSpan(new TestTracer().buildSpan("foo").start());
      DDTracingCommandListener.decorate(span.span, cmd);

      assertThat(span.getComponent()).isEqualTo("mongodb");
      assertThat(span.getDBStatement()).isEqualTo(query.toString().replaceAll("secret", "?"));
    }
  }
}
