// Modified by SignalFx
package com.signalfx.tracing

import com.signalfx.tracing.api.TraceSetting
import spock.lang.*

class TraceSettingTest extends Specification {

  static class CustomException extends Exception {}

  static class OtherCustomException extends CustomException {}

  static class AnotherCustomException extends Exception {}

  static class YetAnotherCustomException extends Exception {}

  @TraceSetting(allowedExceptions = [CustomException])
  static class AnnotatedClass {
    @TraceSetting(allowedExceptions = [OtherCustomException])
    static void annotatedMethod() {}

    static void nonAnnotatedMethod() {}
  }

  static class NonAnnotatedClass {
    @TraceSetting(allowedExceptions = [OtherCustomException])
    static void annotatedMethod() {}

    static void nonAnnotatedMethod() {}
  }

  @TraceSetting(allowedExceptions = [AnotherCustomException])
  static class AnotherAnnotatedClass extends AnnotatedClass {
    @TraceSetting(allowedExceptions = [YetAnotherCustomException])
    static void annotatedMethod() {}
  }

  def "Non-annotated classes have empty allowedExceptions"() {
    setup:
    def allowedExceptions = TraceSetting.Annotated.getAllowedExceptions(NonAnnotatedClass)

    expect:
    allowedExceptions == []
  }

  def "Non-annotated methods have empty allowedExceptions"() {
    setup:
    def allowedExceptions = TraceSetting.Annotated.getAllowedExceptions(NonAnnotatedClass.getMethod('nonAnnotatedMethod'))

    expect:
    allowedExceptions == []
  }

  def "Non-annotated methods have class allowedExceptions"() {
    setup:
    def allowedExceptions = TraceSetting.Annotated.getAllowedExceptions(AnnotatedClass.getMethod('nonAnnotatedMethod'))

    expect:
    allowedExceptions == [CustomException]
  }

  def "Annotated classes have allowedExceptions"() {
    setup:
    def allowedExceptions = TraceSetting.Annotated.getAllowedExceptions(AnnotatedClass)

    expect:
    allowedExceptions == [CustomException]
  }

  def "Annotated methods have allowedExceptions"() {
    setup:
    def allowedExceptions = TraceSetting.Annotated.getAllowedExceptions(NonAnnotatedClass.getMethod('annotatedMethod'))

    expect:
    allowedExceptions == [OtherCustomException]
  }

  def "Annotated class and methods combine allowedExceptions"() {
    setup:
    println(AnnotatedClass.getProperties().toString())
    def allowedExceptions = TraceSetting.Annotated.getAllowedExceptions(AnnotatedClass.getMethod('annotatedMethod'))

    expect:
    allowedExceptions == [OtherCustomException, CustomException]
  }

  def "Annotated superclass and methods combine allowedExceptions"() {
    setup:
    println(AnnotatedClass.getProperties().toString())
    def allowedExceptions = TraceSetting.Annotated.getAllowedExceptions(AnotherAnnotatedClass.getMethod('annotatedMethod'))

    expect:
    allowedExceptions == [YetAnotherCustomException, AnotherCustomException, CustomException]
  }
}
