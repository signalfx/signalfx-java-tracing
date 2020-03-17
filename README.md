# SignalFx Java Agent

## Overview

You can use this document to learn how to set up automatic instrumentation with the SignalFx Java Agent.

(To learn how to set up manual instrumentation with the SignalFx Java Agent, see [Java, Scala, and Kotlin Instrumentation](https://docs.signalfx.com/en/apmv2/apm2/apm2-instrument/apm2-java.html).)

The SignalFx Java Agent (a JVM agent) automatically instruments your Java application to capture and report distributed traces to SignalFx. 

To learn more about JVM agents, see the [documentation from Oracle](https://docs.oracle.com/javase/7/docs/api/java/lang/instrument/package-summary.html)

## Supported software

The SignalFx Java Agent is designed to work with Java runtimes version 8 or above.

Other Java Virtual Machine (JVM) - based languages, such as Scala and Kotlin, are also suppoted, but are not explicitly validated for compatibility with all instrumentations.

The agent supports the following libraries and frameworks: 

| Library | Versions Supported | Instrumentation Name(s) | Notes |
| ---     | ---                | ---                     | ---   |
| **Akka HTTP** | 10.0.0+ | `akka-http`, `akka-http-server`, `akka-http-client` | |
| **Apache HTTP Client** | 4.0+ | `httpclient` | Also supports the DropWizard HTTP Client that subclasses the Apache one |
| **AWS SDK Client** | 1.11.0+ | `aws-sdk` | |
| **Cassandra (DataStax client)** | 3.0+ | `cassandra` | |
| **CouchBase Client** | 2.0.0+ | `couchbase` | |
| **DropWizard Views** | * | `dropwizard`, `dropwizard-view` | |
| **ElasticSearch Client** | 2+ | `elasticsearch` | Supports both REST and transport clients |
| _Grizzly_ | 2.0+ | `grizzly` | |
| **gRPC (Client and Server)** | 1.5.0+ | `grpc` | |
| **java.net.HttpURLConnection** | * | `httpurlconnection` | |
| **Hibernate** | 3.5.0+ | `hibernate` | |
| **Hystrix** | 1.4.0+ | `hystrix` | |
| **JAX-RS Client** | 2.0.0+ | `jaxrs` | Also supports DropWizard client 0.8.0+ |
| **JDBC API** | * | `jdbc` | |
| **Jedis (Redis client)** | 1.4.0+ | `jedis` | Prevent command arguments from being sourced in `db.statement` tag with `-Dsignalfx.instrumentation.redis.capture-command-arguments=false` |
| **Jersey** | 2.1+ | `jersey` | In tandem with JAX-RS Annotations |
| **Jetty Server** | 6.0.0+, 8.0.0+ | `jetty` | |
| **JMS Messaging** | * | `jms` | |
| **JSP** | 7+ | `jsp` | |
| **Kafka Client** | 0.11.0.0+ | `kafka` | Disable trace propagation for unsupported environments with `-Dsignalfx.instrumentation.kafka.attempt-propagation=false` |
| **khttp** | 0.1.0+ | `khttp` | |
| **Lettuce (Redis Client)** | 5.0.0+ | `lettuce` | Prevent command arguments from being sourced in `db.statement` tag with `-Dsignalfx.instrumentation.redis.capture-command-arguments=false` |
| _Java MDC_ | * | `-Dsignalfx.logs.injection=true` on Java invocation | Injects `signalfx.trace_id` and `signalfx.span_id` to MDC contexts |
| **Memcached (SpyMemcached)** | 2.10.0+ | `spymemcached` | |
| **Mongo Client** | 3.1+ | `mongo` | |
| **Mongo Async Client** | 3.3+ | `mongo` | |
| **Netty Client and Server** | 4.0+ | `netty` | Nonstandard HTTP status code tagging w/ `-Dsignalfx.instrumentation.netty.{client,server}.nonstandard.http.status.<code>=true` to circumvent Status5xxDecorator |
| **OkHTTP Client** | 3.0+ | `okhttp` | |
| **Play Web Framework** | 2.4+ | `play` | |
| **RabbitMQ Client** | 2.7.0+ | `rabbitmq` | |
| **Ratpack** | 1.4+ | `ratpack` | |
| **Reactor Core** | 3.1.0+ | `reactor-core` | |
| **RestTemplate** | 3.1.1+ | `rest-template` | |
| **Java Servlet** | 2+ | `servlet` | |
| _Spark Java_ | 2.3+ | `sparkjava` | |
| **Spring Data** | 1.8.0+ | `spring-data` | |
| **Spring Web (MVC)** | 4.0+ | `spring-web` | Includes DispatcherServlet and HandlerAdapter |
| **Spring WebFlux** | 5.0.0+ | `spring-webflux` | |
| **Vertx Web** | 3.0.0+  | `vertx` | This works through the Netty instrumentation for requests, and also includes spans for handlers. |

_Italicized_ libraries are in beta and must be explicitly enabled by setting the
`-Dsignalfx.integration.<name>.enabled=true` system property, where `<name>` is
the name specified in the table.

## Configuration for automated instrumentation 

1. To enable the SignalFx Java Agent, you must download the [latest JAR](https://github.com/signalfx/signalfx-java-tracing/releases/latest).  

2. Add the agent's path to your JVM startup option:

```bash
$ # Specify the Java Agent with the appropriate option and launch your application as usual
$ java -javaagent:signalfx-tracing.jar -jar app.jar
```

3. In addition to using bytecode manipulation to instrument supported libraries and frameworks, the SignalFx Java Agent configures an OpenTracing-compatible tracer to capture and export trace spans. The agent registers this tracer as the OpenTracing `GlobalTracer` to easily enable custom instrumentation throughout your application:

```java
import io.opentracing.util.GlobalTracer;

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

## Additional configurations

The SignalFx Java Agent uses the following property and environment variables to configure tracer functionality and [trace
content](https://docs.signalfx.com/en/latest/apm/apm-overview/apm-metadata.html):

| System Property | Environment Variable | Default Value | Notes |
| ---             | ---                  | ---           | ---   |
| `signalfx.service.name` | `SIGNALFX_SERVICE_NAME` | `"unnamed-java-app"` | |
| `signalfx.agent.host` | `SIGNALFX_AGENT_HOST` | `"localhost"` | |
| `signalfx.endpoint.url` | `SIGNALFX_ENDPOINT_URL` | `"http://localhost:9080/v1/trace"` | Takes priority over constituent Agent properties. |
| `signalfx.tracing.enabled` | `SIGNALFX_TRACING_ENABLED` | `"true"` | Globally enables tracer creation and auto-instrumentation.  Any value not matching `"true"` will be treated as false (`Boolean.valueOf()`). |
| `signalfx.integration.<name>.enabled=true` | none | Varies per instrumentation | `<name>` is the instrumentation name detailed in the supported libraries. |
| `signalfx.span.tags` | `SIGNALFX_SPAN_TAGS` | `null` | Comma-separated list of tags of the form `"key1:val1,key2:val2"` to be included in every reported span. |
| `signalfx.db.statement.max.length` | `SIGNALFX_DB_STATEMENT_MAX_LENGTH` | `1024` | The maximum number of characters written for the OpenTracing `db.statement` tag. |
| `signalfx.recorded.value.max.length` | `SIGNALFX_RECORDED_VALUE_MAX_LENGTH` | `12288` | The maximum number of characters for any Zipkin-encoded tagged or logged value. |
| `signalfx.trace.annotated.method.blacklist` | `SIGNALFX_TRACE_ANNOTATED_METHOD_BLACKLIST` | `null` | Prevent `@Trace` annotation functionality for the target method string of format `package.OuterClass[methodOne,methodTwo];other.package.OuterClass$InnerClass[*];` (`;` is required and `*` for all methods in class). |


**Note: Although several `javaagent` options can be specified for a given Java application, SignalFx cannot confirm support for using multiple agents, especially those that provide similar instrumentation capabilities. Issues from conflicting bytecode modifications may appear.**

**Note: System property values take priority over corresponding environment variables.**

**Note: To enable debug logging for troubleshooting assistance, please set the `datadog.slf4j.simpleLogger.defaultLogLevel=debug` property at runtime.  These logs are extremely verbose.**

```bash
$ export SIGNALFX_SERVICE_NAME=my-app
$ export SIGNALFX_SPAN_TAGS="MyTag:MyValue"
$ java -javaagent:signalfx-tracing.jar <rest of command-line arguments...>
```

The standard invocation assumes that you have a [locally deployed SignalFx Smart
Agent](https://docs.signalfx.com/en/latest/apm/apm-deployment/smart-agent.html)
accessible on `localhost`. If you want to send traces directly to the Smart
Gateway or if your Smart Agent is only accessible on a different host (e.g. in a
containerized environment), set the trace host via the `signalfx.agent.host`
property or its corresponding `SIGNALFX_AGENT_HOST` environment variable to the
appropriate value. You can override the entire trace target URL with the
`signalfx.endpoint.url` property or its corresponding `SIGNALFX_ENDPOINT_URL`
environment variable.

By default, the Java Agent's tracer will have constant sampling (100% chance of
tracing) and report every span to the Agent. Where applicable, context
propagation will be done via [B3
headers](https://github.com/openzipkin/b3-propagation).

See [our example applications that use
this utility](https://github.com/signalfx/tracing-examples/tree/master/signalfx-tracing/signalfx-java-tracing) for
more details on how to use and configure the SignalFx Java Agent.

### Trace annotation

In cases where you'd like to gain some of the benefits of custom instrumentation without
using the OpenTracing `GlobalTracer` and API directly, we offer a `@Trace` annotation you
can use where desired:

```java
import com.signalfx.tracing.api.Trace;

public class MyClass {
  @Trace
  public void MyLogic() {
      <...>
  }
}
```

Each time an annotated method is invoked, a span denoting its duration and detailing any thrown
exceptions will be produced.  This utility is provided assuming you've added the `signalfx-trace-api`
dependency matching the version of your Java Agent:

```
Maven:
<dependency>
   <groupId>com.signalfx.public</groupId>
   <artifactId>signalfx-trace-api</artifactId>
   <version>MyAgentVersion</version>
   <scope>provided</scope>
</dependency>

Gradle:
compileOnly group: 'com.signalfx.public', name: 'signalfx-trace-api', version: 'MyAgentVersion'
```

This annotation can be disabled at runtime using the `signalfx.trace.annotated.method.blacklist` property
and associated environment variable as detailed [above](#configuration-and-usage).

### Cross-thread tracing

The Java agent includes support for keeping track of the span context across thread boundaries.  This can be a bit tricky because it is generally undesirable for spans to be automatically tracked across threads because it is generally impossible for the Java agent to know whether a thread is logically intended to be part of the current operation, or whether it is intended for some background asynchronous task that is distinct from the current operation.  Therefore, the agent requires an explicit marker on spans to make them automatically propagated when using Java's standard concurrency tools (e.g. ``Executor``).  If you already have access to the current scope (e.g. from an ``activate()`` call), you simply set the **async propagation** flag on the span like this:

```
Java

   import com.signalfx.tracing.context.TraceScope;

   // ...

       Span span = GlobalTracer.get().buildSpan("my-operation").start();
       try (Scope sc = GlobalTracer.get().scopeManager().activate(span, true)) {
           // The below cast will always work as long as you haven't set a custom tracer
           ((TraceScope) scope).setAsyncPropagation(true);
           // ... Dispatch work to a Java thread.
           // Any methods calls in the new thread will have their active scope set to the current one.
       }
```       

Or, if you don't have access to the scope in the code that determines whether the operation should be continued across threads, you can get it from the GlobalTracer:

```

   import com.signalfx.tracing.context.TraceScope;

   // ...

      // The below cast will work as long as you haven't set a custom tracer implementation and if there is a currently active span.
      // If the active scope could be null (i.e. no span has been activated in the current scope), you must guard the cast and set.
      ((TraceScope) GlobalTracer.get().scopeManager().active()).setAsyncPropagation(true);
      // ... Dispatch work to a Java thread using an Executor.
      // Any methods calls in the new thread will have their active scope set to the current one.

```

The ``com.signalfx.tracing.context.TraceScope`` class is provided by the agent JAR, but to make it work with your IDE you should add the Maven/Gradle dependency on ``signalfx-trace-api`` as described in :ref:`Trace Annotations <apm-java-trace-annotation>`

If you do not set the async propagation flag, spans generated in different threads will be considered part of a different trace.  You can always pass the ``Span`` instance across thread boundaries via parameters or closures and reactivate it manually in the thread using ``GlobalTracer.get().scopeManager().activate(Span span, boolean closeOnFinish)``.  Just remember that ``Scope`` instances are :strong:`not` thread-safe -- they should not even be passed between threads, even if externally synchronized.


## License and versioning

The SignalFx Java Agent for Tracing is released under the terms of the Apache Software License version 2.0. For more details, see [the license file](./LICENSE).

SignalFx's Java agent is a fork of the [DataDog Java APM
project](https://github.com/DataDog/dd-trace-java); for the time being, the
agent will be versioned in conjunction with the DataDog APM agent that it is
based on, with a SignalFx-specific patch version at the end, of the form
`-sfxN`, where `N` is the SignalFx patch starting at `0`. For example, the DD
APM agent version `0.20.0` would be initially released by us as `0.20.0-sfx0`.
We will attempt to merge in changes from the upstream on a regular basis,
especially after releases.
