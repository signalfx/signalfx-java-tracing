package datadog.trace.api.writer

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import datadog.opentracing.SpanFactory
import datadog.trace.common.writer.ZipkinV2Api
import spock.lang.Specification

import static datadog.trace.agent.test.server.http.TestHttpServer.httpServer

class ZipkinV2ApiTest extends Specification {
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
    client.sendTraces([])

    cleanup:
    agent.close()
  }

  def "non-200 response results in false returned"() {
    setup:
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
    !client.sendTraces([])

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
      "parentId": "0000000000000000",
      "duration" : 0,
      "tags"     : ["thread.name": Thread.currentThread().getName(),
                    "thread.id": "${Thread.currentThread().id}",
                    "resource.name": "fakeResource"],
      "annotations": [["timestamp" : 1000, "value": "{\"event\":\"some event\"}"]],
      "name"     : "fakeOperation",
      "kind": null,
      "localEndpoint": ["serviceName": "my-service"],
      "timestamp"    : 1,
    ])]
    [[SpanFactory.newSpanOf(100L).setTag("resource.name", "my-resource")]] | [new TreeMap<>([
      "traceId" : "0000000000000001",
      "id"  :     "0000000000000001",
      "parentId": "0000000000000000",
      "duration" : 0,
      "tags"     : ["thread.name": Thread.currentThread().getName(),
                    "thread.id": "${Thread.currentThread().id}",
                    "resource.name": "my-resource"],
      "annotations": [],
      "name"     : "fakeOperation",
      "localEndpoint": ["serviceName": "fakeService"],
      "kind"    : null,
      "timestamp"    : 100,
    ])]
    [[SpanFactory.newSpanOf(100L).setTag("span.kind", "CliEnt").log(1000L, "some event").log(2000L, Collections.singletonMap("another event", 1))]] | [new TreeMap<>([
      "traceId" : "0000000000000001",
      "id"  :     "0000000000000001",
      "parentId": "0000000000000000",
      "duration" : 0,
      "tags"     : ["thread.name": Thread.currentThread().getName(),
                    "thread.id": "${Thread.currentThread().id}",
                    "resource.name": "fakeResource"],
      "annotations": [["timestamp" : 1000, "value": "{\"event\":\"some event\"}"], ["timestamp" : 2000, "value":"{\"another event\":1}"]],
      "name"     : "fakeOperation",
      "localEndpoint": ["serviceName": "fakeService"],
      "kind"    : "CliEnt",
      "timestamp"    : 100,
    ])]
    [[SpanFactory.newSpanOf(100L).setTag("span.kind", "SerVeR")]] | [new TreeMap<>([
      "traceId" : "0000000000000001",
      "id"  :     "0000000000000001",
      "parentId": "0000000000000000",
      "duration" : 0,
      "tags"     : ["thread.name": Thread.currentThread().getName(),
                    "thread.id": "${Thread.currentThread().id}",
                    "resource.name": "fakeResource"],
      "annotations": [],
      "name"     : "fakeResource",
      "localEndpoint": ["serviceName": "fakeService"],
      "kind"    : "SerVeR",
      "timestamp"    : 100,
    ])]
  }

  static List<TreeMap<String, Object>> convertList(byte[] bytes) {
    return mapper.readValue(bytes, new TypeReference<List<TreeMap<String, Object>>>() {})
  }
}
