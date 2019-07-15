# SignalFx Java Agent

This is the SignalFx Java Agent, a [JVM agent](https://docs.oracle.com/javase/7/docs/api/java/lang/instrument/package-summary.html)
to automatically instrument your Java application to capture and report distributed traces to SignalFx.  Enabling the
agent is done by downloading the [latest JAR](https://github.com/signalfx/signalfx-java-tracing/releases/latest) and
adding its path to your JVM startup options:

```bash
  $ # Specify the Java Agent with the appropriate option and launch your application as usual
  $ java -javaagent:signalfx-tracing.jar -jar app.jar
```

In addition to using bytecode manipulation to instrument supported libraries and frameworks, the SignalFx Java Agent
configures an OpenTracing-compatible tracer to capture and export trace spans. It registers this tracer as the
OpenTracing `GlobalTracer` to easily enable custom instrumentation throughout your application:

```java
import io.opentracing.util.GlobalTracer;

public class MyClass {
  public void MyLogic() {
    // Use the GlobalTracer utility to create and modify an active span.
    final Scope scope = GlobalTracer.get().buildSpan("MyOperation").startActive(true);
    scope.span().setTag("MyTag", "MyValue");

    <your functionality...>

    // Depending on MyLogic's context within a supported framework or library, the custom span
    // created here can automatically become part of an applicable agent-generated trace,
    // like those created for web framework filter activity.

    // Close the current scope, finishing the span.
    scope.close()
  }
}
```

_The SignalFx Java Agent is designed to work with Java runtime version 8 or above._

## Supported Libraries and Frameworks

| Library | Versions Supported | Instrumentation Name(s) | Notes |
| ---     | ---                | ---                     | ---   |
| **Akka HTTP** | 10.0.0+ | `akka-http`, `akka-http-server`, `akka-http-client` | |
| **Apache HTTP Client** | 4.0+ | `httpclient` | Also supports the DropWizard HTTP Client that subclasses the Apache one |
| **AWS SDK Client** | 1.11.0+ | `aws-sdk` | |
| **Cassandra (DataStax client)** | 3.0+ | `cassandra` | |
| **CouchBase Client** | 2.0.0+ | `couchbase` | |
| **DropWizard Views** | * | `dropwizard`, `dropwizard-view` | |
| **ElasticSearch Client** | 2+ | `elasticsearch` | Supports both REST and transport clients |
| **gRPC (Client and Server)** | 1.5.0+ | `grpc` | |
| **java.net.HttpURLConnection** | * | `httpurlconnection` | |
| **Hystrix** | 1.4.0+ | `hystrix` | |
| **JAX-RS Client** | 2.0.0+ | `jaxrs` | Also supports DropWizard client 0.8.0+ |
| **JDBC API** | * | `jdbc` | |
| **Jedis (Redis client)** | 1.4.0+ | `jedis` | |
| **Jersey** | 2.1+ | `jersey` | In tandem with JAX-RS Annotations |
| **Jetty Server** | 6.0.0+, 8.0.0+ | `jetty` | |
| **JMS Messaging** | * | `jms` | |
| **JSP** | 7+ | `jsp` | |
| **Kafka Client** | 0.11.0.0+ | `kafka` | |
| **Lettuce (Redis Client)** | 5.0.0+ | `lettuce` | |
| _Java MDC_ | * | `-Dsignalfx.logs.injection=true` on Java invocation | Injects `signalfx.trace_id` and `signalfx.span_id` to MDC contexts |
| **Memcached (SpyMemcached)** | 2.10.0+ | `spymemcached` | |
| **Mongo Client** | 3.1+ | `mongo` | |
| **Mongo Async Client** | 3.3+ | `mongo` | |
| **Netty Client and Server** | 4.0+ | `netty` | Nonstandard HTTP status code tagging w/ ```-Dsignalfx.instrumentation.netty.{client,server}.nonstandard.http.status.<code>=true``` to circumvent Status5xxDecorator |
| **OkHTTP Client** | 3.0+ | `okhttp` | |
| **Play Web Framework** | 2.4+ | `play` | |
| **RabbitMQ Client** | 2.7.0+ | `rabbitmq` | |
| _Ratpack_ | 1.4+ | `ratpack` | |
| **Reactor Core** | 3.1.0+ | `reactor-core` | |
| **Java Servlet** | 2+ | `servlet` | |
| _Spark Java_ | 2.3+ | `sparkjava` | |
| **Spring Data** | 1.5.0+ | `spring-data` | Automatic tracing of all `org.springframework.data.repository.Repository` implementor public methods |
| **Spring Web** | 4.0+ | `spring-web` | Includes DispatcherServlet, HandlerAdapter, and RestTemplate |
| **Spring WebFlux** | 5.0.0+ | `spring-webflux` | |
| **Vertx Web** | 4.1.0+  | N/A | This works through the Netty instrumentation |

_Italicized_ libraries are in beta and must be explicitly enabled by setting the
`-Dsignalfx.integration.<name>.enabled=true` system property, where `<name>` is the name
specified in the table.

## Configuration and Usage

As demonstrated above, using the Java Agent requires downloading a recent release JAR and specifying its path as the
value for the `javaagent` JVM startup option.  Depending on your workflow, this should be done via the command line or
the appropriate configuration mechanism for your project management system.

**Note: Although several `javaagent` options can be specified for a given Java application, we cannot confirm support
for using multiple agents, especially those that provide similar instrumentation capabilities, as issues arising from
conflicting bytecode modifications are possible.**

The SignalFx Java Agent uses a few properties or environment variables for configuring tracer functionality and [trace
content](https://docs.signalfx.com/en/latest/apm/apm-overview/apm-metadata.html):

| System Property | Environment Variable | Default Value | Notes |
| ---             | ---                  | ---           | ---   |
| `signalfx.service.name` | `SIGNALFX_SERVICE_NAME` | `"unnamed-java-app"` | |
| `signalfx.agent.host` | `SIGNALFX_AGENT_HOST` | `"localhost"` | |
| `signalfx.endpoint.url` | `SIGNALFX_ENDPOINT_URL` | `"http://localhost:9080/v1/trace"` | Takes priority over constituent Agent properties. |
| `signalfx.integration.<name>.enabled=true` | none | Varies per instrumentation | `<name>` is the instrumentation name detailed in the supported libraries. |
| `signalfx.span.tags` | `SIGNALFX_SPAN_TAGS` | `null` | Comma-separated list of tags of the form `"key1:val1,key2:val2"` to be included in every reported span. |
| `signalfx.db.statement.max.length` | `SIGNALFX_DB_STATEMENT_MAX_LENGTH` | `1024` | The maximum number of characters written for the OpenTracing `db.statement` tag. |

**Note: System property values take priority over corresponding environment variables**

```bash
  $ export SIGNALFX_SERVICE_NAME=my-app
  $ export SIGNALFX_SPAN_TAGS="MyTag:MyValue"
  $ java -javaagent:signalfx-tracing.jar <rest of command-line arguments...>
```

The standard invocation assumes that you have a [locally deployed SignalFx Smart Agent](https://docs.signalfx.com/en/latest/apm/apm-deployment/smart-agent.html)
accessible on `localhost`.  If you want to send traces directly to the Smart Gateway or if your Smart Agent is only
accessible on a different host (e.g. in a containerized environment), set the trace host via the `signalfx.agent.host`
property or its corresponding `SIGNALFX_AGENT_HOST` environment variable to the appropriate value.  You can override the
entire trace target URL with the `signalfx.endpoint.url` property or its corresponding `SIGNALFX_ENDPOINT_URL`
environment variable.

By default, the Java Agent's tracer will have constant sampling (100% chance of tracing) and report every span to the
Agent.  Where applicable, context propagation will be done via [B3 headers](https://github.com/openzipkin/b3-propagation).

See [our example app that uses
this](https://github.com/signalfx/tracing-examples/tree/master/java-agent) for
more details on how to use and configure the SignalFx Java Agent.

# License and Versioning

The SignalFx Java Agent for Tracing is released under the terms of the Apache
Software License version 2.0. See [the license file](./LICENSE) for more details.

SignalFx's Java agent is a fork of the [DataDog Java APM
project](https://github.com/DataDog/dd-trace-java); for the time being, the agent
will be versioned in conjunction with the DataDog APM agent that it is based on,
with a SignalFx-specific patch version at the end, of the form `-sfxN`, where `N`
is the SignalFx patch starting at `0`. For example, the DD APM agent version
`0.20.0` would be initially released by us as `0.20.0-sfx0`.  We will attempt to
merge in changes from the upstream on a regular basis, especially after releases.
