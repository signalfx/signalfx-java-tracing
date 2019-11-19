// Modified by SignalFx
import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.agent.test.utils.PortUtils
import datadog.trace.api.DDSpanTypes
import io.lettuce.core.ClientOptions
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisCommandExecutionException
import io.lettuce.core.RedisConnectionException
import io.lettuce.core.api.StatefulConnection
import io.lettuce.core.api.sync.RedisCommands
import redis.embedded.RedisServer
import spock.lang.Shared

import java.util.concurrent.CompletionException

import static datadog.trace.instrumentation.lettuce.LettuceInstrumentationUtil.AGENT_CRASHING_COMMAND_PREFIX

class LettuceSyncClientTest extends AgentTestRunner {
  public static final String HOST = "127.0.0.1"
  public static final int DB_INDEX = 0
  // Disable autoreconnect so we do not get stray traces popping up on server shutdown
  public static final ClientOptions CLIENT_OPTIONS = ClientOptions.builder().autoReconnect(false).build()

  @Shared
  int port
  @Shared
  int incorrectPort
  @Shared
  String dbAddr
  @Shared
  String dbAddrNonExistent
  @Shared
  String dbUriNonExistent
  @Shared
  String embeddedDbUri

  @Shared
  RedisServer redisServer

  @Shared
  Map<String, String> testHashMap = [
    firstname: "John",
    lastname : "Doe",
    age      : "53"
  ]

  RedisClient redisClient
  StatefulConnection connection
  RedisCommands<String, ?> syncCommands

  def setupSpec() {
    port = PortUtils.randomOpenPort()
    incorrectPort = PortUtils.randomOpenPort()
    dbAddr = HOST + ":" + port + "/" + DB_INDEX
    dbAddrNonExistent = HOST + ":" + incorrectPort + "/" + DB_INDEX
    dbUriNonExistent = "redis://" + dbAddrNonExistent
    embeddedDbUri = "redis://" + dbAddr

    redisServer = RedisServer.builder()
    // bind to localhost to avoid firewall popup
      .setting("bind " + HOST)
    // set max memory to avoid problems in CI
      .setting("maxmemory 128M")
      .port(port).build()
  }

  def setup() {
    redisClient = RedisClient.create(embeddedDbUri)

    redisServer.start()
    connection = redisClient.connect()
    syncCommands = connection.sync()

    syncCommands.set("TESTKEY", "TESTVAL")
    syncCommands.hmset("TESTHM", testHashMap)

    // 2 sets + 1 connect trace
    TEST_WRITER.waitForTraces(3)
    TEST_WRITER.clear()
  }

  def cleanup() {
    connection.close()
    redisServer.stop()
  }

  def "connect"() {
    setup:
    RedisClient testConnectionClient = RedisClient.create(embeddedDbUri)
    testConnectionClient.setOptions(CLIENT_OPTIONS)

    when:
    StatefulConnection connection = testConnectionClient.connect()

    then:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          serviceName "redis"
          operationName "redis.query"
          spanType DDSpanTypes.REDIS
          resourceName "CONNECT:" + dbAddr
          errored false

          tags {
            defaultTags()
            "component" "redis"
            "db.instance" "$DB_INDEX"
            "db.redis.dbIndex" DB_INDEX
            "db.type" "redis"
            "peer.hostname" HOST
            "peer.port" port
            "span.kind" "client"
          }
        }
      }
    }

    cleanup:
    connection.close()
  }

  def "connect exception"() {
    setup:
    RedisClient testConnectionClient = RedisClient.create(dbUriNonExistent)
    testConnectionClient.setOptions(CLIENT_OPTIONS)

    when:
    testConnectionClient.connect()

    then:
    thrown RedisConnectionException
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          serviceName "redis"
          operationName "redis.query"
          spanType DDSpanTypes.REDIS
          resourceName "CONNECT:" + dbAddrNonExistent
          errored true

          tags {
            defaultTags()
            "component" "redis"
            "db.instance" "$DB_INDEX"
            "db.redis.dbIndex" DB_INDEX
            "db.type" "redis"
            errorTags CompletionException, String
            "peer.hostname" HOST
            "peer.port" incorrectPort
            "span.kind" "client"
          }
        }
      }
    }
  }

  def "set command"() {
    setup:
    String res = syncCommands.set("TESTSETKEY", "TESTSETVAL")

    expect:
    res == "OK"
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          serviceName "redis"
          operationName "SET"
          spanType DDSpanTypes.REDIS
          resourceName "SET"
          errored false

          tags {
            defaultTags()
            "component" "redis"
            "db.type" "redis"
            "db.statement" "SET: key<TESTSETKEY> value<TESTSETVAL>"
            "span.kind" "client"
          }
        }
      }
    }
  }

  def "get command"() {
    setup:
    String res = syncCommands.get("TESTKEY")

    expect:
    res == "TESTVAL"
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          serviceName "redis"
          operationName "GET"
          spanType DDSpanTypes.REDIS
          resourceName "GET"
          errored false

          tags {
            defaultTags()
            "component" "redis"
            "db.type" "redis"
            "db.statement" "GET: key<TESTKEY>"
            "span.kind" "client"
          }
        }
      }
    }
  }

  def "get non existent key command"() {
    setup:
    String res = syncCommands.get("NON_EXISTENT_KEY")

    expect:
    res == null
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          serviceName "redis"
          operationName "GET"
          spanType DDSpanTypes.REDIS
          resourceName "GET"
          errored false

          tags {
            defaultTags()
            "component" "redis"
            "db.type" "redis"
            "db.statement" "GET: key<NON_EXISTENT_KEY>"
            "span.kind" "client"
          }
        }
      }
    }
  }

  def "command with no arguments"() {
    setup:
    def keyRetrieved = syncCommands.randomkey()

    expect:
    keyRetrieved != null
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          serviceName "redis"
          operationName "RANDOMKEY"
          spanType DDSpanTypes.REDIS
          resourceName "RANDOMKEY"
          errored false

          tags {
            defaultTags()
            "component" "redis"
            "db.type" "redis"
            "db.statement" "RANDOMKEY"
            "span.kind" "client"
          }
        }
      }
    }
  }

  def "list command"() {
    setup:
    long res = syncCommands.lpush("TESTLIST", "TESTLIST ELEMENT")

    expect:
    res == 1
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          serviceName "redis"
          operationName "LPUSH"
          spanType DDSpanTypes.REDIS
          resourceName "LPUSH"
          errored false

          tags {
            defaultTags()
            "component" "redis"
            "db.type" "redis"
            "db.statement" "LPUSH: key<TESTLIST> value<TESTLIST ELEMENT>"
            "span.kind" "client"
          }
        }
      }
    }
  }

  def "hash set command"() {
    setup:
    def res = syncCommands.hmset("user", testHashMap)

    expect:
    res == "OK"
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          serviceName "redis"
          operationName "HMSET"
          spanType DDSpanTypes.REDIS
          resourceName "HMSET"
          errored false

          tags {
            defaultTags()
            "component" "redis"
            "db.type" "redis"
            "db.statement" "HMSET: key<user> key<firstname> value<John> key<lastname> value<Doe> key<age> value<53>"
            "span.kind" "client"
          }
        }
      }
    }
  }

  def "hash getall command"() {
    setup:
    Map<String, String> res = syncCommands.hgetall("TESTHM")

    expect:
    res == testHashMap
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          serviceName "redis"
          operationName "HGETALL"
          spanType DDSpanTypes.REDIS
          resourceName "HGETALL"
          errored false

          tags {
            defaultTags()
            "component" "redis"
            "db.type" "redis"
            "db.statement" "HGETALL: key<TESTHM>"
            "span.kind" "client"
          }
        }
      }
    }
  }

  def "debug segfault command (returns void) with no argument should produce span"() {
    setup:
    syncCommands.debugSegfault()

    expect:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          serviceName "redis"
          operationName "DEBUG"
          spanType DDSpanTypes.REDIS
          resourceName AGENT_CRASHING_COMMAND_PREFIX + "DEBUG"
          errored false

          tags {
            defaultTags()
            "component" "redis"
            "db.type" "redis"
            "db.statement" "DEBUG: SEGFAULT"
            "span.kind" "client"
          }
        }
      }
    }
  }

  def "auth command arguments shouldn't be captured"() {
    when:
    syncCommands.auth("myPassword")

    then:
    thrown(RedisCommandExecutionException)

    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          serviceName "redis"
          operationName "AUTH"
          spanType DDSpanTypes.REDIS
          resourceName "AUTH"
          errored true

          tags {
            defaultTags()
            "component" "redis"
            "db.type" "redis"
            "db.statement" "AUTH"
            "span.kind" "client"
            errorTags RedisCommandExecutionException, "ERR Client sent AUTH, but no password is set"
          }
        }
      }
    }
  }

  def "shutdown command (returns void) should produce a span"() {
    setup:
    syncCommands.shutdown(false)

    expect:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          serviceName "redis"
          operationName "SHUTDOWN"
          spanType DDSpanTypes.REDIS
          resourceName "SHUTDOWN"
          errored false

          tags {
            defaultTags()
            "component" "redis"
            "db.type" "redis"
            "db.statement" "SHUTDOWN: NOSAVE"
            "span.kind" "client"
          }
        }
      }
    }
  }
}
