package datadog.trace.agent.test.asserts

import datadog.opentracing.DDSpan
import datadog.trace.api.Config
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType

class TagsAssert {
  private final String spanParentId
  private final Map<String, Object> tags
  private final Set<String> assertedTags = new TreeSet<>()

  private TagsAssert(DDSpan span) {
    this.spanParentId = span.parentId
    this.tags = span.tags
  }

  static void assertTags(DDSpan span,
                         @ClosureParams(value = SimpleType, options = ['datadog.trace.agent.test.asserts.TagsAssert'])
                         @DelegatesTo(value = TagsAssert, strategy = Closure.DELEGATE_FIRST) Closure spec) {
    def asserter = new TagsAssert(span)
    def clone = (Closure) spec.clone()
    clone.delegate = asserter
    clone.resolveStrategy = Closure.DELEGATE_FIRST
    clone(asserter)
    asserter.assertTagsAllVerified()
  }

  /**
   * @param distributedRootSpan set to true if current span has a parent span but still considered 'root' for current service
   */
  def defaultTags(boolean distributedRootSpan = false) {
    assertedTags.add("thread.name")
    assertedTags.add("thread.id")
    assertedTags.add(Config.RUNTIME_ID_TAG)
    assertedTags.add(Config.LANGUAGE_TAG_KEY)

    assert tags["thread.name"] != null
    assert tags["thread.id"] != null
    if ("0" == spanParentId || distributedRootSpan) {
      assert tags[Config.RUNTIME_ID_TAG] == Config.get().runtimeId
      assert tags[Config.LANGUAGE_TAG_KEY] == Config.LANGUAGE_TAG_VALUE
    } else {
      assert tags[Config.RUNTIME_ID_TAG] == null
      assert tags[Config.LANGUAGE_TAG_KEY] == null
    }
  }

  def errorTags(Class<Throwable> errorType) {
    errorTags(errorType, null)
  }

  def errorTags(Class<Throwable> errorType, Object message) {
    methodMissing("error", [true].toArray())
    methodMissing("error.type", [errorType.name].toArray())
    methodMissing("error.stack", [String].toArray())

    if (message != null) {
      methodMissing("error.msg", [message].toArray())
    }
  }

  def tag(String name, value) {
    if (value == null) {
      return
    }
    assertedTags.add(name)
    if (value instanceof Class) {
      assert ((Class) value).isInstance(tags[name])
    } else if (value instanceof Closure) {
      assert ((Closure) value).call(tags[name])
    } else {
      assert tags[name] == value
    }
  }

  def tag(String name) {
    return tags[name]
  }

  def methodMissing(String name, args) {
    if (args.length == 0) {
      throw new IllegalArgumentException(args.toString())
    }
    tag(name, args[0])
  }

  void assertTagsAllVerified() {
    def set = new TreeMap<>(tags).keySet()
    set.removeAll(assertedTags)
    // The primary goal is to ensure the set is empty.
    // tags and assertedTags are included via an "always true" comparison
    // so they provide better context in the error message.
    assert tags.entrySet() != assertedTags && set.isEmpty()
  }
}
