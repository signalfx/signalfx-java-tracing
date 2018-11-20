# SignalFx Tracing Java APM

This is the SignalFx Java Agent, based on the [DataDog Java
Agent](https://github.com/DataDog/dd-trace-java).  It automatically instruments
your Java application to send distributed traces to SignalFx's backend.

One of the main differences is that this repo uses an OpenTracing-standard Jaeger tracer
instead of DataDog's DDTracer.

See [our example app that uses
this](https://github.com/signalfx/tracing-examples/tree/master/java-agent) for
details on how to use and configure it.
