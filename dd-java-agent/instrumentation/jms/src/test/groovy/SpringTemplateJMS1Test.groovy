import datadog.trace.agent.test.AgentTestRunner
import org.apache.activemq.ActiveMQConnectionFactory
import org.apache.activemq.ActiveMQMessageConsumer
import org.apache.activemq.junit.EmbeddedActiveMQBroker
import org.springframework.jms.core.JmsTemplate
import spock.lang.Ignore
import spock.lang.Shared

import javax.jms.Connection
import javax.jms.Session
import javax.jms.TextMessage
import java.util.concurrent.TimeUnit

import static JMS1Test.consumerSpan
import static JMS1Test.producerSpan

@Ignore()
class SpringTemplateJMS1Test extends AgentTestRunner {
  @Shared
  EmbeddedActiveMQBroker broker = new EmbeddedActiveMQBroker()
  @Shared
  String messageText = "a message"
  @Shared
  JmsTemplate template
  @Shared
  Session session

  def setupSpec() {
    broker.start()
    final ActiveMQConnectionFactory connectionFactory = broker.createConnectionFactory()
    final Connection connection = connectionFactory.createConnection()
    connection.start()
    session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)

    template = new JmsTemplate(connectionFactory)
    template.receiveTimeout = TimeUnit.SECONDS.toMillis(10)
  }

  def cleanupSpec() {
    broker.stop()
  }

  def "sending a message to #jmsResourceName generates spans"() {
    setup:
    template.convertAndSend(destination, messageText)
    TextMessage receivedMessage = template.receive(destination)

    expect:
    receivedMessage.text == messageText
    assertTraces(1) {
      trace(0, 2) {
        producerSpan(it, 1, jmsResourceName)
        consumerSpan(it, 0, jmsResourceName, false, ActiveMQMessageConsumer, span(1))
      }
    }

    where:
    destination                            | jmsResourceName
    session.createQueue("someSpringQueue") | "Queue someSpringQueue"
  }

  def "send and receive message generates spans"() {
    setup:
    Thread.start {
      TextMessage msg = template.receive(destination)
      assert msg.text == messageText

      // Make sure that first pair of send/receive traces has landed to simplify assertions
      TEST_WRITER.waitForTraces(2)

      template.send(msg.getJMSReplyTo()) {
        session -> template.getMessageConverter().toMessage("responded!", session)
      }
    }
    TextMessage receivedMessage = template.sendAndReceive(destination) {
      session -> template.getMessageConverter().toMessage(messageText, session)
    }

    TEST_WRITER.waitForTraces(3)
    // Manually reorder if reported in the wrong order.
//    if (TEST_WRITER[1][0].operationName == "jms.produce") {
//      def producerTrace = TEST_WRITER[1]
//      TEST_WRITER[1] = TEST_WRITER[0]
//      TEST_WRITER[0] = producerTrace
//    }
//    if (TEST_WRITER[3][0].operationName == "jms.produce") {
//      def producerTrace = TEST_WRITER[3]
//      TEST_WRITER[3] = TEST_WRITER[2]
//      TEST_WRITER[2] = producerTrace
//    }

    expect:
    receivedMessage.text == "responded!"
    assertTraces(1) {
      trace(0, 4) {
        producerSpan(it, 0, jmsResourceName)
        consumerSpan(it, 1, jmsResourceName, false, ActiveMQMessageConsumer)
        producerSpan(it, 2, "Temporary Queue") // receive doesn't propagate the trace, so this is a root
        consumerSpan(it, 3, "Temporary Queue", false, ActiveMQMessageConsumer, span(2))
      }
    }

    where:
    destination                            | jmsResourceName
    session.createQueue("someSpringQueue") | "Queue someSpringQueue"
  }

}
