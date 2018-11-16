// Modified by SignalFx
package datadog.trace.instrumentation.akkahttp

import java.util.Collections

import akka.NotUsed
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream.scaladsl.Flow
import io.opentracing.Span
import io.opentracing.log.Fields.ERROR_OBJECT
import io.opentracing.propagation.Format
import io.opentracing.tag.Tags
import io.opentracing.util.GlobalTracer

import scala.util.{Failure, Success, Try}

object AkkaHttpClientTransformFlow {
  def transform[T](flow: Flow[(HttpRequest, T), (Try[HttpResponse], T), NotUsed]): Flow[(HttpRequest, T), (Try[HttpResponse], T), NotUsed] = {
    var span: Span = null

    Flow.fromFunction((input: (HttpRequest, T)) => {
      val (request, data) = input
      val scope = GlobalTracer.get
        .buildSpan(request.method.value + " " + request.getUri().path())
        .withTag(Tags.SPAN_KIND.getKey, Tags.SPAN_KIND_CLIENT)
        .withTag(Tags.HTTP_METHOD.getKey, request.method.value)
        .withTag(Tags.COMPONENT.getKey, "akka-http-client")
        .withTag(Tags.HTTP_URL.getKey, request.getUri.toString)
        .startActive(false)
      val headers = new AkkaHttpClientInstrumentation.AkkaHttpHeaders(request)
      GlobalTracer.get.inject(scope.span.context, Format.Builtin.HTTP_HEADERS, headers)
      span = scope.span
      scope.close()
      (headers.getRequest, data)
    }).via(flow).map(output => {
      output._1 match {
        case Success(response) =>
          val status = response.status.intValue
          Tags.HTTP_STATUS.set(span, status)
          if (status >= 500)
            Tags.ERROR.set(span, true)
        case Failure(e) =>
          Tags.ERROR.set(span, true)
          span.log(Collections.singletonMap(ERROR_OBJECT, e))
      }
      span.finish()
      output
    })
  }
}
