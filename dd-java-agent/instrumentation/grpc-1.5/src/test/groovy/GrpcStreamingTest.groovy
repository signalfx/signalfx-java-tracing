// Modified by SignalFx
import datadog.trace.agent.test.AgentTestRunner
import example.GreeterGrpc
import example.Helloworld
import io.grpc.BindableService
import io.grpc.ManagedChannel
import io.grpc.Server
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import io.grpc.stub.StreamObserver
import io.opentracing.tag.Tags

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class GrpcStreamingTest extends AgentTestRunner {
  static {
    System.setProperty("dd.integration.grpc.enabled", "true")
  }

  def "test conversation #name"() {
    setup:
    def msgCount = serverMessageCount
    def serverReceived = new CopyOnWriteArrayList<>()
    def clientReceived = new CopyOnWriteArrayList<>()
    def error = new AtomicReference()

    BindableService greeter = new GreeterGrpc.GreeterImplBase() {
      @Override
      StreamObserver<Helloworld.Response> conversation(StreamObserver<Helloworld.Response> observer) {
        return new StreamObserver<Helloworld.Response>() {
          @Override
          void onNext(Helloworld.Response value) {
            serverReceived << value.message

            (1..msgCount).each {
              observer.onNext(value)
            }
          }

          @Override
          void onError(Throwable t) {
            error.set(t)
            observer.onError(t)
          }

          @Override
          void onCompleted() {
            observer.onCompleted()
          }
        }
      }
    }
    Server server = InProcessServerBuilder.forName(getClass().name).addService(greeter).directExecutor().build().start()

    ManagedChannel channel = InProcessChannelBuilder.forName(getClass().name).build()
    GreeterGrpc.GreeterStub client = GreeterGrpc.newStub(channel).withWaitForReady()

    when:
    def observer = client.conversation(new StreamObserver<Helloworld.Response>() {
      @Override
      void onNext(Helloworld.Response value) {
        clientReceived << value.message
      }

      @Override
      void onError(Throwable t) {
        error.set(t)
      }

      @Override
      void onCompleted() {
      }
    })

    clientRange.each {
      def message = Helloworld.Response.newBuilder().setMessage("call $it").build()
      observer.onNext(message)
    }
    observer.onCompleted()

    then:
    error.get() == null

    TEST_WRITER.waitForTraces(1)
    TEST_WRITER.sort()

    def trace = TEST_WRITER.get(0)
    trace.size() == expectedSpans

    def span0 = trace.get(0)
    span0.operationName == "example.Greeter/Conversation"
    span0.parentId() == 0
    def span0Tags = span0.tags()
    span0Tags.get("status.code") == "OK"
    span0Tags.get(Tags.SPAN_KIND.getKey()) == Tags.SPAN_KIND_CLIENT

    def span1 = trace.get(1)
    span1.operationName == "example.Greeter/Conversation"
    span1.parentId() == span0.spanId
    span1.tags().get(Tags.SPAN_KIND.getKey()) == Tags.SPAN_KIND_SERVER

    def numClient = 0
    def numServer = 0
    for (int i = 2 ; i < expectedSpans ; i++) {

      def span = trace.get(i)
      def spanKind = span.tags().get(Tags.SPAN_KIND.getKey())

      def parentId
      if (spanKind == Tags.SPAN_KIND_CLIENT) {
        parentId = span0.spanId
        numClient++
      } else {
        parentId = span1.spanId
        numServer++
      }

      assert span.operationName == "grpc.message"
      assert span.parentId() == parentId
      assert span.tags().get("message.type") == "example.Helloworld\$Response"
    }

    numClient == clientMessageCount * serverMessageCount
    numServer == clientMessageCount

    serverReceived == clientRange.collect { "call $it" }
    clientReceived == serverRange.collect { clientRange.collect { "call $it" } }.flatten().sort()

    cleanup:
    channel?.shutdownNow()?.awaitTermination(10, TimeUnit.SECONDS)
    server?.shutdownNow()?.awaitTermination()

    where:
    name | clientMessageCount | serverMessageCount
    "A"  | 1                  | 1
    "B"  | 2                  | 1
    "C"  | 1                  | 2
    "D"  | 2                  | 2
    "E"  | 3                  | 3

    clientRange = 1..clientMessageCount
    serverRange = 1..serverMessageCount
    expectedSpans = clientMessageCount + clientMessageCount * serverMessageCount + 2
  }
}
