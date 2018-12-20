// Modified by SignalFx

import datadog.opentracing.mock.TestSpan
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
    TEST_WRITER.waitForTraces(1)
    TEST_WRITER.get(0).sort(new Comparator<TestSpan>() {
      @Override
      int compare(TestSpan o1, TestSpan o2) {
        int parentComp = Long.compare(o1.parentId, o2.parentId)
        if (parentComp == 0) {
          return o1.operationName.compareTo(o2.operationName)
        }
        return parentComp
      }
    })

    then:
    error.get() == null

    assertTraces(1) {
      trace(0, clientMessageCount + 1 + (clientMessageCount * serverMessageCount) + 1) {
        span(1) {
          operationName "example.Greeter/Conversation"
          childOf span(0)
          errored false
          tags {
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_SERVER
            "$Tags.COMPONENT.key" "grpc-server"
            defaultTags(true)
          }
        }
        clientRange.each {
          span((clientMessageCount * serverMessageCount) + 1 + it) {
            operationName "grpc.message"
            childOf span(1)
            errored false
            tags {
              "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_SERVER
              "$Tags.COMPONENT.key" "grpc-server"
              "message.type" "example.Helloworld\$Response"
              defaultTags()
            }
          }
        }
        span(0) {
          operationName "example.Greeter/Conversation"
          parent()
          errored false
          tags {
            "status.code" "OK"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "$Tags.COMPONENT.key" "grpc-client"
            defaultTags()
          }
        }
        (1..(clientMessageCount * serverMessageCount)).each {
          span(1 + it) {
            operationName "grpc.message"
            childOf span(0)
            errored false
            tags {
              "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
              "$Tags.COMPONENT.key" "grpc-client"
              "message.type" "example.Helloworld\$Response"
              defaultTags()
            }
          }
        }
      }
    }

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
  }
}
