# SignalFx Java Agent

This is the SignalFx Java Agent, a JVM agent to automatically instrument your
Java application to capture and report distributed traces to SignalFx. This is
a fork of the [DataDog Java APM
project](https://github.com/DataDog/dd-trace-java)

The SignalFx Java Agent automatically configures an OpenTracing-compatible
tracer to capture and export trace spans. It also installs this tracer
as the OpenTracing `GlobalTracer` to enable additional custom instrumentation.

# Usage

The SignalFx Java Agent uses a few environment variables for its configuration.
You should set your application's service name via `SIGNALFX_SERVICE_NAME` and
configure the trace endpoint URL via `SIGNALFX_AGENT_ENDPOINT` to point to your deployed
SignalFx Smart Agent:

```
$ export SIGNALFX_SERVICE_NAME=my-app
$ export SIGNALFX_AGENT_ENDPOINT=http://127.0.0.1:9080/v1/trace
$ java -javaagent:signalfx-tracing.jar <rest of command-line arguments...>
```

See [our example app that uses
this](https://github.com/signalfx/tracing-examples/tree/master/java-agent) for
more details on how to use and configure the SignalFx Java Agent.


## Versioning

For now, the agent will be versioned in conjunction with the DataDog APM agent
that it is based on, with a SignalFx-specific patch version at the end, of the
form `-sfxN`, where `N` is the SignalFx patch starting at `0`.  For
example, the DD APM agent version `0.20.0` would be initially released by us as
`0.20.0-sfx0`.  We will attempt to merge in changes from the upstream (i.e.
DD's repo) on a regular basis, especially after releases.

# Supported libraries and frameworks

*Bold* libraries are enabled out of the box and are fully supported.  Non-bold
libraries are in beta and must be enabled by setting the property
`-Dsfx.integration.NAME.enabled=true`, where `NAME` is the name given in the
table below.

| Library | Versions Supported | Instrumentation Name(s) | Notes |
| ---     | ---                | ---                     | ---   |
| **Akka HTTP** | 10.0.0+ | `akka-http`, `akka-http-server`, `akka-http-client` | |
| **Apache HTTP Client** | 4.0+ | `httpclient` | Also supports the DropWizard HTTP Client that subclasses the Apache one |
| **AWS SDK Client** | 1.10.33+ | `aws-sdk` | |
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
| Jetty Server | 8.0.0+ | `jetty` | |
| **JMS Messaging** | * | `jms` | |
| **JSP** | 7+ | `jsp` | |
| **Kafka Client** | 0.11.0.0+ | `kafka` | |
| **Mongo Client** | 3.1+ | `mongo` | |
| **Mongo Async Client** | 3.3+ | `mongo` | |
| **Netty Client and Server** | 4.0+ | `netty` | |
| **OkHTTP Client** | 3.0+ | `okhttp` | |
| **Play Web Framework** | 2.4+ | `play` | |
| **RabbitMQ Client** | 2.7.0+ | `rabbitmq` | |
| Ratpack | 1.4+ | `ratpack` | |
| **Java Servlet** | 2+ | `servlet` | |
| Spark Java | 2.3+ | `sparkjava` | |
| **Spring Web** | 4.0+ | `spring-web` | |
| **Spring WebFlux** | 5.0.0+ | `spring-webflux` | |
| **Memcached (SpyMemcached)** | 2.10.0+ | `spymemcached` | |
| **Vertx Web** | 4.1.0+  | N/A | This works through the Netty instrumentation |

