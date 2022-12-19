> # :warning: End of Support (EOS) Notice
> **The SignalFx Java Agent has reached End of Support and has been permanently archived.**
>
>The [Splunk Distribution of OpenTelemetry Java](https://github.com/signalfx/splunk-otel-java) is the successor.
> To learn how to migrate, see [Migrate from the SignalFx Java Agent](https://quickdraw.splunk.com/redirect/?product=Observability&location=java.otel.repo.migration&version=current).

---

# SignalFx Java Agent

No longer supported.

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
