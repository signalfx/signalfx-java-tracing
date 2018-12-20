# SignalFx Java Agent

This is the SignalFx Java Agent, a JVM agent to automatically instrument
your Java application to capture and report distributed traces to SignalFx.

The SignalFx Java Agent automatically configures an OpenTracing-compatible
Jaeger tracer to capture and export trace spans. It also installs this tracer
as the OpenTracing `GlobalTracer` to enable additional custom instrumentation.

# Usage

The SignalFx Java Agent uses a few environment variables for its configuration.
You should set your application's service name via `JAEGER_SERVICE_NAME` and
configure the trace endpoint URL via `JAEGER_ENDPOINT` to point to your deployed
SignalFx Smart Gateway:

```
$ export JAEGER_SERVICE_NAME=my-app
$ export JAEGER_ENDPOINT=http://smart-gateway:9080/v1/trace
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

_Coming soon._
