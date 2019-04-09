# SignalFx Java Agent

This is the SignalFx Java Agent, a JVM agent to automatically instrument your
Java application to capture and report distributed traces to SignalFx.

The SignalFx Java Agent automatically configures an OpenTracing-compatible
tracer to capture and export trace spans. It also installs this tracer
as the OpenTracing `GlobalTracer` to enable additional custom instrumentation.

# Usage

The SignalFx Java Agent uses a few environment variables for its configuration.
You should set your application's service name via `SIGNALFX_SERVICE_NAME`:

```
$ export SIGNALFX_SERVICE_NAME=my-app
$ java -javaagent:signalfx-tracing.jar <rest of command-line arguments...>
```

This invocation assumes that you have a [locally deployed SignalFx Smart
Agent](https://docs.signalfx.com/en/latest/apm/apm-deployment/smart-agent.html)
accessable on `localhost`.  If you want to send traces directly to the Smart
Gateway or if your Smart Agent is only accessible on a different host (e.g. in
a containerized environment), set the trace host via the `SIGNALFX_AGENT_HOST`
envvar to the approprate value (default is `localhost`).  You can override the
entire trace target URL with the `SIGNALFX_ENDPOINT_URL` envvar (default:
`http://localhost:9080/v1/trace`).

See [our example app that uses
this](https://github.com/signalfx/tracing-examples/tree/master/java-agent) for
more details on how to use and configure the SignalFx Java Agent.

# Supported libraries and frameworks

_Italicized_ libraries are in beta and must be explictly enabled by setting the
property `-Dsignalfx.integration.NAME.enabled=true`, where `NAME` is the name
given in the table below.

| Library | Versions Supported | Instrumentation Name(s) | Notes |
| ---     | ---                | ---                     | ---   |
| **Akka HTTP** | 10.0.0+ | `akka-http`, `akka-http-server`, `akka-http-client` | |
| **Apache HTTP Client** | 4.0+ | `httpclient` | Also supports the DropWizard HTTP Client that subclasses the Apache one |
| **AWS SDK Client** | 1.11.0+ | `aws-sdk` | |
| **CouchBase Client** | 2.0.0+ | `couchbase` | |
| **Cassandra (DataStax client)** | 2.3.0+ | `cassandra` | |
| **DropWizard Views** | * | `dropwizard`, `dropwizard-view` | |
| **ElasticSearch Client** | 2+ | `elasticsearch` | Supports both REST and transport clients |
| **gRPC (Client and Server)** | 1.5.0+ | `grpc` | |
| **java.net.HttpURLConnection** | * | `httpurlconnection` | |
| **Hystrix** | 1.4.0+ | `hystrix` | |
| **JAX-RS Client** | 2.0.0+ | `jaxrs` | Also supports DropWizard client 0.8.0+ |
| **JDBC API** | * | `jdbc` | |
| **Jedis (Redis client)** | 1.4.0+ | `jedis` | |
| **Lettuce (Redis Client)** | 5.0.0+ | `lettuce` | |
| _Jetty Server_ | 8.0.0+ | `jetty` | |
| **JMS Messaging** | * | `jms` | |
| **JSP** | 7+ | `jsp` | |
| **Kafka Client** | 0.11.0.0+ | `kafka` | |
| _Java MDC_ | * | `-Dsignalfx.logs.injection=true` on Java invocation | Injects `signalfx.trace_id` and `signalfx.span_id` to MDC contexts |
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
| **Spring Web** | 4.0+ | `spring-web` | Includes DispatcherServlet, HandlerAdapter, and RestTemplate |
| **Spring WebFlux** | 5.0.0+ | `spring-webflux` | |
| **Memcached (SpyMemcached)** | 2.10.0+ | `spymemcached` | |
| **Vertx Web** | 4.1.0+  | N/A | This works through the Netty instrumentation |

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
