// Modified by SignalFx
package datadog.trace

import datadog.opentracing.DDTracer
import datadog.opentracing.propagation.HttpCodec
import datadog.opentracing.scopemanager.ContinuableScope
import datadog.trace.api.Config
import datadog.trace.common.sampling.AllSampler
import datadog.trace.common.sampling.RateByServiceSampler
import datadog.trace.common.writer.DDAgentWriter
import datadog.trace.common.writer.ListWriter
import datadog.trace.common.writer.LoggingWriter
import datadog.trace.util.test.DDSpecification
import org.junit.Rule
import org.junit.contrib.java.lang.system.EnvironmentVariables
import org.junit.contrib.java.lang.system.RestoreSystemProperties
import spock.lang.Ignore

import static datadog.trace.api.Config.DEFAULT_MAX_SPANS_PER_TRACE
import static datadog.trace.api.Config.DEFAULT_SERVICE_NAME
import static datadog.trace.api.Config.HEADER_TAGS
import static datadog.trace.api.Config.HEALTH_METRICS_ENABLED
import static datadog.trace.api.Config.PREFIX
import static datadog.trace.api.Config.PRIORITY_SAMPLING
import static datadog.trace.api.Config.SERVICE_MAPPING
import static datadog.trace.api.Config.SPAN_TAGS
import static datadog.trace.api.Config.WRITER_TYPE

class DDTracerTest extends DDSpecification {

  @Rule
  public final RestoreSystemProperties restoreSystemProperties = new RestoreSystemProperties()
  @Rule
  public final EnvironmentVariables environmentVariables = new EnvironmentVariables()

  def setupSpec() {
    // assert that a trace agent isn't running locally as that messes up the test.
    try {
      (new Socket("localhost", 8126)).close()
      throw new IllegalStateException("An agent is already running locally on port 8126. Please stop it if you want to run tests locally.")
    } catch (final ConnectException ioe) {
      // trace agent is not running locally.
    }
  }

  def "verify defaults on tracer"() {
    when:
    def tracer = new DDTracer()

    then:
    tracer.serviceName == "unnamed-java-app"
    tracer.sampler instanceof AllSampler
    tracer.writer.toString() == "DDAgentWriter { api=ZipkinV2Api { traceEndpoint=http://localhost:9080/v1/trace } }"
    tracer.writer.monitor instanceof DDAgentWriter.NoopMonitor

    tracer.spanContextDecorators.size() == 15

    tracer.injector instanceof HttpCodec.CompoundInjector
    tracer.extractor instanceof HttpCodec.CompoundExtractor
  }

  def "verify enabling health monitor"() {
    setup:
    System.setProperty(PREFIX + HEALTH_METRICS_ENABLED, "true")

    when:
    def tracer = new DDTracer(new Config())

    then:
    tracer.writer.toString() == "DDAgentWriter { api=ZipkinV2Api { traceEndpoint=http://localhost:9080/v1/trace }, monitor=StatsD { host=localhost:8125 } }"
    tracer.writer.monitor instanceof DDAgentWriter.StatsDMonitor
  }


  def "verify overriding sampler"() {
    setup:
    System.setProperty(PREFIX + PRIORITY_SAMPLING, "false")
    when:
    def tracer = new DDTracer(new Config())
    then:
    tracer.sampler instanceof AllSampler
  }

  def "verify overriding writer"() {
    setup:
    System.setProperty(PREFIX + WRITER_TYPE, "LoggingWriter")

    when:
    def tracer = new DDTracer(new Config())

    then:
    tracer.writer instanceof LoggingWriter
  }

  @Ignore
  def "verify mapping configs on tracer"() {
    setup:
    System.setProperty(PREFIX + SERVICE_MAPPING, mapString)
    System.setProperty(PREFIX + SPAN_TAGS, mapString)
    System.setProperty(PREFIX + HEADER_TAGS, mapString)

    when:
    def config = new Config()
    def tracer = new DDTracer(config)
    // Datadog extractor gets placed first
    def taggedHeaders = tracer.extractor.extractors[0].taggedHeaders

    then:
    tracer.defaultSpanTags == map
    tracer.serviceNameMappings == map
    taggedHeaders == map

    where:
    mapString       | map
    "a:1, a:2, a:3" | [a: "3"]
    "a:b,c:d,e:"    | [a: "b", c: "d"]
  }

  def "verify single override on #source for #key"() {
    when:
    System.setProperty(PREFIX + key, value)
    def tracer = new DDTracer(new Config())

    then:
    tracer."$source".toString() == expected

    where:

    source   | key                | value           | expected
    "writer" | "default"          | "default"       | "DDAgentWriter { api=ZipkinV2Api { traceEndpoint=http://localhost:9080/v1/trace } }"
    "writer" | "writer.type"      | "LoggingWriter" | "LoggingWriter { }"
    "writer" | "agent.host"       | "somethingelse" | "DDAgentWriter { api=ZipkinV2Api { traceEndpoint=http://somethingelse:9080/v1/trace } }"
    "writer" | "agent.port"       | "777"           | "DDAgentWriter { api=ZipkinV2Api { traceEndpoint=http://localhost:777/v1/trace } }"
    "writer" | "trace.agent.port" | "9999"          | "DDAgentWriter { api=ZipkinV2Api { traceEndpoint=http://localhost:9999/v1/trace } }"
  }

  def "verify sampler/writer constructor"() {
    setup:
    def writer = new ListWriter()
    def sampler = new RateByServiceSampler()

    when:
    def tracer = new DDTracer(DEFAULT_SERVICE_NAME, writer, sampler)

    then:
    tracer.serviceName == DEFAULT_SERVICE_NAME
    tracer.sampler == sampler
    tracer.writer == writer
    tracer.localRootSpanTags[Config.RUNTIME_ID_TAG] == null
    tracer.localRootSpanTags[Config.LANGUAGE_TAG_KEY] == null
  }

  @Ignore
  def "Shares TraceCount with DDApi with #key = #value"() {
    setup:
    System.setProperty(PREFIX + key, value)
    final DDTracer tracer = new DDTracer(new Config())

    expect:
    tracer.writer instanceof DDAgentWriter
    tracer.writer.traceCount.is(((DDAgentWriter) tracer.writer).traceCount)

    where:
    key               | value
    PRIORITY_SAMPLING | "true"
    PRIORITY_SAMPLING | "false"
  }

  def "root tags are applied only to root spans"() {
    setup:
    def tracer = new DDTracer('my_service', new ListWriter(), new AllSampler(), '', ['only_root': 'value'], [:], [:], [:])
    def root = tracer.buildSpan('my_root').start()
    def child = tracer.buildSpan('my_child').asChildOf(root).start()

    expect:
    root.context().tags.containsKey('only_root')
    !child.context().tags.containsKey('only_root')

    cleanup:
    child.finish()
    root.finish()
  }

  def "spans per trace are capped at writing"() {
    setup:
    def writer = new ListWriter()
    def tracer = new DDTracer('cap_span_writes', writer, new AllSampler())
    // one below the limit
    def ok = tracer.buildSpan("ok").start()
    tracer.buildSpan("ok.child").asChildOf(ok).start().finish()
    ok.finish()

    // and one above it
    def tooBig = tracer.buildSpan("tooBig").start()
    for (int i = 0; i < Config.DEFAULT_MAX_SPANS_PER_TRACE; i++) {
      tracer.buildSpan("tooBig.child" + i).asChildOf(tooBig).start().finish()
    }
    tooBig.finish()

    expect:
    writer.size() == 1
    writer.get(0).size() == 2 // parent+child

    cleanup:
    tracer.close()
  }

  def "partial writes are still eventually capped"() {
    setup:
    def writer = new ListWriter()
    def tracer = new DDTracer('partial_write_test_cap', writer, new AllSampler(), Collections.EMPTY_MAP, Collections.EMPTY_MAP, Collections.EMPTY_MAP, Collections.EMPTY_MAP, Config.DEFAULT_PARTIAL_FLUSH_MIN_SPANS)
    // First cause a partial write
    def tooBigEventually = tracer.buildSpan("tooBigEventually").start()
    for(int i=0; i < Config.DEFAULT_PARTIAL_FLUSH_MIN_SPANS + 1; i++) {
      tracer.buildSpan("tooBigEventually.child" + i).asChildOf(tooBigEventually).start().finish()
    }
    if (true) { // keep scope of copy+paste in check
      def cont = tracer.buildSpan("tooBigEventually.cont").asChildOf(tooBigEventually).start()
      def cscope = tracer.scopeManager().activate(cont)
      ((ContinuableScope) cscope).setAsyncPropagation(true)
      def continuation = ((ContinuableScope) cscope).capture()
      continuation.activate().close() // causes partial write
    }


    assert writer.size() == 1
    assert writer.get(0).size() >= Config.DEFAULT_PARTIAL_FLUSH_MIN_SPANS

    // Then add a bunch more (over capping limit) and cause another (capped) partial write
    for(int i=0; i < Config.DEFAULT_MAX_SPANS_PER_TRACE; i++) {
      tracer.buildSpan("tooBigEventually.child.2." + i).asChildOf(tooBigEventually).start().finish()
    }
    if (true) {
      def cont = tracer.buildSpan("tooBigEventually.cont").asChildOf(tooBigEventually).start()
      def cscope = tracer.scopeManager().activate(cont)
      ((ContinuableScope) cscope).setAsyncPropagation(true)
      def continuation = ((ContinuableScope) cscope).capture()
      continuation.activate().close() // causes partial write
    }

    assert writer.size() == 1 // partial write didn't actually happen

    // Then close the trace which causes a (capped) write
    tooBigEventually.finish()
    assert writer.size() == 1 // partial write didn't actually happen


    expect:
    Config.DEFAULT_MAX_SPANS_PER_TRACE > Config.DEFAULT_PARTIAL_FLUSH_MIN_SPANS
    writer.size() == 1 // only 1 partial write happened, second partial and final regular did not

    cleanup:
    tracer.close()
  }

}
