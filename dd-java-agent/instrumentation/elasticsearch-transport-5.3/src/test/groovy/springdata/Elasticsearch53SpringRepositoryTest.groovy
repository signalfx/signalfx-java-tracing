package springdata

import com.anotherchrisberry.spock.extensions.retry.RetryOnFailure
import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.api.DDSpanTypes
import datadog.trace.api.DDTags
import io.opentracing.tag.Tags
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import spock.lang.Shared

import static datadog.trace.agent.test.utils.TraceUtils.runUnderTrace

@RetryOnFailure(times = 3, delaySeconds = 1)
class Elasticsearch53SpringRepositoryTest extends AgentTestRunner {
  @Shared
  ApplicationContext applicationContext = new AnnotationConfigApplicationContext(Config)

  @Shared
  DocRepository repo = applicationContext.getBean(DocRepository)

  def setup() {
    TEST_WRITER.clear()
    runUnderTrace("delete") {
      repo.deleteAll()
    }
    TEST_WRITER.waitForTraces(1)
    TEST_WRITER.clear()
  }

  def "test empty repo"() {
    when:
    def result = repo.findAll()

    then:
    !result.iterator().hasNext()

    and:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          serviceName "elasticsearch"
          resourceName "SearchAction"
          operationName "elasticsearch.query"
          spanType DDSpanTypes.ELASTICSEARCH
          errored false
          tags {
            "$Tags.COMPONENT.key" "elasticsearch-java"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "$DDTags.SPAN_TYPE" DDSpanTypes.ELASTICSEARCH
            "elasticsearch.action" "SearchAction"
            "elasticsearch.request" "SearchRequest"
            "elasticsearch.request.indices" indexName
            "elasticsearch.request.search.types" "doc"
            defaultTags()
          }
        }
      }
    }

    where:
    indexName = "test-index"
  }

  def "test CRUD"() {
    when:
    def doc = new Doc()

    then:
    repo.index(doc) == doc

    and:
    assertTraces(2) {
      trace(0, 1) {
        span(0) {
          resourceName "IndexAction"
          operationName "elasticsearch.query"
          spanType DDSpanTypes.ELASTICSEARCH
          tags {
            "$Tags.COMPONENT.key" "elasticsearch-java"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "$DDTags.SPAN_TYPE" DDSpanTypes.ELASTICSEARCH
            "elasticsearch.action" "IndexAction"
            "elasticsearch.request" "IndexRequest"
            "elasticsearch.request.indices" indexName
            "elasticsearch.request.write.type" "doc"
            "elasticsearch.request.write.version"(-3)
            "elasticsearch.response.status" 201
            "elasticsearch.shard.replication.failed" 0
            "elasticsearch.shard.replication.successful" 1
            "elasticsearch.shard.replication.total" 2
            defaultTags()
          }
        }
      }
      trace(1, 1) {
        span(0) {
          resourceName "RefreshAction"
          operationName "elasticsearch.query"
          spanType DDSpanTypes.ELASTICSEARCH
          tags {
            "$Tags.COMPONENT.key" "elasticsearch-java"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "$DDTags.SPAN_TYPE" DDSpanTypes.ELASTICSEARCH
            "elasticsearch.action" "RefreshAction"
            "elasticsearch.request" "RefreshRequest"
            "elasticsearch.request.indices" indexName
            "elasticsearch.shard.broadcast.failed" 0
            "elasticsearch.shard.broadcast.successful" 5
            "elasticsearch.shard.broadcast.total" 10
            defaultTags()
          }
        }
      }
    }
    TEST_WRITER.clear()

    and:
    repo.findById("1").get() == doc

    and:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          serviceName "elasticsearch"
          resourceName "GetAction"
          operationName "elasticsearch.query"
          spanType DDSpanTypes.ELASTICSEARCH
          tags {
            "$Tags.COMPONENT.key" "elasticsearch-java"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "$DDTags.SPAN_TYPE" DDSpanTypes.ELASTICSEARCH
            "elasticsearch.action" "GetAction"
            "elasticsearch.request" "GetRequest"
            "elasticsearch.request.indices" indexName
            "elasticsearch.type" "doc"
            "elasticsearch.id" "1"
            "elasticsearch.version" Number
            defaultTags()
          }
        }
      }
    }
    TEST_WRITER.clear()

    when:
    doc.data = "other data"

    then:
    repo.index(doc) == doc
    repo.findById("1").get() == doc

    and:
    assertTraces(3) {
      trace(0, 1) {
        span(0) {
          resourceName "IndexAction"
          operationName "elasticsearch.query"
          spanType DDSpanTypes.ELASTICSEARCH
          tags {
            "$Tags.COMPONENT.key" "elasticsearch-java"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "$DDTags.SPAN_TYPE" DDSpanTypes.ELASTICSEARCH
            "elasticsearch.action" "IndexAction"
            "elasticsearch.request" "IndexRequest"
            "elasticsearch.request.indices" indexName
            "elasticsearch.request.write.type" "doc"
            "elasticsearch.request.write.version"(-3)
            "elasticsearch.response.status" 200
            "elasticsearch.shard.replication.failed" 0
            "elasticsearch.shard.replication.successful" 1
            "elasticsearch.shard.replication.total" 2
            defaultTags()
          }
        }
      }
      trace(1, 1) {
        span(0) {
          resourceName "RefreshAction"
          operationName "elasticsearch.query"
          spanType DDSpanTypes.ELASTICSEARCH
          tags {
            "$Tags.COMPONENT.key" "elasticsearch-java"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "$DDTags.SPAN_TYPE" DDSpanTypes.ELASTICSEARCH
            "elasticsearch.action" "RefreshAction"
            "elasticsearch.request" "RefreshRequest"
            "elasticsearch.request.indices" indexName
            "elasticsearch.shard.broadcast.failed" 0
            "elasticsearch.shard.broadcast.successful" 5
            "elasticsearch.shard.broadcast.total" 10
            defaultTags()
          }
        }
      }
      trace(2, 1) {
        span(0) {
          serviceName "elasticsearch"
          resourceName "GetAction"
          operationName "elasticsearch.query"
          spanType DDSpanTypes.ELASTICSEARCH
          tags {
            "$Tags.COMPONENT.key" "elasticsearch-java"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "$DDTags.SPAN_TYPE" DDSpanTypes.ELASTICSEARCH
            "elasticsearch.action" "GetAction"
            "elasticsearch.request" "GetRequest"
            "elasticsearch.request.indices" indexName
            "elasticsearch.type" "doc"
            "elasticsearch.id" "1"
            "elasticsearch.version" Number
            defaultTags()
          }
        }
      }
    }
    TEST_WRITER.clear()

    when:
    repo.deleteById("1")

    then:
    !repo.findAll().iterator().hasNext()

    and:
    assertTraces(3) {
      trace(0, 1) {
        span(0) {
          resourceName "DeleteAction"
          operationName "elasticsearch.query"
          spanType DDSpanTypes.ELASTICSEARCH
          tags {
            "$Tags.COMPONENT.key" "elasticsearch-java"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "$DDTags.SPAN_TYPE" DDSpanTypes.ELASTICSEARCH
            "elasticsearch.action" "DeleteAction"
            "elasticsearch.request" "DeleteRequest"
            "elasticsearch.request.indices" indexName
            "elasticsearch.request.write.type" "doc"
            "elasticsearch.request.write.version"(-3)
            "elasticsearch.shard.replication.failed" 0
            "elasticsearch.shard.replication.successful" 1
            "elasticsearch.shard.replication.total" 2
            defaultTags()
          }
        }
      }
      trace(1, 1) {
        span(0) {
          resourceName "RefreshAction"
          operationName "elasticsearch.query"
          spanType DDSpanTypes.ELASTICSEARCH
          tags {
            "$Tags.COMPONENT.key" "elasticsearch-java"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "$DDTags.SPAN_TYPE" DDSpanTypes.ELASTICSEARCH
            "elasticsearch.action" "RefreshAction"
            "elasticsearch.request" "RefreshRequest"
            "elasticsearch.request.indices" indexName
            "elasticsearch.shard.broadcast.failed" 0
            "elasticsearch.shard.broadcast.successful" 5
            "elasticsearch.shard.broadcast.total" 10
            defaultTags()
          }
        }
      }
      trace(2, 1) {
        span(0) {
          serviceName "elasticsearch"
          resourceName "SearchAction"
          operationName "elasticsearch.query"
          spanType DDSpanTypes.ELASTICSEARCH
          tags {
            "$Tags.COMPONENT.key" "elasticsearch-java"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "$DDTags.SPAN_TYPE" DDSpanTypes.ELASTICSEARCH
            "elasticsearch.action" "SearchAction"
            "elasticsearch.request" "SearchRequest"
            "elasticsearch.request.indices" indexName
            "elasticsearch.request.search.types" "doc"
            defaultTags()
          }
        }
      }
    }

    where:
    indexName = "test-index"
  }
}
