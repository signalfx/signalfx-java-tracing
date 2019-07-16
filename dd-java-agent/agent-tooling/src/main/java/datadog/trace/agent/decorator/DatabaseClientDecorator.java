// Modified by SignalFx
package datadog.trace.agent.decorator;

import datadog.trace.api.Config;
import io.opentracing.Span;
import io.opentracing.tag.Tags;

public abstract class DatabaseClientDecorator<CONNECTION> extends ClientDecorator {

  protected abstract String dbType();

  protected abstract String dbUser(CONNECTION connection);

  protected abstract String dbInstance(CONNECTION connection);

  @Override
  public Span afterStart(final Span span) {
    assert span != null;
    Tags.DB_TYPE.set(span, dbType());
    return super.afterStart(span);
  }

  /**
   * This should be called when the connection is being used, not when it's created.
   *
   * @param span
   * @param connection
   * @return
   */
  public Span onConnection(final Span span, final CONNECTION connection) {
    assert span != null;
    if (connection != null) {
      Tags.DB_USER.set(span, dbUser(connection));
      Tags.DB_INSTANCE.set(span, dbInstance(connection));
    }
    return span;
  }

  public Span onStatement(final Span span, final String statement) {
    assert span != null;
    int dbStatMaxLength = Config.get().getDbStatementMaxLength();
    String outStatement = statement;
    if (outStatement != null) {
      outStatement = outStatement.substring(0, Math.min(outStatement.length(), dbStatMaxLength));
    }
    Tags.DB_STATEMENT.set(span, outStatement);
    return span;
  }
}
