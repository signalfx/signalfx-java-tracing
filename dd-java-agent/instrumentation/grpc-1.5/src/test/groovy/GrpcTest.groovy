// Modified by SignalFx
import datadog.trace.agent.test.AgentTestRunner
import example.GreeterGrpc
import example.Helloworld
import io.grpc.BindableService
import io.grpc.ManagedChannel
import io.grpc.Server
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import io.grpc.stub.StreamObserver
import io.opentracing.tag.Tags

import java.util.concurrent.TimeUnit

class GrpcTest extends AgentTestRunner {
  static {
    System.setProperty("dd.integration.grpc.enabled", "true")
  }

  def "test request-response"() {
    setup:
    BindableService greeter = new GreeterGrpc.GreeterImplBase() {
      @Override
      void sayHello(
        final Helloworld.Request req, final StreamObserver<Helloworld.Response> responseObserver) {
        final Helloworld.Response reply = Helloworld.Response.newBuilder().setMessage("Hello $req.name").build()
        responseObserver.onNext(reply)
        responseObserver.onCompleted()
      }
    }
    Server server = InProcessServerBuilder.forName(getClass().name).addService(greeter).directExecutor().build().start()

    ManagedChannel channel = InProcessChannelBuilder.forName(getClass().name).build()
    GreeterGrpc.GreeterBlockingStub client = GreeterGrpc.newBlockingStub(channel)

    when:
    def response = client.sayHello(Helloworld.Request.newBuilder().setName(name).build())

    then:
    response.message == "Hello $name"
    assertTraces(1) {
      trace(0, 4) {
        span(0) {
          operationName "example.Greeter/SayHello"
          parent()
          errored false
          tags {
            "status.code" "OK"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            defaultTags()
          }
        }
        span(1) {
          operationName "grpc.message"
          childOf span(0)
          errored false
          tags {
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "message.type" "example.Helloworld\$Response"
            defaultTags()
          }
        }
        span(2) {
          operationName "example.Greeter/SayHello"
          childOf span(0)
          errored false
          tags {
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_SERVER
            defaultTags(true)
          }
        }
        span(3) {
          operationName "grpc.message"
          childOf span(2)
          errored false
          tags {
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_SERVER
            "message.type" "example.Helloworld\$Request"
            defaultTags()
          }
        }
      }
    }

    cleanup:
    channel?.shutdownNow()?.awaitTermination(10, TimeUnit.SECONDS)
    server?.shutdownNow()?.awaitTermination()

    where:
    name << ["some name", "some other name"]
  }

  def "test error - #name"() {
    setup:
    def error = status.asException()
    BindableService greeter = new GreeterGrpc.GreeterImplBase() {
      @Override
      void sayHello(
        final Helloworld.Request req, final StreamObserver<Helloworld.Response> responseObserver) {
        responseObserver.onError(error)
      }
    }
    Server server = InProcessServerBuilder.forName(getClass().name).addService(greeter).directExecutor().build().start()

    ManagedChannel channel = InProcessChannelBuilder.forName(getClass().name).build()
    GreeterGrpc.GreeterBlockingStub client = GreeterGrpc.newBlockingStub(channel)

    when:
    client.sayHello(Helloworld.Request.newBuilder().setName(name).build())

    then:
    thrown StatusRuntimeException

    assertTraces(1) {
      trace(0, 3) {
        span(0) {
          operationName "example.Greeter/SayHello"
          parent()
          errored true
          tags {
            "status.code" "${status.code.name()}"
            "status.description" description
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            tag "error", true
            defaultTags()
          }
        }
        span(1) {
          operationName "example.Greeter/SayHello"
          childOf span(0)
          errored false
          tags {
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_SERVER
            defaultTags(true)
          }
        }
        span(2) {
          operationName "grpc.message"
          childOf span(1)
          errored false
          tags {
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_SERVER
            "message.type" "example.Helloworld\$Request"
            defaultTags()
          }
        }
      }
    }

    cleanup:
    channel?.shutdownNow()?.awaitTermination(10, TimeUnit.SECONDS)
    server?.shutdownNow()?.awaitTermination()

    where:
    name                          | status                                                                 | description
    "Runtime - cause"             | Status.UNKNOWN.withCause(new RuntimeException("some error"))           | null
    "Status - cause"              | Status.PERMISSION_DENIED.withCause(new RuntimeException("some error")) | null
    "StatusRuntime - cause"       | Status.UNIMPLEMENTED.withCause(new RuntimeException("some error"))     | null
    "Runtime - description"       | Status.UNKNOWN.withDescription("some description")                     | "some description"
    "Status - description"        | Status.PERMISSION_DENIED.withDescription("some description")           | "some description"
    "StatusRuntime - description" | Status.UNIMPLEMENTED.withDescription("some description")               | "some description"
  }

  def "test error thrown - #name"() {
    setup:
    def error = status.asRuntimeException()
    BindableService greeter = new GreeterGrpc.GreeterImplBase() {
      @Override
      void sayHello(
        final Helloworld.Request req, final StreamObserver<Helloworld.Response> responseObserver) {
        throw error
      }
    }
    Server server = InProcessServerBuilder.forName(getClass().name).addService(greeter).directExecutor().build().start()

    ManagedChannel channel = InProcessChannelBuilder.forName(getClass().name).build()
    GreeterGrpc.GreeterBlockingStub client = GreeterGrpc.newBlockingStub(channel)

    when:
    client.sayHello(Helloworld.Request.newBuilder().setName(name).build())

    then:
    thrown StatusRuntimeException

    assertTraces(1) {
      trace(0, 3) {
        span(0) {
          operationName "example.Greeter/SayHello"
          parent()
          errored true
          tags {
            "status.code" "UNKNOWN"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            tag "error", true
            defaultTags()
          }
        }
        span(1) {
          operationName "example.Greeter/SayHello"
          childOf span(0)
          errored true
          tags {
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_SERVER
            errorTags error.class, error.message
            defaultTags(true)
          }
        }
        span(2) {
          operationName "grpc.message"
          childOf span(1)
          errored false
          tags {
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_SERVER
            "message.type" "example.Helloworld\$Request"
            defaultTags()
          }
        }
      }
    }

    cleanup:
    channel?.shutdownNow()?.awaitTermination(10, TimeUnit.SECONDS)
    server?.shutdownNow()?.awaitTermination()

    where:
    name              | status
    "Runtime - cause" | Status.UNKNOWN.withCause(new RuntimeException("some error"))
    "Status - cause"              | Status.PERMISSION_DENIED.withCause(new RuntimeException("some error"))
    "StatusRuntime - cause"       | Status.UNIMPLEMENTED.withCause(new RuntimeException("some error"))
    "Runtime - description"       | Status.UNKNOWN.withDescription("some description")
    "Status - description"        | Status.PERMISSION_DENIED.withDescription("some description")
    "StatusRuntime - description" | Status.UNIMPLEMENTED.withDescription("some description")
  }
}
