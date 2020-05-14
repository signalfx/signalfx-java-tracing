# SignalFx Java Agent

The SignalFx Java Agent is a
[Java Virtual Machine (JVM) agent](https://docs.oracle.com/javase/7/docs/api/java/lang/instrument/package-summary.html)
that automatically instruments your Java application to capture and
report distributed traces to SignalFx. Download the JAR for the agent's 
[latest release](https://github.com/signalfx/signalfx-java-tracing/releases/latest)
and add its path to your JVM startup options:

```bash
$ curl -L https://github.com/signalfx/signalfx-java-tracing/releases/latest/download/signalfx-tracing.jar -o signalfx-tracing.jar
$ java -javaagent:./signalfx-tracing.jar
```
For more information, see [Configure the SignalFx Java Agent](#Configure-the-SignalFx-Java-Agent).

The agent instruments supported libraries and frameworks with bytecode
manipulation and configures an OpenTracing-compatible tracer to capture
and export trace spans. The agent also registers an OpenTracing `GlobalTracer`
so you can support existing custom instrumentation or add custom
instrumentation to your application later.

By default, the tracer has constant sampling (i.e., 100% of spans) and
reports every span. Context propagation uses
[B3 headers](https://github.com/openzipkin/b3-propagation).

To see the SignalFx Agent in action with sample applications, see
our [examples](https://github.com/signalfx/tracing-examples/tree/master/signalfx-tracing/signalfx-java-tracing).

## Requirements and supported software

Specify the SignalFx Java Agent as the only JVM agent for your application.
If you specify multiple agents, you may encounter issues with at least one
of them.

The agent works with Java runtimes version 8 and higher. Other JVM-based
languages like Scala and Kotlin are also supported, but may not work with all
instrumentations.

These are the supported libraries. _Italicized_ libraries are in beta. Enable
beta libraries by setting this system property:

`-Dsignalfx.integration.<name>.enabled=true`

where `<name>` is the instrumentation name specified in
the table.

| Library | Versions supported | Instrumentation name(s) | Notes |
| ---     | ---                | ---                     | ---   |
| Akka HTTP | 10.0.0+ | `akka-http`,<br>`akka-http-server`,<br>`akka-http-client` | |
| Apache HTTP Client | 4.0+ | `httpclient` | Also supports the DropWizard HTTP Client that subclasses the Apache one. |
| AWS SDK Client | 1.11.0+ | `aws-sdk` | |
| Cassandra (DataStax client) | 3.0+ | `cassandra` | |
| CouchBase Client | 2.0.0+ | `couchbase` | |
| DropWizard Views | * | `dropwizard`,<br>`dropwizard-view` | |
| ElasticSearch Client | 2+ | `elasticsearch` | Supports both REST and transport clients. |
| _Grizzly_ | 2.0+ | `grizzly` | |
| gRPC (Client and Server) | 1.5.0+ | `grpc` | |
| java.net.HttpURLConnection | * | `httpurlconnection` | |
| Hibernate | 3.5.0+ | `hibernate` | |
| Hystrix | 1.4.0+ | `hystrix` | |
| JAX-RS Client | 2.0.0+ | `jaxrs` | Also supports DropWizard client 0.8.0+. Supports exceptions whitelist using `@TraceSetting`. |
| JDBC API | * | `jdbc` | |
| Jedis (Redis client) | 1.4.0+ | `jedis` | Prevent command arguments from being sourced in `db.statement` tag with `-Dsignalfx.instrumentation.redis.capture-command-arguments=false`. |
| Jersey | 2.1+ | `jersey` | In tandem with JAX-RS Annotations. |
| Jetty Server | 6.0.0+, 8.0.0+ | `jetty` | |
| JMS Messaging | * | `jms` | |
| JSP | 7+ | `jsp` | |
| Kafka Client | 0.11.0.0+ | `kafka` | Disable trace propagation for unsupported environments with `-Dsignalfx.instrumentation.kafka.attempt-propagation=false`. |
| khttp | 0.1.0+ | `khttp` | |
| Lettuce (Redis Client) | 5.0.0+ | `lettuce` | Prevent command arguments from being sourced in `db.statement` tag with `-Dsignalfx.instrumentation.redis.capture-command-arguments=false`. |
| _Java MDC_ | * | `-Dsignalfx.logs.injection=true` on Java invocation | Injects `signalfx.trace_id` and `signalfx.span_id` to MDC contexts. |
| Memcached (SpyMemcached) | 2.10.0+ | `spymemcached` | |
| Mongo Client | 3.1+ | `mongo` | |
| Mongo Async Client | 3.3+ | `mongo` | |
| Netty Client and Server | 4.0+ | `netty` | Nonstandard HTTP status code tagging w/ `-Dsignalfx.instrumentation.netty.{client,server}.nonstandard.http.status.<code>=true` to circumvent Status5xxDecorator. |
| OkHTTP Client | 3.0+ | `okhttp` | |
| Play Web Framework | 2.4+ | `play` | |
| RabbitMQ Client | 2.7.0+ | `rabbitmq` | |
| Ratpack | 1.4.0 - 1.7.x | `ratpack` | |
| Reactor Core | 3.1.0+ | `reactor-core` | |
| RestTemplate | 3.1.1+ | `rest-template` | |
| Java Servlet | 2+ | `servlet` | |
| _Spark Java_ | 2.3+ | `sparkjava` | |
| Spring Data | 1.8.0+ | `spring-data` | |
| Spring Web (MVC) | 4.0+ | `spring-web` | Includes DispatcherServlet and HandlerAdapter. Supports exceptions whitelist using `@TraceSetting`. |
| Spring WebFlux | 5.0.0+ | `spring-webflux` | |
| Vertx Web | 3.0.0+  | `vertx` | This works through the Netty instrumentation for requests, and also includes spans for handlers. |

## Configure the SignalFx Java Agent

Send traces from your Java application to a local or remote Smart Agent or
OpenTelemetry Collector.

### Configuration values

The agent needs the following properties or environment variables for configuring
tracer functionality and trace content. System property values take priority
over corresponding environment variables.

| System property | Environment variable | Default value | Notes |
| ---             | ---                  | ---           | ---   |
| `signalfx.service.name` | `SIGNALFX_SERVICE_NAME` | `"unnamed-java-app"` | The name of the service. |
| `signalfx.agent.host` | `SIGNALFX_AGENT_HOST` | `"localhost"` | The endpoint for a SignalFx Smart Agent or OpenTelemetry Collector. |
| `signalfx.endpoint.url` | `SIGNALFX_ENDPOINT_URL` | `"http://localhost:9080/v1/trace"` | Takes priority over constituent Agent properties. |
| `signalfx.tracing.enabled` | `SIGNALFX_TRACING_ENABLED` | `"true"` | Globally enables tracer creation and auto-instrumentation.  Any value not matching `"true"` is treated as false (`Boolean.valueOf()`). |
| `signalfx.integration.<name>.enabled=true` | none | Varies per instrumentation | `<name>` is the instrumentation name detailed in the supported libraries. |
| `signalfx.span.tags` | `SIGNALFX_SPAN_TAGS` | `null` | Comma-separated list of tags included in every reported span. For example, `"key1:val1,key2:val2"`. |
| `signalfx.db.statement.max.length` | `SIGNALFX_DB_STATEMENT_MAX_LENGTH` | `1024` | The maximum number of characters written for the OpenTracing `db.statement` tag. |
| `signalfx.recorded.value.max.length` | `SIGNALFX_RECORDED_VALUE_MAX_LENGTH` | `12288` | The maximum number of characters for any Zipkin-encoded tagged or logged value. |
| `signalfx.trace.annotated.method.blacklist` | `SIGNALFX_TRACE_ANNOTATED_METHOD_BLACKLIST` | `null` | Prevents `@Trace` annotation functionality for the target method string of format `package.OuterClass[methodOne,methodTwo];other.package.OuterClass$InnerClass[*];`. All methods in class need to include `*` and `;`. |
| `signalfx.max.spans.per.trace` | `SIGNALFX_MAX_SPANS_PER_TRACE` | `0 (no limit)` | Drops traces with more spans than this. Intended to prevent runaway traces from flooding upstream systems. |
| `signalfx.max.continuation.depth` | `SIGNALFX_MAX_CONTINUATION_DEPTH` | `100` | Stops propagating asynchronous context at this recursive depth. Intended to prevent runaway traces from leaking memory. |

### Steps

Follow these steps to configure the agent to send traces for `your_app` to a
Smart Agent available on `localhost`. To send traces to a remote Smart
Agent, instead specify the `signalfx.agent.host` system property or
`SIGNALFX_AGENT_HOST` environment variable before you include the Java Agent in your
application.

1. Download the latest version of the agent from the
[releases](https://github.com/signalfx/signalfx-java-tracing/releases) page.
2. Set the required environment variables or system properties for your
application. For more information about the required environment variables,
see the [Configuration values](#configuration-values).
Set this environment variable from the command line:
    ```bash
    $ export SIGNALFX_SERVICE_NAME="your_app"
    ```
1. Include the agent in your Java application: 
    ```bash
    $ java -javaagent:path/to/signalfx-tracing.jar -jar app.jar
    ```

## Troubleshoot the SignalFx Java Agent

Enable debug logging for troubleshooting assistance. Set this property at
runtime:

`-Ddatadog.slf4j.simpleLogger.defaultLogLevel=debug`

These logs are extremely verbose. Enable debug logging only when needed.
Debug logging negatively impacts the performance of your application.

## Manually instrument a Java application

You can use the OpenTracing `GlobalTracer` or a `@Trace` annotation to manually
instrument your Java application.

### Configure the OpenTracing `GlobalTracer`

The SignalFx Java Agent configures an OpenTracing-compatible tracer
to capture and export trace spans. It registers this tracer as the OpenTracing
`GlobalTracer` to easily enable custom instrumentation throughout your
application:
```java
import io.opentracing.util.GlobalTracer;
import io.opentracing.*;

public class MyClass {
  public void MyLogic() {
    // Use the GlobalTracer utility to create and modify an active span.
    final Tracer tracer = GlobalTracer.get();

    // Depending on MyLogic's context within a supported framework or library, the custom span
    // created here can automatically become part of an applicable agent-generated trace,
    // like those created for web framework filter activity.
    final Span span = tracer.buildSpan("MyOperation").start();
    try (Scope scope = tracer.scopeManager().activate(span)) {
        span.setTag("MyTag", "MyValue");
        <your functionality...>
    } finally {
       span.finish();
    }
  }
}
```

### Configure a `Trace` annotation

If you want to configure custom instrumentation and don't want to use the
OpenTracing `GlobalTracer` and API directly, configure a `@Trace` annotation.

You can disable the annotation at runtime with the `signalfx.trace.annotated.method.blacklist`
system property or associated environment variable. For more information,
see [Configuration values](#Configuration-values).

1. Add the `signalfx-trace-api`
dependency matching the version of the agent:
      ```
      Maven:
      <dependency>
        <groupId>com.signalfx.public</groupId>
        <artifactId>signalfx-trace-api</artifactId>
        <version>MyAgentVersion</version>
        <scope>provided</scope>
      </dependency>
      ```
      ```
      Gradle:
      compileOnly group: 'com.signalfx.public', name: 'signalfx-trace-api', version: 'MyAgentVersion'
      ```
2. Add the trace annotation to your application's code:
      ```java
      import com.signalfx.tracing.api.Trace;

      public class MyClass {
        @Trace
        public void MyLogic() {
            <...>
        }
      }
      ```

Each time the application invokes the annotated method, it creates a span that
denote its duration and provides any thrown exceptions. 

### Whitelist exceptions

If you don't want certain exception types to mark their spans with an error
tag, where available you can whitelist an exception using `@TraceSetting` 
annotation. Only some libraries support whitelisting. For more information,
see [Requirements and supported software](#requirements-and-supported-software).

Add this annotation to whitelist exceptions:
```java
import com.signalfx.tracing.api.TraceSetting;

@TraceSetting(allowedExceptions = {InvalidArgumentException.class})
public String getFoo() {
    // This error will not cause the span to have error tag
    throw new InvalidArgumentException();
}
```

### Track span context across threads

Use the Java Agent to track span context across thread boundaries, assuming
your asynchronous or concurrent workers are supported. Provide explicit
marker for spans to automatically propagate them when using Java's standard
concurrency tools. If you already have access to the current scope
(e.g., from an ``activate()`` call), set the `async propagation` flag on the
span like this:

```java
import com.signalfx.tracing.context.TraceScope;

// ...

    Span span = GlobalTracer.get().buildSpan("my-operation").start();
    try (Scope sc = GlobalTracer.get().scopeManager().activate(span, true)) {
        // The below cast will always work as long as you haven't set a custom tracer.
        ((TraceScope) scope).setAsyncPropagation(true);
        // ... Dispatch work to a Java thread.
        // Any methods calls in the new thread will have their active scope set to the current one.
    }
```

If you don't have access to the scope that determines whether the operation
should be continued across threads, you can get it from the `GlobalTracer`
like this:

```java
import com.signalfx.tracing.context.TraceScope;

// ...

    // The below cast will always work as long as you haven't set a custom tracer.
    ((TraceScope) GlobalTracer.get().scopeManager().active()).setAsyncPropagation(true);
    // ... Dispatch work to a Java thread.
    // Any methods calls in the new thread will have their active scope set to the current one.
```

If you don't set the `async propagation` flag, spans generated in different
threads will be considered part of a different trace. You can pass the ``Span``
instance across thread boundaries via parameters or closures and reactivate it
manually in the thread with ``GlobalTracer.get().scopeManager().activate(Span span, boolean closeOnFinish)``.
Just note that ``Scope`` instances aren't thread-safe and shouldn't be passed
between threads, even if externally synchronized.

# License and versioning

The SignalFx Java Agent for Tracing is released under the terms of the Apache
Software License version 2.0. See [the license file](./LICENSE) for more
details.

SignalFx's Java agent is a fork of the [DataDog Java APM
project](https://github.com/DataDog/dd-trace-java); for the time being, the
agent will be versioned in conjunction with the DataDog APM agent that it is
based on, with a SignalFx-specific patch version at the end, of the form
`-sfxN`, where `N` is the SignalFx patch starting at `0`. For example, the DD
APM agent version `0.20.0` would be initially released by us as `0.20.0-sfx0`.
We will attempt to merge in changes from the upstream on a regular basis,
especially after releases.
