// Modified by SignalFx
package datadog.trace.api.writer

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import datadog.opentracing.SpanFactory
import datadog.trace.common.writer.ZipkinV2Api
import datadog.trace.util.test.DDSpecification

import static datadog.trace.agent.test.server.http.TestHttpServer.httpServer

class ZipkinV2ApiTest extends DDSpecification {
  static mapper = new ObjectMapper()

  def "sending an empty list of traces returns no errors"() {
    setup:
    def agent = httpServer {
      handlers {
        post("v1/trace") {
          def status = request.contentLength > 0 ? 200 : 500
          response.status(status).send()
        }
      }
    }
    def client = new ZipkinV2Api("localhost", agent.address.port, "/v1/trace", false)

    expect:
    client.traceEndpoint == "http://localhost:${agent.address.port}/v1/trace"
    def response = client.sendTraces([])
    response.success()
    response.status() == 200


    cleanup:
    agent.close()
  }

  def "non-200 response"() {
    setup:
    println "starting ZipkinV2ApiTest.non-200 response"
    def agent = httpServer {
      handlers {
        post("v1/trace") {
          response.status(404).send()
        }
      }
    }
    def client = new ZipkinV2Api("localhost", agent.address.port, "/v1/trace", false)

    expect:
    client.traceEndpoint == "http://localhost:${agent.address.port}/v1/trace"
    def response = client.sendTraces([])
    !response.success()

    cleanup:
    agent.close()
  }

  def "content is sent as json"() {
    setup:
    def agent = httpServer {
      handlers {
        post("v1/trace") {
          response.send()
        }
      }
    }
    def client = new ZipkinV2Api("localhost", agent.address.port, "/v1/trace", false)

    expect:
    client.traceEndpoint == "http://localhost:${agent.address.port}/v1/trace"
    client.sendTraces(traces)
    agent.lastRequest.contentType == "application/json"
    agent.lastRequest.contentLength == agent.lastRequest.body.length
    convertList(agent.lastRequest.body) == expectedRequestBody

    cleanup:
    agent.close()

    // Populate thread info dynamically as it is different when run via gradle vs idea.
    where:
    traces                                                               | expectedRequestBody
    []                                                                   | []
    [[SpanFactory.newSpanOf(1L).setTag("service", "my-service").log(1000L, "some event")]]     | [new TreeMap<>([
      "traceId" : "0000000000000001",
      "id"  :     "0000000000000001",
      "duration" : 0,
      "tags"     : ["thread.name": Thread.currentThread().getName(),
                    "thread.id": "${Thread.currentThread().id}",
                    "resource.name": "fakeResource"],
      "annotations": [["timestamp" : 1000, "value": "{\"event\":\"some event\"}"]],
      "name"     : "fakeOperation",
      "localEndpoint": ["serviceName": "fakeService"],
      "timestamp"    : 1,
    ])]
    [[SpanFactory.newSpanOf(100L).setTag("resource.name", "my-resource")]] | [new TreeMap<>([
      "traceId" : "0000000000000001",
      "id"  :     "0000000000000001",
      "duration" : 0,
      "tags"     : ["thread.name": Thread.currentThread().getName(),
                    "thread.id": "${Thread.currentThread().id}",
                    "resource.name": "my-resource"],
      "annotations": [],
      "name"     : "fakeOperation",
      "localEndpoint": ["serviceName": "fakeService"],
      "timestamp"    : 100,
    ])]
    [[SpanFactory.newSpanOf(100L).setTag("span.kind", "CliEnt").log(1000L, "some event").log(2000L, Collections.singletonMap("another event", 1))]] | [new TreeMap<>([
      "traceId" : "0000000000000001",
      "id"  :     "0000000000000001",
      "duration" : 0,
      "tags"     : ["thread.name": Thread.currentThread().getName(),
                    "thread.id": "${Thread.currentThread().id}",
                    "resource.name": "fakeResource"],
      "annotations": [["timestamp" : 1000, "value": "{\"event\":\"some event\"}"], ["timestamp" : 2000, "value":"{\"another event\":1}"]],
      "name"     : "fakeOperation",
      "localEndpoint": ["serviceName": "fakeService"],
      "kind"    : "CLIENT",
      "timestamp"    : 100,
    ])]
    [[SpanFactory.newSpanOf(100L).setTag("span.kind", "SerVeR")]] | [new TreeMap<>([
      "traceId" : "0000000000000001",
      "id"  :     "0000000000000001",
      "duration" : 0,
      "tags"     : ["thread.name": Thread.currentThread().getName(),
                    "thread.id": "${Thread.currentThread().id}"],
      "annotations": [],
      "name"     : "fakeResource",
      "localEndpoint": ["serviceName": "fakeService"],
      "kind"    : "SERVER",
      "timestamp"    : 100,
    ])]
  }

  def "Recorded values are truncated"() {
    setup:
    def agent = httpServer {
      handlers {
        post("v1/trace") {
          response.send()
        }
      }
    }
    def client = new ZipkinV2Api("localhost", agent.address.port, "/v1/trace", false)
    def span = SpanFactory.newSpanOf(100L)
    span.setTag("some.tag", "0" * 100000)
    span.log(1000L, Collections.singletonMap("event", "1" * 100000))
    span.log(2000L, Collections.singletonMap("event", new Exception("SomeException")))
    def traces = [[span]]

    expect:
    client.traceEndpoint == "http://localhost:${agent.address.port}/v1/trace"
    client.sendTraces(traces)
    def received = convertList(agent.lastRequest.body)

    // Default recorded value max length + [...]
    received[0]["tags"]["some.tag"].toString().length() == 12293
    received[0]["annotations"][0]["value"].toString().length() == 12293
    // Different java versions have different serialized exception lengths
    received[0]["annotations"][1]["value"].toString().length() <= 12293

    cleanup:
    agent.close()
  }

  static List<TreeMap<String, Object>> convertList(byte[] bytes) {
    return mapper.readValue(bytes, new TypeReference<List<TreeMap<String, Object>>>() {})
  }
}
