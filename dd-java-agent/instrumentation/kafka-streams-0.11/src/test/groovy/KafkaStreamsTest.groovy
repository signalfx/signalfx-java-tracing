// Modified by SignalFx
import datadog.trace.agent.test.AgentTestRunner
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.StreamsConfig
import org.apache.kafka.streams.kstream.KStream
import org.apache.kafka.streams.kstream.KStreamBuilder
import org.apache.kafka.streams.kstream.ValueMapper
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

class KafkaStreamsTest extends AgentTestRunner {
  static final STREAM_PENDING = "test.pending"
  static final STREAM_PROCESSED = "test.processed"

  @Shared
  @ClassRule
  KafkaEmbedded embeddedKafka = new KafkaEmbedded(1, true, STREAM_PENDING, STREAM_PROCESSED)

  def "test kafka produce and consume with streams in-between"() {
    setup:
    def config = new Properties()
    def senderProps = KafkaTestUtils.senderProps(embeddedKafka.getBrokersAsString())
    config.putAll(senderProps)
    config.put(StreamsConfig.APPLICATION_ID_CONFIG, "test-application")
    config.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName())
    config.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName())

    // CONFIGURE CONSUMER
    def consumerFactory = new DefaultKafkaConsumerFactory<String, String>(KafkaTestUtils.consumerProps("sender", "false", embeddedKafka))
    def consumerContainer = new KafkaMessageListenerContainer<>(consumerFactory, new ContainerProperties(STREAM_PROCESSED))

    // create a thread safe queue to store the processed message
    def records = new LinkedBlockingQueue<ConsumerRecord<String, String>>()

    // setup a Kafka message listener
    consumerContainer.setupMessageListener(new MessageListener<String, String>() {
      @Override
      void onMessage(ConsumerRecord<String, String> record) {
        // ensure consistent ordering of traces
        // this is the last processing step so we should see 2 traces here
        TEST_WRITER.waitForTraces(1)
        getTestTracer().activeSpan().setTag("testing", 123)
        records.add(record)
      }
    })

    // start the container and underlying message listener
    consumerContainer.start()

    // wait until the container has the required number of assigned partitions
    ContainerTestUtils.waitForAssignment(consumerContainer, embeddedKafka.getPartitionsPerTopic())

    // CONFIGURE PROCESSOR
    final KStreamBuilder builder = new KStreamBuilder()
    KStream<String, String> textLines = builder.stream(STREAM_PENDING)
    textLines
      .mapValues(new ValueMapper<String, String>() {
      @Override
      String apply(String textLine) {
        TEST_WRITER.waitForTraces(1) // ensure consistent ordering of traces
        getTestTracer().activeSpan().setTag("asdf", "testing")
        return textLine.toLowerCase()
      }
    })
      .to(Serdes.String(), Serdes.String(), STREAM_PROCESSED)
    KafkaStreams streams = new KafkaStreams(builder, config)
    streams.start()

    // CONFIGURE PRODUCER
    def producerFactory = new DefaultKafkaProducerFactory<String, String>(senderProps)
    def kafkaTemplate = new KafkaTemplate<String, String>(producerFactory)

    when:
    String greeting = "TESTING TESTING 123!"
    kafkaTemplate.send(STREAM_PENDING, greeting)

    then:
    // check that the message was received
    def received = records.poll(10, TimeUnit.SECONDS)
    received.value() == greeting.toLowerCase()
    received.key() == null

    TEST_WRITER.waitForTraces(1)
    TEST_WRITER.size() == 1
    def trace = TEST_WRITER.get(0)
    trace.size() == 4

    def consumeProcessed = trace[0]
    def produceProcessed = trace[1]
    def consumePending = trace[2]
    def producePending = trace[3]

    producePending.operationName == "produce.$STREAM_PENDING"
    producePending.parentId == 0

    def producePendingTags = producePending.tags()
    producePendingTags["component"] == "java-kafka"
    producePendingTags["span.kind"] == "producer"
    producePendingTags.size() == 2

    produceProcessed.operationName == "produce.$STREAM_PROCESSED"

    def produceProcessedTags = produceProcessed.tags()
    produceProcessedTags["component"] == "java-kafka"
    produceProcessedTags["span.kind"] == "producer"
    produceProcessedTags.size() == 2

    produceProcessed.parentId == consumePending.spanId

    consumePending.operationName == "consume.$STREAM_PENDING"
    consumePending.parentId == producePending.spanId

    def consumePendingTags = consumePending.tags()
    consumePendingTags["component"] == "java-kafka"
    consumePendingTags["span.kind"] == "consumer"
    consumePendingTags["partition"] >= 0
    consumePendingTags["offset"] == 0
    consumePendingTags["asdf"] == "testing"
    consumePendingTags.size() == 5

    consumeProcessed.operationName == "consume.$STREAM_PROCESSED"
    consumeProcessed.parentId == produceProcessed.spanId

    def consumeProcessedTags = consumeProcessed.tags()
    consumeProcessedTags["component"] == "java-kafka"
    consumeProcessedTags["span.kind"] == "consumer"
    consumeProcessedTags["partition"] >= 0
    consumeProcessedTags["offset"] == 0
    consumeProcessedTags["testing"] == 123
    consumeProcessedTags.size() == 5

    def headers = received.headers()
    headers.iterator().hasNext()
    new String(headers.headers("traceid").iterator().next().value()) == "$produceProcessed.traceId"
    new String(headers.headers("spanid").iterator().next().value()) == "$produceProcessed.spanId"


    cleanup:
    producerFactory?.stop()
    streams?.close()
    consumerContainer?.stop()
  }
}
