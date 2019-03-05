package datadog.trace.instrumentation.jdbc;

import static datadog.trace.agent.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static io.opentracing.log.Fields.ERROR_OBJECT;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.api.DDSpanTypes;
import datadog.trace.api.DDTags;
import datadog.trace.bootstrap.CallDepthThreadLocalMap;
import datadog.trace.bootstrap.ExceptionLogger;
import datadog.trace.bootstrap.JDBCMaps;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.noop.NoopScopeManager.NoopScope;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public final class PreparedStatementInstrumentation extends Instrumenter.Default {
  public PreparedStatementInstrumentation() {
    super("jdbc");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return not(isInterface()).and(safeHasSuperType(named("java.sql.PreparedStatement")));
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        nameStartsWith("execute").and(takesArguments(0)).and(isPublic()),
        PreparedStatementAdvice.class.getName());
  }

  public static class PreparedStatementAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Scope startSpan(@Advice.This final PreparedStatement statement) {
      final int callDepth = CallDepthThreadLocalMap.incrementCallDepth(PreparedStatement.class);
      if (callDepth > 0) {
        return null;
      }
      final String sql = JDBCMaps.preparedStatements.get(statement);
      Connection connection;
      try {
        connection = statement.getConnection();
        try {
          // unwrap the connection to cache the underlying actual connection and to not cache proxy
          // objects
          if (connection.isWrapperFor(Connection.class)) {
            connection = connection.unwrap(Connection.class);
          }
        } catch (final Exception | AbstractMethodError e) {
          // perhaps wrapping isn't supported?
          // ex: org.h2.jdbc.JdbcConnection v1.3.175
          // or: jdts.jdbc which always throws `AbstractMethodError` (at least up to version 1.3)
          // Stick with original connection.
        }
      } catch (final Throwable e) {
        // Had some problem getting the connection.
        ExceptionLogger.LOGGER.debug("Could not get connection for PreparedStatementAdvice", e);
        return NoopScope.INSTANCE;
      }

      JDBCMaps.DBInfo dbInfo = JDBCMaps.connectionInfo.get(connection);
      /**
       * Logic to get the DBInfo from a JDBC Connection, if the connection was never seen before,
       * the connectionInfo map will return null and will attempt to extract DBInfo from the
       * connection. If the DBInfo can't be extracted, then the connection will be stored with the
       * DEFAULT DBInfo as the value in the connectionInfo map to avoid retry overhead.
       *
       * <p>This should be a util method to be shared between PreparedStatementInstrumentation and
       * StatementInstrumentation class, but java.sql.* are on the platform classloaders in Java 9,
       * which prevents us from referencing them in the bootstrap utils.
       */
      {
        if (dbInfo == null) {
          try {
            final DatabaseMetaData metaData = connection.getMetaData();
            final String url = metaData.getURL();
            if (url != null) {
              // Remove end of url to prevent passwords from leaking:
              final String sanitizedURL = url.replaceAll("[?;].*", "");
              final String type = url.split(":", -1)[1];
              String user = metaData.getUserName();
              if (user != null && user.trim().equals("")) {
                user = null;
              }
              dbInfo = new JDBCMaps.DBInfo(sanitizedURL, type, user);
            } else {
              dbInfo = JDBCMaps.DBInfo.DEFAULT;
            }
          } catch (final SQLException se) {
            dbInfo = JDBCMaps.DBInfo.DEFAULT;
          }
          JDBCMaps.connectionInfo.put(connection, dbInfo);
        }
      }

      final Scope scope =
          GlobalTracer.get().buildSpan(dbInfo.getType() + ".query").startActive(true);

      final Span span = scope.span();
      Tags.DB_TYPE.set(span, dbInfo.getType());
      Tags.SPAN_KIND.set(span, Tags.SPAN_KIND_CLIENT);
      Tags.COMPONENT.set(span, "java-jdbc-prepared_statement");

      span.setTag(DDTags.SERVICE_NAME, dbInfo.getType());
      span.setTag(DDTags.RESOURCE_NAME, sql == null ? JDBCMaps.DB_QUERY : sql);
      span.setTag(DDTags.SPAN_TYPE, DDSpanTypes.SQL);
      span.setTag("span.origin.type", statement.getClass().getName());
      span.setTag("db.jdbc.url", dbInfo.getUrl());

      if (dbInfo.getUser() != null) {
        Tags.DB_USER.set(span, dbInfo.getUser());
      }
      return scope;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Enter final Scope scope, @Advice.Thrown final Throwable throwable) {
      if (scope != null) {
        if (throwable != null) {
          final Span span = scope.span();
          Tags.ERROR.set(span, true);
          span.log(Collections.singletonMap(ERROR_OBJECT, throwable));
        }
        scope.close();
        CallDepthThreadLocalMap.reset(PreparedStatement.class);
      }
    }
  }
}
