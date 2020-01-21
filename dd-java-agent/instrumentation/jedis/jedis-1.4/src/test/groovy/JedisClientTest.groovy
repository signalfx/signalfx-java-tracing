// Modified by SignalFx
import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.api.Config
import datadog.trace.api.DDSpanTypes
import datadog.trace.instrumentation.api.Tags
import redis.clients.jedis.Jedis
import redis.embedded.RedisServer
import spock.lang.Shared

class JedisClientTest extends AgentTestRunner {

  public static final int PORT = 6399

  @Shared
  RedisServer redisServer = RedisServer.builder()
  // bind to localhost to avoid firewall popup
    .setting("bind 127.0.0.1")
  // set max memory to avoid problems in CI
    .setting("maxmemory 128M")
    .port(PORT).build()
  @Shared
  Jedis jedis = new Jedis("localhost", PORT)

  def setupSpec() {
    println "Using redis: $redisServer.args"
    redisServer.start()

    // This setting should have no effect since decorator returns null for the instance.
    System.setProperty(Config.PREFIX + Config.DB_CLIENT_HOST_SPLIT_BY_INSTANCE, "true")
  }

  def cleanupSpec() {
    redisServer.stop()
//    jedis.close()  // not available in the early version we're using.

    System.clearProperty(Config.PREFIX + Config.DB_CLIENT_HOST_SPLIT_BY_INSTANCE)
  }

  def setup() {
    jedis.flushAll()
    TEST_WRITER.start()
  }

  def "set command"() {
    when:
    jedis.set("foo", "bar")

    then:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          serviceName "unnamed-java-app"
          operationName "redis.SET"
          resourceName "redis.SET"
          spanType DDSpanTypes.REDIS
          tags {
            "$Tags.DB_STATEMENT" "SET: foo bar"
            "$Tags.COMPONENT" "redis"
            "$Tags.DB_TYPE" "redis"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            defaultTags()
          }
        }
      }
    }
  }

  def "get command"() {
    when:
    jedis.set("foo", "bar")
    def value = jedis.get("foo")

    then:
    value == "bar"

    assertTraces(2) {
      trace(0, 1) {
        span(0) {
          serviceName "unnamed-java-app"
          operationName "redis.SET"
          resourceName "redis.SET"
          spanType DDSpanTypes.REDIS
          tags {
            "$Tags.DB_STATEMENT" "SET: foo bar"
            "$Tags.COMPONENT" "redis"
            "$Tags.DB_TYPE" "redis"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            defaultTags()
          }
        }
      }
      trace(1, 1) {
        span(0) {
          serviceName "unnamed-java-app"
          operationName "redis.GET"
          resourceName "redis.GET"
          spanType DDSpanTypes.REDIS
          tags {
            "$Tags.DB_STATEMENT" "GET: foo"
            "$Tags.COMPONENT" "redis"
            "$Tags.DB_TYPE" "redis"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            defaultTags()
          }
        }
      }
    }
  }

  def "command with no arguments"() {
    when:
    jedis.set("foo", "bar")
    def value = jedis.randomKey()

    then:
    value == "foo"

    assertTraces(2) {
      trace(0, 1) {
        span(0) {
          serviceName "unnamed-java-app"
          operationName "redis.SET"
          resourceName "redis.SET"
          spanType DDSpanTypes.REDIS
          tags {
            "$Tags.DB_STATEMENT" "SET: foo bar"
            "$Tags.COMPONENT" "redis"
            "$Tags.DB_TYPE" "redis"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            defaultTags()
          }
        }
      }
      trace(1, 1) {
        span(0) {
          serviceName "unnamed-java-app"
          operationName "redis.RANDOMKEY"
          resourceName "redis.RANDOMKEY"
          spanType DDSpanTypes.REDIS
          tags {
            "$Tags.DB_STATEMENT" "RANDOMKEY"
            "$Tags.COMPONENT" "redis"
            "$Tags.DB_TYPE" "redis"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            defaultTags()
          }
        }
      }
    }
  }

  def "auth command doesn't leak password"() {
    when:
    jedis.auth("myPassword")

    then:
    thrown(RuntimeException)

    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          serviceName "unnamed-java-app"
          operationName "redis.AUTH"
          resourceName "redis.AUTH"
          spanType DDSpanTypes.REDIS
          tags {
            "$Tags.DB_STATEMENT" "AUTH"
            "$Tags.COMPONENT" "redis"
            "$Tags.DB_TYPE" "redis"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            defaultTags()
          }
        }
      }
    }
  }
}
