# SignalFx Tracing Java APM

This is the SignalFx Java Agent, based on the [DataDog Java
Agent](https://github.com/DataDog/dd-trace-java).  It automatically instruments
your Java application to send distributed traces to SignalFx's backend.

One of the main differences is that this repo uses an OpenTracing-standard Jaeger tracer
instead of DataDog's DDTracer.

See [our example app that uses
this](https://github.com/signalfx/tracing-examples/tree/master/java-agent) for
details on how to use and configure it.


## Versioning

For now, the agent will be versioned in conjunction with the DataDog APM agent
that it is based on, with a SignalFx-specific patch version at the end, of the
form `-sfxN`, where `N` is the SignalFx patch starting at `0`.  For
example, the DD APM agent version `0.20.0` would be initially released by us as
`0.20.0-sfx0`.  We will attempt to merge in changes from the upstream (i.e.
DD's repo) on a regular basis, especially after releases.
