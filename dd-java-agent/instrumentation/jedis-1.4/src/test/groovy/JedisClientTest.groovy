// Modified by SignalFx
import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.api.DDSpanTypes
import io.opentracing.tag.Tags
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
  }

  def cleanupSpec() {
    redisServer.stop()
//    jedis.close()  // not available in the early version we're using.
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
          operationName "redis.query"
          resourceName "redis.query"
          spanType DDSpanTypes.REDIS
          tags {
            "$Tags.DB_STATEMENT.key" "SET"
            "$Tags.COMPONENT.key" "redis"
            "$Tags.DB_TYPE.key" "redis"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
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
          operationName "redis.query"
          resourceName "redis.query"
          spanType DDSpanTypes.REDIS
          tags {
            "$Tags.DB_STATEMENT.key" "SET"
            "$Tags.COMPONENT.key" "redis"
            "$Tags.DB_TYPE.key" "redis"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            defaultTags()
          }
        }
      }
      trace(1, 1) {
        span(0) {
          serviceName "unnamed-java-app"
          operationName "redis.query"
          resourceName "redis.query"
          spanType DDSpanTypes.REDIS
          tags {
            "$Tags.DB_STATEMENT.key" "GET"
            "$Tags.COMPONENT.key" "redis"
            "$Tags.DB_TYPE.key" "redis"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
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
          operationName "redis.query"
          resourceName "redis.query"
          spanType DDSpanTypes.REDIS
          tags {
            "$Tags.DB_STATEMENT.key" "SET"
            "$Tags.COMPONENT.key" "redis"
            "$Tags.DB_TYPE.key" "redis"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            defaultTags()
          }
        }
      }
      trace(1, 1) {
        span(0) {
          serviceName "unnamed-java-app"
          operationName "redis.query"
          resourceName "redis.query"
          spanType DDSpanTypes.REDIS
          tags {
            "$Tags.DB_STATEMENT.key" "RANDOMKEY"
            "$Tags.COMPONENT.key" "redis"
            "$Tags.DB_TYPE.key" "redis"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            defaultTags()
          }
        }
      }
    }
  }
}
