// Modified by SignalFx
package springdata


import com.couchbase.client.java.Bucket
import io.opentracing.tag.Tags
import org.springframework.data.couchbase.core.CouchbaseTemplate
import spock.lang.Shared
import util.AbstractCouchbaseTest

class CouchbaseSpringTemplateTest extends AbstractCouchbaseTest {

  @Shared
  List<CouchbaseTemplate> templates

  def setupSpec() {
    Bucket bucketCouchbase = couchbaseCluster.openBucket(bucketCouchbase.name(), bucketCouchbase.password())
    Bucket bucketMemcache = memcacheCluster.openBucket(bucketMemcache.name(), bucketMemcache.password())

    templates = [new CouchbaseTemplate(couchbaseManager.info(), bucketCouchbase),
                 new CouchbaseTemplate(memcacheManager.info(), bucketMemcache)]
  }


  def "test write/read #name"() {
    setup:
    def doc = new Doc()

    when:
    template.save(doc)

    then:
    template.findById("1", Doc) != null

    when:
    template.remove(doc)

    then:
    template.findById("1", Doc) == null

    and:
    assertTraces(4) {
      trace(0, 1) {
        span(0) {
          operationName "Bucket.upsert"
          errored false
          parent()
          tags {
            "$Tags.COMPONENT.key" "couchbase-client"
            "bucket" name
            defaultTags()
          }
        }
      }
      trace(1, 1) {
        span(0) {
          operationName "Bucket.get"
          errored false
          parent()
          tags {
            "$Tags.COMPONENT.key" "couchbase-client"
            "bucket" name
            defaultTags()
          }
        }
      }
      trace(2, 1) {
        span(0) {
          operationName "Bucket.remove"
          errored false
          parent()
          tags {
            "$Tags.COMPONENT.key" "couchbase-client"
            "bucket" name
            defaultTags()
          }
        }
      }
      trace(3, 1) {
        span(0) {
          operationName "Bucket.get"
          errored false
          parent()
          tags {
            "$Tags.COMPONENT.key" "couchbase-client"
            "bucket" name
            defaultTags()
          }
        }
      }
    }

    where:
    template << templates
    name = template.couchbaseBucket.name()
  }
}
