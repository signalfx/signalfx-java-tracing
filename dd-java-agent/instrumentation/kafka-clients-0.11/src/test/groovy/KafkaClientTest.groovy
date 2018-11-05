// Modified by SignalFx
import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.api.Config
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.junit.ClassRule
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.listener.KafkaMessageListenerContainer
import org.springframework.kafka.listener.MessageListener
import org.springframework.kafka.listener.config.ContainerProperties
import org.springframework.kafka.test.rule.KafkaEmbedded
import org.springframework.kafka.test.utils.ContainerTestUtils
import org.springframework.kafka.test.utils.KafkaTestUtils
import spock.lang.Shared

import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class KafkaClientTest extends AgentTestRunner {
  static final SHARED_TOPIC = "shared.topic"

  @Shared
  @ClassRule
  KafkaEmbedded embeddedKafka = new KafkaEmbedded(1, true, SHARED_TOPIC)

  def "test kafka produce and consume"() {
    setup:
    def senderProps = KafkaTestUtils.senderProps(embeddedKafka.getBrokersAsString())
    def producerFactory = new DefaultKafkaProducerFactory<String, String>(senderProps)
    def kafkaTemplate = new KafkaTemplate<String, String>(producerFactory)

    // set up the Kafka consumer properties
    def consumerProperties = KafkaTestUtils.consumerProps("sender", "false", embeddedKafka)

    // create a Kafka consumer factory
    def consumerFactory = new DefaultKafkaConsumerFactory<String, String>(consumerProperties)

    // set the topic that needs to be consumed
    ContainerProperties containerProperties = new ContainerProperties(SHARED_TOPIC)

    // create a Kafka MessageListenerContainer
    def container = new KafkaMessageListenerContainer<>(consumerFactory, containerProperties)

    // create a thread safe queue to store the received message
    def records = new LinkedBlockingQueue<ConsumerRecord<String, String>>()

    // setup a Kafka message listener
    container.setupMessageListener(new MessageListener<String, String>() {
      @Override
      void onMessage(ConsumerRecord<String, String> record) {
        TEST_WRITER.waitForTraces(1) // ensure consistent ordering of traces
        records.add(record)
      }
    })

    // start the container and underlying message listener
    container.start()

    // wait until the container has the required number of assigned partitions
    ContainerTestUtils.waitForAssignment(container, embeddedKafka.getPartitionsPerTopic())

    when:
    String greeting = "Hello Spring Kafka Sender!"
    kafkaTemplate.send(SHARED_TOPIC, greeting)


    then:
    // check that the message was received
    def received = records.poll(5, TimeUnit.SECONDS)
    received.value() == greeting
    received.key() == null

    TEST_WRITER.waitForTraces(1)
    TEST_WRITER.size() == 1

    def t1 = TEST_WRITER.get(0)
    t1.size() == 2

    def produceSpan = t1[1]
    def consumeSpan = t1[0]

    produceSpan.operationName == "Produce Topic $SHARED_TOPIC"
    produceSpan.parentId == 0

    def produceTags = produceSpan.tags()
    produceTags["component"] == "java-kafka"
    produceTags["span.kind"] == "producer"
    produceTags.size() == 2

    consumeSpan.operationName == "Consume Topic $SHARED_TOPIC"
    consumeSpan.parentId == produceSpan.spanId

    def consumeTags = consumeSpan.tags()
    consumeTags["component"] == "java-kafka"
    consumeTags["span.kind"] == "consumer"
    consumeTags["partition"] >= 0
    consumeTags["offset"] == 0
    consumeTags.size() == 4

    def headers = received.headers()
    headers.iterator().hasNext()
    new String(headers.headers("traceid").iterator().next().value()) == "$produceSpan.traceId"
    new String(headers.headers("spanid").iterator().next().value()) == "$produceSpan.spanId"


    cleanup:
    producerFactory.stop()
    container.stop()
  }

}
