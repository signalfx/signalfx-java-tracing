package test

import com.signalfx.tracing.api.TraceSetting
import datadog.trace.agent.test.base.HttpServerTest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.servlet.view.RedirectView

import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.ERROR
import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.EXCEPTION
import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.REDIRECT
import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.SUCCESS
import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.CLIENT_EXCEPTION

@Controller
class TestController {

  @RequestMapping("/success")
  @ResponseBody
  String success() {
    HttpServerTest.controller(SUCCESS) {
      SUCCESS.body
    }
  }

  @RequestMapping("/redirect")
  @ResponseBody
  RedirectView redirect() {
    HttpServerTest.controller(REDIRECT) {
      new RedirectView(REDIRECT.body)
    }
  }

  @RequestMapping("/error-status")
  ResponseEntity error() {
    HttpServerTest.controller(ERROR) {
      new ResponseEntity(ERROR.body, HttpStatus.valueOf(ERROR.status))
    }
  }

  @RequestMapping("/exception")
  ResponseEntity exception() {
    HttpServerTest.controller(EXCEPTION) {
      throw new Exception(EXCEPTION.body)
    }
  }

  @RequestMapping("/client-exception")
  @TraceSetting(allowedExceptions = IllegalArgumentException)
  ResponseEntity client_exception(String exceptionName) {
    throw (Throwable) Class.forName(exceptionName).newInstance(CLIENT_EXCEPTION.body)
  }

  @ExceptionHandler
  ResponseEntity handleException(Throwable throwable) {
    if (throwable instanceof IllegalArgumentException) {
      new ResponseEntity(throwable.message, HttpStatus.BAD_REQUEST)
    } else {
      new ResponseEntity(throwable.message, HttpStatus.INTERNAL_SERVER_ERROR)
    }
  }
}
